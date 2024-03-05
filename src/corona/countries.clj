;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.countries)

(ns corona.countries
  (:require
   [clojure.set :as cset]
   [clojure.string :as cstr]
   [corona.country-codes :as ccc]
   [corona.telemetry :refer [defn-fun-id errorf]]
   [utils.core :refer [in?]])
  (:import
   (com.neovisionaries.i18n CountryCode CountryCode$Assignment)))

;; (set! *warn-on-reflection* true)

(defn country-code--country-nv-i18n
  "The base data from the internationalization, ISO 3166-1 country codes etc.
  library: com.neovisionaries/nv-i18n \"1.27\""
  []
  (transduce (comp
              (filter (fn [^com.neovisionaries.i18n.CountryCode ccode]
                        (= (.getAssignment ccode)
                           CountryCode$Assignment/OFFICIALLY_ASSIGNED)))
              (map (fn [^com.neovisionaries.i18n.CountryCode ccode]
                     [(str ccode) (.getName ccode)])))
             conj {}
             (CountryCode/values))
  #_(->> (CountryCode/values)
       (filter (fn [ccode] (= (.getAssignment ccode)
                          CountryCode$Assignment/OFFICIALLY_ASSIGNED)))
       (map (fn [ccode] [(str ccode)
                      (.getName ccode)]))
       (into {})))

(def ^:const country-code--country
  (conj
   (country-code--country-nv-i18n)
   ccc/country-code-worldwide--worldwide
   ccc/country-code-default--others
   ;; see
   ;; https://en.wikipedia.org/wiki/List_of_sovereign_states_and_dependent_territories_by_continent_(data_file)
   {
    "XK" "Republic of Kosovo"
    "XD" "United Nations Neutral Zone"
    "XE" "Iraq-Saudi Arabia Neutral Zone"
    "XS" "Spratly Islands"
    "XX" "Disputed Territory"
    }))

(def ^:const country--country-code
  "Mapping: country-names -> 2-letter country codes.
  https://en.wikipedia.org/wiki/ISO_3166-1#Officially_assigned_code_elements"
  (cset/map-invert country-code--country))

(defn get-country-name
  "Country name from 2-letter country code: \"DE\" -> \"Germany\" "
  [ccode]
  (get country-code--country ccode))

(def ^:const country-alias--country-code
  "Mapping of alternative names, spelling, typos to the names of countries used by
  the ISO 3166-1 norm. \"Others\" has no mapping."
  {
   "World"                            "ZZ" ; "Worldwide"

   "Czechia"                          "CZ" ; "Czech Republic"

   "Mainland China"                   "CN" ; "China"

   "South Korea"                      "KR" ; "Korea, Republic of"
   "Korea, South"                     "KR" ; "Korea, Republic of"
   "Korea South"                      "KR" ; "Korea, Republic of"
   "Republic of Korea"                "KR" ; "Korea, Republic of"

   "Taiwan"                           "TW" ; "Taiwan, Province of China"
   "Taiwan*"                          "TW" ; "Taiwan, Province of China"
   "Taipei and environs"              "TW" ; "Taiwan, Province of China"

   "US"                               "US" ; "United States"
   "Puerto Rico (US)"                 "PR"
   "Guam (US)"                        "GU"
   "American Samoa (US)"              "AS"
   "Northern Mariana Islands (US)"    "MP"

   "Macau"                            "MO" ; "Macao"
   "Macao SAR"                        "MO" ; "Macao"
   "Macau (China)"                    "MO"

   "Vietnam"                          "VN" ; "Viet Nam"

   "UK"                               "GB" ; "United Kingdom"
   ;; Conjoin on United Kingdom
   "North Ireland"                    "GB"

   "Isle of Man (UK)"                 "IM"
   "Turks and Caicos Islands (UK)"    "TC"
   "Gibraltar (UK)"                   "GI"
   "Cayman Islands (UK)"              "KY"
   "Bermuda (UK)"                     "BM"
   "Anguilla (UK)"                    "AI"
   "Montserrat (UK)"                  "MS"

   "Russia"                           "RU" ; "Russian Federation"
   ;; Conjoin on Russian Federation
   "South Ossetia"                    "RU"

   "Iran"                             "IR" ; "Iran, Islamic Republic of"
   "Iran (Islamic Republic of)"       "IR" ; "Iran, Islamic Republic of"

   "Saint Barthelemy"                 "BL" ; "Saint Barthélemy"
   "Saint Barthélemy (France)"        "BL"

   "Palestine"                        "PS" ; "Palestine, State of"
   "State of Palestine"               "PS" ; "Palestine, State of"
   "occupied Palestinian territory"   "PS" ; "Palestine, State of"

   "Vatican City"                     "VA" ; "Holy See (Vatican City State)"
   "Holy See"                         "VA" ; "Holy See (Vatican City State)"

   "DR Congo"                         "CD" ; "Congo, the Democratic Republic of the"
   "Congo (Kinshasa)"                 "CD" ; "Congo, the Democratic Republic of the"
   "Democratic Republic of the Congo" "CD" ; "Congo, the Democratic Republic of the"
   "Congo-Kinshasa"                   "CD"

   "Congo (Brazzaville)"              "CG" ; "Congo"
   "Republic of the Congo"            "CG" ; "Congo"
   "Congo-Brazzaville"                "CG"

   "Tanzania"                         "TZ" ; "Tanzania, United Republic of"
   "Venezuela"                        "VE" ; "Venezuela, Bolivarian Republic of"
   "North Korea"                      "KP" ; "Korea, Democratic People's Republic of"
   "Syria"                            "SY" ; "Syrian Arab Republic"
   "Bolivia"                          "BO" ; "Bolivia, Plurinational State of"
   "Laos"                             "LA" ; "Lao People's Democratic Republic"

   "Moldova"                          "MD" ; "Moldova, Republic of"
   "Republic of Moldova"              "MD" ; "Moldova, Republic of"

   "Swaziland"                        "SZ" ; "Eswatini"
   "Cabo Verde"                       "CV" ; "Cape Verde"
   "Brunei"                           "BN" ; "Brunei Darussalam"

   "Sao Tome & Principe"              "ST" ; "Sao Tome and Principe"
   "São Tomé and Príncipe"            "ST" ; "Sao Tome and Principe"
   "Sao Tome and Principe"            "ST" ; "Sao Tome and Principe"

   "Micronesia"                       "FM" ; "Micronesia, Federated States of"
   "F.S. Micronesia"                  "FM" ; "Micronesia, Federated States of"
   "Federated States of Micronesia"   "FM" ; "Micronesia, Federated States of"

   "St. Vincent & Grenadines"         "VC" ; "Saint Vincent and the Grenadines"
   "Saint Vincent"                    "VC" ; "Saint Vincent and the Grenadines"

   "U.S. Virgin Islands"              "VI" ; "Virgin Islands, U.S."
   "U.S. Virgin Islands (US)"         "VI" ; "Virgin Islands, U.S."

   "British Virgin Islands"           "VG" ; "Virgin Islands, British"
   "British Virgin Islands (UK)"      "VG"

   ;; Conjoin on France
   "Saint Martin (France)"            "FR" ; "France"

   "New Caledonia (France)"           "NC"
   "French Polynesia (France)"        "PF"

   "Saint Kitts & Nevis"              "KN" ; "Saint Kitts and Nevis"
   "St. Kitts & Nevis"                "KN" ; "Saint Kitts and Nevis"

   "Faeroe Islands"                   "FO" ; "Faroe Islands"

   "Sint Maarten"                     "SX" ; "Sint Maarten (Dutch part)"
   "Sint Maarten (Netherlands)"       "SX"

   "Aruba (Netherlands)"              "AW"

   "Turks and Caicos"                 "TC" ; "Turks and Caicos Islands"

   "Wallis & Futuna"                  "WF" ; "Wallis and Futuna"
   "Wallis and Futuna (France)"       "WF"

   "Saint Helena"                                     "SH"
   "Saint Helena, Ascensionand Tristan da Cunha (UK)" "SH"

   "Saint Pierre & Miquelon"            "PM" ; "Saint Pierre and Miquelon"
   "Saint Pierre and Miquelon (France)" "PM"

   "Falkland Islands"                 "FK" ; "Falkland Islands (Malvinas)"
   "Falkland Islands (UK)"            "FK"

   "Republic of Ireland"              "IE" ; "Ireland"
   " Azerbaijan"                      "AZ" ; "Azerbaijan"

   "East Timor"                       "TL" ; "Timor-Leste"

   ;; Guernsey and Jersey form Channel Islands. They should have separate statistics.
   ;; https://en.wikipedia.org/wiki/Channel_Islands
   ;; "Guernsey and Jersey"
   ;; "Channel Islands"

   "Jersey (UK)"                      "JE"
   "Guernsey (UK)"                    "GG"

   "Caribbean Netherlands"            "BQ" ; "Bonaire, Sint Eustatius and Saba"

   "Emirates"                         "AE" ; "United Arab Emirates"

   "Bosnia–Herzegovina"               "BA" ; "Bosnia and Herzegovina"
   "Bosnia"                           "BA" ; "Bosnia and Herzegovina"

   "Dominican Rep"                    "DO" ; "Dominican Republic"

   "Macedonia"                        "MK" ; "North Macedonia, Republic of"
   "North Macedonia"                  "MK" ; "North Macedonia, Republic of"

   "Ivory Coast"                      "CI" ; "Côte d'Ivoire"
   "Cote d'Ivoire"                    "CI" ; "Côte d'Ivoire"

   "Saint Martin"                     "MF" ; "Saint Martin (French part)"
   "St. Martin"                       "MF" ; "Saint Martin (French part)"

   "Hong Kong SAR"                    "HK" ; "Hong Kong"
   "Hong Kong (China)"                "HK"

   ;; "Transnistria" recognized as part of Moldova `"MD"` but may have a different
   ;; medical system

   ;; "Northern Cyprus" considered to be a part of Cyprus `"CY"` but may have a
   ;; different medical system

   ;; "Abkhazia" recognized as part of Georgia `"GE"` but may have a different
   ;; medical system

   "Republic of Artsakh"              "AM" ; "Armenia"
   "Cook Islands (NZ)"                "CK"
   "Christmas Island (Australia)"     "CX"
   "Norfolk Island (Australia)"       "NF"
   "Niue (NZ)"                        "NU"
   "Tokelau (NZ)"                     "TK"
   "Greenland (Denmark)"              "GL"
   "Faroe Islands (Denmark)"          "FO"
   "Åland Islands (Finland)"          "AX"
   "Reunion"                          "RE" ; "Réunion"

   "Curacao"                          "CW" ; "Curaçao"
   "Curaçao (Netherlands)"            "CW"

   "The Bahamas"                      "BS" ; "Bahamas"
   "Kosovo"                           "XK" ; "Kosovo, Republic of"
   "Trinidad & Tobago"                "TT" ; "Trinidad and Tobago"
   "Antigua & Barbuda"                "AG" ; "Antigua and Barbuda"
   "Central African Rep"              "CF" ; "Central African Republic"

   "Burma"                            "MM" ; Myanmar

   "Pitcairn Islands (UK)"               "PN"
   "Cocos (Keeling) Islands (Australia)" "CC"

   ;; "Others" has no mapping
   ;; "Cruise Ship" is mapped to the default val
   })

(def ^:const country-alias--country-code-inverted
  "See also `country-name-aliased`"
  (conj
   ;; select desired aliases
   {
    "VA" "Vatican"
    "TW" "Taiwan"
    "DO" "Dom Rep"
    "IR" "Iran"
    "RU" "Russia"
    "PS" "Palestine"
    "AE" "UA Emirates"
    "KR" "South Korea"
    "MK" "Macedonia"
    "BA" "Bosnia"
    "BO" "Bolivia"
    "MD" "Moldova"
    "BN" "Brunei"
    "VE" "Venezuela"
    "VC" "St Vincent"
    "KP" "North Korea"
    "TZ" "Tanzania"
    "XK" "Kosovo"
    "LA" "Laos"
    "SY" "Syria"
    "KN" "St Kitts Nevis"
    "TT" "Trinidad Tobago"
    "AG" "Antigua Barbuda"
    "CF" "Central Africa"
    "US" "USA"
    "GB" "UK"
    "CZ" "Czechia"
    ;; "ST" "St Tome & Prin"
    "ST" "St Tome Principe"
    "PG" "Papua N Guinea"
    "GQ" "Equat Guinea"
    "QQ" "Rest"
    "FM" "Micronesia"
    "PM" "St Pierre Miquelo"
    "SJ" "Svalbard J. Mayen"
    "TC" "Turks Caicos Isla"
    "VI" "Virgin Islands US"
    "HM" "Heard McDonald Is"
    "SX" "St Maarten, Dutch"
    "MF" "St Martin, French"
    "SH" "St He Ascen Cunha"
    "FK" "Falklands/Malvina"
    "CC" "Cocos/Keeling Isl"
    "MP" "Northern Mariana"
    "VG" "Brit Virgin Islan"
    "BQ" "Bona St Eust Saba"
    "IO" "Br Indian Ocean T"
    "UM" "US Min Outlying I"
    "TF" "Fr Southern Terri"
    "GS" "S Georgia Sandwic"
    "CG" "Congo-Brazzaville"
    "CD" "Congo-Kinshasa"
    "VN" "Vietnam"
    }))

(defn- lower-case-keyword
  "ABC -> abc"
  [hm]
  (into {} (map (fn [[k v]] [(cstr/lower-case k) v]) hm)))

(defn-fun-id get-country-code
  "Return 2-letter country code (Alpha-2) according to
  https://en.wikipedia.org/wiki/ISO_3166-1
  Defaults to `default-2-country-code`."
  [country-name]
  (let [country (cstr/lower-case country-name)
        lcases-countries (lower-case-keyword
                          (conj country--country-code
                                country-alias--country-code
                                (cset/map-invert country-alias--country-code-inverted)))]
    (if-let [ccode (get lcases-countries country)]
      ccode
      (do (errorf "\"%s\" has no country code. Using \"%s\""
                  country-name ccc/default-2-country-code)
          ccc/default-2-country-code))))

(defn country-alias
  "Get a country alias or the normal name if an alias doesn't exist"
  [ccode]
  (let [up-ccode (cstr/upper-case ccode)
        country (get country-code--country up-ccode)]
    (get country-alias--country-code-inverted up-ccode country)))

(defn country-name-aliased
  "Use an alias; typically a shorter name for some countries.
  See `country-alias--country-code-inverted`"
  [ccode]
  (if (in?
       [
        "VA" "TW" "DO" "IR" "RU" "PS" "AE" "KR" "MK" "BA" "CD" "BO" "MD" "BN"
        "VE" "VC" "KP" "TZ" "XK" "LA" "SY" "KN" "TT" "AG" "CF" "CZ" "ST" "PG"
        "GQ" "QQ" "FM" "PM" "SJ" "TC" "VI" "HM" "SX" "MF" "SH" "FK" "CC" "BQ"
        "MP" "VG" "IO" "UM" "TF" "GS" "CD" "CG" "VN"
        ]
       ccode)
    (country-alias ccode)
    (get-country-name ccode)))

(def ^:const population-table-countries
  "From
  https://en.wikipedia.org/wiki/List_of_countries_and_dependencies_by_population
  The v2 api service https://coronavirus-tracker-api.herokuapp.com/v2 doesn't
  contain precise data. Values may significantly differ from the
  `corona.tables/population`

  Countries not listed:
  TF - Fr Southern Terri
  MF - St Martin, French
  SJ - Svalbard J. Mayen
  QQ - Rest
  UM - US Min Outlying I
  BQ - Bona St Eust Saba
  BV - Bouvet Island
  GS - S Georgia Sandwic
  AQ - Antarctica
  HM - Heard McDonald Is
  "
  [
   ["CN" #_"China"                                            1404614720]
   ["IN" #_"India"                                            1367703110]
   ["US" #_"United States"                                    330375132]
   ["ID" #_"Indonesia"                                        269603400]
   ["PK" #_"Pakistan"                                         220892331]
   ["BR" #_"Brazil"                                           212122227]
   ["NG" #_"Nigeria"                                          206139587]
   ["BD" #_"Bangladesh"                                       169373348]
   ["RU" #_"Russia"                                           146748590]
   ["MX" #_"Mexico"                                           127792286]
   ["JP" #_"Japan"                                            125810000]
   ["PH" #_"Philippines"                                      109218624]
   ["EG" #_"Egypt"                                            100967392]
   ["ET" #_"Ethiopia"                                         100829000]
   ["VN" #_"Vietnam"                                          96208984]
   ["CD" #_"DR Congo"                                         91994000]
   ["IR" #_"Iran"                                             83816683]
   ["DE" #_"Germany"                                          83157201]
   ["TR" #_"Turkey"                                           83154997]
   ["FR" #_"France"                                           67132000]
   ["GB" #_"United Kingdom"                                   66796807]
   ["TH" #_"Thailand"                                         66557580]
   ["IT" #_"Italy"                                            60095164]
   ["ZA" #_"South Africa"                                     59622350]
   ["TZ" #_"Tanzania"                                         57637628]
   ["MM" #_"Myanmar"                                          54817919]
   ["KR" #_"South Korea"                                      51839953]
   ["CO" #_"Colombia"                                         50372424]
   ["KE" #_"Kenya"                                            47564296]
   ["ES" #_"Spain"                                            47329981]
   ["AR" #_"Argentina"                                        45376763]
   ["DZ" #_"Algeria"                                          43900000]
   ["SD" #_"Sudan"                                            42862170]
   ["UA" #_"Ukraine"                                          41743935]
   ["UG" #_"Uganda"                                           41583600]
   ["IQ" #_"Iraq"                                             40150200]
   ["PL" #_"Poland"                                           38353000]
   ["CA" #_"Canada"                                           38187300]
   ["MA" #_"Morocco"                                          36028348]
   ["UZ" #_"Uzbekistan"                                       34434659]
   ["SA" #_"Saudi Arabia"                                     34218169]
   ["AF" #_"Afghanistan"                                      32890171]
   ["MY" #_"Malaysia"                                         32689860]
   ["PE" #_"Peru"                                             32625948]
   ["AO" #_"Angola"                                           31127674]
   ["GH" #_"Ghana"                                            30955202]
   ["MZ" #_"Mozambique"                                       30066648]
   ["NP" #_"Nepal"                                            29996478]
   ["YE" #_"Yemen"                                            29825968]
   ["VE" #_"Venezuela"                                        28435943]
   ["CI" #_"Ivory Coast"                                      26453542]
   ["MG" #_"Madagascar"                                       26251309]
   ["AU" #_"Australia"                                        25670803]
   ["KP" #_"North Korea"                                      25550000]
   ["CM" #_"Cameroon"                                         24348251]
   ["TW" #_"Taiwan"                                           23574334]
   ["NE" #_"Niger"                                            23196002]
   ["LK" #_"Sri Lanka"                                        21803000]
   ["BF" #_"Burkina Faso"                                     21510181]
   ["ML" #_"Mali"                                             20250833]
   ["CL" #_"Chile"                                            19458310]
   ["RO" #_"Romania"                                          19317984]
   ["MW" #_"Malawi"                                           19129952]
   ["KZ" #_"Kazakhstan"                                       18784120]
   ["ZM" #_"Zambia"                                           17885422]
   ["EC" #_"Ecuador"                                          17577052]
   ["NL" #_"Netherlands"                                      17513331]
   ["SY" #_"Syria"                                            17500657]
   ["GT" #_"Guatemala"                                        16858333]
   ["SN" #_"Senegal"                                          16705608]
   ["TD" #_"Chad"                                             16244513]
   ["SO" #_"Somalia"                                          15893219]
   ["ZW" #_"Zimbabwe"                                         15473818]
   ["KH" #_"Cambodia"                                         15288489]
   ["SS" #_"South Sudan"                                      13249924]
   ["RW" #_"Rwanda"                                           12663116]
   ["GN" #_"Guinea"                                           12559623]
   ["BI" #_"Burundi"                                          12309600]
   ["BJ" #_"Benin"                                            12114193]
   ["HT" #_"Haiti"                                            11743017]
   ["TN" #_"Tunisia"                                          11708370]
   ["BO" #_"Bolivia"                                          11633371]
   ["BE" #_"Belgium"                                          11535652]
   ["CU" #_"Cuba"                                             11193470]
   ["JO" #_"Jordan"                                           10778268]
   ["GR" #_"Greece"                                           10724599]
   ["CZ" #_"Czech Republic"                                   10699142]
   ["DO" #_"Dominican Republic"                               10448499]
   ["SE" #_"Sweden"                                           10358538]
   ["PT" #_"Portugal"                                         10295909]
   ["AZ" #_"Azerbaijan"                                       10095900]
   ["AE" #_"United Arab Emirates"                             9890400]
   ["HU" #_"Hungary"                                          9769526]
   ["BY" #_"Belarus"                                          9408400]
   ["TJ" #_"Tajikistan"                                       9313800]
   ["HN" #_"Honduras"                                         9304380]
   ["IL" #_"Israel"                                           9258150]
   ["PG" #_"Papua New Guinea"                                 8935000]
   ["AT" #_"Austria"                                          8915382]
   ["CH" #_"Switzerland"                                      8632703]
   ["SL" #_"Sierra Leone"                                     8100318]
   ["TG" #_"Togo"                                             7706000]
   ["HK" #_"Hong Kong (China)"                                7509200]
   ["PY" #_"Paraguay"                                         7252672]
   ["LA" #_"Laos"                                             7231210]
   ["BG" #_"Bulgaria"                                         6951482]
   ["RS" #_"Serbia"                                           6926705]
   ["LY" #_"Libya"                                            6871287]
   ["LB" #_"Lebanon"                                          6825442]
   ["SV" #_"El Salvador"                                      6765753]
   ["KG" #_"Kyrgyzstan"                                       6586600]
   ["NI" #_"Nicaragua"                                        6527691]
   ["TM" #_"Turkmenistan"                                     6031187]
   ["DK" #_"Denmark"                                          5825337]
   ["SG" #_"Singapore"                                        5685807]
   ["CF" #_"Central African Republic"                         5633412]
   ["CG" #_"Congo"                                            5518092]
   ["FI" #_"Finland"                                          5502259]
   ["SK" #_"Slovakia"                                         5460136]
   ["NO" #_"Norway"                                           5374807]
   ["CR" #_"Costa Rica"                                       5111238]
   ["PS" #_"Palestine"                                        5101152]
   ["NZ" #_"New Zealand"                                      5091527]
   ["IE" #_"Ireland"                                          4977400]
   ["LR" #_"Liberia"                                          4568298]
   ["OM" #_"Oman"                                             4480333]
   ["KW" #_"Kuwait"                                           4464521]
   ["PA" #_"Panama"                                           4278500]
   ["MR" #_"Mauritania"                                       4173077]
   ["HR" #_"Croatia"                                          4058165]
   ["GE" #_"Georgia"                                          3716858]
   ["ER" #_"Eritrea"                                          3546000]
   ["UY" #_"Uruguay"                                          3530912]
   ["MN" #_"Mongolia"                                         3339765]
   ["BA" #_"Bosnia and Herzegovina"                           3332593]
   ["PR" #_"Puerto Rico (US)"                                 3193694]
   ["AM" #_"Armenia"                                          2963000]
   ["AL" #_"Albania"                                          2845955]
   ["LT" #_"Lithuania"                                        2795459]
   ["QA" #_"Qatar"                                            2735707]
   ["JM" #_"Jamaica"                                          2726667]
   ["MD" #_"Moldova"                                          2640438]
   ["NA" #_"Namibia"                                          2504498]
   ["BW" #_"Botswana"                                         2374698]
   ["GM" #_"Gambia"                                           2335504]
   ["GA" #_"Gabon"                                            2176766]
   ["SI" #_"Slovenia"                                         2097195]
   ["MK" #_"North Macedonia"                                  2076255]
   ["LS" #_"Lesotho"                                          2007201]
   ["LV" #_"Latvia"                                           1899200]
   ["XK" #_"Kosovo"                                           1782115]
   ["GW" #_"Guinea-Bissau"                                    1624945]
   ["BH" #_"Bahrain"                                          1592000]
   ["GQ" #_"Equatorial Guinea"                                1454789]
   ["TT" #_"Trinidad and Tobago"                              1363985]
   ["EE" #_"Estonia"                                          1328976]
   ["TL" #_"East Timor"                                       1299412]
   ["MU" #_"Mauritius"                                        1266000]
   ["SZ" #_"Eswatini"                                         1093238]
   ["DJ" #_"Djibouti"                                         962452]
   ["FJ" #_"Fiji"                                             889327]
   ["CY" #_"Cyprus"                                           875900]
   ["KM" #_"Comoros"                                          758316]
   ["BT" #_"Bhutan"                                           748931]
   ["GY" #_"Guyana"                                           744962]
   ["SB" #_"Solomon Islands"                                  694619]
   ["MO" #_"Macau (China)"                                    685400]
   ["LU" #_"Luxembourg"                                       626108]
   ["ME" #_"Montenegro"                                       621873]
   ["EH" #_"Western Sahara"                                   597000]
   ["SR" #_"Suriname"                                         590100]
   ["CV" #_"Cape Verde"                                       556857]
   ["MT" #_"Malta"                                            514564]
   ;; [nil  #_"Transnistria"                                     469000]
   ["BN" #_"Brunei"                                           459500]
   ["BZ" #_"Belize"                                           419199]
   ["BS" #_"Bahamas"                                          389410]
   ["MV" #_"Maldives"                                         383135]
   ["IS" #_"Iceland"                                          366700]
   ;; [nil  #_"Northern Cyprus"                                  351965]
   ["VU" #_"Vanuatu"                                          304500]
   ["BB" #_"Barbados"                                         287025]
   ["PF" #_"French Polynesia (France)"                        278400]
   ["NC" #_"New Caledonia (France)"                           271407]
   ;; [nil  #_"Abkhazia"                                         245246]
   ["ST" #_"São Tomé and Príncipe"                            210240]
   ["WS" #_"Samoa"                                            202506]
   ["LC" #_"Saint Lucia"                                      178696]
   ["GU" #_"Guam (US)"                                        172400]
   ["CW" #_"Curaçao (Netherlands)"                            156223]
   ["AM" #_"Republic of Artsakh"                              148800]
   ["KI" #_"Kiribati"                                         120100]
   ["AW" #_"Aruba (Netherlands)"                              112269]
   ["GD" #_"Grenada"                                          112003]
   ["VC" #_"Saint Vincent and the Grenadines"                 110696]
   ["JE" #_"Jersey (UK)"                                      107800]
   ["FM" #_"F.S. Micronesia"                                  104650]
   ["VI" #_"U.S. Virgin Islands (US)"                         104578]
   ["TO" #_"Tonga"                                            100651]
   ["SC" #_"Seychelles"                                       98462]
   ["AG" #_"Antigua and Barbuda"                              97895]
   ["IM" #_"Isle of Man (UK)"                                 83314]
   ["AD" #_"Andorra"                                          77543]
   ["DM" #_"Dominica"                                         71808]
   ["KY" #_"Cayman Islands (UK)"                              69914]
   ["BM" #_"Bermuda (UK)"                                     64054]
   ["GG" #_"Guernsey (UK)"                                    63276]
   ["AS" #_"American Samoa (US)"                              56700]
   ["GL" #_"Greenland (Denmark)"                              56367]
   ["MP" #_"Northern Mariana Islands (US)"                    56200]
   ["MH" #_"Marshall Islands"                                 55500]
   ["RU" #_"South Ossetia"                                    53532]
   ["KN" #_"Saint Kitts and Nevis"                            52823]
   ["FO" #_"Faroe Islands (Denmark)"                          52816]
   ["TC" #_"Turks and Caicos Islands (UK)"                    42953]
   ["SX" #_"Sint Maarten (Netherlands)"                       40614]
   ["LI" #_"Liechtenstein"                                    38749]
   ["MC" #_"Monaco"                                           38100]
   ["FR" #_"Saint Martin (France)"                            35334]
   ["GI" #_"Gibraltar (UK)"                                   33691]
   ["SM" #_"San Marino"                                       33630]
   ["AX" #_"Åland Islands (Finland)"                          30074]
   ["VG" #_"British Virgin Islands (UK)"                      30030]
   ["PW" #_"Palau"                                            17900]
   ["CK" #_"Cook Islands (NZ)"                                15200]
   ["AI" #_"Anguilla (UK)"                                    14869]
   ["WF" #_"Wallis and Futuna (France)"                       11558]
   ["NR" #_"Nauru"                                            11000]
   ["TV" #_"Tuvalu"                                           10200]
   ["BL" #_"Saint Barthélemy (France)"                        9961]
   ["PM" #_"Saint Pierre and Miquelon (France)"               5997]
   ["SH" #_"Saint Helena, Ascensionand Tristan da Cunha (UK)" 5633]
   ["MS" #_"Montserrat (UK)"                                  4989]
   ["FK" #_"Falkland Islands (UK)"                            3198]
   ["CX" #_"Christmas Island (Australia)"                     1955]
   ["NF" #_"Norfolk Island (Australia)"                       1735]
   ["NU" #_"Niue (NZ)"                                        1520]
   ["TK" #_"Tokelau (NZ)"                                     1400]
   ["VA" #_"Vatican City"                                     825]
   ["CC" #_"Cocos (Keeling) Islands (Australia)"              555]
   ["PN" #_"Pitcairn Islands (UK)"                            50]
   ])

(def ^:const population-table
  (conj population-table-countries
        ["ZZ" #_"Worldwide" (apply + (map second population-table-countries))]))

(def population
  "Toggle between country-names and codes using:
  => (clojure.set/rename-keys population
      (->> (keys population)
          (map (fn [k] {k (#_country-name get-country-code k)}))
          (reduce into)))

  Compare `corona.tables/population` with `corona.countries/population`
  => (def cdiff (->> (keys corona.countries/population)
                     (map (fn [k] {k (if-let [population-nr (get corona.tables/population k)]
                                      (- (get corona.countries/population k)
                                         population-nr)
                                      nil)}))
                     (reduce into)))"
  #_corona.tables/population
  ((comp
    ;; because e.g. population of Russia is mainland + South Ossetia
    (partial apply merge-with +)
    (partial map (fn [[cname population-nr]]
                   (hash-map cname population-nr))))
   population-table))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.countries)
