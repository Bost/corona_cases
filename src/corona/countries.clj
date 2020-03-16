(ns corona.countries
  (:require
   [clojure.string :as s]
   [corona.core :as c :refer [in?]]
   [clojure.string :as s]))

(def continent-continent-code-hm
  "
  TODO Conflicting 2 letter code of \"Namibia\" and \"North America\".
  https://datahub.io/core/continent-codes#resource-continent-codes
  "
  {"Africa" "AF"
   "North America" "NA"
   "Oceania" "OC"
   "Antarctica" "AN"
   "Asia" "AS"
   "Europe" "EU"
   "South America" "SA"}
  ;; (clojure.set/map-invert continent-continent-code-hm)
  ;; {
  ;;  "AF" "Africa"
  ;;  "NA" "North America"
  ;;  "OC" "Oceania"
  ;;  "AN" "Antarctica"
  ;;  "AS" "Asia"
  ;;  "EU" "Europe"
  ;;  "SA" "South America"
  ;;  }
  )

(defn continent-code [continent]
  (get continent-continent-code-hm continent))
(defn continent-name [continent-code]
  (get (clojure.set/map-invert continent-continent-code-hm) continent-code))

(def country-country-code-hm
  "Mapping of country names to alpha-2 codes.
  https://en.wikipedia.org/wiki/ISO_3166-1#Officially_assigned_code_elements"
  (clojure.set/map-invert c/is-3166-names))

(def aliases-hm
  "Mapping of alternative names, spelling, typos to the names of countries used by
  the ISO 3166-1 norm.

  Conjoin \"North Ireland\" on \"United Kingdom\".

  https://en.wikipedia.org/wiki/Channel_Islands
  \"Guernsey\" and \"Jersey\" form \"Channel Islands\". Conjoin \"Guernsey\" on \"Jersey\".
  \"Jersey\" has higher population.

  \"Others\" has no mapping.

  TODO \"Macau\" is probably just misspelled \"Macao\". Report it to CSSEGISandData/COVID-19.
  "
  {
   "World"           (->> c/country-code-worldwide vals first)
   "Czechia"          "Czech Republic"
   "Mainland China"   "China"
   "South Korea"      "Korea, Republic of"

   "Taiwan"                         "Taiwan, Province of China"
   "Taiwan*"                        "Taiwan, Province of China"
   "Taipei and environs"            "Taiwan, Province of China"

   "US"               "United States"

   ;; TODO Macau is probably just misspelled Macao. Report it to CSSEGISandData/COVID-19
   "Macau"            "Macao"

   "Vietnam"          "Viet Nam"
   "UK"               "United Kingdom"
   "Russia"           "Russian Federation"
   "Iran"             "Iran, Islamic Republic of"
   "Saint Barthelemy" "Saint Barthélemy"

   "Palestine"          "Palestine, State of"
   "State of Palestine" "Palestine, State of"

   "Vatican City"     "Holy See (Vatican City State)"

   "DR Congo"                         "Congo, the Democratic Republic of the"
   "Congo (Kinshasa)"                 "Congo, the Democratic Republic of the"
   "Democratic Republic of the Congo" "Congo, the Democratic Republic of the"

   "Tanzania"                 "Tanzania, United Republic of"
   "Venezuela"                "Venezuela, Bolivarian Republic of"
   "North Korea"              "Korea, Democratic People's Republic of"
   "Syria"                    "Syrian Arab Republic"
   "Bolivia"                  "Bolivia, Plurinational State of"
   "Laos"                     "Lao People's Democratic Republic"
   "Moldova"                  "Moldova, Republic of"
   "Republic of Moldova"      "Moldova, Republic of"
   "Eswatini"                 "Swaziland"
   "Cabo Verde"               "Cape Verde"
   "Brunei"                   "Brunei Darussalam"
   "Sao Tome & Principe"      "Sao Tome and Principe"

   "Micronesia"                     "Micronesia, Federated States of"
   "F.S. Micronesia"                "Micronesia, Federated States of"
   "Federated States of Micronesia" "Micronesia, Federated States of"

   "St. Vincent & Grenadines" "Saint Vincent and the Grenadines"
   "U.S. Virgin Islands"      "Virgin Islands, U.S."
   "Saint Kitts & Nevis"      "Saint Kitts and Nevis"
   "Faeroe Islands"           "Faroe Islands"
   "Sint Maarten"             "Sint Maarten (Dutch part)"
   "Turks and Caicos"         "Turks and Caicos Islands"
   "Saint Martin"             "Saint Martin (French part)"
   "British Virgin Islands"   "Virgin Islands, British"
   "Wallis & Futuna"          "Wallis and Futuna"
   "Saint Helena"             "Saint Helena, Ascension and Tristan da Cunha"
   "Saint Pierre & Miquelon"  "Saint Pierre and Miquelon"
   "Falkland Islands"         "Falkland Islands (Malvinas)"
   "Holy See"                 "Holy See (Vatican City State)"
   "Republic of Ireland"      "Ireland"
   "Ivory Coast"              "Côte d'Ivoire"

   " Azerbaijan"              "Azerbaijan"

   ;; Conjoin North Ireland on United Kingdom
   "North Ireland"            "United Kingdom"

   "East Timor"               "Timor-Leste"
   "São Tomé and Príncipe"    "Sao Tome and Principe"

   ;; Guernsey and Jersey form Channel Islands. Conjoin Guernsey on Jersey.
   ;; Jersey has higher population.
   ;; https://en.wikipedia.org/wiki/Channel_Islands
   "Guernsey and Jersey"      "Jersey"
   "Channel Islands"          "Jersey"

   "Caribbean Netherlands"    "Bonaire, Sint Eustatius and Saba"

   "Emirates"                 "United Arab Emirates"
   ;; "Bosnia–Herzegovina"       "Bosnia and Herzegovina"
   "Bosnia"                   "Bosnia and Herzegovina"
   "Dominican Rep"            "Dominican Republic"
   "Macedonia"                "North Macedonia"

   "occupied Palestinian territory" "Palestine, State of"
   "Korea, South"                   "Korea, Republic of"
   "Republic of Korea"              "Korea, Republic of"
   "Macao SAR"                      "Macao"
   "Iran (Islamic Republic of)"     "Iran, Islamic Republic of"
   "Cote d'Ivoire"                  "Côte d'Ivoire"
   "St. Martin"                     "Saint Martin (French part)"
   "Hong Kong SAR"                  "Hong Kong"
   "Reunion"                        "Réunion"

   "Curacao"                        "Curaçao"
   "Congo (Brazzaville)"            "Congo"

   ;; "Others" has no mapping
   ;; "Cruise Ship" is mapped to the default val

   })

(def aliases-inverted
  "See also `country-name-aliased`"
  (conj (clojure.set/map-invert aliases-hm)
        ;; select desired aliases
        {
         "Moldova, Republic of"                  "Moldova"
         "Congo, the Democratic Republic of the" "DR Congo"
         "Palestine, State of"                   "Palestine"
         "Micronesia, Federated States of"       "Micronesia"
         "Taiwan, Province of China"             "Taiwan"
         }))

(defn country-alias
  "Get a country alias or the normal name if an alias doesn't exist"
  [cc]
  (let [country (get c/is-3166-names (s/upper-case cc))]
    (get aliases-inverted country country)))

(defn country-name
  "Country name from 2-letter country code: \"DE\" -> \"Germany\" "
  [cc]
  (get c/is-3166-names cc))

(defn lower-case [hm]
  (->> hm
       (map (fn [[k v]] [(s/lower-case k) v]))
       (into {})))

(defn country_code
  "Return two letter country code (Alpha-2) according to
  https://en.wikipedia.org/wiki/ISO_3166-1
  Defaults to `c/default-2-country-code`."
  [country-name]
  (let [country (s/lower-case country-name)
        lcases-countries (lower-case country-country-code-hm)]
    (if-let [cc (get lcases-countries country)]
      cc
      (if-let [country-alias (get (lower-case aliases-hm) country)]
        (get country-country-code-hm country-alias)
        (do
          (println (format
                    "No country code found for \"%s\". Using \"%s\""
                    country-name
                    c/default-2-country-code
                    ))
          c/default-2-country-code)))))
