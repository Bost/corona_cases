(ns corona.messages
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.string :as s]
            [com.hypirion.clj-xchart :as chart]
            [corona.api :as data]
            [corona.interpolate :as i]
            [corona.tables :as tab]
            [corona.countries :as co]
            [corona.core :as c :refer [in?]]))

(def cmd-s-about "about")
(def s-confirmed "Confirmed")
(def s-deaths    "Deaths")
(def s-recovered "Recovered")
(def s-sick      "Sick")
(def s-closed    "Closed")

(def home-page
  ;; TODO (env :home-page)
  "https://corona-cases-bot.herokuapp.com/")

(def options {:parse_mode "Markdown" :disable_web_page_preview true})

(defn encode-cmd [s] (str "/" s))
(defn encode-pseudo-cmd [s parse_mode]
  (if (= parse_mode "HTML")
    (let [s (s/replace s "<" "&lt;")
          s (s/replace s ">" "&gt;")]
      s)
    s))

(defn all-affected-country-codes [] (data/all-affected-country-codes))

(defn affected-country-codes [continent-code]
  (->> (data/all-affected-country-codes)
       (filter (fn [country-code]
                 (in? (tab/country-codes-of-continent continent-code)
                      country-code)))
       (into #{})))

(defn all-affected-continent-codes []
  ;; Can't really use the first implementation. See the doc of `tab/regions`
  (->> (all-affected-country-codes)
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

(def ref-mortality
  "https://www.worldometers.info/coronavirus/coronavirus-death-rate/")

(defn link [name url] (str "[" name "]""(" url ")"))

(defn footer [{:keys [cmd-names parse_mode]}]
  (let [spacer "   "]
    (str "Try" spacer (->> cmd-names
                           (map encode-cmd)
                           (map (fn [cmd] (encode-pseudo-cmd cmd parse_mode)))
                           (s/join spacer)))))

(def max-country-name-len
  (->> (all-affected-country-codes)
       (map (fn [cc] (c/country-name cc)))
       (sort-by count)
       last
       count))

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

(defn list-countries [{:keys [continent-code] :as prm}]
  (format
   "% Country/-ies hit:\n\n%s\n\n%s"
   ;; "Countries hit:\n\n<pre>%s</pre>\n\n%s"
   (co/continent-name continent-code)
   (s/join "\n"
           (->>
            (affected-country-codes continent-code)
            (map (fn [cc] (format "<code style=\"color:red;\">%s</code> %s"
                                 (c/right-pad (c/country-name cc)
                                              (/ max-country-name-len 2))
                                 (->> cc
                                      encode-cmd
                                      s/lower-case))))
            (partition 1)
            (map (fn [part] (s/join " " part)))))
   (footer prm)))


(defn info [{:keys [country-code] :as prm}]
  (let [last-day (data/last-day prm)
        {day :f} last-day]
    (format
     "%s\n%s\n%s"
     (str
      "*" (tf/unparse (tf/with-zone (tf/formatter "dd MMM yyyy")
                        (t/default-time-zone)) (tc/from-date day))
      "*    " (c/country-name country-code) " "
      (apply (fn [cc ccc] (format "       %s      %s" cc ccc))
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
                  (str "    See " (link "mortality Rate" ref-mortality)))
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
                        (encode-cmd cmd-s-about))
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

(defn contributors [prm]
  (format "%s\n\n%s\n\n%s"
          (s/join "\n" ["@DerAnweiser"
                        (link "maty535" "https://github.com/maty535")
                        "@kostanjsek"
                        "@DistrictBC"])
          (str
           "The rest of the contributors prefer anonymity or haven't "
           "approved their inclusion to this list yet. 🙏 Thanks you folks.")
          (footer prm)))

(defn interpolated-vals [{:keys [country] :as prm}]
  (i/create-pic
   (str c/bot-name ": " country
        " interpolation - " s-sick "; see /"
        cmd-s-about)
   (data/ill prm)))

(defn about [prm]
  (str
   ;; escape underscores for the markdown parsing
   (s/replace c/bot-name #"_" "\\\\_")
   " version: " c/bot-ver " :  "
   (str
    (link "GitHub" "https://github.com/Bost/corona_cases") ", "
    (link "GitLab" "https://gitlab.com/rostislav.svoboda/corona_cases"))
   "\n"
   "- Percentage calculation: <cases> / confirmed.\n"

   #_(str
      "- Interpolation method: "
      (link "b-spline" "https://en.wikipedia.org/wiki/B-spline")
      "; degree of \"smoothness\" " (i/degree (interpolated-vals prm)) ".\n")

   #_(str
    "- Feb12-spike caused mainly by a change in the diagnosis classification"
    " of the Hubei province.\n")

   #_(str
    "- Data retrieved *CONTINUOUSLY* every " corona.api/time-to-live
    " minutes from " (link data/host data/url) ".\n")

   (str
    "- See also " (link "visual dashboard" "https://arcg.is/0fHmTX") ", "
    (link "worldometer"
          "https://www.worldometers.info/coronavirus/coronavirus-cases/")
    ".\n")

   (format "- Snapshot of %s master branch  /snapshot\n"
           (link "CSSEGISandData/COVID-19"
                 "https://github.com/CSSEGISandData/COVID-19.git"))

   (format
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
