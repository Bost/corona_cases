;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.api.v1)

(ns corona.api.v1
  "Version 1 of the https://coronavirus-tracker-api.herokuapp.com/"
  (:refer-clojure :exclude [pr])
  (:require [corona.api.expdev07 :as data]
            [taoensso.timbre :as timbre :refer [debugf]]
            [corona.common :as com])
  (:import java.text.SimpleDateFormat
           java.util.TimeZone))

;; (set! *warn-on-reflection* true)

;; avoid creating new class each time the `fmt` function is called
(def ^SimpleDateFormat sdf
  "SimpleDateFormat"
  (let [sdf (new SimpleDateFormat "MM/dd/yy")]
    (.setTimeZone sdf (TimeZone/getDefault))
    sdf))

(defn fmt [raw-date] (.parse sdf (data/keyname raw-date)))

(defn xf-for-case
  "E.g.
(
  {:ccode \"SK\" :t #inst \"2020-04-04T00:00:00.000-00:00\" :deaths 1}
  {:ccode \"SK\" :t #inst \"2020-03-31T00:00:00.000-00:00\" :deaths 0}
  {:ccode \"US\" :t #inst \"2020-04-04T00:00:00.000-00:00\" :deaths 8407}
  {:ccode \"US\" :t #inst \"2020-03-31T00:00:00.000-00:00\" :deaths 3873})
  "
  [case-kw]
  ((comp
    (partial sort-by :ccode)
    flatten
    (partial map (fn [[t hms]] ;; process-date
                   ((comp
                     ;; group together provinces of the given country
                     (partial map (fn [[ccode hms]]
                                    {:ccode ccode :t t
                                     case-kw (reduce + (map case-kw hms))}))
                     (partial group-by :ccode))
                    hms)))
    (partial group-by :t)
    flatten
    (partial map (fn [{:keys [country_code history]}] ;;  process-location
                   ((comp
                     #_(partial take-last 4)
                     (partial map (fn [[t v]] {:ccode country_code
                                              :t (fmt t) case-kw v}))
                     (partial sort-by :t))
                    history)))
    #_(partial filter
               (fn [loc]
                 (utils.core/in?
                  ["CR" "TG" "ZA" "PE" "LC" "CH" "RU" "SI" "AU" "KR" "IT" "FI"
                  "SC" "TT" "MY" "SY" "MN" "AM" "DZ" "UY" "TD" "DJ" "BI" "MK"
                  "MU" "LI" "GR" "GY" "CG" "ML" "GM" "SA" "BH" "NE" "BN" "XK"
                  "CD" "DK" "BJ" "ME" "BO" "JO" "CV" "VE" "CI" "UZ" "TN" "IS"
                  "GA" "TZ" "AT" "LT" "NP" "BG" "IL" "PK" "PT" "HR" "MR" "GE"
                  "HU" "TW" "MM" "SR" "VA" "KW" "SE" "GB" "QQ" "VN" "CF" "PA"
                  "VC" "JP" "IR" "AF" "LY" "MZ" "RO" "QA" "CM" "BY" "SD" "AR"
                  "BR" "ZW" "NZ" "FJ" "ID" "SV" "CN" "HT" "RW" "BA" "TL" "JM"
                  "KE" "PY" "CY" "GH" "MA" "SG" "LK" "PH" "SM" "TR" "PS" "BZ"
                  "CU" "AD" "DM" "LR" "OM" "SO" "DO" "AL" "FR" "GW" "BB" "CA"
                  "MG" "KH" "LA" "HN" "TH" "DE" "LB" "KZ" "EC" "NO" "AO" "ET"
                  "MD" "AG" "BE" "MV" "SZ" "CZ" "CL" "BT" "NL" "EG" "SN" "EE"
                  "KN" "BW" "NI" "PG" "IQ" "KG" "US" "ZM" "MC" "GT" "BF" "LU"
                  "UA" "IE" "LV" "GD" "MW" "BS" "AZ" "SK" "GQ" "IN" "ES" "CO"
                  "RS" "NG" "UG" "SL" "ER" "AE" "BD" "MT" "GN" "NA" "MX" "PL"]
                  (:country_code loc))))
    (partial get-in (data/data-with-pop)))
   [case-kw :locations]))

(defn pic-data
  "Returns a collection of hash-maps containing e.g.:
(
  {:ccode \"SK\" :t #inst \"...\" :c 471    :r 10    :d 1    :p 5459642   :a 460}
  {:ccode \"SK\" :t #inst \"...\" :c 363    :r 3     :d 0    :p 5459642   :a 360}
  {:ccode \"US\" :t #inst \"...\" :c 308853 :r 14652 :d 8407 :p 331002651 :a 285794}
  {:ccode \"US\" :t #inst \"...\" :c 188172 :r 7024  :d 3873 :p 331002651 :a 177275}
)"
  []
  ((comp
    (partial apply map
             (fn [{:keys [vaccinated]}
                  {:keys [population]}
                  {:keys [ccode t confirmed]}
                  {:keys [recovered]}
                  {:keys [deaths]}]
               (let [prm {:ccode ccode :t t
                          :c confirmed
                          :r recovered
                          :d deaths
                          :v vaccinated
                          :p population
                          :a (com/calculate-activ confirmed recovered deaths)}]
                 (assoc
                  prm
                  #_(dissoc prm :c)
                  :a100k  ((com/calculate-cases-per-100k :a) prm)
                  :r100k  ((com/calculate-cases-per-100k :r) prm)
                  :d100k  ((com/calculate-cases-per-100k :d) prm)
                  :v100k  ((com/calculate-cases-per-100k :v) prm)
                  :a-rate
                  (let [ret ((com/calc-rate :a) prm)]
                    #_(debugf "[%s] :a-rate %s" "pic-data" ret)
                    ret)
                  :r-rate ((com/calc-rate :r) prm)
                  :d-rate ((com/calc-rate :d) prm)
                  :v-rate
                  (let [ret ((com/calc-rate :v) prm)]
                    #_(debugf "[%s] :v-rate %s" "pic-data" ret)
                    ret)
                  :c-rate ((com/calc-rate :c) prm)))))
    (partial map xf-for-case))
   [:vaccinated :population :confirmed :recovered :deaths]))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.api.v1)
