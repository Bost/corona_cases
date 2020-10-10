(ns corona.country-codes
  (:refer-clojure :exclude [pr])
  (:require
   [taoensso.timbre :as timbre :refer :all])
  )

(def country-code-strings
  ["CR" "TG" "TJ" "ZA" "IM" "PE" "LC" "CH" "RU" "MP" "CK" "SI" "AU" "KR" "IT"
   "FI" "GF" "SC" "SX" "ZZ" "TT" "TK" "MY" "SY" "MN" "TF" "KP" "AM" "DZ" "UY"
   "TD" "DJ" "BI" "MK" "MU" "LI" "NU" "GR" "GY" "CG" "NF" "ML" "AX" "GM" "SA"
   "CX" "BH" "NE" "BN" "XK" "MF" "CD" "DK" "BJ" "ME" "SJ" "BO" "JO" "CV" "VE"
   "CI" "UZ" "TN" "IS" "EH" "TM" "GA" "LS" "TZ" "AT" "LT" "NP" "BG" "IL" "GU"
   "PK" "PT" "HR" "VU" "PF" "BM" "MR" "GE" "HU" "TW" "MM" "VG" "YE" "SR" "PN"
   "VA" "PR" "KW" "SE" "GB" "QQ" "UM" "VN" "CF" "PA" "VC" "JP" "IR" "AF" "LY"
   "MZ" "RO" "QA" "CM" "GG" "BY" "SD" "BQ" "MO" "KY" "AR" "BR" "ZW" "NR" "NZ"
   "AW" "FJ" "ID" "SV" "CN" "FM" "HT" "CC" "RW" "BA" "TL" "JM" "KM" "KE" "WS"
   "TO" "PY" "SH" "CY" "GH" "MA" "SG" "LK" "PH" "SM" "WF" "TR" "PS" "BZ" "CU"
   "TV" "AD" "SB" "DM" "LR" "OM" "SO" "DO" "AL" "BL" "FR" "GW" "MS" "BB" "CA"
   "MG" "KH" "LA" "GP" "BV" "HN" "TH" "DE" "LB" "KZ" "AS" "EC" "NO" "AO" "FK"
   "ET" "GS" "MD" "AG" "BE" "MV" "SZ" "CZ" "CL" "BT" "NL" "EG" "MQ" "SN" "FO"
   "EE" "AQ" "ST" "KN" "BW" "MH" "NI" "PG" "VI" "IQ" "KG" "US" "ZM" "MC" "GI"
   "NC" "GT" "BF" "YT" "LU" "UA" "IE" "LV" "GD" "MW" "BS" "AZ" "SK" "GQ" "TC"
   "RE" "IN" "ES" "GL" "KI" "HK" "CO" "SS" "RS" "IO" "NG" "UG" "CW" "SL" "ER"
   "JE" "AE" "HM" "PM" "BD" "MT" "AI" "GN" "PW" "NA" "MX" "PL"

   "XD" "XE" "XS" "XX"])

(def country-code-docs
  {'xd
   "United Nations Neutral Zone. User-assigned.
\"https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2#XS\""

   'xe
   "Iraq-Saudi Arabia Neutral Zone. User-assigned.
\"https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2#XS\""

   'xs
   "Spratly Islands. User-assigned.
\"https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2#XS\""

   'xx
   "Disputed territory or unknown state or other entity or organization.
\"https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2#XX\""})

;; Undefine: (map #(ns-unmap *ns* %) (keys (ns-interns *ns*)))
(defn intern-country-codes!
  "TODO clarify artificially setting ':tag `String' since:
  (def f ^String \"f\")
  produces error: Metadata can only be applied to IMetas"
  [strings]
  (run! (fn [v]
          (let [symb-v (symbol (clojure.string/lower-case v))]
            (reset-meta! (intern *ns* symb-v v)
                         {:const true :tag `String})))
        strings))

(defn set-docstrings! [docstring-hm]
  (run! (fn [[k v]] (alter-meta! (get (ns-interns *ns*) k) assoc :doc v))
        docstring-hm))

;; (debug "Interning symbols for country-codes...")
(intern-country-codes! country-code-strings)
(info (format "%s country-code symbols interned"
              (count country-code-strings)))

;; (debug "Setting docstrings on interned country-code symbols...")
(set-docstrings! country-code-docs)
(info (format "Docstrings set on %s country-code symbols"
              (count country-code-docs)))

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
