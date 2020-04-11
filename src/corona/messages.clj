(ns corona.messages
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.core.memoize :as memo]
            [clojure.string :as s]
            #_[com.hypirion.clj-xchart :as chart]
            [corona.api.expdev07 :as data]
            [corona.common :as com]
            [corona.core :as c]
            [corona.countries :as cr]))

(def lang-strings
  {
   :world        "world"
   :world-desc   "Start here"
   :start        "start"
   :list         "list"
   :list-desc    "List of countries"
   :about        "about"
   :contributors "contributors"
   :references   "references"
   :feedback     "feedback"
   :confirmed    "Confirmed"
   :deaths       "Deaths"
   :recovered    "Recovered"
   :sick         "Sick"
   :closed       "Closed"
   ;; A文 doesn't get displayed blue as a telegram command. Hmm
   ;; :language     "lang"
   :country      "country"
   })

(def s-world        (:world        lang-strings))
(def s-world-desc   (:world-desc   lang-strings))
(def s-start        (:start        lang-strings))
(def s-list         (:list         lang-strings))
(def s-list-desc    (:list-desc    lang-strings))
(def s-about        (:about        lang-strings))
(def s-contributors (:contributors lang-strings))
(def s-references   (:references   lang-strings))
(def s-feedback     (:feedback     lang-strings))
(def s-confirmed    (:confirmed    lang-strings))
(def s-deaths       (:deaths       lang-strings))
(def s-recovered    (:recovered    lang-strings))
(def s-sick         (:sick         lang-strings))
(def s-closed       (:closed       lang-strings))
;; (def s-language     (:language     lang-strings))
;; (def cmd-s-country  (format "<%s>" (:country lang-strings)))

;; (def lang-de "lang:de")

(def cmd-names [s-world
                s-about
                s-references
                #_cmd-s-country
                s-list
                s-feedback

                ;; s-language
                ;; lang-de
                ])

(defn bot-name-formatted []
  (s/replace c/bot-name #"_" "\\\\_"))

(def options {:parse_mode "Markdown" :disable_web_page_preview true})

(defn pred-fn [country-code] (data/pred-fn country-code))

(defn get-percentage
  ([place total-count] (get-percentage :normal place total-count))
  ([mode place total-count]
   (let [percentage (/ (* place 100.0) total-count)]
     (condp = mode
       :high     (int (Math/ceil  percentage))
       :low      (int (Math/floor percentage))
       :normal   (int (Math/round percentage))
       (throw
        (Exception. (str "ERROR: "
                         "get-percentage [:high|:low|:normal] "
                         "<PLACE> <TOTAL_COUNT>")))))))

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

(defn fmt-to-cols
  "Print the numbers aligned to columns for better readability"
  [{:keys [s n total diff desc calc-rate]}]
  (format "<code>%s %s %s %s</code> %s"
          (c/right-pad s " " 9) ; stays constant
          ;; count of digits to display. Increase it when the number of cases
          ;; increases by an order of magnitude
          (c/left-pad n " " 7)
          (c/left-pad
           (if calc-rate
             (str (get-percentage n total) "%")
             " ")
             " " 4)
          (plus-minus diff)
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

(defn footer [{:keys [cmd-names parse_mode]}]
  (let [spacer "   "]
    (str
     ;; "Try" spacer
     (->> cmd-names
          (map com/encode-cmd)
          (map (fn [cmd] (com/encode-pseudo-cmd cmd parse_mode)))
          (s/join spacer)))))

(defn references [prm]
  (format
   "%s\n\n%s\n\n%s\n\n%s\n\n%s"
   (bot-name-formatted) #_(header prm)
   (format "%s\n%s"
           "Robert Koch-Institut (in German)"
           (format "Infektionskrankheiten A-Z: %s"
                   (link "COVID-19 (Coronavirus SARS-CoV-2)"
                         ref-rober-koch prm)))
   (format "%s\n%s"
           "A bit of 👨‍🏫 math doesn't kill anyone!"
           (format "3Blue1Brown: %s"
                   (link "Exponential growth and epidemics"
                         ref-3blue1brown-exp-growth
                         prm)))

   (format "%s\n%s\n%s"
           "Worldometer - COVID-19 Coronavirus"
           (link "Coronavirus Age Sex Demographics" ref-age-sex prm)
           (link "Mortality rate" ref-mortality-rate prm))

   (footer prm)))

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

(defn list-countries [{:keys [data] :as prm}]
  (let [separator " "
        omag-ill       6 ;; order of magnitude i.e. number of digits
        omag-recovered omag-ill
        omag-deaths    (dec omag-ill)]
    (format
     (format (str "%s\n" ; header
                  "%s\n" ; Day
                  "    %s "  ; Sick
                  "%s"   ; separator
                  "%s "  ; Recovered
                  "%s"   ; separator
                  "%s\n" ; Deaths
                  "%s")
             (header prm)
             (str "Day " (count (data/raw-dates)))
             s-sick
             separator
             s-recovered
             separator
             s-deaths
             "%s\n\n%s")
     (s/join
      "\n"
      (->> data
           #_(take-last 11)
           (map (fn [data-country]
                  (let [{ill :i recovered :r deaths :d
                         country-name :cn country-code :cc} data-country]
                    (format "<code>%s%s%s%s%s %s</code>  %s"
                            (c/left-pad (str ill)       " " omag-ill)
                            separator
                            (c/left-pad (str recovered) " " omag-recovered)
                            separator
                            (c/left-pad (str deaths)    " " omag-deaths)
                            (c/right-pad country-name 17)
                            (s/lower-case (com/encode-cmd country-code))))))
           #_(partition-all 2)
           #_(map (fn [part] (s/join "       " part)))))
     (footer prm))))

(def list-countries-memo
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
    (com/country-name-aliased country-code)
    (apply (fn [cc ccc] (format "     %s    %s" cc ccc))
           (map (fn [s] (->> s s/lower-case com/encode-cmd))
                [country-code
                 (cr/country-code-3-letter country-code)])))
   (str "Day " (count (data/raw-dates)))

   (let [last-day (data/last-day prm)
         delta (data/delta prm)
         {confirmed :c} last-day
         {dc :c} delta]
     (str
      (fmt-to-cols {:s s-confirmed :n confirmed :diff dc
                    :calc-rate false :desc ""})
      "\n"
      (when (pos? confirmed)
        (let [{deaths :d
               recovered :r
               ill :i} last-day
              closed (+ deaths recovered)
              {dd :d dr :r di :i} delta
              dclosed (+ dd dr)]
          (format
           "%s\n%s\n%s\n%s\n"
           (fmt-to-cols
            {:s s-sick      :n ill       :total confirmed :diff di
             :calc-rate true
             :desc ""})
           (fmt-to-cols
            {:s s-recovered :n recovered :total confirmed :diff dr
             :calc-rate true
             :desc ""})
           (fmt-to-cols
            {:s s-deaths    :n deaths    :total confirmed :diff dd
             :calc-rate true
             :desc (format " See %s"
                           (link "mortality rate" ref-mortality-rate prm))
             #_(format " See %s and %s"
                       (link "mortality rate" ref-mortality-rate prm)
                       (com/encode-cmd s-references))})
           (fmt-to-cols
            {:s s-closed :n closed :total confirmed :diff dclosed
             :calc-rate true
             :desc (format "= %s + %s"
                           (s/lower-case s-recovered )
                           (s/lower-case s-deaths))}))))))
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
                         (com/country-name-aliased country-code)
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
   "- Percentage calculation: <cases> / confirmed\n"

   (format (str "- %s master branch snapshot download deactivated. "
                "Send a %s if you need this feature.\n")
           (link "CSSEGISandData/COVID-19"
                 "https://github.com/CSSEGISandData/COVID-19.git" prm)
           (com/encode-cmd s-feedback))

   (format "- %s\n" (com/encode-cmd s-contributors))
   "\n"

   #_(str
      "\n"
      " - " (link "Home page"
                  (def home-page
                    (cond
                      c/env-prod? "https://corona-cases-bot.herokuapp.com/"
                      c/env-test? "https://hokuspokus-bot.herokuapp.com/"
                      :else "http://localhost:5050"))
                  prm))
   (footer prm)))

(def bot-description
  "Keep it in sync with README.md"
  "Coronavirus disease 2019 (COVID-19) information on Telegram Messenger")

(defn bot-father-edit-description [] bot-description)
(defn bot-father-edit-about [] bot-description)
