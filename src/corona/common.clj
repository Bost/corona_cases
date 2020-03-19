(ns corona.common
  (:require [corona.countries :as cr]
            [corona.continents :as cn]
            [corona.api :as data]
            [corona.defs :as d]
            [clojure.string :as s]
            [corona.core :as c :refer [in? dbg]]))

(def continent-countries-hm
  "
https://en.wikipedia.org/wiki/List_of_sovereign_states_and_dependent_territories_by_continent_(data_file)
https://en.wikipedia.org/wiki/List_of_sovereign_states_and_dependent_territories_by_continent

  Kosovo doesn't have any numeric code assigned; XKX is just pseudo-defined by the Worldbank
  TODO verify this hm against other sources"
  [
   ["ASI"      "AF" "AFG" "004" "Afghanistan"]
   ["EUR"      "XK" "XKX"  nil  "Kosovo"]
   ["EUR"      "AL" "ALB" "008" "Albania, Republic of"]
   ["ANA"      "AQ" "ATA" "010" "Antarctica (the territory South of 60 deg S)"]
   ["AFR"      "DZ" "DZA" "012" "Algeria, People's Democratic Republic of"]
   ["OCE"      "AS" "ASM" "016" "American Samoa"]  ;;;
   ["EUR"      "AD" "AND" "020" "Andorra, Principality of"]
   ["AFR"      "AO" "AGO" "024" "Angola, Republic of"]
   ["NAC"      "AG" "ATG" "028" "Antigua and Barbuda"]
   ["ASI"      "AZ" "AZE" "031" "Azerbaijan, Republic of"]
   ["EUR"      "AZ" "AZE" "031" "Azerbaijan, Republic of"]
   ["SAC"      "AR" "ARG" "032" "Argentina, Argentine Republic"]
   ["OCE"      "AU" "AUS" "036" "Australia, Commonwealth of"]
   ["EUR"      "AT" "AUT" "040" "Austria, Republic of"]
   ["NAC"      "BS" "BHS" "044" "Bahamas, Commonwealth of the"]
   ["ASI"      "BH" "BHR" "048" "Bahrain, Kingdom of"]
   ["ASI"      "BD" "BGD" "050" "Bangladesh, People's Republic of"]
   ["ASI"      "AM" "ARM" "051" "Armenia, Republic of"]
   ["EUR"      "AM" "ARM" "051" "Armenia, Republic of"]
   ["NAC"      "BB" "BRB" "052" "Barbados"]
   ["EUR"      "BE" "BEL" "056" "Belgium, Kingdom of"]
   ["NAC"      "BM" "BMU" "060" "Bermuda"]
   ["ASI"      "BT" "BTN" "064" "Bhutan, Kingdom of"]
   ["SAC"      "BO" "BOL" "068" "Bolivia, Republic of"]
   ["EUR"      "BA" "BIH" "070" "Bosnia and Herzegovina"]
   ["AFR"      "BW" "BWA" "072" "Botswana, Republic of"]
   ["ANA"      "BV" "BVT" "074" "Bouvet Island (Bouvetoya)"]
   ["SAC"      "BR" "BRA" "076" "Brazil, Federative Republic of"]
   ["NAC"      "BZ" "BLZ" "084" "Belize"]
   ["ASI"      "IO" "IOT" "086" "British Indian Ocean Territory (Chagos Archipelago)"]
   ["OCE"      "SB" "SLB" "090" "Solomon Islands"]
   ["NAC"      "VG" "VGB" "092" "British Virgin Islands"]
   ["ASI"      "BN" "BRN" "096" "Brunei Darussalam"]
   ["EUR"      "BG" "BGR" "100" "Bulgaria, Republic of"]
   ["ASI"      "MM" "MMR" "104" "Myanmar, Union of"]
   ["AFR"      "BI" "BDI" "108" "Burundi, Republic of"]
   ["EUR"      "BY" "BLR" "112" "Belarus, Republic of"]
   ["ASI"      "KH" "KHM" "116" "Cambodia, Kingdom of"]
   ["AFR"      "CM" "CMR" "120" "Cameroon, Republic of"]
   ["NAC"      "CA" "CAN" "124" "Canada"]
   ["AFR"      "CV" "CPV" "132" "Cape Verde, Republic of"]
   ["NAC"      "KY" "CYM" "136" "Cayman Islands"]
   ["AFR"      "CF" "CAF" "140" "Central African Republic"]
   ["ASI"      "LK" "LKA" "144" "Sri Lanka, Democratic Socialist Republic of"]
   ["AFR"      "TD" "TCD" "148" "Chad, Republic of"]
   ["SAC"      "CL" "CHL" "152" "Chile, Republic of"]
   ["ASI"      "CN" "CHN" "156" "China, People's Republic of"]
   ["ASI"      "TW" "TWN" "158" "Taiwan"]
   ["ASI"      "CX" "CXR" "162" "Christmas Island"]
   ["ASI"      "CC" "CCK" "166" "Cocos (Keeling) Islands"]
   ["SAC"      "CO" "COL" "170" "Colombia, Republic of"]
   ["AFR"      "KM" "COM" "174" "Comoros, Union of the"]
   ["AFR"      "YT" "MYT" "175" "Mayotte"]
   ["AFR"      "CG" "COG" "178" "Congo, Republic of the"]
   ["AFR"      "CD" "COD" "180" "Congo, Democratic Republic of the"]
   ["OCE"      "CK" "COK" "184" "Cook Islands"]
   ["NAC"      "CR" "CRI" "188" "Costa Rica, Republic of"]
   ["EUR"      "HR" "HRV" "191" "Croatia, Republic of"]
   ["NAC"      "CU" "CUB" "192" "Cuba, Republic of"]
   ["ASI"      "CY" "CYP" "196" "Cyprus, Republic of"]
   ["EUR"      "CY" "CYP" "196" "Cyprus, Republic of"]
   ["EUR"      "CZ" "CZE" "203" "Czech Republic"]
   ["AFR"      "BJ" "BEN" "204" "Benin, Republic of"]
   ["EUR"      "DK" "DNK" "208" "Denmark, Kingdom of"]
   ["NAC"      "DM" "DMA" "212" "Dominica, Commonwealth of"]
   ["NAC"      "DO" "DOM" "214" "Dominican Republic"]
   ["SAC"      "EC" "ECU" "218" "Ecuador, Republic of"]
   ["NAC"      "SV" "SLV" "222" "El Salvador, Republic of"]
   ["AFR"      "GQ" "GNQ" "226" "Equatorial Guinea, Republic of"]
   ["AFR"      "ET" "ETH" "231" "Ethiopia, Federal Democratic Republic of"]
   ["AFR"      "ER" "ERI" "232" "Eritrea, State of"]
   ["EUR"      "EE" "EST" "233" "Estonia, Republic of"]
   ["EUR"      "FO" "FRO" "234" "Faroe Islands"]
   ["SAC"      "FK" "FLK" "238" "Falkland Islands (Malvinas)"]
   ["ANA"      "GS" "SGS" "239" "South Georgia and the South Sandwich Islands"]
   ["OCE"      "FJ" "FJI" "242" "Fiji, Republic of the Fiji Islands"]
   ["EUR"      "FI" "FIN" "246" "Finland, Republic of"]
   ["EUR"      "AX" "ALA" "248" "Åland Islands"]
   ["EUR"      "FR" "FRA" "250" "France, French Republic"]
   ["SAC"      "GF" "GUF" "254" "French Guiana"]
   ["OCE"      "PF" "PYF" "258" "French Polynesia"]
   ["ANA"      "TF" "ATF" "260" "French Southern Territories"]
   ["AFR"      "DJ" "DJI" "262" "Djibouti, Republic of"]
   ["AFR"      "GA" "GAB" "266" "Gabon, Gabonese Republic"]
   ["ASI"      "GE" "GEO" "268" "Georgia"]
   ["EUR"      "GE" "GEO" "268" "Georgia"]
   ["AFR"      "GM" "GMB" "270" "Gambia, Republic of the"]
   ["EUR"      "DE" "DEUR" "276" "Germany, Federal Republic of"]
   ["AFR"      "GH" "GHA" "288" "Ghana, Republic of"]
   ["EUR"      "GI" "GIB" "292" "Gibraltar"]
   ["OCE"      "KI" "KIR" "296" "Kiribati, Republic of"]
   ["EUR"      "GR" "GRC" "300" "Greece, Hellenic Republic"]
   ["NAC"      "GL" "GRL" "304" "Greenland"]
   ["NAC"      "GD" "GRD" "308" "Grenada"]
   ["NAC"      "GP" "GLP" "312" "Guadeloupe"]
   ["OCE"      "GU" "GUM" "316" "Guam"]
   ["NAC"      "GT" "GTM" "320" "Guatemala, Republic of"]
   ["AFR"      "GN" "GIN" "324" "Guinea, Republic of"]
   ["SAC"      "GY" "GUY" "328" "Guyana, Co-operative Republic of"]
   ["NAC"      "HT" "HTI" "332" "Haiti, Republic of"]
   ["ANA"      "HM" "HMD" "334" "Heard Island and McDonald Islands"]
   ["EUR"      "VA" "VAT" "336" "Holy See (Vatican City State)"]
   ["NAC"      "HN" "HND" "340" "Honduras, Republic of"]
   ["ASI"      "HK" "HKG" "344" "Hong Kong, Special Administrative Region of China"]
   ["EUR"      "HU" "HUN" "348" "Hungary, Republic of"]
   ["EUR"      "IS" "ISL" "352" "Iceland, Republic of"]
   ["ASI"      "IN" "IND" "356" "India, Republic of"]
   ["ASI"      "ID" "IDN" "360" "Indonesia, Republic of"]
   ["ASI"      "IR" "IRN" "364" "Iran, Islamic Republic of"]
   ["ASI"      "IQ" "IRQ" "368" "Iraq, Republic of"]
   ["EUR"      "IE" "IRL" "372" "Ireland"]
   ["ASI"      "IL" "ISR" "376" "Israel, State of"]
   ["EUR"      "IT" "ITA" "380" "Italy, Italian Republic"]
   ["AFR"      "CI" "CIV" "384" "Côte d'Ivoire, Republic of"]
   ["NAC"      "JM" "JAM" "388" "Jamaica"]
   ["ASI"      "JP" "JPN" "392" "Japan"]
   ["ASI"      "KZ" "KAZ" "398" "Kazakhstan, Republic of"]
   ["EUR"      "KZ" "KAZ" "398" "Kazakhstan, Republic of"]
   ["ASI"      "JO" "JOR" "400" "Jordan, Hashemite Kingdom of"]
   ["AFR"      "KE" "KEN" "404" "Kenya, Republic of"]
   ["ASI"      "KP" "PRK" "408" "Korea, Democratic People's Republic of"]
   ["ASI"      "KR" "KOR" "410" "Korea, Republic of"]
   ["ASI"      "KW" "KWT" "414" "Kuwait, State of"]
   ["ASI"      "KG" "KGZ" "417" "Kyrgyz Republic"]
   ["ASI"      "LA" "LAO" "418" "Lao People's Democratic Republic"]
   ["ASI"      "LB" "LBN" "422" "Lebanon, Lebanese Republic"]
   ["AFR"      "LS" "LSO" "426" "Lesotho, Kingdom of"]
   ["EUR"      "LV" "LVA" "428" "Latvia, Republic of"]
   ["AFR"      "LR" "LBR" "430" "Liberia, Republic of"]
   ["AFR"      "LY" "LBY" "434" "Libyan Arab Jamahiriya"]
   ["EUR"      "LI" "LIE" "438" "Liechtenstein, Principality of"]
   ["EUR"      "LT" "LTU" "440" "Lithuania, Republic of"]
   ["EUR"      "LU" "LUX" "442" "Luxembourg, Grand Duchy of"]
   ["ASI"      "MO" "MAC" "446" "Macao, Special Administrative Region of China"]
   ["AFR"      "MG" "MDG" "450" "Madagascar, Republic of"]
   ["AFR"      "MW" "MWI" "454" "Malawi, Republic of"]
   ["ASI"      "MY" "MYS" "458" "Malaysia"]
   ["ASI"      "MV" "MDV" "462" "Maldives, Republic of"]
   ["AFR"      "ML" "MLI" "466" "Mali, Republic of"]
   ["EUR"      "MT" "MLT" "470" "Malta, Republic of"]
   ["NAC"      "MQ" "MTQ" "474" "Martinique"]
   ["AFR"      "MR" "MRT" "478" "Mauritania, Islamic Republic of"]
   ["AFR"      "MU" "MUS" "480" "Mauritius, Republic of"]
   ["NAC"      "MX" "MEX" "484" "Mexico, United Mexican States"]
   ["EUR"      "MC" "MCO" "492" "Monaco, Principality of"]
   ["ASI"      "MN" "MNG" "496" "Mongolia"]
   ["EUR"      "MD" "MDA" "498" "Moldova, Republic of"]
   ["EUR"      "ME" "MNE" "499" "Montenegro, Republic of"]
   ["NAC"      "MS" "MSR" "500" "Montserrat"]
   ["AFR"      "MA" "MAR" "504" "Morocco, Kingdom of"]
   ["AFR"      "MZ" "MOZ" "508" "Mozambique, Republic of"]
   ["ASI"      "OM" "OMN" "512" "Oman, Sultanate of"]
   ["AFR"      "NA" "NAM" "516" "Namibia, Republic of"]
   ["OCE"      "NR" "NRU" "520" "Nauru, Republic of"]
   ["ASI"      "NP" "NPL" "524" "Nepal, State of"]
   ["EUR"      "NL" "NLD" "528" "Netherlands, Kingdom of the"]
   ["NAC"      "AN" "ANT" "530" "Netherlands Antilles"]
   ["NAC"      "CW" "CUW" "531" "Curaçao"]
   ["NAC"      "AW" "ABW" "533" "Aruba"]
   ["NAC"      "SX" "SXM" "534" "Sint Maarten (Netherlands)"]
   ["NAC"      "BQ" "BES" "535" "Bonaire, Sint Eustatius and Saba"]
   ["OCE"      "NC" "NCL" "540" "New Caledonia"]
   ["OCE"      "VU" "VUT" "548" "Vanuatu, Republic of"]
   ["OCE"      "NZ" "NZL" "554" "New Zealand"]
   ["NAC"      "NI" "NIC" "558" "Nicaragua, Republic of"]
   ["AFR"      "NE" "NER" "562" "Niger, Republic of"]
   ["AFR"      "NG" "NGA" "566" "Nigeria, Federal Republic of"]
   ["OCE"      "NU" "NIU" "570" "Niue"]
   ["OCE"      "NF" "NFK" "574" "Norfolk Island"]
   ["EUR"      "NO" "NOR" "578" "Norway, Kingdom of"]
   ["OCE"      "MP" "MNP" "580" "Northern Mariana Islands, Commonwealth of the"]
   ["NAC"      "UM" "UMI" "581" "United States Minor Outlying Islands"]
   ["OCE"      "UM" "UMI" "581" "United States Minor Outlying Islands"]
   ["OCE"      "FM" "FSM" "583" "Micronesia, Federated States of"]
   ["OCE"      "MH" "MHL" "584" "Marshall Islands, Republic of the"]
   ["OCE"      "PW" "PLW" "585" "Palau, Republic of"]
   ["ASI"      "PK" "PAK" "586" "Pakistan, Islamic Republic of"]
   ["ASI"      "PS" "PSE" "275" "Palestine, state of"]
   ["NAC"      "PA" "PAN" "591" "Panama, Republic of"]
   ["OCE"      "PG" "PNG" "598" "Papua New Guinea, Independent State of"]
   ["SAC"      "PY" "PRY" "600" "Paraguay, Republic of"]
   ["SAC"      "PE" "PER" "604" "Peru, Republic of"]
   ["ASI"      "PH" "PHL" "608" "Philippines, Republic of the"]
   ["OCE"      "PN" "PCN" "612" "Pitcairn Islands"]
   ["EUR"      "PL" "POL" "616" "Poland, Republic of"]
   ["EUR"      "PT" "PRT" "620" "Portugal, Portuguese Republic"]
   ["AFR"      "GW" "GNB" "624" "Guinea-Bissau, Republic of"]
   ["ASI"      "TL" "TLS" "626" "Timor-Leste, Democratic Republic of"]
   ["NAC"      "PR" "PRI" "630" "Puerto Rico, Commonwealth of"]
   ["ASI"      "QA" "QAT" "634" "Qatar, State of"]
   ["AFR"      "RE" "REUR" "638" "Reunion"]
   ["EUR"      "RO" "ROU" "642" "Romania"]
   ["ASI"      "RU" "RUS" "643" "Russian Federation"]
   ["EUR"      "RU" "RUS" "643" "Russian Federation"]
   ["AFR"      "RW" "RWA" "646" "Rwanda, Republic of"]
   ["NAC"      "BL" "BLM" "652" "Saint Barthelemy"]
   ["AFR"      "SH" "SHN" "654" "Saint Helena"]
   ["NAC"      "KN" "KNA" "659" "Saint Kitts and Nevis, Federation of"]
   ["NAC"      "AI" "AIA" "660" "Anguilla"]
   ["NAC"      "LC" "LCA" "662" "Saint Lucia"]
   ["NAC"      "MF" "MAF" "663" "Saint Martin"]
   ["NAC"      "PM" "SPM" "666" "Saint Pierre and Miquelon"]
   ["NAC"      "VC" "VCT" "670" "Saint Vincent and the Grenadines"]
   ["EUR"      "SM" "SMR" "674" "San Marino, Republic of"]
   ["AFR"      "ST" "STP" "678" "São Tomé and Príncipe, Democratic Republic of"]
   ["ASI"      "SA" "SAU" "682" "Saudi Arabia, Kingdom of"]
   ["AFR"      "SN" "SEN" "686" "Senegal, Republic of"]
   ["EUR"      "RS" "SRB" "688" "Serbia, Republic of"]
   ["AFR"      "SC" "SYC" "690" "Seychelles, Republic of"]
   ["AFR"      "SL" "SLE" "694" "Sierra Leone, Republic of"]
   ["ASI"      "SG" "SGP" "702" "Singapore, Republic of"]
   ["EUR"      "SK" "SVK" "703" "Slovakia (Slovak Republic)"]
   ["ASI"      "VN" "VNM" "704" "Vietnam, Socialist Republic of"]
   ["EUR"      "SI" "SVN" "705" "Slovenia, Republic of"]
   ["AFR"      "SO" "SOM" "706" "Somalia, Somali Republic"]
   ["AFR"      "ZA" "ZAF" "710" "South Africa, Republic of"]
   ["AFR"      "ZW" "ZWE" "716" "Zimbabwe, Republic of"]
   ["EUR"      "ES" "ESP" "724" "Spain, Kingdom of"]
   ["AFR"      "SS" "SSD" "728" "South Sudan"]
   ["AFR"      "SD" "SDN" "729" "Sudan, Republic of"]
   ["AFR"      "EH" "ESH" "732" "Western Sahara"]
   ["SAC"      "SR" "SUR" "740" "Suriname, Republic of"]
   ["EUR"      "SJ" "SJM" "744" "Svalbard & Jan Mayen Islands"]
   ["AFR"      "SZ" "SWZ" "748" "Swaziland, Kingdom of"]
   ["EUR"      "SE" "SWE" "752" "Sweden, Kingdom of"]
   ["EUR"      "CH" "CHE" "756" "Switzerland, Swiss Confederation"]
   ["ASI"      "SY" "SYR" "760" "Syrian Arab Republic"]
   ["ASI"      "TJ" "TJK" "762" "Tajikistan, Republic of"]
   ["ASI"      "TH" "THA" "764" "Thailand, Kingdom of"]
   ["AFR"      "TG" "TGO" "768" "Togo, Togolese Republic"]
   ["OCE"      "TK" "TKL" "772" "Tokelau"]
   ["OCE"      "TO" "TON" "776" "Tonga, Kingdom of"]
   ["NAC"      "TT" "TTO" "780" "Trinidad and Tobago, Republic of"]
   ["ASI"      "AE" "ARE" "784" "United Arab Emirates"]
   ["AFR"      "TN" "TUN" "788" "Tunisia, Tunisian Republic"]
   ["ASI"      "TR" "TUR" "792" "Turkey, Republic of"]
   ["EUR"      "TR" "TUR" "792" "Turkey, Republic of"]
   ["ASI"      "TM" "TKM" "795" "Turkmenistan"]
   ["NAC"      "TC" "TCA" "796" "Turks and Caicos Islands"]
   ["OCE"      "TV" "TUV" "798" "Tuvalu"]
   ["AFR"      "UG" "UGA" "800" "Uganda, Republic of"]
   ["EUR"      "UA" "UKR" "804" "Ukraine"]
   ["EUR"      "MK" "MKD" "807" "Macedonia, The Former Yugoslav Republic of"]
   ["AFR"      "EG" "EGY" "818" "Egypt, Arab Republic of"]
   ["EUR"      "GB" "GBR" "826" "United Kingdom of Great Britain & Northern Ireland"]
   ["EUR"      "GG" "GGY" "831" "Guernsey, Bailiwick of"]
   ["EUR"      "JE" "JEY" "832" "Jersey, Bailiwick of"]
   ["EUR"      "IM" "IMN" "833" "Isle of Man"]
   ["AFR"      "TZ" "TZA" "834" "Tanzania, United Republic of"]
   ["NAC"      "US" "USA" "840" "United States of America"]
   ["NAC"      "VI" "VIR" "850" "United States Virgin Islands"]
   ["AFR"      "BF" "BFA" "854" "Burkina Faso"]
   ["SAC"      "UY" "URY" "858" "Uruguay, Eastern Republic of"]
   ["ASI"      "UZ" "UZB" "860" "Uzbekistan, Republic of"]
   ["SAC"      "VE" "VEN" "862" "Venezuela, Bolivarian Republic of"]
   ["OCE"      "WF" "WLF" "876" "Wallis and Futuna"]
   ["OCE"      "WS" "WSM" "882" "Samoa, Independent State of"]
   ["ASI"      "YE" "YEM" "887" "Yemen"]
   ["AFR"      "ZM" "ZMB" "894" "Zambia, Republic of"]
   ["ASI"      "XD"  nil   nil  "United Nations Neutral Zone"]
   ["ASI"      "XE"  nil   nil  "Iraq-Saudi Arabia Neutral Zone"]
   ["ASI"      "XS"  nil   nil  "Spratly Islands"]
   ["OCE"      "XX"  nil   nil  "Disputed Territory"]

   [d/cruise-ship-country-code
    d/cruise-ship-2-country-code
    d/cruise-ship-3-country-code
    nil   nil  d/others]

   [d/default-continent-code
    d/default-2-country-code
    d/default-3-country-code
    nil   nil  d/others]

   ;; [d/default-continent-code
   ;;  d/worldwide-2-country-code
   ;;  d/worldwide-3-country-code
   ;;  nil   nil  d/worldwide]
   ])

(defn continent-code [continent]
  (get cn/continent-names--continent-codes-hm continent))

(defn continent-name [continent-code]
  (get (clojure.set/map-invert cn/continent-names--continent-codes-hm)
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
        (or
         (get d/cruise-ship-country-code country-name)
         (do (println (format
                       "No country code found for \"%s\". Using \"%s\""
                       country-name
                       d/default-2-country-code
                       ))
             d/default-2-country-code))))))

(defn country-name
  "Country name from 2-letter country code: \"DE\" -> \"Germany\" "
  [cc]
  (get cr/country-code--country cc))

(defn country-alias
  "Get a country alias or the normal name if an alias doesn't exist"
  [cc]
  (let [country (get cr/country-code--country (s/upper-case cc))]
    (get cr/aliases-inverted country country)))

(defn country-name-aliased [cc]
  (if (in? ["VA" "TW" "DO" "IR" "RU" "PS" "AE" "KR" "MK" "BA" "CD" "BO"
            "MD" "BN" "VE" "VC" "KP" "TZ" "VC" "XK" #_"CZ"]
           cc)
    (country-alias cc)
    (country-name cc)))

(defn continent-code--country-codes [continent-code]
  (->> continent-countries-hm
       (filter (fn [[continent-c country-c & rest]]
                 (= continent-code continent-c)))
       (mapv (fn [[_ country-c & rest]] country-c))))

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

(def max-affected-country-name-len
  (->> (all-affected-country-codes)
       (map (fn [cc]
              (country-name-aliased cc )
              #_(cr/country-name cc)))
       (sort-by count)
       last
       count))

(defn all-affected-continent-codes
  "TODO worldwide"
  []
  (->> (all-affected-country-codes)
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
