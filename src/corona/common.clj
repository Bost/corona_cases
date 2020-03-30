(ns corona.common
  (:require [clojure.set :as cset]
            [clojure.string :as s]
            [corona.api.expdev07 :as data]
            [corona.core :as c :refer [in?]]
            [corona.countries :as cr]
            [corona.defs :as d]))

(def sorry
  (str
   "Just found out: quite many countries are located on more than one "
   "continent. That makes displaying statistics for continents meaningless. "
   "Apologies for serving you misleading information previously."))

(def sorry-ws
  (str
   "Just found out: quite many countries are located on more than one "
   "continent. The hash map continent-code -> country-codes was buggy. "
   "This service won't be supported anymore. At least not in the near future. "
   "Apologies for serving you misleading information previously."
   ""))

(defn lower-case [hm]
  (->> hm
       (map (fn [[k v]] [(s/lower-case k) v]))
       (into {})))

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
  (let [country (get cr/country-code--country (s/upper-case cc))]
    (get cr/aliases-inverted country country)))

(defn all-affected-country-codes []
  (data/all-affected-country-codes))

(defn all-affected-country-names
  "TODO remove worldwide"
  []
  (->> (all-affected-country-codes)
       (map country-name)))

(defn country-name-aliased [cc]
  (if (in? ["VA" "TW" "DO" "IR" "RU" "PS" "AE" "KR" "MK" "BA" "CD" "BO"
            "MD" "BN" "VE" "VC" "KP" "TZ" "VC" "XK" "LA" "SY" "KN"
            "TT" "AG" "CF" #_"CZ"]
           cc)
    (country-alias cc)
    (country-name cc)))

(def max-affected-country-name-len
  (->> (all-affected-country-codes)
       (map (fn [cc]
              (country-name-aliased cc )
              #_(cr/country-name cc)))
       (sort-by count)
       last
       count))
