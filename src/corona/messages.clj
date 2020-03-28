(ns corona.messages
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.core.memoize :as memo]
            [clojure.string :as s]
            [com.hypirion.clj-xchart :as chart]
            [corona.api.expdev07 :as data]
            [corona.common :as com]
            [corona.core :as c]
            [corona.countries :as cr]
            [corona.defs :as d]))

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
   :language     "lang"
   :country      "country"
   :snapshot     "snapshot"
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
(def s-language     (:language     lang-strings))
(def s-snapshot     (:snapshot     lang-strings))
(def cmd-s-country  (format "<%s>" (:country lang-strings)))

(def lang-de "lang:de")

(def cmd-names [s-world
                s-about
                s-references
                cmd-s-country
                s-list
                s-feedback

                #_s-language
                #_lang-de
                ])
(defn bot-name-formatted []
  (s/replace c/bot-name #"_" "\\\\_"))

(def home-page
  ;; TODO (env :home-page)
  "https://corona-cases-bot.herokuapp.com/")

(defn stats-per-country [{:keys [cc] :as prm}]
  (->> (assoc prm :pred (data/pred-fn cc))
       (data/last-day)
       (conj {:cn (com/country-name-aliased cc)
              :cc cc})))

(defn all-affected-country-codes [prm]
  (->> (com/all-affected-country-codes)
       (map (fn [cc]
              (stats-per-country (assoc prm :cc cc))))))

(def options {:parse_mode "Markdown" :disable_web_page_preview true})

(defn pred-fn [country-code] (data/pred-fn country-code))

(defn encode-cmd [s] (str "/" s))
(defn encode-pseudo-cmd [s parse_mode]
  (if (= parse_mode "HTML")
    (let [s (s/replace s "<" "&lt;")
          s (s/replace s ">" "&gt;")]
      s)
    s))

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

(defn fmt [{:keys [s n total diff desc calc-rate]}]
  (format "<code>%s %s %s %s</code> %s"
          (c/right-pad s " " 9)
          (c/left-pad n " " 6)
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
          (map encode-cmd)
          (map (fn [cmd] (encode-pseudo-cmd cmd parse_mode)))
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

(defn language [prm]
  (format
   "/lang:%s\n/lang:%s\n/lang:%s\n"
   "sk"
   "de"
   "en"
   (footer prm)))

(defn remember-20-seconds [prm]
  (format "%s\n%s"
          "Remember, at least *20* seconds!"
          #_"https://www.who.int/gpsc/clean_hands_protection/en/"
          "https://www.who.int/gpsc/media/how_to_handwash_lge.gif"))

(defn format-last-day [prm]
  (tf/unparse (tf/with-zone (tf/formatter "dd MMM yyyy")
                (t/default-time-zone))
              (->> prm data/last-day :f tc/from-date)))

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

(defn fmt-continents
  [{:keys [data] :as prm}]
  (let [separator " "]
    (format
     (format (str
              ;; "%s\n" ;; header
              ;; "%s\n" ;; day
              "%s "  ;; sick
              "%s"   ;; separator
              "%s "  ;; recovered
              "%s"   ;; separator
              "%s\n" ;; deaths
              "%s"   ;; listing
                  )
             ;; (header prm)
             ;; (str "Day " (count (data/raw-dates)))
             s-sick
             separator
             s-recovered
             separator
             s-deaths
             (str "%s\n\n"
                  "Total countries hit: %s"))
     (s/join
      "\n"
      (->> data
           #_(take-last 11)
           (map (fn [data-continent]
                  (let [{ill :i recovered :r deaths :d
                         name :cn code :cc} data-continent]
                    (format (str
                             "<code>%s%s%s%s%s %s</code>"
                             ;; "  %s" ; continent commands
                             )
                            (c/right-pad (str ill) 6)
                            separator
                            (c/right-pad (str recovered) 6)
                            separator
                            (c/right-pad (str deaths) 5)
                            (c/right-pad name 17)
                            ;; TODO implement continent commands
                            #_(->> code encode-cmd s/lower-case)
                            ))))
           #_(partition-all 2)
           #_(map (fn [part] (s/join "       " part)))))
     (count (com/all-affected-country-codes)))))

(defn fmt-worldwide
  "TODO show counts for every continent"
  [{:keys [data] :as prm}]
  (let [separator " "]
    (format
     (format (str
              ;; "%s\n" ;; header
              ;; "%s\n" ;; day
              "%s "  ;; sick
              "%s"   ;; separator
              "%s "  ;; recovered
              "%s"   ;; separator
              "%s\n" ;; deaths
              "%s"   ;; listing
                  )
             ;; (header prm)
             ;; (str "Day " (count (data/raw-dates)))
             s-sick
             separator
             s-recovered
             separator
             s-deaths
             (str "%s\n\n"
                  "Total countries hit: %s"))
     (s/join
      "\n"
      #_"listing..."
      (->> data
           #_(take-last 11)
           (map (fn [data-continent]
                  (let [{ill :i recovered :r deaths :d
                         name :cn code :cc} data-continent]
                    (format "<code>%s%s%s%s%s %s</code>  %s"
                            (c/right-pad (str ill) 6)
                            separator
                            (c/right-pad (str recovered) 6)
                            separator
                            (c/right-pad (str deaths) 5)
                            (c/right-pad name 17)
                            (->> code encode-cmd s/lower-case)
                            ))))
           #_(partition-all 2)
           #_(map (fn [part] (s/join "       " part)))))
     (count (com/all-affected-country-codes)))))

(defn list-continents [prm]
  (format
   (str
    "%s\n" ; header
    "%s\n" ; day
    "%s\n\n" ; continents
    "%s\n\n" ; worldwide
    "%s" ; footer
    )
   (header prm)
   (str "Day " (count (data/raw-dates)))
   (->> (com/all-affected-continent-codes)
        (map (fn [country-code]
               (let [hms (->> (com/continent-code--country-codes country-code)
                              (map (fn [cc]
                                     (stats-per-country (assoc prm :cc cc)))))]
                 {:i (reduce + (map :i hms))
                  :r (reduce + (map :r hms))
                  :d (reduce + (map :d hms))
                  :cc country-code
                  :cn (com/continent-name country-code)})))
        (sort-by :i <)
        (assoc prm :data)
        fmt-continents)

   (->> [d/country-code-worldwide
         ;; TODO verify it Others is listed among countries
         #_d/country-code-others]
        (reduce into)
        (mapv (fn [[k _]] k))
        (map (fn [cc]
               (stats-per-country (assoc prm :cc cc))))
        (sort-by :i <)
        (assoc prm :data)
        fmt-worldwide)
   (footer prm)))

(defn list-countries [{:keys [data] :as prm}]
  (let [separator " "]
    (format
     (format (str "%s\n" ; header
                  "%s\n" ; day
                  "%s "  ; sick
                  "%s"   ; separator
                  "%s "  ; recovered
                  "%s"   ; separator
                  "%s\n" ; deaths
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
                            (c/right-pad (str ill) 6)
                            separator
                            (c/right-pad (str recovered) 6)
                            separator
                            (c/right-pad (str deaths) 5)
                            (c/right-pad country-name 17)
                            (->> country-code encode-cmd s/lower-case)
                            ))))
           #_(partition-all 2)
           #_(map (fn [part] (s/join "       " part)))))
     (footer prm))))

(def list-continents-memo
  (memo/ttl list-continents {} :ttl/threshold (* 60 60 1000)))
(def list-countries-memo
  (memo/ttl list-countries {} :ttl/threshold (* 60 60 1000)))

(defn info
  "TODO analyze negative diffs for /usa"
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
           (map (fn [s] (->> s s/lower-case encode-cmd))
                [country-code
                 (cr/country-code-3-letter country-code)])))
   (str "Day " (count (data/raw-dates)))

   (let [last-day (data/last-day prm)
         delta (data/delta prm)
         {confirmed :c} last-day
         {dc :c} delta]
     (str
      (fmt {:s s-confirmed :n confirmed :diff dc
            :desc "" :calc-rate false}) "\n"
      (if (pos? confirmed)
        (let [{deaths :d recovered :r ill :i} last-day
              closed (+ deaths recovered)
              {dd :d dr :r di :i} delta
              dclosed (+ dd dr)]
          (format
           "%s\n%s\n%s\n%s\n"
           (fmt {:s s-sick :n ill :total confirmed :diff di :desc ""
                 :calc-rate true})
           (fmt {:s s-recovered :n recovered :total confirmed :diff dr :desc ""
                 :calc-rate true})
           (fmt {:s s-deaths :n deaths :total confirmed :diff dd
                 :calc-rate true
                 :desc (format " See %s"
                               (link "mortality rate" ref-mortality-rate prm))
                 #_(format " See %s and %s"
                           (link "mortality rate" ref-mortality-rate prm)
                           (encode-cmd s-references))})
           (fmt {:s s-closed :n closed :total confirmed :diff dclosed
                 :calc-rate true
                 :desc (format "= %s + %s"
                               (s/lower-case s-recovered )
                               (s/lower-case s-deaths))}))))))
   (footer prm)))

;; By default Vars are static, but Vars can be marked as dynamic to
;; allow per-thread bindings via the macro binding. Within each thread
;; they obey a stack discipline:
#_(def ^:dynamic points [[0 0] [1 3] [2 0] [5 2] [6 1] [8 2] [11 1]])

(defn absolute-vals [{:keys [country-code] :as prm}]
  (let [confirmed (data/confirmed prm)
        deaths    (data/deaths    prm)
        recovered (data/recovered prm)
        ill       (data/ill       prm)
        dates     (data/dates)]
    (-> (chart/xy-chart
         (conj {}
               {s-sick
                {:x dates :y ill
                 :style (conj {}
                              {:marker-type :none}
                              #_{:render-style :line}
                              #_{:marker-color :blue}
                              #_{:line-color :blue})}}
               {s-confirmed
                {:x dates :y confirmed
                 :style (conj {}
                              {:marker-type :none}
                              {:render-style :line}
                              {:line-color :black}
                              #_{:fill-color :orange})}}
               {s-deaths
                {:x dates :y deaths
                 :style (conj {}
                              {:marker-type :none}
                              {:render-style :line}
                              {:line-color :red})}}
               {s-recovered
                {:x dates :y recovered
                 :style (conj {}
                              {:marker-type :none}
                              {:render-style :line}
                              {:line-color :green})}}
               )
         (conj {}
               {:title
                (format "%s; %s: %s; see %s"
                        (format-last-day prm)
                        c/bot-name
                        (com/country-name-aliased country-code)
                        (encode-cmd s-about))
                :render-style :area
                :legend {:position :inside-nw}
                :x-axis {:title "Day"}
                :y-axis {:title "Cases"}}
               #_{:theme :matlab}
               #_{:width 640 :height 500}
               {:width 800 :height 600}
               {:date-pattern "MMMd"}))
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
   " version: " c/bot-ver " :  "
   (str
    (link "GitHub" "https://github.com/Bost/corona_cases" prm) ", "
    (link "GitLab" "https://gitlab.com/rostislav.svoboda/corona_cases" prm))
   "\n"
   "Percentage calculation: <cases> / confirmed\n"

   #_(str
    "- Feb12-spike caused mainly by a change in the diagnosis classification"
    " of the Hubei province.\n")

   #_(str
    "- Data retrieved *CONTINUOUSLY* every " data/time-to-live
    " minutes from " (link data/host data/url prm) ".\n")

   (str
    "Useful visualizations: " (link "JHU CSSE" "https://arcg.is/0fHmTX" prm) ", "
    (link "Worldometer"
          "https://www.worldometers.info/coronavirus/coronavirus-cases/" prm)
    "\n")

   (format "%s master branch %s\n"
           (link "CSSEGISandData/COVID-19"
                 "https://github.com/CSSEGISandData/COVID-19.git" prm)
           (encode-cmd s-snapshot))

   (format "The %s\n" (encode-cmd s-contributors))

   (format "Statistics per single %s - see the %s\n"
           (encode-cmd
            (encode-pseudo-cmd cmd-s-country parse_mode))
           (encode-cmd s-list))

   "\n"

   #_(format
    (str
     "- Country *specific* information using %s country codes & names. "
     "Examples:\n"
     "/fr    /fra      /France\n"
     "/us    /usa    /UnitedStates   (without spaces)\n\n")
    (link "ISO 3166"
          "https://en.wikipedia.org/wiki/ISO_3166-1#Current_codes" prm))

   ;; TODO home page; average recovery time
   #_(str
      "\n"
      " - " (link "Home page" home-page prm))
   (footer prm)))

(def bot-description
  "Keep it in sync with README.md"
  "Coronavirus disease 2019 (COVID-19) information on Telegram Messenger")

(defn bot-father-edit-description [] bot-description)
(defn bot-father-edit-about [] bot-description)
