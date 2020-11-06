(printf "Current-ns [%s] loading %s\n" *ns* 'corona.lang)

(ns corona.lang
  (:require
   [corona.common :as co]
   [clojure.string :as s]
   ))

;; (set! *warn-on-reflection* true)

;; A文 doesn't get displayed blue as a telegram command. Hmm
;; :language     "lang"
(def ^:const percentage-calc "Percentage calculation")
(def ^:const cases           "cases")
(def ^:const randking-desc   "Ranking on the list of all %s countries:\n%s")
(def ^:const world           "world")
(def ^:const world-desc      "Start here")
(def ^:const start           "start")
(def ^:const explain         "explain")
(def ^:const contributors    "contributors")
(def ^:const feedback        "feedback")
(def ^:const conf            "Conf")
(def ^:const confirmed       "Confirmed")
(def ^:const confirmed-cases "Confirmed cases")
(def ^:const deaths          "Deaths")
(def ^:const death-cases     "Death cases")
(def ^:const recov           "Recov")
(def ^:const recovered       "Recovered")
(def ^:const recov-estim     "Recov. estimated")
(def ^:const recovered-cases "Recovered cases")
(def ^:const active          "Active")
(def ^:const active-cases    "Active cases")
(def ^:const estimated       "Estimated")
(def ^:const estimated-cases "Estimated cases")
(def ^:const hundred-k       "100k")
(def ^:const cmd-active-per-1e5 (str "a" hundred-k))
(def ^:const cmd-recove-per-1e5 (str "r" hundred-k))
(def ^:const cmd-deaths-per-1e5 (str "d" hundred-k))
(def ^:const cmd-closed-per-1e5 (str "c" hundred-k))
(def ^:const cases-per-1e5 "Cases per 100 000 people")

(def ^:const active-per-1e5
  "Active cases per 100 000"
  (str "Act" hundred-k))
(def ^:const recove-per-1e5
  "Recovered cases per 100 000"
  (str "Rec" hundred-k))
(def ^:const deaths-per-1e5
  "Deaths per 100 000"
  (str "Dea" hundred-k))
(def ^:const closed-per-1e5
  "Closed per 100 000"
  (str "Clo" hundred-k))

(def ^:const active-max
  "Peak of active cases from all reports"
  "ActMax")

(def ^:const closed        "Closed")
(def ^:const closed-cases  "Closed cases")

;; TODO rename day -> report
(def ^:const day
  "Coincidentally there is 1 report per day"
  "Day\\Report")
(def ^:const sick-absolute "Active absolute")
(def ^:const absolute      "absolute")
(def ^:const people        "People") ;; "Population" is too long see `fmt-to-cols`
(def ^:const stats         "Stats")
(def ^:const list-desc     "List of countries")
(def ^:const listings      "lists")

;; https://en.wikipedia.org/wiki/Moving_average#Moving_median
;; simple moving median SMM

;; https://en.wikipedia.org/wiki/Moving_average#Simple_moving_average
;; simple moving average SMA

(def ^:const active-last-7
  "Active cases in last 7 reports"
  "ActL7")

(def ^:const active-last-7-med
  "Active cases in last 7 reports - simple moving Median rounded"
  (str active-last-7 "Med"))

(def ^:const active-last-7-avg
  "Active cases in last 7 reports - simple moving Average rounded"
  (str active-last-7 "Avg"))

(def ^:const active-last-8th
  "Active cases of the last 8th report"
  "ActL8th")

(def ^:const active-change-last-7-avg
  "Active cases Change - simple moving Average of last 7 values"
  "ActCL7Avg")

(def ^:const millions-rounded "Mill")

(defn list-sorted-by [case-kw]
  (->> [conf recov deaths active
        cmd-active-per-1e5 cmd-recove-per-1e5 cmd-deaths-per-1e5]
       (map s/lower-case)
       (zipmap co/basic-cases)
       case-kw))

(defn list-sorted-by-desc [case-kw]
  (format "Countries sorted by nr. of %s" ;; "... in ascending order"
          (case-kw (zipmap co/basic-cases
                           (map s/lower-case
                                [confirmed-cases recovered-cases deaths
                                 active-cases
                                 active-per-1e5
                                 recove-per-1e5
                                 deaths-per-1e5])))))

(def ^:const buttons "Shortened button names"
  (zipmap co/absolute-cases ["Co" "Re" "De" "Ac"]))

(def ^:const plot-type "Buttons for plot-types" {:sum "Σ" :abs "A"})

;; (def ^:const language     (:language     lang-strings))
;; (def ^:const cmd-country  (format "<%s>" (:country lang-strings)))

;; (def ^:const lang-de "lang:de")

(printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.lang)
