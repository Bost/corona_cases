(ns corona.messages
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.string :as s]
            [com.hypirion.clj-xchart :as chart]
            [corona.csv :as data]
            [corona.tables :as tab]
            [corona.countries :as co]
            [corona.core :as c :refer [in?]]))

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

(def options {:parse_mode "Markdown" :disable_web_page_preview true})

(defn country-name-aliased [cc]
  (if (in? ["VA" "TW" "DO" "IR" "RU" "PS" "AE" "KR" "MK"
            #_"CZ" "BA" "CD" "BO" "MD" "BN"] cc)
    (co/country-alias cc)
    (c/country-name cc)))

(def max-country-name-len
  (->> (data/all-affected-country-codes)
       (map (fn [cc]
              (country-name-aliased cc )
              #_(c/country-name cc)))
       (sort-by count)
       last
       count))

(defn affected-country--name-code [prm]
  (->> (data/all-affected-country-codes)
       distinct
       #_(take 5)
       (map (fn [cc]
              (->> (fn [loc]
                     (condp = cc ;; cc is upper-cased
                       c/worldwide-2-country-code
                       true

                       c/default-2-country-code
                       ;; XX comes from the web service
                       (= "XX" (:country_code loc))

                       (= cc (:country_code loc))))
                   (assoc prm :pred)
                   (data/last-day)
                   (conj {:cn (country-name-aliased cc)
                          :cc cc}))))))

(defn encode-cmd [s] (str "/" s))
(defn encode-pseudo-cmd [s parse_mode]
  (if (= parse_mode "HTML")
    (let [s (s/replace s "<" "&lt;")
          s (s/replace s ">" "&gt;")]
      s)
    s))

(defn affected-country-codes [continent-code]
  (->> (data/all-affected-country-codes)
       (filter (fn [country-code]
                 (in? (tab/country-codes-of-continent continent-code)
                      country-code)))
       (into #{})))

(defn all-affected-continent-codes []
  ;; Can't really use the first implementation. See the doc of `tab/regions`
  (->> (data/all-affected-country-codes)
       (map (fn [acc]
              (->> tab/continent-countries-map
                   (filter (fn [[continent-code county-code]]
                             (= acc county-code))))))
       (remove empty?)
       (reduce into [])
       (map (fn [[continent-code &rest]] continent-code))
       (into #{})))

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

(defn fmt [s n total explanation]
  (format "%s: %s  ~ %s%%  %s\n" s n (get-percentage n total) explanation))

(def ref-mortality-rate
  "https://www.worldometers.info/coronavirus/coronavirus-death-rate/")

(def ref-rober-koch
  "https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/nCoV.html")

(def ref-3blue1brown-exp-growth
  "https://youtu.be/Kas0tIxDvrg")

(def ref-age-sex
  "https://www.worldometers.info/coronavirus/coronavirus-age-sex-demographics/")

(defn link [name url] (str "[" name "]""(" url ")"))

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
                   (link "COVID-19 (Coronavirus SARS-CoV-2)" ref-rober-koch)))
   (format "%s\n%s"
           "A bit of 👨‍🏫 math doesn't kill anyone!"
           (format "3Blue1Brown: %s"
                   (link "Exponential growth and epidemics"
                         ref-3blue1brown-exp-growth)))

   (format "%s\n%s\n%s"
           "Worldometer - COVID-19 Coronavirus"
           (link "Coronavirus Age Sex Demographics" ref-age-sex)
           (link "Mortality rate" ref-mortality-rate))

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

(def max-continent-name-len
  (->> (all-affected-continent-codes)
       (map (fn [cc] (co/continent-name cc)))
       (sort-by count)
       last
       count))

(defn list-continents
    "TODO show counts for every continent"
    [prm]
    (format
     "Continent(s) hit:\n\n%s\n\n%s"
     ;; "Countries hit:\n\n<pre>%s</pre>\n\n%s"
     (s/join "\n"
             (->> (all-affected-continent-codes)
                  (map (fn [cc] (format "<code style=\"color:red;\">%s  </code>%s"
                                        (c/right-pad (co/continent-name cc)
                                                     max-continent-name-len)
                                        (->> cc
                                             encode-cmd
                                             s/lower-case))))))
     (footer prm)))

(defn header [{:keys [day parse_mode] :as prm}]
  (format
   (str
    (condp = parse_mode
      "HTML" "<b>%s</b>"
      ;; i.e. "Markdown"
      "*%s*")
    " %s")
   (tf/unparse (tf/with-zone (tf/formatter "dd MMM yyyy")
                 (t/default-time-zone)) (tc/from-date day))
   (condp = parse_mode
     "HTML" c/bot-name
     ;; i.e. "Markdown"
     (s/replace c/bot-name #"_" "\\\\_"))))

(defn list-countries [{:keys [data] :as prm}]
  (let [separator " "]
    (format
     (format (str "%s\n" ;; header
                  "%s "  ;; sick
                  "%s"   ;; separator
                  "%s "  ;; recovered
                  "%s"   ;; separator
                  "%s\n" ;; deaths
                  "%s")
             (header (assoc prm :day (:f data/last-day)))
             s-sick
             separator
             s-recovered
             separator
             s-deaths
             "%s\n\nTotal countries hit: %s\n\n%s")
     #_(co/continent-name continent-code)
     (s/join
      "\n"
      (->> data
           #_(take-last 11)
           (map (fn [data-country]
                  (let [{ill :i recovered :r deaths :d
                         country-name :cn country-code :cc} data-country]
                    (format "<code>%s</code>%s<code>%s</code>%s<code>%s</code> <code>%s</code>  %s"
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
     (count (data/all-affected-country-codes))
     (footer prm))))

(defn info [{:keys [country-code] :as prm}]
  (let [last-day (data/last-day prm)
        {day :f} last-day]
    (format
     "%s\n%s\n%s"
     (str
      (header (assoc prm :day day))
      "  "
      (c/country-name country-code) " "
      (apply (fn [cc ccc] (format "     %s    %s" cc ccc))
             (map (fn [s] (->> s s/lower-case encode-cmd))
                  [country-code
                   (c/country-code-3-letter country-code)])))

     (let [{confirmed :c} last-day]
       (str
        s-confirmed ": " confirmed "\n"
        (if (pos? confirmed)
          (let [{deaths :d recovered :r ill :i} last-day
                closed (+ deaths recovered)]
            (str
             (fmt s-sick      ill       confirmed "")
             (fmt s-recovered recovered confirmed "")
             (fmt s-deaths    deaths    confirmed
                  (format "      See %s and %s"
                          (link "mortality rate" ref-mortality-rate)
                          (encode-cmd s-references)))
             (fmt s-closed    closed    confirmed
                  (format "= %s + %s"
                          (s/lower-case s-recovered)
                          (s/lower-case s-deaths))))))))

     (footer prm))))

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
                (format "%s: %s; see %s"
                        c/bot-name
                        (c/country-name country-code)
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
                        (link "maty535" "https://github.com/maty535")
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
    (link "GitHub" "https://github.com/Bost/corona_cases") ", "
    (link "GitLab" "https://gitlab.com/rostislav.svoboda/corona_cases"))
   "\n"
   "Percentage calculation: <cases> / confirmed\n"

   #_(str
    "- Feb12-spike caused mainly by a change in the diagnosis classification"
    " of the Hubei province.\n")

   #_(str
    "- Data retrieved *CONTINUOUSLY* every " corona.api/time-to-live
    " minutes from " (link data/host data/url) ".\n")

   (str
    "Useful visualizations: " (link "JHU CSSE" "https://arcg.is/0fHmTX") ", "
    (link "Worldometer"
          "https://www.worldometers.info/coronavirus/coronavirus-cases/")
    "\n")

   (format "%s master branch %s\n"
           (link "CSSEGISandData/COVID-19"
                 "https://github.com/CSSEGISandData/COVID-19.git")
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
          "https://en.wikipedia.org/wiki/ISO_3166-1#Current_codes"))

   ;; TODO home page; average recovery time
   #_(str
      "\n"
      " - " (link "Home page" home-page))
   (footer prm)))

(def bot-description
  "Keep it in sync with README.md"
  "Coronavirus disease 2019 (COVID-19) information on Telegram Messenger")

(defn bot-father-edit-description [] bot-description)
(defn bot-father-edit-about [] bot-description)
