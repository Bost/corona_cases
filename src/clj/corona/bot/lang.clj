(ns corona.bot.lang
  (:require [corona.bot.common :as com]
            [clojure.string :as s]))

;; A文 doesn't get displayed blue as a telegram command. Hmm
;; :language     "lang"
(def world         "world")
(def world-desc    "Start here")
(def start         "start")
(def about         "about")
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

(def sick          "Active")
(def sick-cases    "Active cases")
(def sick-per-1e5  "Act/100k")

(def closed        "Closed")
(def closed-cases  "Closed cases")

(def day
  "Coincidentally there seems to be 1 report per day"
  "Day\\Report")
(def sick-absolute "Active absolute")
(def absolute      "absolute")
(def population    "People") ;; "Population" is too long see `fmt-to-cols`
(def stats         "Stats")

(def list-desc     "List of countries")

(def floating-avg
  "Active cases Change over last 7 Reports - floating Average"
  "ASCAvg7r")

(def sick-today    "ActiveNow")
(def sick-week-ago "Active7ReportsAgo")
(def millions-rounded "Mill")

(defn list-sorted-by [case-kw]
  (->> [conf recov deaths sick]
       (map s/lower-case)
       (zipmap com/all-crdi-cases)
       case-kw))

(defn list-sorted-by-desc [case-kw]
  (format "Countries sorted by nr. of %s" ;; "... in ascending order"
          (->> [confirmed-cases recovered-cases deaths sick-cases]
               (map s/lower-case)
               (zipmap com/all-crdi-cases)
               case-kw)))

(def buttons "Shortened button names"
  (zipmap com/all-crdi-cases ["Co" "Re" "De" "Ac"]))

(def type "Buttons for plot-types" {:sum "Σ" :abs "A"})

;; (def language     (:language     lang-strings))
;; (def cmd-country  (format "<%s>" (:country lang-strings)))

;; (def lang-de "lang:de")
