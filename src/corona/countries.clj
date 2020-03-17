(ns corona.countries)

(def worldwide-2-country-code
  "\"ZZ\" can be user-assigned."
  "ZZ")
(def worldwide-country-codes
  "\"ZZZ\" can be user-assigned."
  {worldwide-2-country-code "ZZZ"})

(def default-2-country-code
  "\"QQ\" can be user-assigned."
  "QQ")

(def default-3-country-code
  "\"QQQ\" can be user-assigned."
  "QQQ")

(def default-country-codes
  "\"QQQ\" can be user-assigned."
  {default-2-country-code default-3-country-code})

(def country-code-worldwide {worldwide-2-country-code "Worldwide"})
(def country-code-others    {default-2-country-code    "Others"})

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
   default-country-codes
   worldwide-country-codes))

(defn country-code-3-letter
  "3-letter country code from 2-letter country code: \"DE\" -> \"DEU\" "
  [cc] (get country-code-2-to-3-hm cc))

(defn all-country-codes [] (keys country-code-2-to-3-hm))

(def default-affected-country-codes
  (->> [country-code-worldwide country-code-others]
       (reduce into)
       (mapv (fn [[k v]] k))))

(def country-code--country
  (conj
    {
     "AF" "Afghanistan"
     "AX" "Åland Islands"
     "AL" "Albania"
     "DZ" "Algeria"
     "AS" "American Samoa"
     "AD" "Andorra"
     "AO" "Angola"
     "AI" "Anguilla"
     "AQ" "Antarctica"
     "AG" "Antigua and Barbuda"
     "AR" "Argentina"
     "AM" "Armenia"
     "AW" "Aruba"
     "AU" "Australia"
     "AT" "Austria"
     "AZ" "Azerbaijan"
     "BS" "Bahamas"
     "BH" "Bahrain"
     "BD" "Bangladesh"
     "BB" "Barbados"
     "BY" "Belarus"
     "BE" "Belgium"
     "BZ" "Belize"
     "BJ" "Benin"
     "BM" "Bermuda"
     "BT" "Bhutan"
     "BO" "Bolivia, Plurinational State of"
     "BQ" "Bonaire, Sint Eustatius and Saba"
     "BA" "Bosnia and Herzegovina"
     "BW" "Botswana"
     "BV" "Bouvet Island"
     "BR" "Brazil"
     "IO" "British Indian Ocean Territory"
     "BN" "Brunei Darussalam"
     "BG" "Bulgaria"
     "BF" "Burkina Faso"
     "BI" "Burundi"
     "KH" "Cambodia"
     "CM" "Cameroon"
     "CA" "Canada"
     "CV" "Cape Verde"
     "KY" "Cayman Islands"
     "CF" "Central African Republic"
     "TD" "Chad"
     "CL" "Chile"
     "CN" "China"
     "CX" "Christmas Island"
     "CC" "Cocos (Keeling) Islands"
     "CO" "Colombia"
     "KM" "Comoros"
     "CG" "Congo"
     "CD" "Congo, the Democratic Republic of the"
     "CK" "Cook Islands"
     "CR" "Costa Rica"
     "CI" "Côte d'Ivoire"
     "HR" "Croatia"
     "CU" "Cuba"
     "CW" "Curaçao"
     "CY" "Cyprus"
     "CZ" "Czech Republic"
     "DK" "Denmark"
     "DJ" "Djibouti"
     "DM" "Dominica"
     "DO" "Dominican Republic"
     "EC" "Ecuador"
     "EG" "Egypt"
     "SV" "El Salvador"
     "GQ" "Equatorial Guinea"
     "ER" "Eritrea"
     "EE" "Estonia"
     "ET" "Ethiopia"
     "FK" "Falkland Islands (Malvinas)"
     "FO" "Faroe Islands"
     "FJ" "Fiji"
     "FI" "Finland"
     "FR" "France"
     "GF" "French Guiana"
     "PF" "French Polynesia"
     "TF" "French Southern Territories"
     "GA" "Gabon"
     "GM" "Gambia"
     "GE" "Georgia"
     "DE" "Germany"
     "GH" "Ghana"
     "GI" "Gibraltar"
     "GR" "Greece"
     "GL" "Greenland"
     "GD" "Grenada"
     "GP" "Guadeloupe"
     "GU" "Guam"
     "GT" "Guatemala"
     "GG" "Guernsey"
     "GN" "Guinea"
     "GW" "Guinea-Bissau"
     "GY" "Guyana"
     "HT" "Haiti"
     "HM" "Heard Island and McDonald Islands"
     "VA" "Holy See (Vatican City State)"
     "HN" "Honduras"
     "HK" "Hong Kong"
     "HU" "Hungary"
     "IS" "Iceland"
     "IN" "India"
     "ID" "Indonesia"
     "IR" "Iran, Islamic Republic of"
     "IQ" "Iraq"
     "IE" "Ireland"
     "IM" "Isle of Man"
     "IL" "Israel"
     "IT" "Italy"
     "JM" "Jamaica"
     "JP" "Japan"
     "JE" "Jersey"
     "JO" "Jordan"
     "KZ" "Kazakhstan"
     "KE" "Kenya"
     "KI" "Kiribati"
     "KP" "Korea, Democratic People's Republic of"
     "KR" "Korea, Republic of"
     "XK" "Kosovo"
     "KW" "Kuwait"
     "KG" "Kyrgyzstan"
     "LA" "Lao People's Democratic Republic"
     "LV" "Latvia"
     "LB" "Lebanon"
     "LS" "Lesotho"
     "LR" "Liberia"
     "LY" "Libya"
     "LI" "Liechtenstein"
     "LT" "Lithuania"
     "LU" "Luxembourg"
     "MO" "Macao"
     "MK" "North Macedonia"
     "MG" "Madagascar"
     "MW" "Malawi"
     "MY" "Malaysia"
     "MV" "Maldives"
     "ML" "Mali"
     "MT" "Malta"
     "MH" "Marshall Islands"
     "MQ" "Martinique"
     "MR" "Mauritania"
     "MU" "Mauritius"
     "YT" "Mayotte"
     "MX" "Mexico"
     "FM" "Micronesia, Federated States of"
     "MD" "Moldova, Republic of"
     "MC" "Monaco"
     "MN" "Mongolia"
     "ME" "Montenegro"
     "MS" "Montserrat"
     "MA" "Morocco"
     "MZ" "Mozambique"
     "MM" "Myanmar"
     "NA" "Namibia"
     "NR" "Nauru"
     "NP" "Nepal"
     "NL" "Netherlands"
     "NC" "New Caledonia"
     "NZ" "New Zealand"
     "NI" "Nicaragua"
     "NE" "Niger"
     "NG" "Nigeria"
     "NU" "Niue"
     "NF" "Norfolk Island"
     "MP" "Northern Mariana Islands"
     "NO" "Norway"
     "OM" "Oman"
     "PK" "Pakistan"
     "PW" "Palau"
     "PS" "Palestine, State of"
     "PA" "Panama"
     "PG" "Papua New Guinea"
     "PY" "Paraguay"
     "PE" "Peru"
     "PH" "Philippines"
     "PN" "Pitcairn"
     "PL" "Poland"
     "PT" "Portugal"
     "PR" "Puerto Rico"
     "QA" "Qatar"
     "RE" "Réunion"
     "RO" "Romania"
     "RU" "Russian Federation"
     "RW" "Rwanda"
     "BL" "Saint Barthélemy"
     "SH" "Saint Helena, Ascension and Tristan da Cunha"
     "KN" "Saint Kitts and Nevis"
     "LC" "Saint Lucia"
     "MF" "Saint Martin (French part)"
     "PM" "Saint Pierre and Miquelon"
     "VC" "Saint Vincent and the Grenadines"
     "WS" "Samoa"
     "SM" "San Marino"
     "ST" "Sao Tome and Principe"
     "SA" "Saudi Arabia"
     "SN" "Senegal"
     "RS" "Serbia"
     "SC" "Seychelles"
     "SL" "Sierra Leone"
     "SG" "Singapore"
     "SX" "Sint Maarten (Dutch part)"
     "SK" "Slovakia"
     "SI" "Slovenia"
     "SB" "Solomon Islands"
     "SO" "Somalia"
     "ZA" "South Africa"
     "GS" "South Georgia and the South Sandwich Islands"
     "SS" "South Sudan"
     "ES" "Spain"
     "LK" "Sri Lanka"
     "SD" "Sudan"
     "SR" "Suriname"
     "SJ" "Svalbard and Jan Mayen"
     "SZ" "Swaziland"
     "SE" "Sweden"
     "CH" "Switzerland"
     "SY" "Syrian Arab Republic"
     "TW" "Taiwan, Province of China"
     "TJ" "Tajikistan"
     "TZ" "Tanzania, United Republic of"
     "TH" "Thailand"
     "TL" "Timor-Leste"
     "TG" "Togo"
     "TK" "Tokelau"
     "TO" "Tonga"
     "TT" "Trinidad and Tobago"
     "TN" "Tunisia"
     "TR" "Turkey"
     "TM" "Turkmenistan"
     "TC" "Turks and Caicos Islands"
     "TV" "Tuvalu"
     "UG" "Uganda"
     "UA" "Ukraine"
     "AE" "United Arab Emirates"
     "GB" "United Kingdom"
     "US" "United States"
     "UM" "United States Minor Outlying Islands"
     "UY" "Uruguay"
     "UZ" "Uzbekistan"
     "VU" "Vanuatu"
     "VE" "Venezuela, Bolivarian Republic of"
     "VN" "Viet Nam"
     "VG" "Virgin Islands, British"
     "VI" "Virgin Islands, U.S."
     "WF" "Wallis and Futuna"
     "EH" "Western Sahara" "YE" "Yemen"
     "ZM" "Zambia"
     "ZW" "Zimbabwe"
     }
    country-code-worldwide
    country-code-others))

(def country--country-code
  "Mapping of country names to alpha-2 codes.
  https://en.wikipedia.org/wiki/ISO_3166-1#Officially_assigned_code_elements"
  (clojure.set/map-invert country-code--country))

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
   "World"           (->> country-code-worldwide vals first)
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
   "Saint Vincent"                   "Saint Vincent and the Grenadines"
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
   "Republic of the Congo"          "Congo"
   "The Bahamas"                    "Bahamas"
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
         }))

(def default-mappings
  {"Cruise Ship" default-2-country-code})

