(ns corona.countries
  (:require [corona.defs :as d]
            [clojure.set :as cset])
  (:import com.neovisionaries.i18n.CountryCode))

(def country-code-2-to-3-hm
  "Mapping of country codes 2 -> 3 letters"
  (conj
   {"AF" "AFG" "AX" "ALA" "AL" "ALB" "DZ" "DZA" "AS" "ASM" "AD" "AND" "AO" "AGO"
    "AI" "AIA" "AQ" "ATA" "AG" "ATG" "AR" "ARG" "AM" "ARM" "AW" "ABW" "AU" "AUS"
    "AT" "AUT" "AZ" "AZE" "BS" "BHS" "BH" "BHR" "BD" "BGD" "BB" "BRB" "BY" "BLR"
    "BE" "BEL" "BZ" "BLZ" "BJ" "BEN" "BM" "BMU" "BT" "BTN" "BO" "BOL" "BQ" "BES"
    "BA" "BIH" "BW" "BWA" "BV" "BVT" "BR" "BRA" "IO" "IOT" "BN" "BRN" "BG" "BGR"
    "BF" "BFA" "BI" "BDI" "CV" "CPV" "KH" "KHM" "CM" "CMR" "CA" "CAN" "KY" "CYM"
    "CF" "CAF" "TD" "TCD" "CL" "CHL" "CN" "CHN" "CX" "CXR" "CC" "CCK" "CO" "COL"
    "KM" "COM" "CG" "COG" "CD" "COD" "CK" "COK" "CR" "CRI" "CI" "CIV" "HR" "HRV"
    "CU" "CUB" "CW" "CUW" "CY" "CYP" "CZ" "CZE" "DK" "DNK" "DJ" "DJI" "DM" "DMA"
    "DO" "DOM" "EC" "ECU" "EG" "EGY" "SV" "SLV" "GQ" "GNQ" "ER" "ERI" "EE" "EST"
    "SZ" "SWZ" "ET" "ETH" "FK" "FLK" "FO" "FRO" "FJ" "FJI" "FI" "FIN" "FR" "FRA"
    "GF" "GUF" "PF" "PYF" "TF" "ATF" "GA" "GAB" "GM" "GMB" "GE" "GEO" "DE" "DEU"
    "GH" "GHA" "GI" "GIB" "GR" "GRC" "GL" "GRL" "GD" "GRD" "GP" "GLP" "GU" "GUM"
    "GT" "GTM" "GG" "GGY" "GN" "GIN" "GW" "GNB" "GY" "GUY" "HT" "HTI" "HM" "HMD"
    "VA" "VAT" "HN" "HND" "HK" "HKG" "HU" "HUN" "IS" "ISL" "IN" "IND" "ID" "IDN"
    "IR" "IRN" "IQ" "IRQ" "IE" "IRL" "IM" "IMN" "IL" "ISR" "IT" "ITA" "JM" "JAM"
    "JP" "JPN" "JE" "JEY" "JO" "JOR" "KZ" "KAZ" "KE" "KEN" "KI" "KIR" "KP" "PRK"
    "KR" "KOR" "KW" "KWT" "KG" "KGZ" "LA" "LAO" "LV" "LVA" "LB" "LBN" "LS" "LSO"
    "LR" "LBR" "LY" "LBY" "LI" "LIE" "LT" "LTU" "LU" "LUX" "MO" "MAC" "MG" "MDG"
    "MW" "MWI" "MY" "MYS" "MV" "MDV" "ML" "MLI" "MT" "MLT" "MH" "MHL" "MQ" "MTQ"
    "MR" "MRT" "MU" "MUS" "YT" "MYT" "MX" "MEX" "FM" "FSM" "MD" "MDA" "MC" "MCO"
    "MN" "MNG" "ME" "MNE" "MS" "MSR" "MA" "MAR" "MZ" "MOZ" "MM" "MMR" "NA" "NAM"
    "NR" "NRU" "NP" "NPL" "NL" "NLD" "NC" "NCL" "NZ" "NZL" "NI" "NIC" "NE" "NER"
    "NG" "NGA" "NU" "NIU" "NF" "NFK" "MK" "MKD" "MP" "MNP" "NO" "NOR" "OM" "OMN"
    "PK" "PAK" "PW" "PLW" "PS" "PSE" "PA" "PAN" "PG" "PNG" "PY" "PRY" "PE" "PER"
    "PH" "PHL" "PN" "PCN" "PL" "POL" "PT" "PRT" "PR" "PRI" "QA" "QAT" "RE" "REU"
    "RO" "ROU" "RU" "RUS" "RW" "RWA" "BL" "BLM" "SH" "SHN" "KN" "KNA" "LC" "LCA"
    "MF" "MAF" "PM" "SPM" "VC" "VCT" "WS" "WSM" "SM" "SMR" "ST" "STP" "SA" "SAU"
    "SN" "SEN" "RS" "SRB" "SC" "SYC" "SL" "SLE" "SG" "SGP" "SX" "SXM" "SK" "SVK"
    "SI" "SVN" "SB" "SLB" "SO" "SOM" "ZA" "ZAF" "GS" "SGS" "SS" "SSD" "ES" "ESP"
    "LK" "LKA" "SD" "SDN" "SR" "SUR" "SJ" "SJM" "SE" "SWE" "CH" "CHE" "SY" "SYR"
    "TW" "TWN" "TJ" "TJK" "TZ" "TZA" "TH" "THA" "TL" "TLS" "TG" "TGO" "TK" "TKL"
    "TO" "TON" "TT" "TTO" "TN" "TUN" "TR" "TUR" "TM" "TKM" "TC" "TCA" "TV" "TUV"
    "UG" "UGA" "UA" "UKR" "AE" "ARE" "GB" "GBR" "US" "USA" "UM" "UMI" "UY" "URY"
    "UZ" "UZB" "VU" "VUT" "VE" "VEN" "VN" "VNM" "VG" "VGB" "VI" "VIR" "WF" "WLF"
    "EH" "ESH" "YE" "YEM"
    ;; TODO verify the 3 letter code for Kosovo - is it really XKX?
    ;; See https://github.com/covid19-data/covid19-data/issues/16
    "XK" "XKX"
    "ZM" "ZMB" "ZW" "ZWE"}
   d/default-country-codes
   d/worldwide-country-codes))

(defn country-code-3-letter
  "3-letter country code from 2-letter country code: \"DE\" -> \"DEU\" "
  [cc] (get country-code-2-to-3-hm cc))

(defn all-country-codes [] (keys country-code-2-to-3-hm))

(def default-affected-country-codes
  {d/country-code-worldwide d/country-code-others})

;; Continent code
(def default-affected-continent-codes [])

(defn country-code--country-nv-i18n []
  (->> (CountryCode/values)
       (map (fn [cc] [(str cc)
                      (.getName cc)]))
       (into {})))

(def country-code--country
  (conj
   (country-code--country-nv-i18n)
    d/country-code-worldwide
    d/country-code-others
    ;; see
    ;; https://en.wikipedia.org/wiki/List_of_sovereign_states_and_dependent_territories_by_continent_(data_file)
    {
     "XD" "United Nations Neutral Zone"
     "XE" "Iraq-Saudi Arabia Neutral Zone"
     "XS" "Spratly Islands"
     "XX" "Disputed Territory"
     }))


;; java.util.Locale doesn't show country names according to the ISO norm
;; (defn country-code--country-locale
;;   []
;;   (->> (java.util.Locale/getISOCountries)
;;        (map (fn [cc] [cc
;;                      (-> (.getDisplayCountry (new Locale "" cc))
;;                          #_(s/replace "&" "and"))]))
;;        (into {})))

(def country--country-code
  "Mapping of country names to alpha-2 codes.
  https://en.wikipedia.org/wiki/ISO_3166-1#Officially_assigned_code_elements"
  (cset/map-invert country-code--country))

(defn cn [country-code] (get country-code--country country-code))

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
   "World"                            (cn d/worldwide-2-country-code)
   "Czechia"                          (cn "CZ") ; "Czech Republic"
   "Mainland China"                   (cn "CN") ; "China"
   "South Korea"                      (cn "KR") ; "Korea, Republic of"
   "Korea, South"                     (cn "KR") ; "Korea, Republic of"
   "Republic of Korea"                (cn "KR") ; "Korea, Republic of"

   "Taiwan"                           (cn "TW") ; "Taiwan, Province of China"
   "Taiwan*"                          (cn "TW") ; "Taiwan, Province of China"
   "Taipei and environs"              (cn "TW") ; "Taiwan, Province of China"

   "US"                               (cn "US") ; "United States"

   ;; TODO Macau is probably just misspelled Macao. Report it to CSSEGISandData/COVID-19
   "Macau"                            (cn "MO")
   "Macao SAR"                        (cn "MO") ; "Macao"

   "Vietnam"                          (cn "GB") ; "Viet Nam"
   "UK"                               (cn "GB") ; "United Kingdom"
   "Russia"                           (cn "RU") ; "Russian Federation"
   "Iran"                             (cn "IR") ; "Iran, Islamic Republic of"
   "Iran (Islamic Republic of)"       (cn "IR") ; "Iran, Islamic Republic of"
   "Saint Barthelemy"                 (cn "BL") ; "Saint Barthélemy"

   "Palestine"                        (cn "PS") ; "Palestine, State of"
   "State of Palestine"               (cn "PS") ; "Palestine, State of"
   "occupied Palestinian territory"   (cn "PS") ; "Palestine, State of"

   "Vatican City"                     (cn "VA") ; "Holy See (Vatican City State)"
   "Holy See"                         (cn "VA") ; "Holy See (Vatican City State)"

   "DR Congo"                         (cn "CD") ; "Congo, the Democratic Republic of the"
   "Congo (Kinshasa)"                 (cn "CD") ; "Congo, the Democratic Republic of the"
   "Democratic Republic of the Congo" (cn "CD") ; "Congo, the Democratic Republic of the"

   "Tanzania"                         (cn "TZ") ; "Tanzania, United Republic of"
   "Venezuela"                        (cn "VE") ; "Venezuela, Bolivarian Republic of"
   "North Korea"                      (cn "KP") ; "Korea, Democratic People's Republic of"
   "Syria"                            (cn "SY") ; "Syrian Arab Republic"
   "Bolivia"                          (cn "BO") ; "Bolivia, Plurinational State of"
   "Laos"                             (cn "LA") ; "Lao People's Democratic Republic"
   "Moldova"                          (cn "MD") ; "Moldova, Republic of"
   "Republic of Moldova"              (cn "MD") ; "Moldova, Republic of"
   "Swaziland"                        (cn "SZ") ; "Eswatini"
   "Cabo Verde"                       (cn "CV") ; "Cape Verde"
   "Brunei"                           (cn "BN") ; "Brunei Darussalam"
   "Sao Tome & Principe"              (cn "ST") ; "Sao Tome and Principe"
   "São Tomé and Príncipe"            (cn "ST") ; "Sao Tome and Principe"
   "Micronesia"                       (cn "FM") ; "Micronesia, Federated States of"
   "F.S. Micronesia"                  (cn "FM") ; "Micronesia, Federated States of"
   "Federated States of Micronesia"   (cn "FM") ; "Micronesia, Federated States of"

   "St. Vincent & Grenadines"         (cn "VC") ; "Saint Vincent and the Grenadines"
   "Saint Vincent"                    (cn "VC") ; "Saint Vincent and the Grenadines"
   "U.S. Virgin Islands"              (cn "VI") ; "Virgin Islands, U.S."
   "British Virgin Islands"           (cn "VG") ; "Virgin Islands, British"
   "Saint Kitts & Nevis"              (cn "KN") ; "Saint Kitts and Nevis"
   "St. Kitts & Nevis"                (cn "KN") ; "Saint Kitts and Nevis"
   "Faeroe Islands"                   (cn "FO") ; "Faroe Islands"
   "Sint Maarten"                     (cn "SX") ; "Sint Maarten (Dutch part)"
   "Turks and Caicos"                 (cn "TC") ; "Turks and Caicos Islands"
   "Wallis & Futuna"                  (cn "WF") ; "Wallis and Futuna"
   "Saint Helena"                     (cn "SH") ; "Saint Helena, Ascension and Tristan da Cunha"
   "Saint Pierre & Miquelon"          (cn "PM") ; "Saint Pierre and Miquelon"
   "Falkland Islands"                 (cn "FK") ; "Falkland Islands (Malvinas)"
   "Republic of Ireland"              (cn "IE") ; "Ireland"
   " Azerbaijan"                      (cn "AZ") ; "Azerbaijan"

   ;; Conjoin North Ireland on United Kingdom
   "North Ireland"                    (cn "GB") ; "United Kingdom"
   "East Timor"                       (cn "TL") ; "Timor-Leste"

   ;; Guernsey and Jersey form Channel Islands. Conjoin Guernsey on Jersey.
   ;; Jersey has higher population.
   ;; https://en.wikipedia.org/wiki/Channel_Islands
   "Guernsey and Jersey"              (cn "JE") ; "Jersey"
   "Channel Islands"                  (cn "JE") ; "Jersey"
   "Caribbean Netherlands"            (cn "JE") ; "Bonaire, Sint Eustatius and Saba"
   "Emirates"                         (cn "AE") ; "United Arab Emirates"
   ;; "Bosnia–Herzegovina"            (cn "BA") ; "Bosnia and Herzegovina"
   "Bosnia"                           (cn "BA") ; "Bosnia and Herzegovina"
   "Dominican Rep"                    (cn "DO") ; "Dominican Republic"
   "Macedonia"                        (cn "MK") ; "North Macedonia, Republic of"
   "North Macedonia"                  (cn "MK") ; "North Macedonia, Republic of"
   "Ivory Coast"                      (cn "CI") ; "Côte d'Ivoire"
   "Cote d'Ivoire"                    (cn "CI") ; "Côte d'Ivoire"
   "Saint Martin"                     (cn "MF") ; "Saint Martin (French part)"
   "St. Martin"                       (cn "MF") ; "Saint Martin (French part)"
   "Hong Kong SAR"                    (cn "HK") ; "Hong Kong"
   "Reunion"                          (cn "RE") ; "Réunion"
   "Curacao"                          (cn "CW") ; "Curaçao"
   "Congo (Brazzaville)"              (cn "CG") ; "Congo"
   "Republic of the Congo"            (cn "CG") ; "Congo"
   "The Bahamas"                      (cn "BS") ; "Bahamas"
   "Kosovo"                           (cn "XK") ; "Kosovo, Republic of"
   "Trinidad & Tobago"                (cn "TT") ; "Trinidad and Tobago"
   "Antigua & Barbuda"                (cn "AG") ; "Antigua and Barbuda"
   "Central African Rep"              (cn "CF") ; "Central African Republic"

   "Burma"                            (cn "MM") ; Myanmar


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
         "Saint Vincent and the Grenadines"      "Saint Vincent"
         "North Macedonia, Republic of"          "Macedonia"
         "Saint Kitts and Nevis"                 "St. Kitts & Nevis"
         "Trinidad and Tobago"                   "Trinidad & Tobago"
         "Antigua and Barbuda"                   "Antigua & Barbuda"
         "Central African Republic"              "Central Afri Rep"
         }))
