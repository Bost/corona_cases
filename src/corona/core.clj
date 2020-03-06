(ns corona.core
  (:require [environ.core :refer [env]]
            [clojure.java.io :as io]))

(def project-name "corona_cases") ;; see project.clj: defproject
(def token (env :telegram-token))
(def chat-id "112885364")
(def bot-name (str "@" project-name "_bot"))

(defn telegram-token-suffix []
  (let [suffix (.substring token (- (count token) 3))]
    (if (or (= suffix "Fq8") (= suffix "MR8"))
      suffix
      (throw (Exception.
              (format "Unrecognized TELEGRAM_TOKEN suffix: %s" suffix))))))

(def bot-type
  (let [suffix (telegram-token-suffix)]
    (case suffix
      "Fq8" "PROD"
      "MR8" "TEST")))

(def bot-ver
  (str (let [pom-props
             (with-open
               [pom-props-reader
                (->> (format "META-INF/maven/%s/%s/pom.properties"
                             project-name project-name)
                     io/resource
                     io/reader)]
               (doto (java.util.Properties.)
                 (.load pom-props-reader)))]
         (get pom-props "version"))
       "-" (env :bot-ver)))

(def bot (str bot-ver ":" bot-type))

(defn fix-octal-val
  "(read-string s-day \"08\") produces a NumberFormatException
  https://clojuredocs.org/clojure.core/read-string#example-5ccee021e4b0ca44402ef71a"
  [s]
  (clojure.string/replace s #"^0+" ""))

(defn read-number [v]
  (if (or (empty? v) (= "0" v))
    0
    (-> v fix-octal-val read-string)))

(def is-3166-abbrevs
  {
   "AF" "AFG" "AX" "ALA" "AL" "ALB" "DZ" "DZA" "AS" "ASM" "AD" "AND" "AO" "AGO"
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
  "EH" "ESH" "YE" "YEM" "ZM" "ZMB" "ZW" "ZWE"
   })

(defn create-cmd-fn []
  (fn [chat-id] (refresh-cmd-fn     cmd-names chat-id))
  )
