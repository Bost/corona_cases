(ns corona.continents
  "See
  https://github.com/dathere/covid19-time-series-utilities/blob/master/covid19-refine/workdir/location-lookup/location-lookup.csv
  https://github.com/EtienneCmb/COVID-19/blob/master/report/continents.json
  https://datahub.io/core/continent-codes#resource-continent-codes
  ")

(def default-continent-code "CCC")

(def continent-names--continent-codes-hm
  "
  2-letter alpha code:
  NA clashes for \"Namibia\" and \"North America\".
  https://datahub.io/core/continent-codes#resource-continent-codes

  3-letter alpha codes:
  \"NAM\" would clash with \"Namibia\" - grr, again!
  \"ANT\" clashes with \"Netherlands Antilles\"

  (clojure.set/map-invert continent-names--continent-codes-hm)
  "
  {
   "Africa"        "AFR"
   "North America" "NAC"  ;; NAM clashes with \"Namibia\"
   "Oceania"       "OCE"
   "Antarctica"    "ANA"  ;; ANT clashes with \"Netherlands Antilles\"
   "Asia"          "ASI"
   "Europe"        "EUR"
   "South America" "SAC"
   "Cruise Ship"   default-continent-code
   }
  )
