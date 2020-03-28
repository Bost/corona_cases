(ns corona.continents
  "See
  https://github.com/dathere/covid19-time-series-utilities/blob/master/covid19-refine/workdir/location-lookup/location-lookup.csv
  https://github.com/EtienneCmb/COVID-19/blob/master/report/continents.json
  https://datahub.io/core/continent-codes#resource-continent-codes
  "
  (:require [corona.defs :as d]))

(def continent-names--continent-codes-hm
  "
  2-letter alpha code:
  NA would clashes with the codes of Namibia and North America.
  https://datahub.io/core/continent-codes#resource-continent-codes

  3-letter alpha codes:
  NAM would clash with the code for Namibia
  ANT would clash with the code for Netherlands Antilles

  (clojure.set/map-invert continent-names--continent-codes-hm)
  "
  {
   "Africa"        "AFR"
   "North America" "NAC"  ;; NAM would clash with the code for Namibia
   "Oceania"       "OCE"
   "Antarctica"    "ANA"  ;; ANT would clash with the code for Netherlands Antilles
   "Asia"          "ASI"
   "Europe"        "EUR"
   "South America" "SAC"
   d/others        d/default-2-country-code
   })
