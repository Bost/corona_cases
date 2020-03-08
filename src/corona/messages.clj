(ns corona.messages
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.string :as s]
            [com.hypirion.clj-xchart :as chart]
            [corona.api :as a]
            [corona.interpolate :as i]
            [corona.core :as c]))

(def cmd-s-about "about")
(def s-confirmed "Confirmed")
(def s-deaths    "Deaths")
(def s-recovered "Recovered")
(def s-sick      "Sick")
(def s-closed    "Closed")

(def home-page
  ;; TODO (env :home-page)
  "https://corona-cases-bot.herokuapp.com/")

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

(defn footer [{:keys [cmd-names]}]
  (let [spacer "   "]
    (str "Try" spacer (s/join spacer (map c/encode-cmd cmd-names)))))

(defn info [{:keys [country-code] :as prm}]
  (let [last-day (a/last-day prm)
        {day :f} last-day]
    (format
     "%s\n%s\n%s"
     (str
      "*" (tf/unparse (tf/with-zone (tf/formatter "dd MMM yyyy")
                        (t/default-time-zone)) (tc/from-date day))
      "*    " (get c/is-3166-names country-code) " "
      (apply (fn [cc ccc] (format "       %s      %s" cc ccc))
             (map (fn [s] (->> s s/lower-case c/encode-cmd))
                  [country-code
                   (get c/is-3166-abbrevs country-code)])))

     (let [{confirmed :c} last-day]
       (str
        s-confirmed ": " confirmed "\n"
        (if (pos? confirmed)
          #_(in? (a/affected-country-codes) country-code)
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
  (let [confirmed (a/confirmed prm)
        deaths    (a/deaths    prm)
        recovered (a/recovered prm)
        ill       (a/ill       prm)
        dates     (a/dates)]
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
                (format "%s: %s; see /%s"
                        c/bot-name
                        (get c/is-3166-names country-code)
                        cmd-s-about)
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
                        "@kostanjsek"])
          (str
           "The rest of the contributors prefer anonymity or haven't "
           "approved their inclusion to this list yet. 🙏 Thanks you folks.")
          (footer prm)))

(defn interpolated-vals [{:keys [country] :as prm}]
  (i/create-pic
   (str c/bot-name ": " country
        " interpolation - " s-sick "; see /"
        cmd-s-about)
   (a/ill prm)))

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

   (str
    "- Data retrieved *CONTINUOUSLY* every " corona.api/time-to-live
    " minutes from " (link a/host a/url) ".\n")

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
     "\n"
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
