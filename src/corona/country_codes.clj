;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.country-codes)

(ns corona.country-codes
  "This namespace seems to be the first one loaded by the class loader."
  (:refer-clojure :exclude [pr])
  (:require [clojure.set :as cset]
            [clojure.string :as cstr]
            taoensso.encore
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as appenders]))

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
     (str
      (force timestamp_)
      " "
      ;; #?(:clj (force hostname_))  #?(:clj " ")
      ((comp
        (fn [s] (subs s 0 1))
        cstr/upper-case
        name)
       (or (level log-level-map) level))
      " "
      "["
      (or ((comp
            (fn [n] (subs ?ns-str n))
            inc
            (fn [s] (.indexOf s ".")))
           ?ns-str)
          ?file "?")
      ;; line number indication doesn't work from the defn-fun-id macro
      ;; ":" (or ?line "?")
      "] "
      (force msg_)
      (when-not no-stacktrace?
        (when-let [err ?err]
          (str taoensso.encore/system-newline
               (timbre/stacktrace err opts))))))))

(def ^:const zone-id "Europe/Berlin")

#_(timbre/set-config! timbre/default-config)
(timbre/merge-config!
 {:output-fn
  log-output
  #_timbre/default-output-fn
  ;; TODO log only last N days
  :appenders {:spit (appenders/spit-appender {:fname "corona.log"})}
  :timestamp-opts
  (conj {:timezone (java.util.TimeZone/getTimeZone zone-id)}
        {:pattern "HH:mm:ss.SSSX"})})

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

(def ^:const worldwide-country-codes {"ZZ" "ZZZ"})
(def ^:const worldwide-2-country-code (-> worldwide-country-codes keys first))
(def ^:const worldwide-3-country-code (-> worldwide-country-codes vals first))
(def ^:const worldwide              "Worldwide")
(def ^:const country-code-worldwide worldwide-2-country-code)
(def ^:const country-code-worldwide--worldwide {worldwide-2-country-code worldwide})

(def ^:const default-country-codes   {"QQ" "QQQ"})
(def ^:const default-2-country-code (-> default-country-codes keys first))
(def ^:const others                 "Others")
(def ^:const country-code-others     default-2-country-code)
(def ^:const country-code-default--others {default-2-country-code others})

(def ^:const country-code-2-to-3-hm
  "Mapping: 2-letter country codes -> 3-letter country codes"
  (conj
   {
    "CR" "CRI"
    "TG" "TGO"
    "TJ" "TJK"
    "ZA" "ZAF"
    "IM" "IMN"
    "PE" "PER"
    "LC" "LCA"
    "CH" "CHE"
    "RU" "RUS"
    "MP" "MNP"
    "CK" "COK"
    "SI" "SVN"
    "AU" "AUS"
    "KR" "KOR"
    "IT" "ITA"
    "FI" "FIN"
    "GF" "GUF"
    "SC" "SYC"
    "SX" "SXM"
    "ZZ" "ZZZ"
    "TT" "TTO"
    "TK" "TKL"
    "MY" "MYS"
    "SY" "SYR"
    "MN" "MNG"
    "TF" "ATF"
    "KP" "PRK"
    "AM" "ARM"
    "DZ" "DZA"
    "UY" "URY"
    "TD" "TCD"
    "DJ" "DJI"
    "BI" "BDI"
    "MK" "MKD"
    "MU" "MUS"
    "LI" "LIE"
    "NU" "NIU"
    "GR" "GRC"
    "GY" "GUY"
    "CG" "COG"
    "NF" "NFK"
    "ML" "MLI"
    "AX" "ALA"
    "GM" "GMB"
    "SA" "SAU"
    "CX" "CXR"
    "BH" "BHR"
    "NE" "NER"
    "BN" "BRN"
    "XK" "XKX"
    "MF" "MAF"
    "CD" "COD"
    "DK" "DNK"
    "BJ" "BEN"
    "ME" "MNE"
    "SJ" "SJM"
    "BO" "BOL"
    "JO" "JOR"
    "CV" "CPV"
    "VE" "VEN"
    "CI" "CIV"
    "UZ" "UZB"
    "TN" "TUN"
    "IS" "ISL"
    "EH" "ESH"
    "TM" "TKM"
    "GA" "GAB"
    "LS" "LSO"
    "TZ" "TZA"
    "AT" "AUT"
    "LT" "LTU"
    "NP" "NPL"
    "BG" "BGR"
    "IL" "ISR"
    "GU" "GUM"
    "PK" "PAK"
    "PT" "PRT"
    "HR" "HRV"
    "VU" "VUT"
    "PF" "PYF"
    "BM" "BMU"
    "MR" "MRT"
    "GE" "GEO"
    "HU" "HUN"
    "TW" "TWN"
    "MM" "MMR"
    "VG" "VGB"
    "YE" "YEM"
    "SR" "SUR"
    "PN" "PCN"
    "VA" "VAT"
    "PR" "PRI"
    "KW" "KWT"
    "SE" "SWE"
    "GB" "GBR"
    "QQ" "QQQ"
    "UM" "UMI"
    "VN" "VNM"
    "CF" "CAF"
    "PA" "PAN"
    "VC" "VCT"
    "JP" "JPN"
    "IR" "IRN"
    "AF" "AFG"
    "LY" "LBY"
    "MZ" "MOZ"
    "RO" "ROU"
    "QA" "QAT"
    "CM" "CMR"
    "GG" "GGY"
    "BY" "BLR"
    "SD" "SDN"
    "BQ" "BES"
    "MO" "MAC"
    "KY" "CYM"
    "AR" "ARG"
    "BR" "BRA"
    "ZW" "ZWE"
    "NR" "NRU"
    "NZ" "NZL"
    "AW" "ABW"
    "FJ" "FJI"
    "ID" "IDN"
    "SV" "SLV"
    "CN" "CHN"
    "FM" "FSM"
    "HT" "HTI"
    "CC" "CCK"
    "RW" "RWA"
    "BA" "BIH"
    "TL" "TLS"
    "JM" "JAM"
    "KM" "COM"
    "KE" "KEN"
    "WS" "WSM"
    "TO" "TON"
    "PY" "PRY"
    "SH" "SHN"
    "CY" "CYP"
    "GH" "GHA"
    "MA" "MAR"
    "SG" "SGP"
    "LK" "LKA"
    "PH" "PHL"
    "SM" "SMR"
    "WF" "WLF"
    "TR" "TUR"
    "PS" "PSE"
    "BZ" "BLZ"
    "CU" "CUB"
    "TV" "TUV"
    "AD" "AND"
    "SB" "SLB"
    "DM" "DMA"
    "LR" "LBR"
    "OM" "OMN"
    "SO" "SOM"
    "DO" "DOM"
    "AL" "ALB"
    "BL" "BLM"
    "FR" "FRA"
    "GW" "GNB"
    "MS" "MSR"
    "BB" "BRB"
    "CA" "CAN"
    "MG" "MDG"
    "KH" "KHM"
    "LA" "LAO"
    "GP" "GLP"
    "BV" "BVT"
    "HN" "HND"
    "TH" "THA"
    "DE" "DEU"
    "LB" "LBN"
    "KZ" "KAZ"
    "AS" "ASM"
    "EC" "ECU"
    "NO" "NOR"
    "AO" "AGO"
    "FK" "FLK"
    "ET" "ETH"
    "GS" "SGS"
    "MD" "MDA"
    "AG" "ATG"
    "BE" "BEL"
    "MV" "MDV"
    "SZ" "SWZ"
    "CZ" "CZE"
    "CL" "CHL"
    "BT" "BTN"
    "NL" "NLD"
    "EG" "EGY"
    "MQ" "MTQ"
    "SN" "SEN"
    "FO" "FRO"
    "EE" "EST"
    "AQ" "ATA"
    "ST" "STP"
    "KN" "KNA"
    "BW" "BWA"
    "MH" "MHL"
    "NI" "NIC"
    "PG" "PNG"
    "VI" "VIR"
    "IQ" "IRQ"
    "KG" "KGZ"
    "US" "USA"
    "ZM" "ZMB"
    "MC" "MCO"
    "GI" "GIB"
    "NC" "NCL"
    "GT" "GTM"
    "BF" "BFA"
    "YT" "MYT"
    "LU" "LUX"
    "UA" "UKR"
    "IE" "IRL"
    "LV" "LVA"
    "GD" "GRD"
    "MW" "MWI"
    "BS" "BHS"
    "AZ" "AZE"
    "SK" "SVK"
    "GQ" "GNQ"
    "TC" "TCA"
    "RE" "REU"
    "IN" "IND"
    "ES" "ESP"
    "GL" "GRL"
    "KI" "KIR"
    "HK" "HKG"
    "CO" "COL"
    "SS" "SSD"
    "RS" "SRB"
    "IO" "IOT"
    "NG" "NGA"
    "UG" "UGA"
    "CW" "CUW"
    "SL" "SLE"
    "ER" "ERI"
    "JE" "JEY"
    "AE" "ARE"
    "HM" "HMD"
    "PM" "SPM"
    "BD" "BGD"
    "MT" "MLT"
    "AI" "AIA"
    "GN" "GIN"
    "PW" "PLW"
    "NA" "NAM"
    "MX" "MEX"
    "PL" "POL"
    }
   default-country-codes
   worldwide-country-codes))

(defn country-code-3-letter
  "3-letter country code from 2-letter country code: \"DE\" -> \"DEU\" "
  [ccode]
  (get country-code-2-to-3-hm ccode))

(defn country-code-2-letter
  "2-letter country code from 3-letter country code: \"DEU\" -> \"DE\" "
  [cccode]
  (get (clojure.set/map-invert country-code-2-to-3-hm) cccode))

(def excluded-country-codes
  "No data provided for this countries in the API service (the json)"
  #{
    "IM" "MP" "CK" "GF" "SX" "TK" "TF" "KP" "NU" "NF" "AX" "CX" "MF" "SJ" "TM"
    "GU" "VU" "PF" "BM" "VG" "PN" "PR" "QQ" "UM" "GG" "BQ" "MO" "KY" "NR" "AW"
    "FM" "CC" "WS" "TO" "SH" "WF" "TV" "BL" "MS" "GP" "BV" "AS" "FK" "GS" "MQ"
    "FO" "AQ" "MH" "VI" "GI" "NC" "YT" "TC" "RE" "GL" "KI" "HK" "IO" "CW" "JE"
    "HM" "PM" "AI" "PW"
    "EH" ;; Western Sahara Cases https://github.com/CSSEGISandData/COVID-19/issues/3436

    ;; 0 active cases - fighting OutOfMemoryError, saving memory
    ;; "IM" "MP" "CK" "GF" "SX" "TK" "TF" "KP" "NU" "NF" "AX" "CX" "MF" "SJ" "EH"
    ;; "TM" "GU" "PF" "BM" "VG" "PN" "PR" "QQ" "UM" "GG" "BQ" "MO" "KY" "NR" "AW"
    ;; "FM" "CC" "TO" "SH" "WF" "TV" "BL" "MS" "GP" "BV" "AS" "FK" "GS" "MQ" "FO"
    ;; "AQ" "VI" "GI" "NC" "YT" "TC" "RE" "GL" "KI" "HK" "IO" "CW" "JE" "HM" "PM"
    ;; "AI" "PW"
    })

(def no-population-country-codes ["TF" "MF" "SJ" "QQ" "UM" "BQ" "BV" "GS" "AQ" "HM"])

(def all-country-codes
  "All country codes (potentially including worldwide \"ZZ\")"
  (or
   #_#{"BE"}
   #_#{"GG"}
   #_#{"AR"}
   #_#{"SK" "PE" "KR" "TW" "AR" "TH" "MX"}
   #_#{"US" "DE" "ZZ"}
   #_#{"SK" "GG" "QQ" "DE" "ZZ"}
   #_#{"ZZ" "GB" "SK" "DE" "AT" "CZ" "US" "FR" "PL" "IT" "ES" "SE" "UA" "HU"}
   (clojure.set/difference
    ((comp set keys)
     country-code-2-to-3-hm)
    #_excluded-country-codes)))

(def relevant-country-codes
  (clojure.set/difference all-country-codes
                          #{country-code-worldwide}))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.country-codes)
