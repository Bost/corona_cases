(ns corona.country-codes
  (:refer-clojure :exclude [pr]))

(def cr "CR") (def tg "TG") (def tj "TJ") (def za "ZA") (def im "IM")
(def pe "PE") (def lc "LC") (def ch "CH") (def ru "RU") (def mp "MP")
(def ck "CK") (def si "SI") (def au "AU") (def kr "KR") (def it "IT")
(def fi "FI") (def gf "GF") (def sc "SC") (def sx "SX") (def zz "ZZ")
(def tt "TT") (def tk "TK") (def my "MY") (def sy "SY") (def mn "MN")
(def tf "TF") (def kp "KP") (def am "AM") (def dz "DZ") (def uy "UY")
(def td "TD") (def dj "DJ") (def bi "BI") (def mk "MK") (def mu "MU")
(def li "LI") (def nu "NU") (def gr "GR") (def gy "GY") (def cg "CG")
(def nf "NF") (def ml "ML") (def ax "AX") (def gm "GM") (def sa "SA")
(def cx "CX") (def bh "BH") (def ne "NE") (def bn "BN") (def xk "XK")
(def mf "MF") (def cd "CD") (def dk "DK") (def bj "BJ") (def me "ME")
(def sj "SJ") (def bo "BO") (def jo "JO") (def cv "CV") (def ve "VE")
(def ci "CI") (def uz "UZ") (def tn "TN") (def is "IS") (def eh "EH")
(def tm "TM") (def ga "GA") (def ls "LS") (def tz "TZ") (def at "AT")
(def lt "LT") (def np "NP") (def bg "BG") (def il "IL") (def gu "GU")
(def pk "PK") (def pt "PT") (def hr "HR") (def vu "VU") (def pf "PF")
(def bm "BM") (def mr "MR") (def ge "GE") (def hu "HU") (def tw "TW")
(def mm "MM") (def vg "VG") (def ye "YE") (def sr "SR") (def pn "PN")
(def va "VA") (def pr "PR") (def kw "KW") (def se "SE") (def gb "GB")
(def qq "QQ") (def um "UM") (def vn "VN") (def cf "CF") (def pa "PA")
(def vc "VC") (def jp "JP") (def ir "IR") (def af "AF") (def ly "LY")
(def mz "MZ") (def ro "RO") (def qa "QA") (def cm "CM") (def gg "GG")
(def by "BY") (def sd "SD") (def bq "BQ") (def mo "MO") (def ky "KY")
(def ar "AR") (def br "BR") (def zw "ZW") (def nr "NR") (def nz "NZ")
(def aw "AW") (def fj "FJ") (def id "ID") (def sv "SV") (def cn "CN")
(def fm "FM") (def ht "HT") (def cc "CC") (def rw "RW") (def ba "BA")
(def tl "TL") (def jm "JM") (def km "KM") (def ke "KE") (def ws "WS")
(def to "TO") (def py "PY") (def sh "SH") (def cy "CY") (def gh "GH")
(def ma "MA") (def sg "SG") (def lk "LK") (def ph "PH") (def sm "SM")
(def wf "WF") (def tr "TR") (def ps "PS") (def bz "BZ") (def cu "CU")
(def tv "TV") (def ad "AD") (def sb "SB") (def dm "DM") (def lr "LR")
(def om "OM") (def so "SO") (def do "DO") (def al "AL") (def bl "BL")
(def fr "FR") (def gw "GW") (def ms "MS") (def bb "BB") (def ca "CA")
(def mg "MG") (def kh "KH") (def la "LA") (def gp "GP") (def bv "BV")
(def hn "HN") (def th "TH") (def de "DE") (def lb "LB") (def kz "KZ")
(def as "AS") (def ec "EC") (def no "NO") (def ao "AO") (def fk "FK")
(def et "ET") (def gs "GS") (def md "MD") (def ag "AG") (def be "BE")
(def mv "MV") (def sz "SZ") (def cz "CZ") (def cl "CL") (def bt "BT")
(def nl "NL") (def eg "EG") (def mq "MQ") (def sn "SN") (def fo "FO")
(def ee "EE") (def aq "AQ") (def st "ST") (def kn "KN") (def bw "BW")
(def mh "MH") (def ni "NI") (def pg "PG") (def vi "VI") (def iq "IQ")
(def kg "KG") (def us "US") (def zm "ZM") (def mc "MC") (def gi "GI")
(def nc "NC") (def gt "GT") (def bf "BF") (def yt "YT") (def lu "LU")
(def ua "UA") (def ie "IE") (def lv "LV") (def gd "GD") (def mw "MW")
(def bs "BS") (def az "AZ") (def sk "SK") (def gq "GQ") (def tc "TC")
(def re "RE") (def in "IN") (def es "ES") (def gl "GL") (def ki "KI")
(def hk "HK") (def co "CO") (def ss "SS") (def rs "RS") (def io "IO")
(def ng "NG") (def ug "UG") (def cw "CW") (def sl "SL") (def er "ER")
(def je "JE") (def ae "AE") (def hm "HM") (def pm "PM") (def bd "BD")
(def mt "MT") (def ai "AI") (def gn "GN") (def pw "PW") (def na "NA")
(def mx "MX") (def pl "PL")

(def xd "XD") (def xe "XE") (def xs "XS") (def xx "XX")

(def worldwide-country-codes {zz "ZZZ"})
(def worldwide-2-country-code (-> worldwide-country-codes keys first))
(def worldwide-3-country-code (-> worldwide-country-codes vals first))
(def worldwide              "Worldwide")
(def country-code-worldwide {worldwide-2-country-code worldwide})

(def default-country-codes   {qq "QQQ"})
(def default-2-country-code (-> default-country-codes keys first))
(def default-3-country-code (-> default-country-codes vals first))
(def others                 "Others")
(def country-code-others    {default-2-country-code others})

(def cruise-ship-2-country-code default-2-country-code)
(def cruise-ship-3-country-code default-3-country-code)
(def cruise-ship                "Cruise Ship")
(def country-code-cruise-ship   {cruise-ship-2-country-code cruise-ship})

(def country-code-2-to-3-hm
  "Mapping: 2-letter country codes -> 3-letter country codes"
  (conj
   {
    cr "CRI" tg "TGO" tj "TJK" za "ZAF" im "IMN" pe "PER" lc "LCA"
    ch "CHE" ru "RUS" mp "MNP" ck "COK" si "SVN" au "AUS" kr "KOR" it "ITA"
    fi "FIN" gf "GUF" sc "SYC" sx "SXM" zz "ZZZ" tt "TTO" tk "TKL" my "MYS"
    sy "SYR" mn "MNG" tf "ATF" kp "PRK" am "ARM" dz "DZA" uy "URY" td "TCD"
    dj "DJI" bi "BDI" mk "MKD" mu "MUS" li "LIE" nu "NIU" gr "GRC" gy "GUY"
    cg "COG" nf "NFK" ml "MLI" ax "ALA" gm "GMB" sa "SAU" cx "CXR" bh "BHR"
    ne "NER" bn "BRN" xk "XKX" mf "MAF" cd "COD" dk "DNK" bj "BEN" me "MNE"
    sj "SJM" bo "BOL" jo "JOR" cv "CPV" ve "VEN" ci "CIV" uz "UZB" tn "TUN"
    is "ISL" eh "ESH" tm "TKM" ga "GAB" ls "LSO" tz "TZA" at "AUT" lt "LTU"
    np "NPL" bg "BGR" il "ISR" gu "GUM" pk "PAK" pt "PRT" hr "HRV" vu "VUT"
    pf "PYF" bm "BMU" mr "MRT" ge "GEO" hu "HUN" tw "TWN" mm "MMR" vg "VGB"
    ye "YEM" sr "SUR" pn "PCN" va "VAT" pr "PRI" kw "KWT" se "SWE" gb "GBR"
    qq "QQQ" um "UMI" vn "VNM" cf "CAF" pa "PAN" vc "VCT" jp "JPN" ir "IRN"
    af "AFG" ly "LBY" mz "MOZ" ro "ROU" qa "QAT" cm "CMR" gg "GGY" by "BLR"
    sd "SDN" bq "BES" mo "MAC" ky "CYM" ar "ARG" br "BRA" zw "ZWE" nr "NRU"
    nz "NZL" aw "ABW" fj "FJI" id "IDN" sv "SLV" cn "CHN" fm "FSM" ht "HTI"
    cc "CCK" rw "RWA" ba "BIH" tl "TLS" jm "JAM" km "COM" ke "KEN" ws "WSM"
    to "TON" py "PRY" sh "SHN" cy "CYP" gh "GHA" ma "MAR" sg "SGP" lk "LKA"
    ph "PHL" sm "SMR" wf "WLF" tr "TUR" ps "PSE" bz "BLZ" cu "CUB" tv "TUV"
    ad "AND" sb "SLB" dm "DMA" lr "LBR" om "OMN" so "SOM" do "DOM" al "ALB"
    bl "BLM" fr "FRA" gw "GNB" ms "MSR" bb "BRB" ca "CAN" mg "MDG" kh "KHM"
    la "LAO" gp "GLP" bv "BVT" hn "HND" th "THA" de "DEU" lb "LBN" kz "KAZ"
    as "ASM" ec "ECU" no "NOR" ao "AGO" fk "FLK" et "ETH" gs "SGS" md "MDA"
    ag "ATG" be "BEL" mv "MDV" sz "SWZ" cz "CZE" cl "CHL" bt "BTN" nl "NLD"
    eg "EGY" mq "MTQ" sn "SEN" fo "FRO" ee "EST" aq "ATA" st "STP" kn "KNA"
    bw "BWA" mh "MHL" ni "NIC" pg "PNG" vi "VIR" iq "IRQ" kg "KGZ" us "USA"
    zm "ZMB" mc "MCO" gi "GIB" nc "NCL" gt "GTM" bf "BFA" yt "MYT" lu "LUX"
    ua "UKR" ie "IRL" lv "LVA" gd "GRD" mw "MWI" bs "BHS" az "AZE" sk "SVK"
    gq "GNQ" tc "TCA" re "REU" in "IND" es "ESP" gl "GRL" ki "KIR" hk "HKG"
    co "COL" ss "SSD" rs "SRB" io "IOT" ng "NGA" ug "UGA" cw "CUW" sl "SLE"
    er "ERI" je "JEY" ae "ARE" hm "HMD" pm "SPM" bd "BGD" mt "MLT" ai "AIA"
    gn "GIN" pw "PLW" na "NAM" mx "MEX" pl "POL"
    } default-country-codes
   worldwide-country-codes))

(defn country-code-3-letter
  "3-letter country code from 2-letter country code: \"DE\" -> \"DEU\" "
  [cc]
  (get country-code-2-to-3-hm cc))

(defn all-country-codes [] (keys country-code-2-to-3-hm))
