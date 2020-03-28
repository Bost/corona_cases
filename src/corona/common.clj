(ns corona.common
  (:require [clojure.set :as cset]
            [clojure.string :as s]
            [corona.api.expdev07 :as data]
            [corona.continents :as cn]
            [corona.core :as c :refer [in?]]
            [corona.countries :as cr]
            [corona.defs :as d]))

(def continent-countries-hm
  "
  https//en.wikipedia.org/wiki/List-of-sovereign-states-and-dependent-territories-by-continent-(data-file)
  https//en.wikipedia.org/wiki/List-of-sovereign-states-and-dependent-territories-by-continent
  "
  {
   "AF" ["ASI"]
   "XK" ["EUR"]
   "AL" ["EUR"]
   "AQ" ["ANA"]
   "DZ" ["AFR"]
   "AS" ["OCE"]
   "AD" ["EUR"]
   "AO" ["AFR"]
   "AG" ["NAC"]
   "AZ" ["EUR" "ASI"]
   "AR" ["SAC"]
   "AU" ["OCE"]
   "AT" ["EUR"]
   "BS" ["NAC"]
   "BH" ["ASI"]
   "BD" ["ASI"]
   "AM" ["EUR" "ASI"]
   "BB" ["NAC"]
   "BE" ["EUR"]
   "BM" ["NAC"]
   "BT" ["ASI"]
   "BO" ["SAC"]
   "BA" ["EUR"]
   "BW" ["AFR"]
   "BV" ["ANA"]
   "BR" ["SAC"]
   "BZ" ["NAC"]
   "IO" ["ASI"]
   "SB" ["OCE"]
   "VG" ["NAC"]
   "BN" ["ASI"]
   "BG" ["EUR"]
   "MM" ["ASI"]
   "BI" ["AFR"]
   "BY" ["EUR"]
   "KH" ["ASI"]
   "CM" ["AFR"]
   "CA" ["NAC"]
   "CV" ["AFR"]
   "KY" ["NAC"]
   "CF" ["AFR"]
   "LK" ["ASI"]
   "TD" ["AFR"]
   "CL" ["SAC"]
   "CN" ["ASI"]
   "TW" ["ASI"]
   "CX" ["ASI"]
   "CC" ["ASI"]
   "CO" ["SAC"]
   "KM" ["AFR"]
   "YT" ["AFR"]
   "CG" ["AFR"]
   "CD" ["AFR"]
   "CK" ["OCE"]
   "CR" ["NAC"]
   "HR" ["EUR"]
   "CU" ["NAC"]
   "CY" ["EUR" "ASI"]
   "CZ" ["EUR"]
   "BJ" ["AFR"]
   "DK" ["EUR"]
   "DM" ["NAC"]
   "DO" ["NAC"]
   "EC" ["SAC"]
   "SV" ["NAC"]
   "GQ" ["AFR"]
   "ET" ["AFR"]
   "ER" ["AFR"]
   "EE" ["EUR"]
   "FO" ["EUR"]
   "FK" ["SAC"]
   "GS" ["ANA"]
   "FJ" ["OCE"]
   "FI" ["EUR"]
   "AX" ["EUR"]
   "FR" ["EUR"]
   "GF" ["SAC"]
   "PF" ["OCE"]
   "TF" ["ANA"]
   "DJ" ["AFR"]
   "GA" ["AFR"]
   "GE" ["EUR" "ASI"]
   "GM" ["AFR"]
   "DE" ["EUR"]
   "GH" ["AFR"]
   "GI" ["EUR"]
   "KI" ["OCE"]
   "GR" ["EUR"]
   "GL" ["NAC"]
   "GD" ["NAC"]
   "GP" ["NAC"]
   "GU" ["OCE"]
   "GT" ["NAC"]
   "GN" ["AFR"]
   "GY" ["SAC"]
   "HT" ["NAC"]
   "HM" ["ANA"]
   "VA" ["EUR"]
   "HN" ["NAC"]
   "HK" ["ASI"]
   "HU" ["EUR"]
   "IS" ["EUR"]
   "IN" ["ASI"]
   "ID" ["ASI"]
   "IR" ["ASI"]
   "IQ" ["ASI"]
   "IE" ["EUR"]
   "IL" ["ASI"]
   "IT" ["EUR"]
   "CI" ["AFR"]
   "JM" ["NAC"]
   "JP" ["ASI"]
   "KZ" ["EUR" "ASI"]
   "JO" ["ASI"]
   "KE" ["AFR"]
   "KP" ["ASI"]
   "KR" ["ASI"]
   "KW" ["ASI"]
   "KG" ["ASI"]
   "LA" ["ASI"]
   "LB" ["ASI"]
   "LS" ["AFR"]
   "LV" ["EUR"]
   "LR" ["AFR"]
   "LY" ["AFR"]
   "LI" ["EUR"]
   "LT" ["EUR"]
   "LU" ["EUR"]
   "MO" ["ASI"]
   "MG" ["AFR"]
   "MW" ["AFR"]
   "MY" ["ASI"]
   "MV" ["ASI"]
   "ML" ["AFR"]
   "MT" ["EUR"]
   "MQ" ["NAC"]
   "MR" ["AFR"]
   "MU" ["AFR"]
   "MX" ["NAC"]
   "MC" ["EUR"]
   "MN" ["ASI"]
   "MD" ["EUR"]
   "ME" ["EUR"]
   "MS" ["NAC"]
   "MA" ["AFR"]
   "MZ" ["AFR"]
   "OM" ["ASI"]
   "NA" ["AFR"]
   "NR" ["OCE"]
   "NP" ["ASI"]
   "NL" ["EUR"]
   "AN" ["NAC"]
   "CW" ["NAC"]
   "AW" ["NAC"]
   "SX" ["NAC"]
   "BQ" ["NAC"]
   "NC" ["OCE"]
   "VU" ["OCE"]
   "NZ" ["OCE"]
   "NI" ["NAC"]
   "NE" ["AFR"]
   "NG" ["AFR"]
   "NU" ["OCE"]
   "NF" ["OCE"]
   "NO" ["EUR"]
   "MP" ["OCE"]
   "UM" ["OCE" "NAC"]
   "FM" ["OCE"]
   "MH" ["OCE"]
   "PW" ["OCE"]
   "PK" ["ASI"]
   "PS" ["ASI"]
   "PA" ["NAC"]
   "PG" ["OCE"]
   "PY" ["SAC"]
   "PE" ["SAC"]
   "PH" ["ASI"]
   "PN" ["OCE"]
   "PL" ["EUR"]
   "PT" ["EUR"]
   "GW" ["AFR"]
   "TL" ["ASI"]
   "PR" ["NAC"]
   "QA" ["ASI"]
   "RE" ["AFR"]
   "RO" ["EUR"]
   "RU" ["EUR" "ASI"]
   "RW" ["AFR"]
   "BL" ["NAC"]
   "SH" ["AFR"]
   "KN" ["NAC"]
   "AI" ["NAC"]
   "LC" ["NAC"]
   "MF" ["NAC"]
   "PM" ["NAC"]
   "VC" ["NAC"]
   "SM" ["EUR"]
   "ST" ["AFR"]
   "SA" ["ASI"]
   "SN" ["AFR"]
   "RS" ["EUR"]
   "SC" ["AFR"]
   "SL" ["AFR"]
   "SG" ["ASI"]
   "SK" ["EUR"]
   "VN" ["ASI"]
   "SI" ["EUR"]
   "SO" ["AFR"]
   "ZA" ["AFR"]
   "ZW" ["AFR"]
   "ES" ["EUR"]
   "SS" ["AFR"]
   "SD" ["AFR"]
   "EH" ["AFR"]
   "SR" ["SAC"]
   "SJ" ["EUR"]
   "SZ" ["AFR"]
   "SE" ["EUR"]
   "CH" ["EUR"]
   "SY" ["ASI"]
   "TJ" ["ASI"]
   "TH" ["ASI"]
   "TG" ["AFR"]
   "TK" ["OCE"]
   "TO" ["OCE"]
   "TT" ["NAC"]
   "AE" ["ASI"]
   "TN" ["AFR"]
   "TR" ["EUR" "ASI"]
   "TM" ["ASI"]
   "TC" ["NAC"]
   "TV" ["OCE"]
   "UG" ["AFR"]
   "UA" ["EUR"]
   "MK" ["EUR"]
   "EG" ["AFR"]
   "GB" ["EUR"]
   "GG" ["EUR"]
   "JE" ["EUR"]
   "IM" ["EUR"]
   "TZ" ["AFR"]
   "US" ["NAC"]
   "VI" ["NAC"]
   "BF" ["AFR"]
   "UY" ["SAC"]
   "UZ" ["ASI"]
   "VE" ["SAC"]
   "WF" ["OCE"]
   "WS" ["OCE"]
   "YE" ["ASI"]
   "ZM" ["AFR"]
   "XD" ["ASI"]
   "XE" ["ASI"]
   "XS" ["ASI"]
   ;; TODO "XX - Disputed Territory" conflicts with `default-country-code`
   ;; "XX" ["OCE"]
   d/default-2-country-code d/default-continent-codes
   })

(defn continent-code [continent]
  (get cn/continent-names--continent-codes-hm continent))

(defn continent-name [continent-code]
  (get (cset/map-invert cn/continent-names--continent-codes-hm)
       continent-code))

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

(defn continent-code--country-codes [continent-code]
  (->> continent-countries-hm
       (filter (fn [[country-c continent-cs]] (in? continent-cs continent-code)))
       (mapv (fn [[country-c _]] country-c))))

(defn all-continent-codes--country-codes
  "Turkey and Russia are in Asia and Europe. Assigned to Europe.
  TODO countries on multiple continents
  TODO use group-by"
  []
  (->> cn/continent-names--continent-codes-hm
       (map (fn [[_ ccode]]
              {ccode (continent-code--country-codes ccode)}))
       (into {})))

(defn all-continent-names--country-names
  []
  (->> cn/continent-names--continent-codes-hm
       (map (fn [[cname ccode]]
              {cname (->> (continent-code--country-codes ccode)
                          (mapv country-name))}))
       (into {})))

(defn country-code--continet-code [country-code]
  (->> (all-continent-codes--country-codes)
       (keep (fn [[continent-code country-codes]]
                 (if (in? country-codes country-code)
                   continent-code)))
       first))

(defn all-affected-country-codes []
  (data/all-affected-country-codes))

(defn all-affected-country-names
  "TODO remove worldwide"
  []
  (->> (all-affected-country-codes)
       (map country-name)))

(defn all-affected-continent-codes
  "All continents except Antarctica are affected now.
  TODO implement quick checking for Antarctica"
  []
  ["EUR" "ASI" "NAC" "SAC" "OCE" "AFR" "CCC"]
  #_(->> (all-affected-country-codes)
       #_(take 1)
       #_(map (fn [cc] [cc (country-code--continet-code cc)]))
       #_(filter (fn [[cc ccc]] (nil? ccc)))
       (map country-code--continet-code)
       distinct))

(defn all-affected-continent-names
  "TODO worldwide"
  []
  (->> (all-affected-continent-codes)
       (map continent-name)))

(def max-affected-continent-name-len
  (->> (all-affected-continent-names)
       (sort-by count)
       last
       count))

(defn country-name-aliased [cc]
  (if (in? (all-affected-continent-codes) cc)
    (continent-name cc)
    (if (in? ["VA" "TW" "DO" "IR" "RU" "PS" "AE" "KR" "MK" "BA" "CD" "BO"
              "MD" "BN" "VE" "VC" "KP" "TZ" "VC" "XK" "LA" "SY" "KN"
              "TT" "AG" "CF" #_"CZ"]
             cc)
      (country-alias cc)
      (country-name cc))))

(def max-affected-country-name-len
  (->> (all-affected-country-codes)
       (map (fn [cc]
              (country-name-aliased cc )
              #_(cr/country-name cc)))
       (sort-by count)
       last
       count))
