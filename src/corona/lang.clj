(ns corona.lang
  (:require
   [corona.common :as com]
   [clojure.string :as s]))

;; A文 doesn't get displayed blue as a telegram command. Hmm
;; :language     "lang"
(def world         "world")
(def world-desc    "Start here")
(def start         "start")
(def explain       "explain")
(def contributors  "contributors")
(def feedback      "feedback")

(def conf            "Conf")
(def confirmed       "Confirmed")
(def confirmed-cases "Confirmed cases")

(def deaths         "Deaths")
(def death-cases    "Death cases")

(def recov           "Recov")
(def recovered       "Recovered")
(def recovered-cases "Recovered cases")

(def active         "Active")
(def active-cases   "Active cases")
(def hundred-k      "100k")

(def cmd-active-per-1e5    (str "a" hundred-k))
(def cmd-recovered-per-1e5 (str "r" hundred-k))
(def cmd-deaths-per-1e5    (str "d" hundred-k))
(def cmd-closed-per-1e5    (str "c" hundred-k))

(def active-per-1e5
  "Active cases per 100 000"
  (str "Act" hundred-k))
(def recovered-per-1e5
  "Recovered cases per 100 000"
  (str "Rec" hundred-k))
(def deaths-per-1e5
  "Deaths per 100 000"
  (str "Dea" hundred-k))
(def closed-per-1e5
  "Closed per 100 000"
  (str "Clo" hundred-k))

(def closed        "Closed")
(def closed-cases  "Closed cases")

(def day
  "Coincidentally there seems to be 1 report per day"
  "Day\\Report")
(def sick-absolute "Active absolute")
(def absolute      "absolute")
(def people        "People") ;; "Population" is too long see `fmt-to-cols`
(def stats         "Stats")

(def list-desc     "List of countries")
(def listings      "lists")

;; TODO
;; https://en.wikipedia.org/wiki/Moving_average#Moving_median
;; simple moving median SMM

;; https://en.wikipedia.org/wiki/Moving_average#Simple_moving_average
;; simple moving average SMA

(def active-last-7-med
  "Active cases in last 7 reports - simple moving Median rounded"
  "ActL7Med")

(def active-last-7-avg
  "Active cases in last 7 reports - simple moving Average rounded"
  "ActL7Avg")

(def active-last-8th
  "Active cases of the last 8th report"
  "ActL8th")

(def active-change-last-7-avg
  "Active cases Change - simple moving Average of last 7 values"
  "ActCL7Avg")

(def millions-rounded "Mill")

(defn list-sorted-by [case-kw]
  (->> [conf recov deaths active
        cmd-active-per-1e5 cmd-recovered-per-1e5 cmd-deaths-per-1e5]
       (map s/lower-case)
       (zipmap com/basic-cases)
       case-kw))

(defn list-sorted-by-desc [case-kw]
  (format "Countries sorted by nr. of %s" ;; "... in ascending order"
          (->> [confirmed-cases recovered-cases deaths
                active-cases
                active-per-1e5
                recovered-per-1e5
                deaths-per-1e5
                ]
               (map s/lower-case)
               (zipmap com/basic-cases)
               case-kw)))

(def buttons "Shortened button names"
  (zipmap com/absolute-cases ["Co" "Re" "De" "Ac"]))

(def type "Buttons for plot-types" {:sum "Σ" :abs "A"})

;; (def language     (:language     lang-strings))
;; (def cmd-country  (format "<%s>" (:country lang-strings)))

;; (def lang-de "lang:de")
