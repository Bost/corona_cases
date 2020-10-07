(ns corona.countries
  (:require
   [clojure.set :as cset]
   [corona.country-codes :refer :all]
   [utils.core :refer [in?] :exclude [id]]
   [clojure.string :as s]
   [taoensso.timbre :as timbre :refer :all]
   )
  (:import com.neovisionaries.i18n.CountryCode
           com.neovisionaries.i18n.CountryCode$Assignment))

;; nothing should be default affected!!!
(def ^:const default-affected-country-codes
  (->> [country-code-worldwide
        country-code-others]
       (reduce into)
       (mapv (fn [[k _]] k))))

(defn country-code--country-nv-i18n
  "The base data from the internationalization, ISO 3166-1 country codes etc.
  library: com.neovisionaries/nv-i18n \"1.27\""
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

(def ^:const country-code--country
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

(def ^:const country--country-code
  "Mapping: country-names -> 2-letter country codes.
  https://en.wikipedia.org/wiki/ISO_3166-1#Officially_assigned_code_elements"
  (cset/map-invert country-code--country))

(defn country-name
  "Country name from 2-letter country code: \"DE\" -> \"Germany\" "
  [cc]
  (get country-code--country cc))

(def ^:const country-alias--country-code
  "Mapping of alternative names, spelling, typos to the names of countries used by
  the ISO 3166-1 norm. \"Others\" has no mapping."
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
   "Puerto Rico (US)"                 pr
   "Guam (US)"                        gu
   "American Samoa (US)"              as
   "Northern Mariana Islands (US)"    mp

   "Macau"                            mo ; "Macao"
   "Macao SAR"                        mo ; "Macao"
   "Macau (China)"                    mo

   "Vietnam"                          vn ; "Viet Nam"

   "UK"                               gb ; "United Kingdom"
   ;; Conjoin on United Kingdom
   "North Ireland"                    gb

   "Isle of Man (UK)"                 im
   "Turks and Caicos Islands (UK)"    tc
   "Gibraltar (UK)"                   gi
   "Cayman Islands (UK)"              ky
   "Bermuda (UK)"                     bm
   "Anguilla (UK)"                    ai
   "Montserrat (UK)"                  ms

   "Russia"                           ru ; "Russian Federation"
   ;; Conjoin on Russian Federation
   "South Ossetia"                    ru

   "Iran"                             ir ; "Iran, Islamic Republic of"
   "Iran (Islamic Republic of)"       ir ; "Iran, Islamic Republic of"

   "Saint Barthelemy"                 bl ; "Saint Barthélemy"
   "Saint Barthélemy (France)"        bl

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
   "U.S. Virgin Islands (US)"         vi ; "Virgin Islands, U.S."

   "British Virgin Islands"           vg ; "Virgin Islands, British"
   "British Virgin Islands (UK)"      vg

   ;; Conjoin on France
   "Saint Martin (France)"            fr ; "France"

   "New Caledonia (France)"           nc
   "French Polynesia (France)"        pf

   "Saint Kitts & Nevis"              kn ; "Saint Kitts and Nevis"
   "St. Kitts & Nevis"                kn ; "Saint Kitts and Nevis"

   "Faeroe Islands"                   fo ; "Faroe Islands"

   "Sint Maarten"                     sx ; "Sint Maarten (Dutch part)"
   "Sint Maarten (Netherlands)"       sx

   "Aruba (Netherlands)"              aw

   "Turks and Caicos"                 tc ; "Turks and Caicos Islands"

   "Wallis & Futuna"                  wf ; "Wallis and Futuna"
   "Wallis and Futuna (France)"       wf

   "Saint Helena"                     sh ; "Saint Helena, Ascension and Tristan da Cunha"
   "Saint Helena, Ascensionand Tristan da Cunha (UK)" sh

   "Saint Pierre & Miquelon"            pm ; "Saint Pierre and Miquelon"
   "Saint Pierre and Miquelon (France)" pm

   "Falkland Islands"                 fk ; "Falkland Islands (Malvinas)"
   "Falkland Islands (UK)"            fk

   "Republic of Ireland"              ie ; "Ireland"
   " Azerbaijan"                      az ; "Azerbaijan"

   "East Timor"                       tl ; "Timor-Leste"

   ;; Guernsey and Jersey form Channel Islands. They should have separate statistics.
   ;; https://en.wikipedia.org/wiki/Channel_Islands
   ;; "Guernsey and Jersey"
   ;; "Channel Islands"

   "Jersey (UK)"                      je
   "Guernsey (UK)"                    gg

   "Caribbean Netherlands"            bq ; "Bonaire, Sint Eustatius and Saba"

   "Emirates"                         ae ; "United Arab Emirates"

   "Bosnia–Herzegovina"               ba ; "Bosnia and Herzegovina"
   "Bosnia"                           ba ; "Bosnia and Herzegovina"

   "Dominican Rep"                    do ; "Dominican Republic"

   "Macedonia"                        mk ; "North Macedonia, Republic of"
   "North Macedonia"                  mk ; "North Macedonia, Republic of"

   "Ivory Coast"                      ci ; "Côte d'Ivoire"
   "Cote d'Ivoire"                    ci ; "Côte d'Ivoire"

   "Saint Martin"                     mf ; "Saint Martin (French part)"
   "St. Martin"                       mf ; "Saint Martin (French part)"

   "Hong Kong SAR"                    hk ; "Hong Kong"
   "Hong Kong (China)"                hk

   ;; "Transnistria" recognized as part of Moldova `md` but may have a different
   ;; medical system

   ;; "Northern Cyprus" considered to be a part of Cyprus `cy` but may have a
   ;; different medical system

   ;; "Abkhazia" recognized as part of Georgia `ge` but may have a different
   ;; medical system

   "Republic of Artsakh"              am ; "Armenia"
   "Cook Islands (NZ)"                ck
   "Christmas Island (Australia)"     cx
   "Norfolk Island (Australia)"       nf
   "Niue (NZ)"                        nu
   "Tokelau (NZ)"                     tk
   "Greenland (Denmark)"              gl
   "Faroe Islands (Denmark)"          fo
   "Åland Islands (Finland)"          ax
   "Reunion"                          re ; "Réunion"

   "Curacao"                          cw ; "Curaçao"
   "Curaçao (Netherlands)"            cw

   "The Bahamas"                      bs ; "Bahamas"
   "Kosovo"                           xk ; "Kosovo, Republic of"
   "Trinidad & Tobago"                tt ; "Trinidad and Tobago"
   "Antigua & Barbuda"                ag ; "Antigua and Barbuda"
   "Central African Rep"              cf ; "Central African Republic"

   "Burma"                            mm ; Myanmar

   "Pitcairn Islands (UK)"               pn
   "Cocos (Keeling) Islands (Australia)" cc

   ;; "Others" has no mapping
   ;; "Cruise Ship" is mapped to the default val
   })

(def ^:const country-alias--country-code-inverted
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

    vn "Vietnam"
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
      (do (error (format
                  "\"%s\" has no country code. Using \"%s\""
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
        cd cg vn]
       country-code)
    (country-alias country-code)
    (country-name country-code)))

(def ^:const population-table
  "From
  https://en.wikipedia.org/wiki/List_of_countries_and_dependencies_by_population
  The v2 api service https://coronavirus-tracker-api.herokuapp.com/v2 doesn't
  contain precise data. Values may significantly differ from the
  `corona.tables/population`

  Countries not listed:
  zz - Worldwide
  tf - Fr Southern Terri
  mf - St Martin, French
  sj - Svalbard J. Mayen
  qq - Rest
  um - US Min Outlying I
  bq - Bona St Eust Saba
  bv - Bouvet Island
  gs - S Georgia Sandwic
  aq - Antarctica
  hm - Heard McDonald Is
  "
  [
   ["China"                                            1404614720]
   ["India"                                            1367703110]
   ["United States"                                    330375132]
   ["Indonesia"                                        269603400]
   ["Pakistan"                                         220892331]
   ["Brazil"                                           212122227]
   ["Nigeria"                                          206139587]
   ["Bangladesh"                                       169373348]
   ["Russia"                                           146748590]
   ["Mexico"                                           127792286]
   ["Japan"                                            125810000]
   ["Philippines"                                      109218624]
   ["Egypt"                                            100967392]
   ["Ethiopia"                                         100829000]
   ["Vietnam"                                          96208984]
   ["DR Congo"                                         91994000]
   ["Iran"                                             83816683]
   ["Germany"                                          83157201]
   ["Turkey"                                           83154997]
   ["France"                                           67132000]
   ["United Kingdom"                                   66796807]
   ["Thailand"                                         66557580]
   ["Italy"                                            60095164]
   ["South Africa"                                     59622350]
   ["Tanzania"                                         57637628]
   ["Myanmar"                                          54817919]
   ["South Korea"                                      51839953]
   ["Colombia"                                         50372424]
   ["Kenya"                                            47564296]
   ["Spain"                                            47329981]
   ["Argentina"                                        45376763]
   ["Algeria"                                          43900000]
   ["Sudan"                                            42862170]
   ["Ukraine"                                          41743935]
   ["Uganda"                                           41583600]
   ["Iraq"                                             40150200]
   ["Poland"                                           38353000]
   ["Canada"                                           38187300]
   ["Morocco"                                          36028348]
   ["Uzbekistan"                                       34434659]
   ["Saudi Arabia"                                     34218169]
   ["Afghanistan"                                      32890171]
   ["Malaysia"                                         32689860]
   ["Peru"                                             32625948]
   ["Angola"                                           31127674]
   ["Ghana"                                            30955202]
   ["Mozambique"                                       30066648]
   ["Nepal"                                            29996478]
   ["Yemen"                                            29825968]
   ["Venezuela"                                        28435943]
   ["Ivory Coast"                                      26453542]
   ["Madagascar"                                       26251309]
   ["Australia"                                        25670803]
   ["North Korea"                                      25550000]
   ["Cameroon"                                         24348251]
   ["Taiwan"                                           23574334]
   ["Niger"                                            23196002]
   ["Sri Lanka"                                        21803000]
   ["Burkina Faso"                                     21510181]
   ["Mali"                                             20250833]
   ["Chile"                                            19458310]
   ["Romania"                                          19317984]
   ["Malawi"                                           19129952]
   ["Kazakhstan"                                       18784120]
   ["Zambia"                                           17885422]
   ["Ecuador"                                          17577052]
   ["Netherlands"                                      17513331]
   ["Syria"                                            17500657]
   ["Guatemala"                                        16858333]
   ["Senegal"                                          16705608]
   ["Chad"                                             16244513]
   ["Somalia"                                          15893219]
   ["Zimbabwe"                                         15473818]
   ["Cambodia"                                         15288489]
   ["South Sudan"                                      13249924]
   ["Rwanda"                                           12663116]
   ["Guinea"                                           12559623]
   ["Burundi"                                          12309600]
   ["Benin"                                            12114193]
   ["Haiti"                                            11743017]
   ["Tunisia"                                          11708370]
   ["Bolivia"                                          11633371]
   ["Belgium"                                          11535652]
   ["Cuba"                                             11193470]
   ["Jordan"                                           10778268]
   ["Greece"                                           10724599]
   ["Czech Republic"                                   10699142]
   ["Dominican Republic"                               10448499]
   ["Sweden"                                           10358538]
   ["Portugal"                                         10295909]
   ["Azerbaijan"                                       10095900]
   ["United Arab Emirates"                             9890400]
   ["Hungary"                                          9769526]
   ["Belarus"                                          9408400]
   ["Tajikistan"                                       9313800]
   ["Honduras"                                         9304380]
   ["Israel"                                           9258150]
   ["Papua New Guinea"                                 8935000]
   ["Austria"                                          8915382]
   ["Switzerland"                                      8632703]
   ["Sierra Leone"                                     8100318]
   ["Togo"                                             7706000]
   ["Hong Kong (China)"                                7509200]
   ["Paraguay"                                         7252672]
   ["Laos"                                             7231210]
   ["Bulgaria"                                         6951482]
   ["Serbia"                                           6926705]
   ["Libya"                                            6871287]
   ["Lebanon"                                          6825442]
   ["El Salvador"                                      6765753]
   ["Kyrgyzstan"                                       6586600]
   ["Nicaragua"                                        6527691]
   ["Turkmenistan"                                     6031187]
   ["Denmark"                                          5825337]
   ["Singapore"                                        5685807]
   ["Central African Republic"                         5633412]
   ["Congo"                                            5518092]
   ["Finland"                                          5502259]
   ["Slovakia"                                         5460136]
   ["Norway"                                           5374807]
   ["Costa Rica"                                       5111238]
   ["Palestine"                                        5101152]
   ["New Zealand"                                      5091527]
   ["Ireland"                                          4977400]
   ["Liberia"                                          4568298]
   ["Oman"                                             4480333]
   ["Kuwait"                                           4464521]
   ["Panama"                                           4278500]
   ["Mauritania"                                       4173077]
   ["Croatia"                                          4058165]
   ["Georgia"                                          3716858]
   ["Eritrea"                                          3546000]
   ["Uruguay"                                          3530912]
   ["Mongolia"                                         3339765]
   ["Bosnia and Herzegovina"                           3332593]
   ["Puerto Rico (US)"                                 3193694]
   ["Armenia"                                          2963000]
   ["Albania"                                          2845955]
   ["Lithuania"                                        2795459]
   ["Qatar"                                            2735707]
   ["Jamaica"                                          2726667]
   ["Moldova"                                          2640438]
   ["Namibia"                                          2504498]
   ["Botswana"                                         2374698]
   ["Gambia"                                           2335504]
   ["Gabon"                                            2176766]
   ["Slovenia"                                         2097195]
   ["North Macedonia"                                  2076255]
   ["Lesotho"                                          2007201]
   ["Latvia"                                           1899200]
   ["Kosovo"                                           1782115]
   ["Guinea-Bissau"                                    1624945]
   ["Bahrain"                                          1592000]
   ["Equatorial Guinea"                                1454789]
   ["Trinidad and Tobago"                              1363985]
   ["Estonia"                                          1328976]
   ["East Timor"                                       1299412]
   ["Mauritius"                                        1266000]
   ["Eswatini"                                         1093238]
   ["Djibouti"                                         962452]
   ["Fiji"                                             889327]
   ["Cyprus"                                           875900]
   ["Comoros"                                          758316]
   ["Bhutan"                                           748931]
   ["Guyana"                                           744962]
   ["Solomon Islands"                                  694619]
   ["Macau (China)"                                    685400]
   ["Luxembourg"                                       626108]
   ["Montenegro"                                       621873]
   ["Western Sahara"                                   597000]
   ["Suriname"                                         590100]
   ["Cape Verde"                                       556857]
   ["Malta"                                            514564]
   ["Transnistria"                                     469000]
   ["Brunei"                                           459500]
   ["Belize"                                           419199]
   ["Bahamas"                                          389410]
   ["Maldives"                                         383135]
   ["Iceland"                                          366700]
   ["Northern Cyprus"                                  351965]
   ["Vanuatu"                                          304500]
   ["Barbados"                                         287025]
   ["French Polynesia (France)"                        278400]
   ["New Caledonia (France)"                           271407]
   ["Abkhazia"                                         245246]
   ["São Tomé and Príncipe"                            210240]
   ["Samoa"                                            202506]
   ["Saint Lucia"                                      178696]
   ["Guam (US)"                                        172400]
   ["Curaçao (Netherlands)"                            156223]
   ["Republic of Artsakh"                              148800]
   ["Kiribati"                                         120100]
   ["Aruba (Netherlands)"                              112269]
   ["Grenada"                                          112003]
   ["Saint Vincent and the Grenadines"                 110696]
   ["Jersey (UK)"                                      107800]
   ["F.S. Micronesia"                                  104650]
   ["U.S. Virgin Islands (US)"                         104578]
   ["Tonga"                                            100651]
   ["Seychelles"                                       98462]
   ["Antigua and Barbuda"                              97895]
   ["Isle of Man (UK)"                                 83314]
   ["Andorra"                                          77543]
   ["Dominica"                                         71808]
   ["Cayman Islands (UK)"                              69914]
   ["Bermuda (UK)"                                     64054]
   ["Guernsey (UK)"                                    63276]
   ["American Samoa (US)"                              56700]
   ["Greenland (Denmark)"                              56367]
   ["Northern Mariana Islands (US)"                    56200]
   ["Marshall Islands"                                 55500]
   ["South Ossetia"                                    53532]
   ["Saint Kitts and Nevis"                            52823]
   ["Faroe Islands (Denmark)"                          52816]
   ["Turks and Caicos Islands (UK)"                    42953]
   ["Sint Maarten (Netherlands)"                       40614]
   ["Liechtenstein"                                    38749]
   ["Monaco"                                           38100]
   ["Saint Martin (France)"                            35334]
   ["Gibraltar (UK)"                                   33691]
   ["San Marino"                                       33630]
   ["Åland Islands (Finland)"                          30074]
   ["British Virgin Islands (UK)"                      30030]
   ["Palau"                                            17900]
   ["Cook Islands (NZ)"                                15200]
   ["Anguilla (UK)"                                    14869]
   ["Wallis and Futuna (France)"                       11558]
   ["Nauru"                                            11000]
   ["Tuvalu"                                           10200]
   ["Saint Barthélemy (France)"                        9961]
   ["Saint Pierre and Miquelon (France)"               5997]
   ["Saint Helena, Ascensionand Tristan da Cunha (UK)" 5633]
   ["Montserrat (UK)"                                  4989]
   ["Falkland Islands (UK)"                            3198]
   ["Christmas Island (Australia)"                     1955]
   ["Norfolk Island (Australia)"                       1735]
   ["Niue (NZ)"                                        1520]
   ["Tokelau (NZ)"                                     1400]
   ["Vatican City"                                     825]
   ["Cocos (Keeling) Islands (Australia)"              555]
   ["Pitcairn Islands (UK)"                            50]
   ])

(def population
  "
  Toggle between country-names and codes using:
  => (clojure.set/rename-keys population
      (->> (keys population)
          (map (fn [k] {k (#_country-name country-code k)}))
          (reduce into)))

  Compare `corona.tables/population` with `corona.countries/population`
  => (def cdiff (->> (keys corona.countries/population)
                     (map (fn [k] {k (if-let [n (get corona.tables/population k)]
                                      (- (get corona.countries/population k)
                                         n)
                                      nil)}))
                     (reduce into)))
  "
  #_corona.tables/population
  (->> population-table
       (map (fn [[c n]] {(country-code c) n}))
       ;; because e.g. population of Russia is mainland + South Ossetia
       (apply merge-with +)))
