(ns corona.core
  (:require [environ.core :refer [env]]
            [clojure.string :as s]
            [clojure.java.io :as io]))

(defmacro dbg [body]
  `(let [x# ~body]
     (println "dbg:" '~body "=" x#)
     x#))

(defn in?
  "true if seq contains elm"
  [seq elm]
  (boolean (some (fn [e] (= elm e)) seq)))

(def project-name "corona_cases") ;; see project.clj: defproject
(def token (env :telegram-token))
(def chat-id "112885364")
(def bot-name (str "@" project-name "_bot"))

(defn calculate-ill [c r d] (- c (+ r d)))

(defn telegram-token-suffix []
  (let [suffix (.substring token (- (count token) 3))]
    (if (or (= suffix "Fq8") (= suffix "MR8"))
      suffix
      (throw (Exception.
              (format "Unrecognized TELEGRAM_TOKEN suffix: %s" suffix))))))

(def bot-type
  (let [suffix (telegram-token-suffix)]
    (case suffix
      "Fq8" "PROD"
      "MR8" "TEST")))

(def bot-ver
  (str (let [pom-props
             (with-open
               [pom-props-reader
                (->> (format "META-INF/maven/%s/%s/pom.properties"
                             project-name project-name)
                     io/resource
                     io/reader)]
               (doto (java.util.Properties.)
                 (.load pom-props-reader)))]
         (get pom-props "version"))
       "-" (env :bot-ver)))

(def bot (str bot-ver ":" bot-type))

(defn fix-octal-val
  "(read-string s-day \"08\") produces a NumberFormatException
  https://clojuredocs.org/clojure.core/read-string#example-5ccee021e4b0ca44402ef71a"
  [s]
  (s/replace s #"^0+" ""))

(defn read-number [v]
  (if (or (empty? v) (= "0" v))
    0
    (-> v fix-octal-val read-string)))

(def worldwide-2-country-code
  "\"ZZ\" can be user-assigned."
  "ZZ")
(def worldwide-country-codes
  "\"ZZZ\" can be user-assigned."
  {worldwide-2-country-code "ZZZ"})

(def default-2-country-code
  "\"QQ\" can be user-assigned."
  "QQ")
(def default-country-codes
  "\"QQQ\" can be user-assigned."
  {default-2-country-code "QQQ"})

(def country-code-worldwide {worldwide-2-country-code "Worldwide"})
(def country-code-others {default-2-country-code "Others"})

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
    "EH" "ESH" "YE" "YEM" "ZM" "ZMB" "ZW" "ZWE"}
   default-country-codes
   worldwide-country-codes))

(defn country-code-3-letter
  "3-letter country code from 2-letter country code: \"DE\" -> \"DEU\" "
  [cc] (get country-code-2-to-3-hm cc))

(def is-3166-names
  (conj
   {"AF" "Afghanistan" "AX" "Åland Islands" "AL" "Albania" "DZ" "Algeria" "AS"
    "American Samoa" "AD" "Andorra" "AO" "Angola" "AI" "Anguilla" "AQ"
    "Antarctica" "AG" "Antigua and Barbuda" "AR" "Argentina" "AM" "Armenia" "AW"
    "Aruba" "AU" "Australia" "AT" "Austria" "AZ" "Azerbaijan" "BS" "Bahamas"
    "BH" "Bahrain" "BD" "Bangladesh" "BB" "Barbados" "BY" "Belarus" "BE"
    "Belgium" "BZ" "Belize" "BJ" "Benin" "BM" "Bermuda" "BT" "Bhutan" "BO"
    "Bolivia, Plurinational State of" "BQ" "Bonaire, Sint Eustatius and Saba"
    "BA" "Bosnia and Herzegovina" "BW" "Botswana" "BV" "Bouvet Island" "BR"
    "Brazil" "IO" "British Indian Ocean Territory" "BN" "Brunei Darussalam" "BG"
    "Bulgaria" "BF" "Burkina Faso" "BI" "Burundi" "KH" "Cambodia" "CM"
    "Cameroon" "CA" "Canada" "CV" "Cape Verde" "KY" "Cayman Islands" "CF"
    "Central African Republic" "TD" "Chad" "CL" "Chile" "CN" "China" "CX"
    "Christmas Island" "CC" "Cocos (Keeling) Islands" "CO" "Colombia" "KM"
    "Comoros" "CG" "Congo" "CD" "Congo, the Democratic Republic of the" "CK"
    "Cook Islands" "CR" "Costa Rica" "CI" "Côte d'Ivoire" "HR" "Croatia" "CU"
    "Cuba" "CW" "Curaçao" "CY" "Cyprus" "CZ" "Czech Republic" "DK" "Denmark"
    "DJ" "Djibouti" "DM" "Dominica" "DO" "Dominican Republic" "EC" "Ecuador"
    "EG" "Egypt" "SV" "El Salvador" "GQ" "Equatorial Guinea" "ER" "Eritrea" "EE"
    "Estonia" "ET" "Ethiopia" "FK" "Falkland Islands (Malvinas)"
    "FO" "Faroe Islands" "FJ" "Fiji" "FI" "Finland" "FR" "France" "GF" "French Guiana" "PF"
    "French Polynesia" "TF" "French Southern Territories" "GA" "Gabon" "GM"
    "Gambia" "GE" "Georgia" "DE" "Germany" "GH" "Ghana" "GI" "Gibraltar" "GR"
    "Greece" "GL" "Greenland" "GD" "Grenada" "GP" "Guadeloupe" "GU" "Guam" "GT"
    "Guatemala" "GG" "Guernsey" "GN" "Guinea" "GW" "Guinea-Bissau" "GY" "Guyana"
    "HT" "Haiti" "HM" "Heard Island and McDonald Islands"
    "VA" "Holy See (Vatican City State)"
    "HN" "Honduras" "HK" "Hong Kong" "HU" "Hungary" "IS"
    "Iceland" "IN" "India" "ID" "Indonesia" "IR" "Iran, Islamic Republic of"
    "IQ" "Iraq" "IE" "Ireland" "IM" "Isle of Man" "IL" "Israel" "IT" "Italy"
    "JM" "Jamaica" "JP" "Japan" "JE" "Jersey" "JO" "Jordan" "KZ" "Kazakhstan"
    "KE" "Kenya" "KI" "Kiribati" "KP" "Korea, Democratic People's Republic of"
    "KR" "Korea, Republic of" "KW" "Kuwait" "KG" "Kyrgyzstan" "LA" "Lao People's
    Democratic Republic" "LV" "Latvia" "LB" "Lebanon" "LS" "Lesotho" "LR"
    "Liberia" "LY" "Libya" "LI" "Liechtenstein" "LT" "Lithuania" "LU"
    "Luxembourg" "MO" "Macao" "MK" "North Macedonia" "MG" "Madagascar" "MW"
    "Malawi" "MY" "Malaysia" "MV" "Maldives" "ML" "Mali" "MT" "Malta" "MH"
    "Marshall Islands" "MQ" "Martinique" "MR" "Mauritania" "MU" "Mauritius" "YT"
    "Mayotte" "MX" "Mexico" "FM" "Micronesia, Federated States of" "MD"
    "Moldova, Republic of" "MC" "Monaco" "MN" "Mongolia" "ME" "Montenegro" "MS"
    "Montserrat" "MA" "Morocco" "MZ" "Mozambique" "MM" "Myanmar" "NA" "Namibia"
    "NR" "Nauru" "NP" "Nepal" "NL" "Netherlands" "NC" "New Caledonia"
    "NZ" "New Zealand" "NI" "Nicaragua" "NE" "Niger" "NG" "Nigeria" "NU" "Niue" "NF"
    "Norfolk Island" "MP" "Northern Mariana Islands" "NO" "Norway" "OM" "Oman"
    "PK" "Pakistan" "PW" "Palau" "PS" "Palestine, State of" "PA" "Panama" "PG"
    "Papua New Guinea" "PY" "Paraguay" "PE" "Peru" "PH" "Philippines" "PN"
    "Pitcairn" "PL" "Poland" "PT" "Portugal" "PR" "Puerto Rico" "QA" "Qatar"
    "RE" "Réunion" "RO" "Romania" "RU" "Russian Federation" "RW" "Rwanda" "BL"
    "Saint Barthélemy" "SH" "Saint Helena, Ascension and Tristan da Cunha" "KN"
    "Saint Kitts and Nevis" "LC" "Saint Lucia" "MF" "Saint Martin (French part)"
    "PM" "Saint Pierre and Miquelon" "VC" "Saint Vincent and the Grenadines"
    "WS" "Samoa" "SM" "San Marino" "ST" "Sao Tome and Principe"
    "SA" "Saudi Arabia" "SN" "Senegal" "RS" "Serbia" "SC" "Seychelles" "SL" "Sierra Leone"
    "SG" "Singapore" "SX" "Sint Maarten (Dutch part)" "SK" "Slovakia" "SI"
    "Slovenia" "SB" "Solomon Islands" "SO" "Somalia" "ZA" "South Africa" "GS"
    "South Georgia and the South Sandwich Islands" "SS" "South Sudan" "ES"
    "Spain" "LK" "Sri Lanka" "SD" "Sudan" "SR" "Suriname" "SJ" "Svalbard and Jan
    Mayen" "SZ" "Swaziland" "SE" "Sweden" "CH" "Switzerland" "SY" "Syrian Arab
    Republic" "TW" "Taiwan, Province of China" "TJ" "Tajikistan" "TZ" "Tanzania,
    United Republic of" "TH" "Thailand" "TL" "Timor-Leste" "TG" "Togo" "TK"
    "Tokelau" "TO" "Tonga" "TT" "Trinidad and Tobago" "TN" "Tunisia" "TR"
    "Turkey" "TM" "Turkmenistan" "TC" "Turks and Caicos Islands" "TV" "Tuvalu"
    "UG" "Uganda" "UA" "Ukraine" "AE" "United Arab Emirates"
    "GB" "United Kingdom" "US" "United States" "UM" "United States Minor Outlying Islands"
    "UY" "Uruguay" "UZ" "Uzbekistan" "VU" "Vanuatu" "VE" "Venezuela, Bolivarian
    Republic of" "VN" "Viet Nam" "VG" "Virgin Islands, British" "VI" "Virgin
    Islands, U.S." "WF" "Wallis and Futuna" "EH" "Western Sahara" "YE" "Yemen"
    "ZM" "Zambia" "ZW" "Zimbabwe"}
   country-code-worldwide
   country-code-others))

(defn country-name
  "Country name from 2-letter country code: \"DE\" -> \"Germany\" "
  [cc] (get is-3166-names cc))

(defn all-country-codes [] (keys country-code-2-to-3-hm))

(def default-affected-country-codes
  (->> [country-code-worldwide country-code-others]
       (reduce into)
       (mapv (fn [[k v]] k))))

(defn left-pad [s padding-len]
  (s/replace (format (str "%" padding-len "s") s) " " "0"))

#_
(defn left-pad [s padding-len]
  (str (s/join (repeat (- padding-len (count s)) " "))
       s))

(defn right-pad [s padding-len]
  (str s
       (s/join (repeat (- padding-len (count s)) " "))))
