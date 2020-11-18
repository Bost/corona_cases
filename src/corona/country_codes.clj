(printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.country-codes)

(ns corona.country-codes
  "This namespace seems to be the first one loaded by the class loader."
  (:refer-clojure :exclude [pr])
  (:require
   [clojure.string :as cstr]
   [taoensso.encore]
   [taoensso.timbre :as timbre
    ;; :refer [debugf info infof warn errorf fatalf]
    ]

   [corona.common :as com]))

;; (set! *warn-on-reflection* true)

(def log-level-map ^:const {:debug :dbg :info :inf :warn :wrn :error :err})

(defn log-output
  "Default (fn [data]) -> string output fn.
    Use`(partial log-output <opts-map>)` to modify default opts."
  ([     data] (log-output nil data))
  ([opts data] ; For partials
   (let [{:keys [no-stacktrace? #_stacktrace-fonts]} opts
         {:keys [level ?err #_vargs msg_ ?ns-str ?file #_hostname_
                 timestamp_ ?line]} data]
     ;; (println "no-stacktrace?" no-stacktrace?)
     ;; (println "stacktrace-fonts" stacktrace-fonts)
     ;; (println "level" level)
     ;; (println "?err" ?err)
     ;; (println "msg_" msg_)
     ;; (println "?ns-str" ?ns-str)
     ;; (println "?file" ?file)
     ;; (println "hostname_" hostname_)
     ;; (println "(force hostname_)" (force hostname_))
     ;; (println "timestamp_" timestamp_)
     ;; (println "?line" ?line)
     (str
      (force timestamp_)
      " "
      ;; #?(:clj (force hostname_))  #?(:clj " ")
      (cstr/upper-case (name (or (level log-level-map)
                                 level)))  " "
      "[" (or ?ns-str ?file "?") ":" (or ?line "?") "] "
      (force msg_)
      (when-not no-stacktrace?
        (when-let [err ?err]
          (str taoensso.encore/system-newline
               (timbre/stacktrace err opts))))))))

(def ^:const zone-id "Europe/Berlin")

;; (set-config! default-config)
(timbre/merge-config!
 {:output-fn log-output #_default-output-fn
  :timestamp-opts
  (conj {:timezone (java.util.TimeZone/getTimeZone zone-id) #_:utc}
        (when com/env-devel? {:pattern "HH:mm:ss.SSSX"}))})

(def country-codes
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
  (def foo ^String \"foo\")
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

;; ;; (debug "Interning symbols for country-codes...")
;; (intern-country-codes! country-codes)
;; (infof "%s country-code symbols interned" (count country-codes))

;; ;; (debug "Setting docstrings on interned country-code symbols...")
;; (set-docstrings! country-code-docs)
;; (infof "Docstrings set on %s country-code symbols" (count country-code-docs))

(def ^:const ^String cr "CR")
(def ^:const ^String tg "TG")
(def ^:const ^String tj "TJ")
(def ^:const ^String za "ZA")
(def ^:const ^String im "IM")
(def ^:const ^String pe "PE")
(def ^:const ^String lc "LC")
(def ^:const ^String ch "CH")
(def ^:const ^String ru "RU")
(def ^:const ^String mp "MP")
(def ^:const ^String ck "CK")
(def ^:const ^String si "SI")
(def ^:const ^String au "AU")
(def ^:const ^String kr "KR")
(def ^:const ^String it "IT")
(def ^:const ^String fi "FI")
(def ^:const ^String gf "GF")
(def ^:const ^String sc "SC")
(def ^:const ^String sx "SX")
(def ^:const ^String zz "ZZ")
(def ^:const ^String tt "TT")
(def ^:const ^String tk "TK")
(def ^:const ^String my "MY")
(def ^:const ^String sy "SY")
(def ^:const ^String mn "MN")
(def ^:const ^String tf "TF")
(def ^:const ^String kp "KP")
(def ^:const ^String am "AM")
(def ^:const ^String dz "DZ")
(def ^:const ^String uy "UY")
(def ^:const ^String td "TD")
(def ^:const ^String dj "DJ")
(def ^:const ^String bi "BI")
(def ^:const ^String mk "MK")
(def ^:const ^String mu "MU")
(def ^:const ^String li "LI")
(def ^:const ^String nu "NU")
(def ^:const ^String gr "GR")
(def ^:const ^String gy "GY")
(def ^:const ^String cg "CG")
(def ^:const ^String nf "NF")
(def ^:const ^String ml "ML")
(def ^:const ^String ax "AX")
(def ^:const ^String gm "GM")
(def ^:const ^String sa "SA")
(def ^:const ^String cx "CX")
(def ^:const ^String bh "BH")
(def ^:const ^String ne "NE")
(def ^:const ^String bn "BN")
(def ^:const ^String xk "XK")
(def ^:const ^String mf "MF")
(def ^:const ^String cd "CD")
(def ^:const ^String dk "DK")
(def ^:const ^String bj "BJ")
(def ^:const ^String me "ME")
(def ^:const ^String sj "SJ")
(def ^:const ^String bo "BO")
(def ^:const ^String jo "JO")
(def ^:const ^String cv "CV")
(def ^:const ^String ve "VE")
(def ^:const ^String ci "CI")
(def ^:const ^String uz "UZ")
(def ^:const ^String tn "TN")
(def ^:const ^String is "IS")
(def ^:const ^String eh "EH")
(def ^:const ^String tm "TM")
(def ^:const ^String ga "GA")
(def ^:const ^String ls "LS")
(def ^:const ^String tz "TZ")
(def ^:const ^String at "AT")
(def ^:const ^String lt "LT")
(def ^:const ^String np "NP")
(def ^:const ^String bg "BG")
(def ^:const ^String il "IL")
(def ^:const ^String gu "GU")
(def ^:const ^String pk "PK")
(def ^:const ^String pt "PT")
(def ^:const ^String hr "HR")
(def ^:const ^String vu "VU")
(def ^:const ^String pf "PF")
(def ^:const ^String bm "BM")
(def ^:const ^String mr "MR")
(def ^:const ^String ge "GE")
(def ^:const ^String hu "HU")
(def ^:const ^String tw "TW")
(def ^:const ^String mm "MM")
(def ^:const ^String vg "VG")
(def ^:const ^String ye "YE")
(def ^:const ^String sr "SR")
(def ^:const ^String pn "PN")
(def ^:const ^String va "VA")
(def ^:const ^String pr "PR")
(def ^:const ^String kw "KW")
(def ^:const ^String se "SE")
(def ^:const ^String gb "GB")
(def ^:const ^String qq "QQ")
(def ^:const ^String um "UM")
(def ^:const ^String vn "VN")
(def ^:const ^String cf "CF")
(def ^:const ^String pa "PA")
(def ^:const ^String vc "VC")
(def ^:const ^String jp "JP")
(def ^:const ^String ir "IR")
(def ^:const ^String af "AF")
(def ^:const ^String ly "LY")
(def ^:const ^String mz "MZ")
(def ^:const ^String ro "RO")
(def ^:const ^String qa "QA")
(def ^:const ^String cm "CM")
(def ^:const ^String gg "GG")
(def ^:const ^String by "BY")
(def ^:const ^String sd "SD")
(def ^:const ^String bq "BQ")
(def ^:const ^String mo "MO")
(def ^:const ^String ky "KY")
(def ^:const ^String ar "AR")
(def ^:const ^String br "BR")
(def ^:const ^String zw "ZW")
(def ^:const ^String nr "NR")
(def ^:const ^String nz "NZ")
(def ^:const ^String aw "AW")
(def ^:const ^String fj "FJ")
(def ^:const ^String id "ID")
(def ^:const ^String sv "SV")
(def ^:const ^String cn "CN")
(def ^:const ^String fm "FM")
(def ^:const ^String ht "HT")
(def ^:const ^String cc "CC")
(def ^:const ^String rw "RW")
(def ^:const ^String ba "BA")
(def ^:const ^String tl "TL")
(def ^:const ^String jm "JM")
(def ^:const ^String km "KM")
(def ^:const ^String ke "KE")
(def ^:const ^String ws "WS")
(def ^:const ^String to "TO")
(def ^:const ^String py "PY")
(def ^:const ^String sh "SH")
(def ^:const ^String cy "CY")
(def ^:const ^String gh "GH")
(def ^:const ^String ma "MA")
(def ^:const ^String sg "SG")
(def ^:const ^String lk "LK")
(def ^:const ^String ph "PH")
(def ^:const ^String sm "SM")
(def ^:const ^String wf "WF")
(def ^:const ^String tr "TR")
(def ^:const ^String ps "PS")
(def ^:const ^String bz "BZ")
(def ^:const ^String cu "CU")
(def ^:const ^String tv "TV")
(def ^:const ^String ad "AD")
(def ^:const ^String sb "SB")
(def ^:const ^String dm "DM")
(def ^:const ^String lr "LR")
(def ^:const ^String om "OM")
(def ^:const ^String so "SO")
(def ^:const ^String do "DO")
(def ^:const ^String al "AL")
(def ^:const ^String bl "BL")
(def ^:const ^String fr "FR")
(def ^:const ^String gw "GW")
(def ^:const ^String ms "MS")
(def ^:const ^String bb "BB")
(def ^:const ^String ca "CA")
(def ^:const ^String mg "MG")
(def ^:const ^String kh "KH")
(def ^:const ^String la "LA")
(def ^:const ^String gp "GP")
(def ^:const ^String bv "BV")
(def ^:const ^String hn "HN")
(def ^:const ^String th "TH")
(def ^:const ^String de "DE")
(def ^:const ^String lb "LB")
(def ^:const ^String kz "KZ")
(def ^:const ^String as "AS")
(def ^:const ^String ec "EC")
(def ^:const ^String no "NO")
(def ^:const ^String ao "AO")
(def ^:const ^String fk "FK")
(def ^:const ^String et "ET")
(def ^:const ^String gs "GS")
(def ^:const ^String md "MD")
(def ^:const ^String ag "AG")
(def ^:const ^String be "BE")
(def ^:const ^String mv "MV")
(def ^:const ^String sz "SZ")
(def ^:const ^String cz "CZ")
(def ^:const ^String cl "CL")
(def ^:const ^String bt "BT")
(def ^:const ^String nl "NL")
(def ^:const ^String eg "EG")
(def ^:const ^String mq "MQ")
(def ^:const ^String sn "SN")
(def ^:const ^String fo "FO")
(def ^:const ^String ee "EE")
(def ^:const ^String aq "AQ")
(def ^:const ^String st "ST")
(def ^:const ^String kn "KN")
(def ^:const ^String bw "BW")
(def ^:const ^String mh "MH")
(def ^:const ^String ni "NI")
(def ^:const ^String pg "PG")
(def ^:const ^String vi "VI")
(def ^:const ^String iq "IQ")
(def ^:const ^String kg "KG")
(def ^:const ^String us "US")
(def ^:const ^String zm "ZM")
(def ^:const ^String mc "MC")
(def ^:const ^String gi "GI")
(def ^:const ^String nc "NC")
(def ^:const ^String gt "GT")
(def ^:const ^String bf "BF")
(def ^:const ^String yt "YT")
(def ^:const ^String lu "LU")
(def ^:const ^String ua "UA")
(def ^:const ^String ie "IE")
(def ^:const ^String lv "LV")
(def ^:const ^String gd "GD")
(def ^:const ^String mw "MW")
(def ^:const ^String bs "BS")
(def ^:const ^String az "AZ")
(def ^:const ^String sk "SK")
(def ^:const ^String gq "GQ")
(def ^:const ^String tc "TC")
(def ^:const ^String re "RE")
(def ^:const ^String in "IN")
(def ^:const ^String es "ES")
(def ^:const ^String gl "GL")
(def ^:const ^String ki "KI")
(def ^:const ^String hk "HK")
(def ^:const ^String co "CO")
(def ^:const ^String ss "SS")
(def ^:const ^String rs "RS")
(def ^:const ^String io "IO")
(def ^:const ^String ng "NG")
(def ^:const ^String ug "UG")
(def ^:const ^String cw "CW")
(def ^:const ^String sl "SL")
(def ^:const ^String er "ER")
(def ^:const ^String je "JE")
(def ^:const ^String ae "AE")
(def ^:const ^String hm "HM")
(def ^:const ^String pm "PM")
(def ^:const ^String bd "BD")
(def ^:const ^String mt "MT")
(def ^:const ^String ai "AI")
(def ^:const ^String gn "GN")
(def ^:const ^String pw "PW")
(def ^:const ^String na "NA")
(def ^:const ^String mx "MX")
(def ^:const ^String pl "PL")

(def ^:const ^String xd
  "United Nations Neutral Zone. User-assigned.
  \"https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2#XS\""
  "XD")

(def ^:const ^String xe
  "Iraq-Saudi Arabia Neutral Zone. User-assigned.
  \"https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2#XS\""
  "XE")

(def ^:const ^String xs
  "Spratly Islands. User-assigned.
\"https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2#XS\""
  "XS")

(def ^:const ^String xx
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
  [ccode]
  (get country-code-2-to-3-hm ccode))

(def excluded-country-codes
  "No data provided for this countries in the API service (the json)"
  [im mp ck gf sx tk tf kp nu nf ax cx mf sj tm gu vu pf bm vg pn pr qq um gg bq
  mo ky nr aw fm cc ws to sh wf tv bl ms gp bv as fk gs mq fo aq mh vi gi nc yt
  tc re gl ki hk io cw je hm pm ai pw])

(def all-country-codes
  "TODO all-country-codes should be a set"
  #_[sk cz gg zz]
  #_[gb sk de at cz us fr pl it es se ua hu zz]
  (keys country-code-2-to-3-hm))

(printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.country-codes)
