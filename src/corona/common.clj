(ns corona.common
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.string :as s]
            [corona.core :as c :refer [in?]]
            [corona.countries :as cr]
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
  "Country w/ the number of cases less than the threshold are grouped into
  \"Others\"."
  15000)

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

(def api-server
  ;; "localhost:8000"
  "covid-tracker-us.herokuapp.com"
  #_"coronavirus-tracker-api.herokuapp.com")

(def time-to-live "In minutes" 15)

(defn lower-case
  "ABC -> abc"
  [hm]
  (into {} (map (fn [[k v]] [(s/lower-case k) v]) hm)))

(defn country-code
  "Return two letter country code (Alpha-2) according to
  https://en.wikipedia.org/wiki/ISO_3166-1
  Defaults to `default-2-country-code`."
  [country-name]
  (let [country (s/lower-case country-name)
        lcases-countries (lower-case cr/country--country-code)]
    (if-let [cc (get lcases-countries country)]
      cc
      (if-let [country-alias (get (lower-case cr/aliases-hm) country)]
        (get cr/country--country-code country-alias)
        (do (println (format
                      "No country code found for \"%s\". Using \"%s\""
                      country-name
                      d/default-2-country-code))
            d/default-2-country-code)))))

(defn country-name
  "Country name from 2-letter country code: \"DE\" -> \"Germany\" "
  [cc]
  (get cr/country-code--country cc))

(defn country-alias
  "Get a country alias or the normal name if an alias doesn't exist"
  [cc]
  (let [up-cc (s/upper-case cc)
        country (get cr/country-code--country up-cc)]
    (get cr/aliases-inverted up-cc country)))

(defn country-name-aliased
  "Use an alias; typically a shorter name for some countries"
  [cc]
  (if (in? [
            "VA" "TW" "DO" "IR" "RU" "PS" "AE" "KR" "MK" "BA" "CD" "BO"
            "MD" "BN" "VE" "VC" "KP" "TZ" "XK" "LA" "SY" "KN" "TT" "AG"
            "CF" #_"CZ"
            ]
           cc)
    (country-alias cc)
    (country-name cc)))

