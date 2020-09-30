(ns corona.messages
  (:require
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.string :as s]
   [corona.api.expdev07 :as data]
   [corona.api.v1 :as v1]
   [corona.common :as co]
   [corona.countries :as cr]
   [corona.country-codes :as cc]
   [corona.lang :as l]
   [corona.plot :as p]
   [morse.api :as morse]
   [utils.core :as u :refer [in? dbgv dbgi] :exclude [id]]
   [utils.num :as un]
   [incanter.stats :as istats]
   [incanter.zoo :as izoo]
   )
  (:import java.awt.image.BufferedImage
           java.io.ByteArrayOutputStream
           javax.imageio.ImageIO))

(defn bot-name-formatted []
  (s/replace co/bot-name #"_" "\\\\_"))

(def ^:const options {:parse_mode "Markdown" :disable_web_page_preview true})

(defn pred-fn [country-code] (data/pred-fn country-code))

(defn round-nr [value] (int (un/round-precision value 0)))

(def ^:const max-diff-order-of-magnitude 7)

(defn plus-minus
  "Display \"+0\" when n is zero"
  [n]
  (co/left-pad
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

(def ^:const padding-s
  "Stays constant" 9)
(def ^:const padding-n
  "Count of digits to display. Increase it when the nr of cases. Increases by an
  order of magnitude" 8)

(defn fmt-to-cols-narrower
  "Info-message numbers of aligned to columns for better readability"
  [{:keys [s n total diff desc calc-rate show-n calc-diff]
    :or {show-n true calc-diff true
         desc ""}}]
  (format "<code>%s %s</code> %s"
          (co/right-pad s " " (- padding-s 2))
          (co/left-pad (if show-n n "") " " (+ padding-n 2))
          desc))

(defn fmt-val-to-cols
  [{:keys [s n show-n desc]
    :or {show-n true desc ""}}]
  (format "<code>%s %s</code> %s"
          (co/right-pad s " " padding-s)
          (co/left-pad (if show-n n "") " " padding-n)
          desc))

(defn fmt-to-cols
  "Info-message numbers of aligned to columns for better readability"
  [{:keys [s n total diff calc-rate show-n calc-diff
           s1 n1 cmd1]
    :or {show-n true calc-diff true
         s1 "" n1 "" cmd1 ""}}]
  (format "<code>%s %s %s %s  %s %s </code>%s"
          (co/right-pad s " " padding-s)
          (co/left-pad (if show-n n "") " " padding-n)
          (co/left-pad (if calc-rate (str (un/percentage n total) "%") " ")
                      " " 4)
          (if calc-diff
            (plus-minus diff)
            (co/left-pad "" " " max-diff-order-of-magnitude))
          s1
          (co/left-pad n1 " " 4)
          (co/encode-cmd cmd1)))

(def ^:const ref-mortality-rate
  "https://www.worldometers.info/coronavirus/coronavirus-death-rate/")

(def ^:const ref-rober-koch
  "https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/nCoV.html")

(def ^:const ref-3blue1brown-exp-growth
  "https://youtu.be/Kas0tIxDvrg")

(def ^:const ref-age-sex
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
     (->> [l/world l/explain]
          (map co/encode-cmd)
          (map (fn [cmd] (co/encode-pseudo-cmd cmd parse_mode)))
          (s/join spacer))
     spacer l/listings ":  "
     (->> (mapv l/list-sorted-by co/listing-cases-absolute)
          (map co/encode-cmd)
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
                       {:text (str (case-kw l/buttons)
                                   (type l/type))
                        :callback_data (pr-str (assoc prm
                                                      :case case-kw
                                                      :type type))})
                     co/absolute-cases))
             [:sum :abs]))]})})

(defn worldwide? [country-code]
  (in? [cc/worldwide-2-country-code cc/worldwide-3-country-code cc/worldwide]
       country-code))

(defn callback-handler-fn [{:keys [data] :as prm}]
  (let [{country-code :cc
         chat-id :chat-id
         type :type
         case :case} (edn/read-string data)]
    (when (worldwide? country-code)
      (morse/send-photo
       co/token chat-id
       (buttons {:chat-id chat-id :cc country-code})
       (toByteArrayAutoClosable
        (let [plot-fn (if (= type :sum) p/plot-all-by-case p/plot-all-absolute)]
          (plot-fn
           {:day (count (data/raw-dates-unsorted)) :case case :type type
            :threshold (co/min-threshold case)
            :threshold-increase (co/threshold-increase case)
            :stats (v1/pic-data)})))))))

;; (defn language [prm]
;;   (format
;;    "/lang:%s\n/lang:%s\n/lang:%s\n"
;;    "sk"
;;    "de"
;;    "en"
;;    (footer prm)))

(defn last-day-val [prm] (:f (data/last-day prm)))
(defn format-last-day [prm] (co/fmt-date (last-day-val prm)))

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
     "HTML" co/bot-name
     ;; i.e. "Markdown"
     (s/replace co/bot-name #"_" "\\\\_"))))

;; https://clojurians.zulipchat.com/#narrow/stream/180378-slack-archive/topic/beginners/near/191238200

(defn list-countries
  "Listing commands in the message footer correspond to the columns in the listing.
  See also `footer`, `bot-father-edit-cmds`."
  [{:keys [data msg-idx cnt-msgs sort-by-case] :as prm}]
  (let [
        cnt-reports (count (data/raw-dates)) ;; TODO must be calculated only once
        spacer " "
        sort-indicator "▴" ;; " " "▲"
        omag-active    7 ;; order of magnitude i.e. number of digits
        omag-recov  (inc omag-active)
        omag-deaths (dec omag-active)]
    (format
     (format (str "%s\n" ; header
                  "%s\n" ; Day\Report
                  "    %s "  ; Active
                  "%s"   ; spacer
                  "%s "  ; Recovered
                  "%s"   ; spacer
                  "%s\n" ; Deaths
                  "%s")
             (header prm)
             (format "%s %s;  %s/%s" l/day (count (data/raw-dates)) msg-idx cnt-msgs)
             (str l/active    (if (= :i sort-by-case) sort-indicator " "))
             spacer
             (str l/recovered (if (= :r sort-by-case) sort-indicator " "))
             spacer
             (str l/deaths    (if (= :d sort-by-case) sort-indicator " "))
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
                     (co/left-pad (:i stats) " " omag-active)
                     spacer
                     (co/left-pad (:r stats) " " omag-recov)
                     spacer
                     (co/left-pad (:d stats) " " omag-deaths)
                     (co/right-pad (:cn stats) 17)
                     (s/lower-case (co/encode-cmd (:cc stats)))))
           (->> data
                #_(take-last 11)
                #_(partition-all 2)
                #_(map (fn [part] (s/join "       " part))))))
     ""
     #_(if (= msg-idx cnt-msgs)
       (str "\n\n" (l/list-sorted-by-desc sort-by-case))
       "")
     (footer prm))))

(def list-countries-memo
  #_list-countries
  (co/memo-ttl list-countries))

(defn list-per-100k
  "Listing commands in the message footer correspond to the columns in the listing.
  See also `footer`, `bot-father-edit-cmds`."
  [{:keys [data msg-idx cnt-msgs sort-by-case] :as prm}]
  (let [spacer " "
        sort-indicator "▴" ;; " " "▲"
        ;; omag - order of magnitude i.e. number of digits
        omag-active-per-100k    4
        omag-recovered-per-100k omag-active-per-100k
        omag-deaths-per-100k    (dec omag-active-per-100k)
        ]
    (format
     (format (str "%s\n" ; header
                  "%s\n" ; Day\Report
                  "%s "  ; Act100k
                  "%s"   ; spacer
                  "%s "  ; Rec100k
                  "%s"   ; spacer
                  "%s"   ; Dea100k
                  "\n"
                  "%s")
             (header prm)
             (format "%s %s;  %s/%s" l/day (count (data/raw-dates)) msg-idx cnt-msgs)
             (str l/active-per-1e5    (if (= :i100k sort-by-case) sort-indicator " "))
             spacer
             (str l/recovered-per-1e5 (if (= :r100k sort-by-case) sort-indicator " "))
             spacer
             (str l/deaths-per-1e5    (if (= :d100k sort-by-case) sort-indicator " "))
             (str
              "%s"   ; listing table
              "%s"   ; sorted-by description; has its own new-line
              "\n\n"
              "%s"   ; footer
              ))
     (s/join
      "\n"
      (map (fn [stats]
             (format "<code>   %s%s   %s%s    %s %s</code>  %s"
                     (co/left-pad (:i100k stats) " " omag-active-per-100k)
                     spacer
                     (co/left-pad (:r100k stats) " " omag-recovered-per-100k)
                     spacer
                     (co/left-pad (:d100k stats) " " omag-deaths-per-100k)
                     (co/right-pad (:cn stats) 17)
                     (s/lower-case (co/encode-cmd (:cc stats)))))
           (->> data
                #_(take-last 11)
                #_(partition-all 2)
                #_(map (fn [part] (s/join "       " part))))))
     ""
     #_(if (= msg-idx cnt-msgs)
       (str "\n\n" (l/list-sorted-by-desc sort-by-case))
       "")
     (footer prm))))

(def list-per-100k-memo
  #_list-per-100k
  (co/memo-ttl list-per-100k))

(defn diff-coll-vals
  "Differences between values. E.g.:
  (diff-coll-valls [1 3 6 10 9 9 10])
  ;; => [2 3 4 -1 0 1]"
  [coll]
  (loop [[head & tail] coll
         result []]
    (if (and head (seq tail))
      (recur tail (conj result (- (first tail) head)))
      result)))

(defn detailed-info
  "Shows the table with the absolute and %-wise nr of cases, cases per-100k etc.
  TODO 1. 'Country does not report recovered cases'
  TODO 2. Estimate recovered cased (based on 1.) with avg recovery time 14 days
  TODO 3. show Case / Infection Fatality Rate (CFR / IFR)
  TODO show country ranking place in the info-message
  TODO Bayes' Theorem applied to PCR test: https://youtu.be/M8xlOm2wPAA
  (need 1. PCR-test accuracy, 2. Covid 19 disease prevalence)
  "
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
           (map (fn [s] (->> s s/lower-case co/encode-cmd))
                [country-code
                 (cc/country-code-3-letter country-code)])))
   (str l/day " " (count (data/raw-dates)))

   (let [max-active-val (apply max (data/active prm))
         max-active-idx (.lastIndexOf (data/active prm) max-active-val)
         max-active-date (nth (data/dates) max-active-idx)
         last-day (data/last-day prm)
         delta (data/delta prm)
         {confirmed :c population :p} last-day
         population-rounded (un/round-div-precision population 1e6 1)
         {dc :c} delta]
     (str
      (str
       (fmt-to-cols-narrower
        {:s l/people :n population
         ;; :total 0
         ;; :diff ""
         :calc-rate false
         :calc-diff false
         :desc (format "= %s %s" population-rounded l/millions-rounded)})
       "\n")
      (fmt-to-cols {:s l/confirmed :n confirmed :diff dc :calc-rate false})
      "\n"
      (when (pos? confirmed)
        (let [{deaths             :d
               recovered          :r
               active             :i
               active-per-100k    :i100k
               recovered-per-100k :r100k
               deaths-per-100k    :d100k
               closed-per-100k    :c100k
               } last-day
              {last-8-reports :i} (data/last-8-reports prm)
              [last-8th-report & last-7-reports] last-8-reports
              [last-7th-report & _] last-7-reports
              closed (+ deaths recovered)
              {dd :d dr :r di :i} delta
              dclosed (+ dd dr)]
          (format
           (str "%s\n" ; l/active
                "%s\n" ; l/active-max
                "%s\n" ; l/active-last-7-med
                "%s\n" ; l/active-last-7-avg
                "%s\n" ; l/active-change-last-7-avg
                "%s\n" ; l/recovered
                "%s\n" ; l/deaths
                "%s\n" ; l/closed
                "\n"   ; visual separation for the l/active-last-7
                "%s\n" ; l/active-last-7
                )
           (fmt-to-cols
            {:s l/active :n active :total confirmed :diff di :calc-rate true
             :s1 l/active-per-1e5
             :n1 active-per-100k
             :cmd1 l/cmd-active-per-1e5})

           (fmt-val-to-cols
            {:s l/active-max
             :n max-active-val
             :show-n true
             :desc (format "(%s)" (co/fmt-date max-active-date))})

           ;; TODO add effective reproduction number (R)
           (fmt-to-cols
            {:s l/active-last-7-med
             :n (->> last-7-reports (izoo/roll-median 7) first int)
             :total population :diff "" :calc-rate false :show-n true
             :calc-diff false})
           (fmt-to-cols
            {:s l/active-last-7-avg
             :n (-> last-7-reports istats/mean round-nr)
             :total population :diff "" :calc-rate false :show-n true
             :calc-diff false})
           (fmt-to-cols
            {:s l/active-change-last-7-avg

             ;; ActC(t0)    = active(t0)    - active(t0-1d)
             ;; ActC(t0-1d) = active(t0-1d) - active(t0-2d)
             ;; ActC(t0-2d) = active(t0-2d) - active(t0-3d)
             ;; ActC(t0-3d) = active(t0-2d) - active(t0-4d)
             ;; ActC(t0-4d) = active(t0-2d) - active(t0-5d)
             ;; ActC(t0-5d) = active(t0-2d) - active(t0-6d)
             ;; ActC(t0-6d) = active(t0-6d) - active(t0-7d)

             ;; ActCL7CAvg =
             ;; = (ActC(t0)+ActC(t0-1d)+ActC+(t0-2d)+...+ActC(t0-6d)) / 7
             ;; = (active(t0) - active(t0-7d)) / 7
             :n (-> (/ (- active last-8th-report) 7.0) round-nr plus-minus)
             :total population :diff "" :calc-rate false :show-n true
             :calc-diff false})
           (fmt-to-cols
            {:s l/recovered :n recovered :total confirmed :diff dr
             :calc-rate true
             :s1 l/recovered-per-1e5
             :n1 recovered-per-100k
             :cmd1 l/cmd-recovered-per-1e5})
           (fmt-to-cols
            {:s l/deaths :n deaths :total confirmed :diff dd :calc-rate true
             :s1 l/deaths-per-1e5
             :n1 deaths-per-100k
             :cmd1 l/cmd-deaths-per-1e5})
           (fmt-to-cols
            {:s l/closed :n closed :total confirmed :diff dclosed
             :calc-rate true
             :s1 l/closed-per-1e5
             :n1 closed-per-100k
             ;; TODO :cmd1 l/cmd-closed-per-1e5
             })
           (format
            #_"%s\n%s"
            "<code>%s</code>\n%s"
            #_"<code>%s\n%s</code>" l/active-last-7
                   (u/sjoin last-7-reports)))))))
   (footer prm)))

;; By default Vars are static, but Vars can be marked as dynamic to
;; allow per-thread bindings via the macro binding. Within each thread
;; they obey a stack discipline:
#_(def ^:dynamic points [[0 0] [1 3] [2 0] [5 2] [6 1] [8 2] [11 1]])

#_(defn absolute-vals [{:keys [country-code] :as prm}]
  (let [line-style {:marker-type :none :render-style :line}
        dates {:x (data/dates)}]
    (-> (chart/xy-chart
         {l/active    (assoc dates :y (data/active prm)
                             :style {:marker-type :none})
          l/confirmed (assoc dates :y (data/confirmed prm)
                             :style (assoc line-style :line-color :black))
          l/deaths    (assoc dates :y (data/deaths prm)
                             :style (assoc line-style :line-color :red))
          l/recovered (assoc dates :y (data/recovered prm)
                             :style (assoc line-style :line-color :green))}
         {:title (format "%s; %s: %s; see %s"
                         (format-last-day prm)
                         co/bot-name
                         (cr/country-name-aliased country-code)
                         (co/encode-cmd l/explain))
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

(defn explain [{:keys [parse_mode] :as prm}]
  (str
   ;; escape underscores for the markdown parsing
   (bot-name-formatted)
   " " co/bot-ver " "
   (str
    (link "GitHub" "https://github.com/Bost/corona_cases" prm) ", "
    (link "GitLab" "https://gitlab.com/rostislav.svoboda/corona_cases" prm)
    "\n")
   "\n"
   (format "- %s cases = %s + %s\n"
           l/closed
           l/recovered
           l/deaths)
   (format "- Percentage calculation: <cases> / %s\n" l/confirmed)
   (format (str "- %s:\n"
                "  %s\n")
           l/active-max
           (:doc (meta #'l/active-max)))
   (format (str "- %s:\n"
                "  %s\n")
           l/active-last-7
           (:doc (meta #'l/active-last-7)))
   (format (str "- %s:\n"
                "  %s\n")
           l/active-last-7-med
           (:doc (meta #'l/active-last-7-med)))
   (format (str "- %s:\n"
                "  %s\n")
           l/active-last-7-avg
           (:doc (meta #'l/active-last-7-avg)))
   (format (str "- %s = (%s - %s) / 7\n"
                "  %s\n")
           l/active-change-last-7-avg
           l/active l/active-last-8th
           (:doc (meta #'l/active-change-last-7-avg)))
   #_(str
      "\n"
      " - " (link "Home page"
                  (def home-page
                    (cond
                      co/env-prod? "https://corona-cases-bot.herokuapp.com/"
                      co/env-test? "https://hokuspokus-bot.herokuapp.com/"
                      :else "http://localhost:5050"))
                  prm))
   ;; (abbreviated) content of the former reference message
   (format (str "- %s, %s, %s, %s:\n"
                "  %s\n")
           l/active-per-1e5
           l/recovered-per-1e5
           l/deaths-per-1e5
           l/closed-per-1e5
           "Cases per 100 000 people")
   "\n"
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
   (format "- Thanks goes to %s. Please send %s \n"
           (co/encode-cmd l/contributors)
           (co/encode-cmd l/feedback))
   "\n"
   (footer prm)))

(def ^:const bot-description
  "Keep it in sync with README.md"
  "Coronavirus disease 2019 (COVID-19) information on Telegram Messenger")

(defn bot-father-edit-description [] bot-description)
(defn bot-father-edit-about [] bot-description)
(defn bot-father-edit-inline-placeholder
  "Appears when a user types: @<botname>
  See https://core.telegram.org/bots/inline"
  [] "Coronavirus Information")
