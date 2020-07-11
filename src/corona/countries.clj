(ns corona.countries
  (:require
   [clojure.set :as cset]
   [corona.country-codes :refer :all]
   [utils.core :refer [in?] :exclude [id]]
   [clojure.string :as s]
   )
  (:import com.neovisionaries.i18n.CountryCode
           com.neovisionaries.i18n.CountryCode$Assignment))

;; nothing should be default affected!!!
(def default-affected-country-codes
  (->> [country-code-worldwide
        country-code-others]
       (reduce into)
       (mapv (fn [[k _]] k))))

(defn country-code--country-nv-i18n
  "The base data from the internationalization, ISO 3166-1 country codes etc.
  library: com.neovisionaries/nv-i18n \"1.27\" "
  []
  (transduce (comp
              (filter (fn [cc] (= (.getAssignment cc)
                                 CountryCode$Assignment/OFFICIALLY_ASSIGNED)))
              (map (fn [cc] [(str cc) (.getName cc)])))
             conj {}
             (CountryCode/values))
  #_(->> (CountryCode/values)
       (filter (fn [cc] (= (.getAssignment cc)
                          CountryCode$Assignment/OFFICIALLY_ASSIGNED)))
       (map (fn [cc] [(str cc)
                      (.getName cc)]))
       (into {})))

(def country-code--country
  (conj
   (country-code--country-nv-i18n)
    country-code-worldwide
    country-code-others
    ;; see
    ;; https://en.wikipedia.org/wiki/List_of_sovereign_states_and_dependent_territories_by_continent_(data_file)
    {
     xk "Republic of Kosovo"
     xd "United Nations Neutral Zone"
     xe "Iraq-Saudi Arabia Neutral Zone"
     xs "Spratly Islands"
     xx "Disputed Territory"
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
  "Mapping: country-names -> 2-letter country codes.
  https://en.wikipedia.org/wiki/ISO_3166-1#Officially_assigned_code_elements"
  (cset/map-invert country-code--country))

(defn country-name
  "Country name from 2-letter country code: \"DE\" -> \"Germany\" "
  [cc]
  (get country-code--country cc))

(def country-alias--country-code
  "Mapping of alternative names, spelling, typos to the names of countries used by
  the ISO 3166-1 norm.

  Conjoin \"North Ireland\" on \"United Kingdom\".

  https://en.wikipedia.org/wiki/Channel_Islands
  \"Guernsey\" and \"Jersey\" form \"Channel Islands\". Conjoin \"Guernsey\" on \"Jersey\".
  \"Jersey\" has higher population.

  \"Others\" has no mapping.
  "
  {
   "World"                            zz ; "Worldwide"
   "Czechia"                          cz ; "Czech Republic"
   "Mainland China"                   cn ; "China"
   "South Korea"                      kr ; "Korea, Republic of"
   "Korea, South"                     kr ; "Korea, Republic of"
   "Korea South"                      kr ; "Korea, Republic of"
   "Republic of Korea"                kr ; "Korea, Republic of"

   "Taiwan"                           tw ; "Taiwan, Province of China"
   "Taiwan*"                          tw ; "Taiwan, Province of China"
   "Taipei and environs"              tw ; "Taiwan, Province of China"

   "US"                               us ; "United States"

   "Macau"                            mo
   "Macao SAR"                        mo ; "Macao"

   "Vietnam"                          vn ; "Viet Nam"
   "UK"                               gb ; "United Kingdom"
   "Russia"                           ru ; "Russian Federation"
   "Iran"                             ir ; "Iran, Islamic Republic of"
   "Iran (Islamic Republic of)"       ir ; "Iran, Islamic Republic of"
   "Saint Barthelemy"                 bl ; "Saint Barthélemy"

   "Palestine"                        ps ; "Palestine, State of"
   "State of Palestine"               ps ; "Palestine, State of"
   "occupied Palestinian territory"   ps ; "Palestine, State of"

   "Vatican City"                     va ; "Holy See (Vatican City State)"
   "Holy See"                         va ; "Holy See (Vatican City State)"

   "DR Congo"                         cd ; "Congo, the Democratic Republic of the"
   "Congo (Kinshasa)"                 cd ; "Congo, the Democratic Republic of the"
   "Democratic Republic of the Congo" cd ; "Congo, the Democratic Republic of the"
   "Congo-Kinshasa"                   cd

   "Congo (Brazzaville)"              cg ; "Congo"
   "Republic of the Congo"            cg ; "Congo"
   "Congo-Brazzaville"                cg

   "Tanzania"                         tz ; "Tanzania, United Republic of"
   "Venezuela"                        ve ; "Venezuela, Bolivarian Republic of"
   "North Korea"                      kp ; "Korea, Democratic People's Republic of"
   "Syria"                            sy ; "Syrian Arab Republic"
   "Bolivia"                          bo ; "Bolivia, Plurinational State of"
   "Laos"                             la ; "Lao People's Democratic Republic"
   "Moldova"                          md ; "Moldova, Republic of"
   "Republic of Moldova"              md ; "Moldova, Republic of"
   "Swaziland"                        sz ; "Eswatini"
   "Cabo Verde"                       cv ; "Cape Verde"
   "Brunei"                           bn ; "Brunei Darussalam"
   "Sao Tome & Principe"              st ; "Sao Tome and Principe"
   "São Tomé and Príncipe"            st ; "Sao Tome and Principe"
   "Sao Tome and Principe"            st ; "Sao Tome and Principe"
   "Micronesia"                       fm ; "Micronesia, Federated States of"
   "F.S. Micronesia"                  fm ; "Micronesia, Federated States of"
   "Federated States of Micronesia"   fm ; "Micronesia, Federated States of"

   "St. Vincent & Grenadines"         vc ; "Saint Vincent and the Grenadines"
   "Saint Vincent"                    vc ; "Saint Vincent and the Grenadines"
   "U.S. Virgin Islands"              vi ; "Virgin Islands, U.S."
   "British Virgin Islands"           vg ; "Virgin Islands, British"
   "Saint Kitts & Nevis"              kn ; "Saint Kitts and Nevis"
   "St. Kitts & Nevis"                kn ; "Saint Kitts and Nevis"
   "Faeroe Islands"                   fo ; "Faroe Islands"
   "Sint Maarten"                     sx ; "Sint Maarten (Dutch part)"
   "Turks and Caicos"                 tc ; "Turks and Caicos Islands"
   "Wallis & Futuna"                  wf ; "Wallis and Futuna"
   "Saint Helena"                     sh ; "Saint Helena, Ascension and Tristan da Cunha"
   "Saint Pierre & Miquelon"          pm ; "Saint Pierre and Miquelon"
   "Falkland Islands"                 fk ; "Falkland Islands (Malvinas)"
   "Republic of Ireland"              ie ; "Ireland"
   " Azerbaijan"                      az ; "Azerbaijan"

   ;; Conjoin North Ireland on United Kingdom
   "North Ireland"                    gb ; "United Kingdom"
   "East Timor"                       tl ; "Timor-Leste"

   ;; Guernsey and Jersey form Channel Islands. ConjinGuernsey on Jersey.
   ;; Jersey has higher population.
   ;; https://en.wikipedia.org/wiki/Channel_Islands
   "Guernsey and Jersey"              je ; "Jersey"
   "Channel Islands"                  je ; "Jersey"
   "Caribbean Netherlands"            je ; "Bonaire, Sint Eustatius and Saba"
   "Emirates"                         ae ; "United Arab Emirates"
   ;; "Bosnia–Herzegovina"            ba ; "Bosnia and Herzegovina"
   "Bosnia"                           ba ; "Bosnia and Herzegovina"
   "Dominican Rep"                    do ; "Dominican Republic"
   "Macedonia"                        mk ; "North Macedonia, Republic of"
   "North Macedonia"                  mk ; "North Macedonia, Republic of"
   "Ivory Coast"                      ci ; "Côte d'Ivoire"
   "Cote d'Ivoire"                    ci ; "Côte d'Ivoire"
   "Saint Martin"                     mf ; "Saint Martin (French part)"
   "St. Martin"                       mf ; "Saint Martin (French part)"
   "Hong Kong SAR"                    hk ; "Hong Kong"
   "Reunion"                          re ; "Réunion"
   "Curacao"                          cw ; "Curaçao"
   "The Bahamas"                      bs ; "Bahamas"
   "Kosovo"                           xk ; "Kosovo, Republic of"
   "Trinidad & Tobago"                tt ; "Trinidad and Tobago"
   "Antigua & Barbuda"                ag ; "Antigua and Barbuda"
   "Central African Rep"              cf ; "Central African Republic"

   "Burma"                            mm ; Myanmar

;; "Others" has no mapping
   ;; "Cruise Ship" is mapped to the default val
   })

(def country-alias--country-code-inverted
  "See also `country-name-aliased`"
  (conj
   ;; (clojure.set/map-invert country-alias--country-code)
   ;; select desired aliases
   {
    va "Vatican"
    tw "Taiwan"
    do "Dom Rep"
    ir "Iran"
    ru "Russia"
    ps "Palestine"
    ae "UA Emirates"
    kr "South Korea"
    mk "Macedonia"
    ba "Bosnia"
    bo "Bolivia"
    md "Moldova"
    bn "Brunei"
    ve "Venezuela"
    vc "St Vincent"
    kp "North Korea"
    tz "Tanzania"
    xk "Kosovo"
    la "Laos"
    sy "Syria"
    kn "St Kitts Nevis"
    tt "Trinidad Tobago"
    ag "Antigua Barbuda"
    cf "Central Africa"
    us "USA"
    gb "UK"
    cz "Czechia"
    ;; st "St Tome & Prin"
    st "St Tome Principe"
    pg "Papua N Guinea"
    gq "Equat Guinea"
    qq "Rest"
    fm "Micronesia"

    pm "St Pierre Miquelo"
    sj "Svalbard J. Mayen"
    tc "Turks Caicos Isla"
    vi "Virgin Islands US"
    hm "Heard McDonald Is"
    sx "St Maarten, Dutch"
    mf "St Martin, French"
    sh "St He Ascen Cunha"
    fk "Falklands/Malvina"
    cc "Cocos/Keeling Isl"
    mp "Northern Mariana"
    vg "Brit Virgin Islan"
    bq "Bona St Eust Saba"
    io "Br Indian Ocean T"
    um "US Min Outlying I"
    tf "Fr Southern Terri"
    gs "S Georgia Sandwic"

    cg "Congo-Brazzaville"
    cd "Congo-Kinshasa"
    }))

(defn- lower-case-keyword
  "ABC -> abc"
  [hm]
  (into {} (map (fn [[k v]] [(s/lower-case k) v]) hm)))

(defn country-code
  "Return 2-letter country code (Alpha-2) according to
  https://en.wikipedia.org/wiki/ISO_3166-1
  Defaults to `default-2-country-code`."
  [country-name]
  (let [country (s/lower-case country-name)
        lcases-countries (lower-case-keyword
                          (conj country--country-code
                                country-alias--country-code
                                (cset/map-invert country-alias--country-code-inverted)))]
    (if-let [cc (get lcases-countries country)]
      cc
      (do (println (format
                    "No country code found for \"%s\". Using \"%s\""
                    country-name
                    default-2-country-code))
          default-2-country-code))))

(defn country-alias
  "Get a country alias or the normal name if an alias doesn't exist"
  [cc]
  (let [up-cc (s/upper-case cc)
        country (get country-code--country up-cc)]
    (get country-alias--country-code-inverted up-cc country)))

(defn country-name-aliased
  "Use an alias; typically a shorter name for some countries.
  See `country-alias--country-code-inverted`"
  [country-code]
  (if (in?
       [va tw do ir ru ps ae kr mk ba cd bo md bn ve vc kp tz xk la sy kn tt ag
        cf cz st pg gq qq fm pm sj tc vi hm sx mf sh fk cc bq mp vg io um tf gs
        cd cg]
       country-code)
    (country-alias country-code)
    (country-name country-code)))

(def population
  "The v2 api service doesn't contain precise numbers.
  TODO calculate population using tables/data"
  {
   "China" 1401754280 ;; 1439323776
   "India" 1359772087
   "United States" 329448153
   "Indonesia" 266911900
   "Brazil" 211252866
   "Pakistan" 218939520
   "Nigeria" 206139587
   "Bangladesh" 168265026
   "Russia" 146745098
   "Mexico" 126577691
   "Japan" 126010000
   "Philippines" 108402887
   "Egypt" 100127124
   "Ethiopia" 98665000
   "Vietnam" 96208984
   "Iran" 83279228
   "Turkey" 83154997
   "Germany" 83149300
   "France" 67064000
   "Thailand" 66481242
   "United Kingdom" 66435600
   "Italy" 60243406
   "South Africa" 58775022
   "Tanzania" 55890747
   "Myanmar" 54339766
   "South Korea" 51780579
   "Colombia" 49395678
   "Kenya" 47564296
   "Spain" 47100396
   "Argentina" 44938712
   "Algeria" 43000000
   "Sudan" 42343075
   "Ukraine" 41902416
   "Uganda" 40299300
   "Iraq" 39127900
   "Poland" 38386000
   "Canada" 37956869
   "Morocco" 35838381
   "Saudi Arabia" 34218169
   "Uzbekistan" 34068416
   "Malaysia" 32718760
   "Afghanistan" 32225560
   "Venezuela" 32219521
   "Peru" 32131400
   "Angola" 31127674
   "Ghana" 30280811
   "Mozambique" 30066648
   "Yemen" 29825968
   "Nepal" 29609623
   "Cameroon" 26545864
   "Ivory Coast" 25823071
   "Madagascar" 25680342
   "Australia" 25645795
   "North Korea" 25450000
   "Taiwan" 23604265
   "Niger" 22314743
   "Sri Lanka" 21803000
   "Burkina Faso" 20870060
   "Mali" 19973000
   "Romania" 19405156
   "Chile" 19107216
   "Kazakhstan" 18662768
   "Malawi" 17563749
   "Syria" 17500657
   "Netherlands" 17444381
   "Ecuador" 17443880
   "Zambia" 17381168
   "Guatemala" 16604026
   "Senegal" 16209125
   "Somalia" 15893219
   "Chad" 15692969
   "Cambodia" 15288489
   "Zimbabwe" 15159624
   "South Sudan" 12778250
   "Rwanda" 12374397
   "Guinea" 12218357
   "Benin" 11733059
   "Tunisia" 11722038
   "Haiti" 11577779
   "Belgium" 11524454
   "Bolivia" 11469896
   "Cuba" 11209628
   "Burundi" 10953317
   "Greece" 10724599
   "Czechia" 10681161
   "Jordan" 10635640
   "Dominican Republic" 10358320
   "Sweden" 10333456
   "Portugal" 10276617
   "Azerbaijan" 10067108
   "United Arab Emirates" 9890400
   "Hungary" 9772756
   "Belarus" 9413446
   "Israel" 9171450
   "Honduras" 9158345
   "Tajikistan" 9127000
   "Papua New Guinea" 8935000
   "Austria" 8902600
   "Switzerland" 8586550
   "Sierra Leone" 7901454
   "Togo" 7538000
   "Hong Kong" 7500700
   "Paraguay" 7152703
   "Laos" 7123205
   "Bulgaria" 7000039
   "Serbia" 6963764
   "Libya" 6871287
   "Lebanon" 6825442
   "Kyrgyzstan" 6523500
   "El Salvador" 6486201
   "Nicaragua" 6460411
   "Turkmenistan" 6031187
   "Denmark" 5822763
   "Singapore" 5703600
   "Finland" 5527573
   "Central African Republic" 5496011
   "Slovakia" 5456362
   "Norway" 5367580
   "Costa Rica" 5058007
   "occupied Palestinian territory" 4976684
   "New Zealand" 4970195
   "Ireland" 4921500
   "Oman" 4664790
   "Liberia" 4475353
   "Kuwait" 4420110
   "Panama" 4218808
   "Mauritania" 4077347
   "Croatia" 4076246
   "Georgia" 3723464
   "Uruguay" 3518552
   "Eritrea" 3497117
   "Mongolia" 3307476
   "Bosnia and Herzegovina" 3301000
   "Puerto Rico" 3193694
   "Armenia" 2957500
   "Albania" 2862427
   "Lithuania" 2793350
   "Qatar" 2747282
   "Jamaica" 2726667
   "Moldova" 2681735
   "Namibia" 2458936
   "Gambia" 2347706
   "Botswana" 2338851
   "Gabon" 2172579
   "Slovenia" 2094060
   "North Macedonia" 2077132
   "Lesotho" 2007201
   "Latvia" 1906800
   "Kosovo" 1795666
   "Guinea-Bissau" 1604528
   "Bahrain" 1543300
   "East Timor" 1387149
   "Trinidad and Tobago" 1363985
   "Equatorial Guinea" 1358276
   "Estonia" 1328360
   "Mauritius" 1265985
   "Eswatini" 1093238
   "Djibouti" 1078373
   "Fiji" 884887
   "Cyprus" 875900
   "Comoros" 873724
   "Guyana" 782766
   "Bhutan" 741672
   "Solomon Islands" 680806
   "Macau" 679600
   "Montenegro" 622359
   "Luxembourg" 613894
   "Western Sahara" 582463
   "Suriname" 581372
   "Cape Verde" 550483
   "Malta" 493559
   "Transnistria" 469000
   "Brunei" 442400
   "Belize" 408487
   "Bahamas" 385340
   "Maldives" 374775
   "Iceland" 364260
   "Northern Cyprus" 351965
   "Vanuatu" 304500
   "Barbados" 287025
   "New Caledonia" 282200
   "French Polynesia" 275918
   "Abkhazia" 244832
   "São Tomé and Príncipe" 201784
   "Samoa" 200874
   "Saint Lucia" 178696
   "Guam" 172400
   "Curaçao" 158665
   "lag of Artsakh.svg Artsakh" 148000
   "Kiribati" 120100
   "Aruba" 112309
   "Grenada" 112003
   "Saint Vincent and the Grenadines" 110608
   "Jersey" 106800
   "U.S. Virgin Islands" 104578
   "F.S. Micronesia" 104468
   "Tonga" 100651
   "Seychelles" 97625
   "Antigua and Barbuda" 96453
   "Isle of Man" 83314
   "Andorra" 77543
   "Dominica" 71808
   "Cayman Islands" 65813
   "Bermuda" 64027
   "Guernsey" 62792
   "American Samoa" 56700
   "Greenland" 56081
   "Northern Mariana Islands" 56200
   "Marshall Islands" 55500
   "South Ossetia" 53532
   "Saint Kitts and Nevis" 52823
   "Faroe Islands" 52124
   "Turks and Caicos Islands" 41369
   "Sint Maarten" 40614
   "Liechtenstein" 38557
   "Monaco" 38300
   "Saint Martin" 35746
   "Gibraltar" 33701
   "San Marino" 33574
   "British Virgin Islands" 30030
   "Åland Islands" 29885
   "Palau" 17900
   "Cook Islands" 15200
   "Anguilla" 14869
   "Wallis and Futuna" 11700
   "Nauru" 11000
   "Tuvalu" 10200
   "Saint Barthélemy" 9793
   "Saint Pierre and Miquelon" 6008
   "Saint Helena Ascension and Tristan da Cunha" 5633
   "Montserrat" 4989
   "Falkland Islands" 3198
   "Christmas Island" 1928
   "Norfolk Island" 1756
   "Niue" 1520
   "Tokelau" 1400
   "Vatican City" 799
   "Cocos (Keeling) Islands" 538
   "Pitcairn Islands" 50
   "Martinique" 376480
   "French Guiana" 	290691
   "Mayotte" 279471

   "Congo-Brazzaville"  5244359 ;; cg
   "Congo-Kinshasa"    89561404 ;; cd

   "Cote d'Ivoire" 23740424
   "Reunion" 859959
   "Guadeloupe" 395700
   "The Bahamas" 385637
   })
