(ns corona.country-codes
  (:refer-clojure :exclude [pr]))

(def ^:const cr "CR") (def ^:const tg "TG") (def ^:const tj "TJ") (def ^:const za "ZA") (def ^:const im "IM")
(def ^:const pe "PE") (def ^:const lc "LC") (def ^:const ch "CH") (def ^:const ru "RU") (def ^:const mp "MP")
(def ^:const ck "CK") (def ^:const si "SI") (def ^:const au "AU") (def ^:const kr "KR") (def ^:const it "IT")
(def ^:const fi "FI") (def ^:const gf "GF") (def ^:const sc "SC") (def ^:const sx "SX") (def ^:const zz "ZZ")
(def ^:const tt "TT") (def ^:const tk "TK") (def ^:const my "MY") (def ^:const sy "SY") (def ^:const mn "MN")
(def ^:const tf "TF") (def ^:const kp "KP") (def ^:const am "AM") (def ^:const dz "DZ") (def ^:const uy "UY")
(def ^:const td "TD") (def ^:const dj "DJ") (def ^:const bi "BI") (def ^:const mk "MK") (def ^:const mu "MU")
(def ^:const li "LI") (def ^:const nu "NU") (def ^:const gr "GR") (def ^:const gy "GY") (def ^:const cg "CG")
(def ^:const nf "NF") (def ^:const ml "ML") (def ^:const ax "AX") (def ^:const gm "GM") (def ^:const sa "SA")
(def ^:const cx "CX") (def ^:const bh "BH") (def ^:const ne "NE") (def ^:const bn "BN") (def ^:const xk "XK")
(def ^:const mf "MF") (def ^:const cd "CD") (def ^:const dk "DK") (def ^:const bj "BJ") (def ^:const me "ME")
(def ^:const sj "SJ") (def ^:const bo "BO") (def ^:const jo "JO") (def ^:const cv "CV") (def ^:const ve "VE")
(def ^:const ci "CI") (def ^:const uz "UZ") (def ^:const tn "TN") (def ^:const is "IS") (def ^:const eh "EH")
(def ^:const tm "TM") (def ^:const ga "GA") (def ^:const ls "LS") (def ^:const tz "TZ") (def ^:const at "AT")
(def ^:const lt "LT") (def ^:const np "NP") (def ^:const bg "BG") (def ^:const il "IL") (def ^:const gu "GU")
(def ^:const pk "PK") (def ^:const pt "PT") (def ^:const hr "HR") (def ^:const vu "VU") (def ^:const pf "PF")
(def ^:const bm "BM") (def ^:const mr "MR") (def ^:const ge "GE") (def ^:const hu "HU") (def ^:const tw "TW")
(def ^:const mm "MM") (def ^:const vg "VG") (def ^:const ye "YE") (def ^:const sr "SR") (def ^:const pn "PN")
(def ^:const va "VA") (def ^:const pr "PR") (def ^:const kw "KW") (def ^:const se "SE") (def ^:const gb "GB")
(def ^:const qq "QQ") (def ^:const um "UM") (def ^:const vn "VN") (def ^:const cf "CF") (def ^:const pa "PA")
(def ^:const vc "VC") (def ^:const jp "JP") (def ^:const ir "IR") (def ^:const af "AF") (def ^:const ly "LY")
(def ^:const mz "MZ") (def ^:const ro "RO") (def ^:const qa "QA") (def ^:const cm "CM") (def ^:const gg "GG")
(def ^:const by "BY") (def ^:const sd "SD") (def ^:const bq "BQ") (def ^:const mo "MO") (def ^:const ky "KY")
(def ^:const ar "AR") (def ^:const br "BR") (def ^:const zw "ZW") (def ^:const nr "NR") (def ^:const nz "NZ")
(def ^:const aw "AW") (def ^:const fj "FJ") (def ^:const id "ID") (def ^:const sv "SV") (def ^:const cn "CN")
(def ^:const fm "FM") (def ^:const ht "HT") (def ^:const cc "CC") (def ^:const rw "RW") (def ^:const ba "BA")
(def ^:const tl "TL") (def ^:const jm "JM") (def ^:const km "KM") (def ^:const ke "KE") (def ^:const ws "WS")
(def ^:const to "TO") (def ^:const py "PY") (def ^:const sh "SH") (def ^:const cy "CY") (def ^:const gh "GH")
(def ^:const ma "MA") (def ^:const sg "SG") (def ^:const lk "LK") (def ^:const ph "PH") (def ^:const sm "SM")
(def ^:const wf "WF") (def ^:const tr "TR") (def ^:const ps "PS") (def ^:const bz "BZ") (def ^:const cu "CU")
(def ^:const tv "TV") (def ^:const ad "AD") (def ^:const sb "SB") (def ^:const dm "DM") (def ^:const lr "LR")
(def ^:const om "OM") (def ^:const so "SO") (def ^:const do "DO") (def ^:const al "AL") (def ^:const bl "BL")
(def ^:const fr "FR") (def ^:const gw "GW") (def ^:const ms "MS") (def ^:const bb "BB") (def ^:const ca "CA")
(def ^:const mg "MG") (def ^:const kh "KH") (def ^:const la "LA") (def ^:const gp "GP") (def ^:const bv "BV")
(def ^:const hn "HN") (def ^:const th "TH") (def ^:const de "DE") (def ^:const lb "LB") (def ^:const kz "KZ")
(def ^:const as "AS") (def ^:const ec "EC") (def ^:const no "NO") (def ^:const ao "AO") (def ^:const fk "FK")
(def ^:const et "ET") (def ^:const gs "GS") (def ^:const md "MD") (def ^:const ag "AG") (def ^:const be "BE")
(def ^:const mv "MV") (def ^:const sz "SZ") (def ^:const cz "CZ") (def ^:const cl "CL") (def ^:const bt "BT")
(def ^:const nl "NL") (def ^:const eg "EG") (def ^:const mq "MQ") (def ^:const sn "SN") (def ^:const fo "FO")
(def ^:const ee "EE") (def ^:const aq "AQ") (def ^:const st "ST") (def ^:const kn "KN") (def ^:const bw "BW")
(def ^:const mh "MH") (def ^:const ni "NI") (def ^:const pg "PG") (def ^:const vi "VI") (def ^:const iq "IQ")
(def ^:const kg "KG") (def ^:const us "US") (def ^:const zm "ZM") (def ^:const mc "MC") (def ^:const gi "GI")
(def ^:const nc "NC") (def ^:const gt "GT") (def ^:const bf "BF") (def ^:const yt "YT") (def ^:const lu "LU")
(def ^:const ua "UA") (def ^:const ie "IE") (def ^:const lv "LV") (def ^:const gd "GD") (def ^:const mw "MW")
(def ^:const bs "BS") (def ^:const az "AZ") (def ^:const sk "SK") (def ^:const gq "GQ") (def ^:const tc "TC")
(def ^:const re "RE") (def ^:const in "IN") (def ^:const es "ES") (def ^:const gl "GL") (def ^:const ki "KI")
(def ^:const hk "HK") (def ^:const co "CO") (def ^:const ss "SS") (def ^:const rs "RS") (def ^:const io "IO")
(def ^:const ng "NG") (def ^:const ug "UG") (def ^:const cw "CW") (def ^:const sl "SL") (def ^:const er "ER")
(def ^:const je "JE") (def ^:const ae "AE") (def ^:const hm "HM") (def ^:const pm "PM") (def ^:const bd "BD")
(def ^:const mt "MT") (def ^:const ai "AI") (def ^:const gn "GN") (def ^:const pw "PW") (def ^:const na "NA")
(def ^:const mx "MX") (def ^:const pl "PL")

(def ^:const xd "XD") (def ^:const xe "XE") (def ^:const xs "XS") (def ^:const xx "XX")
(def ^:const xd
  "United Nations Neutral Zone. User-assigned.
  \"https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2#XS\""
  "XD")

(def ^:const xe
  "Iraq-Saudi Arabia Neutral Zone. User-assigned.
  \"https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2#XS\""
  "XE")

(def ^:const xs
  "Spratly Islands. User-assigned.
\"https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2#XS\""
  "XS")

(def ^:const xx
  "Disputed Territory. Indicator for unknown states, other entities or organizations.
\"https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2#XX\""
  "XX")

(def ^:const worldwide-country-codes {zz "ZZZ"})
(def ^:const worldwide-2-country-code (-> worldwide-country-codes keys first))
(def ^:const worldwide-3-country-code (-> worldwide-country-codes vals first))
(def ^:const worldwide              "Worldwide")
(def ^:const country-code-worldwide {worldwide-2-country-code worldwide})

(def ^:const default-country-codes   {qq "QQQ"})
(def ^:const default-2-country-code (-> default-country-codes keys first))
(def ^:const default-3-country-code (-> default-country-codes vals first))
(def ^:const others                 "Others")
(def ^:const country-code-others    {default-2-country-code others})

(def ^:const cruise-ship-2-country-code default-2-country-code)
(def ^:const cruise-ship-3-country-code default-3-country-code)
(def ^:const cruise-ship                "Cruise Ship")
(def ^:const country-code-cruise-ship   {cruise-ship-2-country-code cruise-ship})

(def ^:const country-code-2-to-3-hm
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
