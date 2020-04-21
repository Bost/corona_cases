(ns corona.lang)

;; A文 doesn't get displayed blue as a telegram command. Hmm
;; :language     "lang"
(def s-world         "world")
(def s-world-desc    "Start here")
(def s-start         "start")
(def s-list          "list")
(def s-list-desc     "List of countries")
(def s-about         "about")
(def s-contributors  "contributors")
(def s-references    "references")
(def s-feedback      "feedback")
(def s-confirmed     "Confirmed")
(def s-conf          "Conf")
(def s-deaths        "Deaths")
(def s-recovered     "Recovered")
(def s-recov         "Recov")
(def s-sick          "Sick")
(def s-closed        "Closed")
(def s-sick-absolute "country")
(def s-day           "Day")
(def s-sick-absolute "Sick absolute")
(def s-absolute      "absolute")
(def s-population    "Population")
(def s-stats         "Stats")
(def s-sick-cases    "Sick cases")

(def s-buttons
  "Shortened button names"
  {
   :c s-conf
   :i s-sick
   :r s-recov
   :d s-deaths
   })

(def s-type
  "Buttons for plot-types"
  {
   :sum "sum"
   :abs "abs"
   })

;; (def s-language     (:language     lang-strings))
;; (def cmd-s-country  (format "<%s>" (:country lang-strings)))

;; (def lang-de "lang:de")
