(ns corona.countries
  (:require [clojure.set :as cset]
            [corona.country-codes :refer :all]
            [corona.core :as c :refer [in?]]
            [clojure.string :as s]
            [corona.defs :as d])
  (:import com.neovisionaries.i18n.CountryCode))

(def country-code-2-to-3-hm
  "Mapping of country codes 2 -> 3 letters"
  (conj
   {
    cr "CRI"
    tg "TGO"
    tj "TJK"
    za "ZAF"
    im "IMN"
    pe "PER"
    lc "LCA"
    ch "CHE"
    ru "RUS"
    mp "MNP"
    ck "COK"
    si "SVN"
    au "AUS"
    kr "KOR"
    it "ITA"
    fi "FIN"
    gf "GUF"
    sc "SYC"
    sx "SXM"
    zz "ZZZ"
    tt "TTO"
    tk "TKL"
    my "MYS"
    sy "SYR"
    mn "MNG"
    tf "ATF"
    kp "PRK"
    am "ARM"
    dz "DZA"
    uy "URY"
    td "TCD"
    dj "DJI"
    bi "BDI"
    mk "MKD"
    mu "MUS"
    li "LIE"
    nu "NIU"
    gr "GRC"
    gy "GUY"
    cg "COG"
    nf "NFK"
    ml "MLI"
    ax "ALA"
    gm "GMB"
    sa "SAU"
    cx "CXR"
    bh "BHR"
    ne "NER"
    bn "BRN"
    xk "XKX"
    mf "MAF"
    cd "COD"
    dk "DNK"
    bj "BEN"
    me "MNE"
    sj "SJM"
    bo "BOL"
    jo "JOR"
    cv "CPV"
    ve "VEN"
    ci "CIV"
    uz "UZB"
    tn "TUN"
    is "ISL"
    eh "ESH"
    tm "TKM"
    ga "GAB"
    ls "LSO"
    tz "TZA"
    at "AUT"
    lt "LTU"
    np "NPL"
    bg "BGR"
    il "ISR"
    gu "GUM"
    pk "PAK"
    pt "PRT"
    hr "HRV"
    vu "VUT"
    pf "PYF"
    bm "BMU"
    mr "MRT"
    ge "GEO"
    hu "HUN"
    tw "TWN"
    mm "MMR"
    vg "VGB"
    ye "YEM"
    sr "SUR"
    pn "PCN"
    va "VAT"
    pr "PRI"
    kw "KWT"
    se "SWE"
    gb "GBR"
    qq "QQQ"
    um "UMI"
    vn "VNM"
    cf "CAF"
    pa "PAN"
    vc "VCT"
    jp "JPN"
    ir "IRN"
    af "AFG"
    ly "LBY"
    mz "MOZ"
    ro "ROU"
    qa "QAT"
    cm "CMR"
    gg "GGY"
    by "BLR"
    sd "SDN"
    bq "BES"
    mo "MAC"
    ky "CYM"
    ar "ARG"
    br "BRA"
    zw "ZWE"
    nr "NRU"
    nz "NZL"
    aw "ABW"
    fj "FJI"
    id "IDN"
    sv "SLV"
    cn "CHN"
    fm "FSM"
    ht "HTI"
    cc "CCK"
    rw "RWA"
    ba "BIH"
    tl "TLS"
    jm "JAM"
    km "COM"
    ke "KEN"
    ws "WSM"
    to "TON"
    py "PRY"
    sh "SHN"
    cy "CYP"
    gh "GHA"
    ma "MAR"
    sg "SGP"
    lk "LKA"
    ph "PHL"
    sm "SMR"
    wf "WLF"
    tr "TUR"
    ps "PSE"
    bz "BLZ"
    cu "CUB"
    tv "TUV"
    ad "AND"
    sb "SLB"
    dm "DMA"
    lr "LBR"
    om "OMN"
    so "SOM"
    do "DOM"
    al "ALB"
    bl "BLM"
    fr "FRA"
    gw "GNB"
    ms "MSR"
    bb "BRB"
    ca "CAN"
    mg "MDG"
    kh "KHM"
    la "LAO"
    gp "GLP"
    bv "BVT"
    hn "HND"
    th "THA"
    de "DEU"
    lb "LBN"
    kz "KAZ"
    as "ASM"
    ec "ECU"
    no "NOR"
    ao "AGO"
    fk "FLK"
    et "ETH"
    gs "SGS"
    md "MDA"
    ag "ATG"
    be "BEL"
    mv "MDV"
    sz "SWZ"
    cz "CZE"
    cl "CHL"
    bt "BTN"
    nl "NLD"
    eg "EGY"
    mq "MTQ"
    sn "SEN"
    fo "FRO"
    ee "EST"
    aq "ATA"
    st "STP"
    kn "KNA"
    bw "BWA"
    mh "MHL"
    ni "NIC"
    pg "PNG"
    vi "VIR"
    iq "IRQ"
    kg "KGZ"
    us "USA"
    zm "ZMB"
    mc "MCO"
    gi "GIB"
    nc "NCL"
    gt "GTM"
    bf "BFA"
    yt "MYT"
    lu "LUX"
    ua "UKR"
    ie "IRL"
    lv "LVA"
    gd "GRD"
    mw "MWI"
    bs "BHS"
    az "AZE"
    sk "SVK"
    gq "GNQ"
    tc "TCA"
    re "REU"
    in "IND"
    es "ESP"
    gl "GRL"
    ki "KIR"
    hk "HKG"
    co "COL"
    ss "SSD"
    rs "SRB"
    io "IOT"
    ng "NGA"
    ug "UGA"
    cw "CUW"
    sl "SLE"
    er "ERI"
    je "JEY"
    ae "ARE"
    hm "HMD"
    pm "SPM"
    bd "BGD"
    mt "MLT"
    ai "AIA"
    gn "GIN"
    pw "PLW"
    na "NAM"
    mx "MEX"
    pl "POL"}
   d/default-country-codes
   d/worldwide-country-codes))

(defn country-code-3-letter
  "3-letter country code from 2-letter country code: \"DE\" -> \"DEU\" "
  [cc]
  (get country-code-2-to-3-hm cc))

(defn all-country-codes [] (keys country-code-2-to-3-hm))


;; nothing should be default affected!!!
(def default-affected [d/country-code-worldwide
                       d/country-code-others])

(def default-affected-country-codes
  (->> default-affected
       (reduce into)
       (mapv (fn [[k _]] k))))

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

(defn country-name
  "Country name from 2-letter country code: \"DE\" -> \"Germany\" "
  [cc]
  (get country-code--country cc))


(def aliases-hm
  "Mapping of alternative names, spelling, typos to the names of countries used by
  the ISO 3166-1 norm.

  Conjoin \"North Ireland\" on \"United Kingdom\".

  https://en.wikipedia.org/wiki/Channel_Islands
  \"Guernsey\" and \"Jersey\" form \"Channel Islands\". Conjoin \"Guernsey\" on \"Jersey\".
  \"Jersey\" has higher population.

  \"Others\" has no mapping.
  "
  {
   "World"                            (country-name zz) ; "Worldwide"
   "Czechia"                          (country-name cz) ; "Czech Republic"
   "Mainland China"                   (country-name cn) ; "China"
   "South Korea"                      (country-name kr) ; "Korea, Republic of"
   "Korea, South"                     (country-name kr) ; "Korea, Republic of"
   "Republic of Korea"                (country-name kr) ; "Korea, Republic of"

   "Taiwan"                           (country-name tw) ; "Taiwan, Province of China"
   "Taiwan*"                          (country-name tw) ; "Taiwan, Province of China"
   "Taipei and environs"              (country-name tw) ; "Taiwan, Province of China"

   "US"                               (country-name us) ; "United States"

   "Macau"                            (country-name mo)
   "Macao SAR"                        (country-name mo) ; "Macao"

   "Vietnam"                          (country-name gb) ; "Viet Nam"
   "UK"                               (country-name gb) ; "United Kingdom"
   "Russia"                           (country-name ru) ; "Russian Federation"
   "Iran"                             (country-name ir) ; "Iran, Islamic Republic of"
   "Iran (Islamic Republic of)"       (country-name ir) ; "Iran, Islamic Republic of"
   "Saint Barthelemy"                 (country-name bl) ; "Saint Barthélemy"

   "Palestine"                        (country-name ps) ; "Palestine, State of"
   "State of Palestine"               (country-name ps) ; "Palestine, State of"
   "occupied Palestinian territory"   (country-name ps) ; "Palestine, State of"

   "Vatican City"                     (country-name va) ; "Holy See (Vatican City State)"
   "Holy See"                         (country-name va) ; "Holy See (Vatican City State)"

   "DR Congo"                         (country-name cd) ; "Congo, the Democratic Republic of the"
   "Congo (Kinshasa)"                 (country-name cd) ; "Congo, the Democratic Republic of the"
   "Democratic Republic of the Congo" (country-name cd) ; "Congo, the Democratic Republic of the"

   "Tanzania"                         (country-name tz) ; "Tanzania, United Republic of"
   "Venezuela"                        (country-name ve) ; "Venezuela, Bolivarian Republic of"
   "North Korea"                      (country-name kp) ; "Korea, Democratic People's Republic of"
   "Syria"                            (country-name sy) ; "Syrian Arab Republic"
   "Bolivia"                          (country-name bo) ; "Bolivia, Plurinational State of"
   "Laos"                             (country-name la) ; "Lao People's Democratic Republic"
   "Moldova"                          (country-name md) ; "Moldova, Republic of"
   "Republic of Moldova"              (country-name md) ; "Moldova, Republic of"
   "Swaziland"                        (country-name sz) ; "Eswatini"
   "Cabo Verde"                       (country-name cv) ; "Cape Verde"
   "Brunei"                           (country-name bn) ; "Brunei Darussalam"
   "Sao Tome & Principe"              (country-name st) ; "Sao Tome and Principe"
   "São Tomé and Príncipe"            (country-name st) ; "Sao Tome and Principe"
   "Micronesia"                       (country-name fm) ; "Micronesia, Federated States of"
   "F.S. Micronesia"                  (country-name fm) ; "Micronesia, Federated States of"
   "Federated States of Micronesia"   (country-name fm) ; "Micronesia, Federated States of"

   "St. Vincent & Grenadines"         (country-name vc) ; "Saint Vincent and the Grenadines"
   "Saint Vincent"                    (country-name vc) ; "Saint Vincent and the Grenadines"
   "U.S. Virgin Islands"              (country-name vi) ; "Virgin Islands, U.S."
   "British Virgin Islands"           (country-name vg) ; "Virgin Islands, British"
   "Saint Kitts & Nevis"              (country-name kn) ; "Saint Kitts and Nevis"
   "St. Kitts & Nevis"                (country-name kn) ; "Saint Kitts and Nevis"
   "Faeroe Islands"                   (country-name fo) ; "Faroe Islands"
   "Sint Maarten"                     (country-name sx) ; "Sint Maarten (Dutch part)"
   "Turks and Caicos"                 (country-name tc) ; "Turks and Caicos Islands"
   "Wallis & Futuna"                  (country-name wf) ; "Wallis and Futuna"
   "Saint Helena"                     (country-name sh) ; "Saint Helena, Ascension and Tristan da Cunha"
   "Saint Pierre & Miquelon"          (country-name pm) ; "Saint Pierre and Miquelon"
   "Falkland Islands"                 (country-name fk) ; "Falkland Islands (Malvinas)"
   "Republic of Ireland"              (country-name ie) ; "Ireland"
   " Azerbaijan"                      (country-name az) ; "Azerbaijan"

   ;; Conjoin North Ireland on United Kingdom
   "North Ireland"                    (country-name gb) ; "United Kingdom"
   "East Timor"                       (country-name tl) ; "Timor-Leste"

   ;; Guernsey and Jersey form Channel Islands. ConjinGuernsey on Jersey.
   ;; Jersey has higher population.
   ;; https://en.wikipedia.org/wiki/Channel_Islands
   "Guernsey and Jersey"              (country-name je) ; "Jersey"
   "Channel Islands"                  (country-name je) ; "Jersey"
   "Caribbean Netherlands"            (country-name je) ; "Bonaire, Sint Eustatius and Saba"
   "Emirates"                         (country-name ae) ; "United Arab Emirates"
   ;; "Bosnia–Herzegovina"            (country-name ba) ; "Bosnia and Herzegovina"
   "Bosnia"                           (country-name ba) ; "Bosnia and Herzegovina"
   "Dominican Rep"                    (country-name do) ; "Dominican Republic"
   "Macedonia"                        (country-name mk) ; "North Macedonia, Republic of"
   "North Macedonia"                  (country-name mk) ; "North Macedonia, Republic of"
   "Ivory Coast"                      (country-name ci) ; "Côte d'Ivoire"
   "Cote d'Ivoire"                    (country-name ci) ; "Côte d'Ivoire"
   "Saint Martin"                     (country-name mf) ; "Saint Martin (French part)"
   "St. Martin"                       (country-name mf) ; "Saint Martin (French part)"
   "Hong Kong SAR"                    (country-name hk) ; "Hong Kong"
   "Reunion"                          (country-name re) ; "Réunion"
   "Curacao"                          (country-name cw) ; "Curaçao"
   "Congo (Brazzaville)"              (country-name cg) ; "Congo"
   "Republic of the Congo"            (country-name cg) ; "Congo"
   "The Bahamas"                      (country-name bs) ; "Bahamas"
   "Kosovo"                           (country-name xk) ; "Kosovo, Republic of"
   "Trinidad & Tobago"                (country-name tt) ; "Trinidad and Tobago"
   "Antigua & Barbuda"                (country-name ag) ; "Antigua and Barbuda"
   "Central African Rep"              (country-name cf) ; "Central African Republic"

   "Burma"                            (country-name mm) ; Myanmar

   ;; "Others" has no mapping
   ;; "Cruise Ship" is mapped to the default val
   })

(defn- lower-case-keyword
  "ABC -> abc"
  [hm]
  (into {} (map (fn [[k v]] [(s/lower-case k) v]) hm)))

(defn country-code
  "Return two letter country code (Alpha-2) according to
  https://en.wikipedia.org/wiki/ISO_3166-1
  Defaults to `default-2-country-code`."
  [country-name]
  (let [country (s/lower-case country-name)
        lcases-countries (lower-case-keyword country--country-code)]
    (if-let [cc (get lcases-countries country)]
      cc
      (if-let [country-alias (get (lower-case-keyword aliases-hm) country)]
        (get country--country-code country-alias)
        (do (println (format
                      "No country code found for \"%s\". Using \"%s\""
                      country-name
                      d/default-2-country-code))
            d/default-2-country-code)))))

(def aliases-inverted
  "See also `country-name-aliased`"
  (conj
   ;; (clojure.set/map-invert aliases-hm)
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
    cd "DR Congo"
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
    st "St Tome Principe"
    pg "Papua N Guinea"
    gq "Equat Guinea"
    qq "Rest"
    }))

(defn country-alias
  "Get a country alias or the normal name if an alias doesn't exist"
  [cc]
  (let [up-cc (s/upper-case cc)
        country (get country-code--country up-cc)]
    (get aliases-inverted up-cc country)))

(defn country-name-aliased
  "Use an alias; typically a shorter name for some countries.
  See `aliases-inverted`"
  [cc]
  (if (in?
       [va tw do ir ru ps ae kr mk ba cd bo md bn ve vc kp tz xk la sy kn tt ag
        cf cz st pg gq qq] cc)
    (country-alias cc)
    (country-name cc)))
