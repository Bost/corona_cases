(ns corona.messages
  (:require [clojure.core.memoize :as memo]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.string :as s]
            [corona.api.expdev07 :as data]
            [corona.api.v1 :as v1]
            [corona.common :as com]
            [corona.core :as c]
            [corona.countries :as cr]
            [corona.defs :as d]
            [corona.lang :refer :all]
            [corona.plot :as p]
            [morse.api :as morse]
            [utils.core :refer :all])
  (:import java.awt.image.BufferedImage
           java.io.ByteArrayOutputStream
           javax.imageio.ImageIO))

(defn bot-name-formatted []
  (s/replace c/bot-name #"_" "\\\\_"))

(def options {:parse_mode "Markdown" :disable_web_page_preview true})

(defn pred-fn [country-code] (data/pred-fn country-code))

(defmacro static-fn [f] `(fn [x#] (~f x#)))

(defn round
  "TODO consider using :pre / :hooks. See `wrap-fn-pre-post-hooks`"
  ([x] (round :normal x))
  ([mode x]
   (let [f (case mode
             :high    (static-fn Math/ceil)
             :low     (static-fn Math/floor)
             :normal  (static-fn Math/round)
             (throw (Exception.
                     "ERROR: round [:high|:low|:normal] <VALUE>")))]
     (long (f x)))))

(defn percentage
  "See https://groups.google.com/forum/#!topic/clojure/nH-E5uD8CY4"
  ([place total-count] (percentage :normal place total-count))
  ([mode place total-count]
   (round mode (/ (* place 100.0) total-count))))

(defn per-1e5
  "See https://groups.google.com/forum/#!topic/clojure/nH-E5uD8CY4"
  ([place total-count] (per-1e5 :normal place total-count))
  ([mode place total-count]
   (round mode (/ (* place 1e5) total-count))))

;; TODO ask about unicode whitespace char
;; Unicode whitespace ;;;;;​ ;;;;;
;; Normal whitespace  ;;;;; ;;;;;

(defn round-precision [value precision]
  (let [multiplier (Math/pow 10.0 precision)]
    (/ (Math/round (* value multiplier)) multiplier)))

(defn round-div-precision [dividend divisor precision]
  (round-precision (/ (float dividend) divisor) precision))

(def max-diff-order-of-magnitude 6)

(defn plus-minus
  "Display \"+0\" when n is zero"
  [n]
  (c/left-pad
   (if (zero? n)
     "+0"
     (str
      #_(cond (pos? n) "↑"
              (neg? n) "↓"
              :else "")
      (cond (pos? n) "+"
            :else "")
      n))
   " " max-diff-order-of-magnitude))

(defn fmt-to-cols-narrower
  "Info-message numbers of aligned to columns for better readability"
  [{:keys [s n total diff desc calc-rate show-n calc-diff]
    :or {show-n true calc-diff true}}]
  (format "<code>%s %s</code> %s"
          (c/right-pad s " " 8) ; stays constant
          ;; count of digits to display. Increase it when the number of cases
          ;; increases by an order of magnitude
          (c/left-pad (if show-n n "") " " 10)
          desc))

(defn fmt-to-cols
  "Info-message numbers of aligned to columns for better readability"
  [{:keys [s n total diff desc calc-rate show-n calc-diff]
    :or {show-n true calc-diff true}}]
  (format "<code>%s %s %s %s</code> %s"
          (c/right-pad s " " 9) ; stays constant
          ;; count of digits to display. Increase it when the number of cases
          ;; increases by an order of magnitude
          (c/left-pad (if show-n n "") " " 9)
          (c/left-pad (if calc-rate (str (percentage n total) "%") " ")
                      " " 4)
          (if calc-diff
            (plus-minus diff)
            (c/left-pad "" " " max-diff-order-of-magnitude))
          desc))

(def ref-mortality-rate
  "https://www.worldometers.info/coronavirus/coronavirus-death-rate/")

(def ref-rober-koch
  "https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/nCoV.html")

(def ref-3blue1brown-exp-growth
  "https://youtu.be/Kas0tIxDvrg")

(def ref-age-sex
  "https://www.worldometers.info/coronavirus/coronavirus-age-sex-demographics/")

(defn link [name url {:keys [parse_mode] :as prm}]
  (if (= parse_mode "HTML")
    (format "<a href=\"%s\">%s</a>" url name)
    (format "[%s](%s)" name url)))

(defn footer
  "Listing commands in the message footer correspond to the columns in the listing.
  See also `list-countries`, `bot-father-edit-cmds`."
  [{:keys [parse_mode]}]
  (let [spacer "   "]
    (str
     ;; "Try" spacer
     (->> [s-world s-about]
          (map com/encode-cmd)
          (map (fn [cmd] (com/encode-pseudo-cmd cmd parse_mode)))
          (s/join spacer))
     spacer "listings:  "
     (->> (map s-list-sorted-by com/listing-ird-cases)
          (map com/encode-cmd)
          (s/join spacer)))))

(defn toByteArrayAutoClosable
  "Thanks to https://stackoverflow.com/a/15414490"
  [^BufferedImage image]
  (with-open [out (new ByteArrayOutputStream)]
    (ImageIO/write image "png" out)
    (.toByteArray out)))

(defn buttons [prm]
  {:reply_markup
   (json/write-str
    {:inline_keyboard
     [(reduce
       into
       (mapv (fn [type]
               (mapv (fn [case-kw]
                       {:text (str (case-kw s-buttons)
                                   (type s-type))
                        :callback_data (pr-str (assoc prm
                                                      :case case-kw
                                                      :type type))})
                     com/all-crdi-cases))
             [:sum :abs]))]})})

(defn worldwide? [country-code]
  (in? [d/worldwide-2-country-code d/worldwide-3-country-code d/worldwide]
       country-code))

(defn callback-handler-fn [{:keys [data] :as prm}]
  (let [{country-code :cc
         chat-id :chat-id
         type :type
         case :case} (edn/read-string data)]
    (when (worldwide? country-code)
      (morse/send-photo
       c/token chat-id
       (buttons {:chat-id chat-id :cc country-code})
       (toByteArrayAutoClosable
        (let [plot-fn (if (= type :sum) p/plot-all-by-case p/plot-all-absolute)]
          (plot-fn
           {:day (count (data/raw-dates-unsorted)) :case case :type type
            :threshold (com/min-threshold case)
            :threshold-increase (com/threshold-increase case)
            :stats (v1/pic-data)})))))))

;; (defn language [prm]
;;   (format
;;    "/lang:%s\n/lang:%s\n/lang:%s\n"
;;    "sk"
;;    "de"
;;    "en"
;;    (footer prm)))

(defn format-last-day [prm] (com/fmt-date (:f (data/last-day prm))))

(defn header [{:keys [parse_mode] :as prm}]
  (format
   (str
    (condp = parse_mode
      "HTML" "<b>%s</b>"
      ;; i.e. "Markdown"
      "*%s*")
    " %s")
   (format-last-day prm)
   (condp = parse_mode
     "HTML" c/bot-name
     ;; i.e. "Markdown"
     (s/replace c/bot-name #"_" "\\\\_"))))

;; https://clojurians.zulipchat.com/#narrow/stream/180378-slack-archive/topic/beginners/near/191238200

(defn list-countries
  "Listing commands in the message footer correspond to the columns in the listing.
  See also `footer`, `bot-father-edit-cmds`."
  [{:keys [data msg-idx cnt-msgs sort-by-case] :as prm}]
  (let [spacer " "
        sort-indicator "▴" ;; " " "▲"
        omag-ill    7 ;; order of magnitude i.e. number of digits
        omag-recov  omag-ill
        omag-deaths (dec omag-ill)]
    (format
     (format (str "%s\n" ; header
                  "%s\n" ; Day\Report
                  "         %s "  ; Sick
                  "%s"   ; spacer
                  "%s "  ; Recovered
                  "%s"   ; spacer
                  "%s\n" ; Deaths
                  "%s")
             (header prm)
             (format "%s %s;  %s/%s" s-day (count (data/raw-dates)) msg-idx cnt-msgs)
             (str s-sick      (if (= :i sort-by-case) sort-indicator " "))
             spacer
             (str s-recovered (if (= :r sort-by-case) sort-indicator " "))
             spacer
             (str s-deaths    (if (= :d sort-by-case) sort-indicator " "))
             (str
              "%s"   ; listing table
              "%s"   ; sorted-by description; has its own new-line
              "\n\n"
              "%s"   ; footer
              ))
     (s/join
      "\n"
      (map (fn [stats]
             (format "<code>%s%s%s%s%s %s</code>  %s"
                     (c/left-pad (:i stats) " " omag-ill)
                     spacer
                     (c/left-pad (:r stats) " " omag-recov)
                     spacer
                     (c/left-pad (:d stats) " " omag-deaths)
                     (c/right-pad (:cn stats) 17)
                     (s/lower-case (com/encode-cmd (:cc stats)))))
           (->> data
                #_(take-last 11)
                #_(partition-all 2)
                #_(map (fn [part] (s/join "       " part))))))
     ""
     #_(if (= msg-idx cnt-msgs)
       (str "\n\n" (s-list-sorted-by-desc sort-by-case))
       "")
     (footer prm))))

(def list-countries-memo
  #_list-countries
  (memo/ttl list-countries {} :ttl/threshold (* 60 60 1000)))

(defn info
  "Shows the table with the absolute and %-wise number of cases"
  [{:keys [country-code] :as prm}]
  (format
   (str
    "%s\n"  ; extended header
    "%s\n"  ; day
    "%s\n"  ; data
    "%s\n"  ; footer
    )
   (format
    (str "%s  " ; header
         "%s "  ; country
         "%s"   ; country commands
         )
    (header prm)
    (cr/country-name-aliased country-code)
    (apply (fn [cc ccc] (format "     %s    %s" cc ccc))
           (map (fn [s] (->> s s/lower-case com/encode-cmd))
                [country-code
                 (cr/country-code-3-letter country-code)])))
   (str s-day " " (count (data/raw-dates)))

   (let [last-day (data/last-day prm)
         delta (data/delta prm)
         {confirmed :c population :p} last-day
         {dc :c} delta]
     (str
      (str
       (fmt-to-cols-narrower
        {:s s-population :n population
         ;; :total 0
         ;; :diff ""
         :calc-rate false
         :calc-diff false
         :desc (str "= "(round-div-precision population 1e6 1) " Mill")})
       "\n")
      (fmt-to-cols {:s s-confirmed :n confirmed :diff dc
                    :calc-rate false :desc ""})
      "\n"
      (when (pos? confirmed)
        (let [{deaths :d
               recovered :r
               ill :i
               } last-day
              {ill-week-ago :i} (data/week-ago prm)
              closed (+ deaths recovered)
              {dd :d dr :r di :i} delta
              dclosed (+ dd dr)]
          (format
           "%s\n%s\n%s\n%s\n%s\n"
           (fmt-to-cols
            {:s s-sick      :n ill       :total confirmed :diff di
             :calc-rate true
             :desc ""})
           ;; TODO add effective reproduction number (R)
           (fmt-to-cols
            {:s s-sick-per-1e5 :n (per-1e5 ill population) :total population :diff ""
             :calc-rate false
             :show-n true
             :calc-diff false
             :desc ""})
           (fmt-to-cols
            {:s s-floating-avg :n (round-precision
                               (/ (- ill ill-week-ago) 7.0)
                               2) :total population :diff ""
             :calc-rate false
             :show-n true
             :calc-diff false
             :desc ""})
           (fmt-to-cols
            {:s "Sick-7r" :n ill-week-ago :total population :diff ""
             :calc-rate false
             :show-n true
             :calc-diff false
             :desc ""})
           (fmt-to-cols
            {:s s-recovered :n recovered :total confirmed :diff dr
             :calc-rate true
             :desc ""})
           (fmt-to-cols
            {:s s-deaths    :n deaths    :total confirmed :diff dd
             :calc-rate true
             :desc ""})
           (fmt-to-cols
            {:s s-closed    :n closed :total confirmed :diff dclosed
             :calc-rate true
             :desc ""}))))))
   (footer prm)))

;; By default Vars are static, but Vars can be marked as dynamic to
;; allow per-thread bindings via the macro binding. Within each thread
;; they obey a stack discipline:
#_(def ^:dynamic points [[0 0] [1 3] [2 0] [5 2] [6 1] [8 2] [11 1]])

#_(defn absolute-vals [{:keys [country-code] :as prm}]
  (let [line-style {:marker-type :none :render-style :line}
        dates {:x (data/dates)}]
    (-> (chart/xy-chart
         {s-sick      (assoc dates :y (data/ill prm)
                             :style {:marker-type :none})
          s-confirmed (assoc dates :y (data/confirmed prm)
                             :style (assoc line-style :line-color :black))
          s-deaths    (assoc dates :y (data/deaths prm)
                             :style (assoc line-style :line-color :red))
          s-recovered (assoc dates :y (data/recovered prm)
                             :style (assoc line-style :line-color :green))}
         {:title (format "%s; %s: %s; see %s"
                         (format-last-day prm)
                         c/bot-name
                         (cr/country-name-aliased country-code)
                         (com/encode-cmd s-about))
          :render-style :area
          :legend {:position :inside-nw}
          ;; :x-axis {:title "Date"}
          ;; :y-axis {:title "Cases"}
          ;; :theme :matlab
          ;; :width 640 :height 500
          :width 800 :height 600
          :date-pattern "MMMd"})
        #_(chart/view)
        (chart/to-bytes :png))))

(defn feedback [prm]
  (str "Just write a message to @RostislavSvoboda thanks."))

(defn contributors [prm]
  (format "%s\n\n%s\n\n%s"
          (s/join "\n" ["@DerAnweiser"
                        (link "maty535" "https://github.com/maty535" prm)
                        "@kostanjsek"
                        "@DistrictBC"
                        "Michael J."
                        "Johannes D."
                        ])
          (str
           "The rest of the contributors prefer anonymity or haven't "
           "approved their inclusion to this list yet. 🙏 Thanks folks.")
          (footer prm)))

(defn about [{:keys [parse_mode] :as prm}]
  (str
   ;; escape underscores for the markdown parsing
   (bot-name-formatted)
   " " c/bot-ver " "
   (str
    (link "GitHub" "https://github.com/Bost/corona_cases" prm) ", "
    (link "GitLab" "https://gitlab.com/rostislav.svoboda/corona_cases" prm)
    "\n")
   "\n"
   (format "- Closed cases = %s + %s\n"
           (s/lower-case s-recovered)
           (s/lower-case s-deaths))
   "- Percentage calculation: <cases> / confirmed\n"
   (format "- %s = (%s - %s) / 7\n" s-floating-avg s-sick-today s-sick-week-ago)
   #_(str
      "\n"
      " - " (link "Home page"
                  (def home-page
                    (cond
                      c/env-prod? "https://corona-cases-bot.herokuapp.com/"
                      c/env-test? "https://hokuspokus-bot.herokuapp.com/"
                      :else "http://localhost:5050"))
                  prm))
   ;; (abbreviated) content of the former reference message
   (format "%s %s\n"
           "- Robert Koch-Institut "
           (link "COVID-19 (Coronavirus SARS-CoV-2)"
                 ref-rober-koch prm))
   (format "- 3Blue1Brown: %s\n"
           (link "Exponential growth and epidemics"
                 ref-3blue1brown-exp-growth
                 prm))
   (format "%s\n  %s\n  %s\n"
           "- Worldometer - COVID-19 Coronavirus"
           (link "Coronavirus Age Sex Demographics" ref-age-sex prm)
           (link "Mortality rate" ref-mortality-rate prm))
   (format "- Thanks goes to %s. Send %s \n"
           (com/encode-cmd s-contributors)
           (com/encode-cmd s-feedback))
   "\n"
   (footer prm)))

(def bot-description
  "Keep it in sync with README.md"
  "Coronavirus disease 2019 (COVID-19) information on Telegram Messenger")

(defn bot-father-edit-description [] bot-description)
(defn bot-father-edit-about [] bot-description)
(defn bot-father-edit-inline-placeholder
  "Appears when a user types: @<botname>
  See https://core.telegram.org/bots/inline"
  [] "Coronavirus Information")
