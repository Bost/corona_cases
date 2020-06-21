(ns corona.lang
  (:require [corona.common :as com]
            [clojure.string :as s]))

;; A文 doesn't get displayed blue as a telegram command. Hmm
;; :language     "lang"
(def s-world         "world")
(def s-world-desc    "Start here")
(def s-start         "start")
(def s-about         "about")
(def s-contributors  "contributors")
(def s-references    "references")
(def s-feedback      "feedback")

(def s-conf            "Conf")
(def s-confirmed       "Confirmed")
(def s-confirmed-cases "Confirmed cases")

(def s-deaths         "Deaths")
(def s-death-cases    "Death cases")

(def s-recov           "Recov")
(def s-recovered       "Recovered")
(def s-recovered-cases "Recovered cases")

(def s-sick          "Sick")
(def s-sick-cases    "Sick cases")
(def s-sick-per-1e5  "Sick/100k")

(def s-closed        "Closed")
(def s-closed-cases  "Closed cases")

(def s-day           "Day")
(def s-sick-absolute "Sick absolute")
(def s-absolute      "absolute")
(def s-population    "People") ;; "Population" is too long see `fmt-to-cols`
(def s-stats         "Stats")

(def s-list          "list")
(def s-list-desc     "List of countries")

(defn s-list-sorted-by [case-kw]
  (->> [s-conf s-recov s-deaths s-sick]
       (map s/lower-case)
       (zipmap com/all-crdi-cases)
       case-kw))

(defn s-list-sorted-by-desc [case-kw]
  (format "Countries sorted by nr. of %s" ;; "... in ascending order"
          (->> [s-confirmed-cases s-recovered-cases s-deaths s-sick-cases]
               (map s/lower-case)
               (zipmap com/all-crdi-cases)
               case-kw)))

(def s-buttons "Shortened button names"
  (zipmap com/all-crdi-cases ["Co" "Re" "De" "Si"]))

(def s-type "Buttons for plot-types" {:sum "Σ" :abs "A"})

;; (def s-language     (:language     lang-strings))
;; (def cmd-s-country  (format "<%s>" (:country lang-strings)))

;; (def lang-de "lang:de")
