(ns corona.common
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.string :as s]
            [corona.core :as c :refer [in?]]
            [corona.lang :refer :all]
            [corona.countries :as cr]
            [corona.country-codes :refer :all]
            [corona.defs :as d]))

(defn encode-cmd [s] (str "/" s))

(defn encode-pseudo-cmd [s parse_mode]
  (if (= parse_mode "HTML")
    (let [s (s/replace s "<" "&lt;")
          s (s/replace s ">" "&gt;")]
      s)
    s))

(defn fmt-date [date]
  (tf/unparse (tf/with-zone (tf/formatter "dd MMM yyyy")
                (t/default-time-zone))
              (tc/from-date date)))

(def threshold
  " Country w/ the number of cases less than the threshold are grouped into
  \"Rest\".
  TODO implement adaptive threshold so that max 10 countries are shown in the
  graph.
  "
  20000)

(def sorry-ws
  (str
   "Just found out: quite many countries are located on more than one "
   "continent. The hash map continent-code -> country-codes was buggy. "
   "This service won't be supported anymore. At least not in the near future. "
   "Apologies for serving you misleading information previously."
   ""))

;; TODO evaluate web services
;; https://sheets.googleapis.com/v4/spreadsheets/1jxkZpw2XjQzG04VTwChsqRnWn4-FsHH6a7UHVxvO95c/values/Dati?majorDimension=ROWS&key=AIzaSyAy6NFBLKa42yB9KMkFNucI4NLyXxlJ6jQ

;; https://github.com/iceweasel1/COVID-19-Germany

(def api-data-source
  "jhu"

  ;; csbs throws:
  ;; Execution error (ArityException) at cljplot.impl.line/eval34748$fn (line.clj:155).
  ;; Wrong number of args (0) passed to: cljplot.common/fast-max
  #_"csbs")

(def heroku-host-api-server
  "covid-tracker-us.herokuapp.com"
  #_"coronavirus-tracker-api.herokuapp.com")

(def api-server  (cond (or c/env-prod? c/env-test?) heroku-host-api-server
                       :else                        "localhost:8000"))

(def time-to-live "In minutes" 15)
