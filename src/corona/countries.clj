(ns corona.countries
  (:require [corona.defs :as d]
            [clojure.set :as cset])
  (:import com.neovisionaries.i18n.CountryCode))

;; TODO proper country-code types (created by a macro?) (def de "DE") for every
;; country

(def cr "CR")
(def tg "TG")
(def tj "TJ")
(def za "ZA")
(def im "IM")
(def pe "PE")
(def lc "LC")
(def ch "CH")
(def ru "RU")
(def mp "MP")
(def ck "CK")
(def si "SI")
(def au "AU")
(def kr "KR")
(def it "IT")
(def fi "FI")
(def gf "GF")
(def sc "SC")
(def sx "SX")
(def zz "ZZ")
(def tt "TT")
(def tk "TK")
(def my "MY")
(def sy "SY")
(def mn "MN")
(def tf "TF")
(def kp "KP")
(def am "AM")
(def dz "DZ")
(def uy "UY")
(def td "TD")
(def dj "DJ")
(def bi "BI")
(def mk "MK")
(def mu "MU")
(def li "LI")
(def nu "NU")
(def gr "GR")
(def gy "GY")
(def cg "CG")
(def nf "NF")
(def ml "ML")
(def ax "AX")
(def gm "GM")
(def sa "SA")
(def cx "CX")
(def bh "BH")
(def ne "NE")
(def bn "BN")
(def xk "XK")
(def mf "MF")
(def cd "CD")
(def dk "DK")
(def bj "BJ")
(def me "ME")
(def sj "SJ")
(def bo "BO")
(def jo "JO")
(def cv "CV")
(def ve "VE")
(def ci "CI")
(def uz "UZ")
(def tn "TN")
(def is "IS")
(def eh "EH")
(def tm "TM")
(def ga "GA")
(def ls "LS")
(def tz "TZ")
(def at "AT")
(def lt "LT")
(def np "NP")
(def bg "BG")
(def il "IL")
(def gu "GU")
(def pk "PK")
(def pt "PT")
(def hr "HR")
(def vu "VU")
(def pf "PF")
(def bm "BM")
(def mr "MR")
(def ge "GE")
(def hu "HU")
(def tw "TW")
(def mm "MM")
(def vg "VG")
(def ye "YE")
(def sr "SR")
(def pn "PN")
(def va "VA")
(def pr "PR")
(def kw "KW")
(def se "SE")
(def gb "GB")
(def qq "QQ")
(def um "UM")
(def vn "VN")
(def cf "CF")
(def pa "PA")
(def vc "VC")
(def jp "JP")
(def ir "IR")
(def af "AF")
(def ly "LY")
(def mz "MZ")
(def ro "RO")
(def qa "QA")
(def cm "CM")
(def gg "GG")
(def by "BY")
(def sd "SD")
(def bq "BQ")
(def mo "MO")
(def ky "KY")
(def ar "AR")
(def br "BR")
(def zw "ZW")
(def nr "NR")
(def nz "NZ")
(def aw "AW")
(def fj "FJ")
(def id "ID")
(def sv "SV")
(def cn "CN")
(def fm "FM")
(def ht "HT")
(def cc "CC")
(def rw "RW")
(def ba "BA")
(def tl "TL")
(def jm "JM")
(def km "KM")
(def ke "KE")
(def ws "WS")
(def to "TO")
(def py "PY")
(def sh "SH")
(def cy "CY")
(def gh "GH")
(def ma "MA")
(def sg "SG")
(def lk "LK")
(def ph "PH")
(def sm "SM")
(def wf "WF")
(def tr "TR")
(def ps "PS")
(def bz "BZ")
(def cu "CU")
(def tv "TV")
(def ad "AD")
(def sb "SB")
(def dm "DM")
(def lr "LR")
(def om "OM")
(def so "SO")
(def do "DO")
(def al "AL")
(def bl "BL")
(def fr "FR")
(def gw "GW")
(def ms "MS")
(def bb "BB")
(def ca "CA")
(def mg "MG")
(def kh "KH")
(def la "LA")
(def gp "GP")
(def bv "BV")
(def hn "HN")
(def th "TH")
(def de "DE")
(def lb "LB")
(def kz "KZ")
(def as "AS")
(def ec "EC")
(def no "NO")
(def ao "AO")
(def fk "FK")
(def et "ET")
(def gs "GS")
(def md "MD")
(def ag "AG")
(def be "BE")
(def mv "MV")
(def sz "SZ")
(def cz "CZ")
(def cl "CL")
(def bt "BT")
(def nl "NL")
(def eg "EG")
(def mq "MQ")
(def sn "SN")
(def fo "FO")
(def ee "EE")
(def aq "AQ")
(def st "ST")
(def kn "KN")
(def bw "BW")
(def mh "MH")
(def ni "NI")
(def pg "PG")
(def vi "VI")
(def iq "IQ")
(def kg "KG")
(def us "US")
(def zm "ZM")
(def mc "MC")
(def gi "GI")
(def nc "NC")
(def gt "GT")
(def bf "BF")
(def yt "YT")
(def lu "LU")
(def ua "UA")
(def ie "IE")
(def lv "LV")
(def gd "GD")
(def mw "MW")
(def bs "BS")
(def az "AZ")
(def sk "SK")
(def gq "GQ")
(def tc "TC")
(def re "RE")
(def in "IN")
(def es "ES")
(def gl "GL")
(def ki "KI")
(def hk "HK")
(def co "CO")
(def ss "SS")
(def rs "RS")
(def io "IO")
(def ng "NG")
(def ug "UG")
(def cw "CW")
(def sl "SL")
(def er "ER")
(def je "JE")
(def ae "AE")
(def hm "HM")
(def pm "PM")
(def bd "BD")
(def mt "MT")
(def ai "AI")
(def gn "GN")
(def pw "PW")
(def na "NA")
(def mx "MX")
(def pl "PL")

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

(defn cn [country-code] (get country-code--country country-code))

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
  (conj
   ;; (clojure.set/map-invert aliases-hm)
   ;; select desired aliases
   {
    "VA" "Vatican"
    "TW" "Taiwan"
    "DO" "Dominican Rep"
    "IR" "Iran"
    "RU" "Russia"
    "PS" "Palestine"
    "AE" "UA Emirates"
    "KR" "South Korea"
    "MK" "Macedonia"
    "BA" "Bosnia"
    "CD" "DR Congo"
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
    "KN" "St Kitts & Nevis"
    "TT" "Trinidad & Tobago"
    "AG" "Antigua & Barbuda"
    "CF" "Central Afri Rep"
    "US" "USA"
    "GB" "UK"
    #_"CZ"
    }))
