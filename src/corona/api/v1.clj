;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.api.v1)

(ns corona.api.v1
  "Version 1 of the https://coronavirus-tracker-api.herokuapp.com/"
  (:refer-clojure :exclude [pr])
  (:require [corona.api.expdev07 :as data]
            [corona.common :as com]
            [corona.country-codes :as ccc]
            [taoensso.timbre :as timbre]
            [corona.macro :refer [defn-fun-id debugf errorf warnf]]
            [utils.core :as utc])
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
  [cnt-raw-dates data-with-pop case-kw]
  ((comp
    #_(partial sort-by (juxt :ccode :t))
    flatten
    (partial map (fn [[t hms]] ;; process-date
                   ((comp
                     #_(fn [ms] (debugf "(count ms) %s" (count ms)) ms)
                     (fn [ms]
                       (conj ms
                             {:ccode "ZZ" :t t
                              case-kw (reduce + (map case-kw ms))}))
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
                     ;; The reason for `take-last` is the
                     ;; https://github.com/owid/covid-19-data/issues/1113
                     (partial take-last cnt-raw-dates)
                     (partial sort-by :t)
                     (partial map (fn [[t v]] {:ccode country_code
                                              :t (fmt t) case-kw v})))
                    history)))
    #_(partial filter
               (fn [loc]
                 (utils.core/in?
                  #{
                    "CR" "TG" "ZA" "PE" "LC" "CH" "RU" "SI" "AU" "KR" "IT" "FI"
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
                    "RS" "NG" "UG" "SL" "ER" "AE" "BD" "MT" "GN" "NA" "MX" "PL"}
                  (:country_code loc))))
    (partial get-in data-with-pop))
   [case-kw :locations]))

(defn normalize "" [default-hms k hms]
  (let [hms-set ((comp
                  set
                  (partial map (fn [hmc] (select-keys hmc [:ccode :t]))))
                 hms)]
    ((comp
      (partial concat hms)
      (partial keep (fn [dhm] (when-not (contains? hms-set dhm)
                               (assoc dhm k 0)))))
     default-hms)))

(defn-fun-id pic-data
  "Returns a collection of hash-maps containing e.g.:
(
  {:ccode \"SK\" :t #inst \"...\" :c 471    :r 10    :d 1    :p 5459642   :a 460}
  {:ccode \"SK\" :t #inst \"...\" :c 363    :r 3     :d 0    :p 5459642   :a 360}
  {:ccode \"US\" :t #inst \"...\" :c 308853 :r 14652 :d 8407 :p 331002651 :a 285794}
  {:ccode \"US\" :t #inst \"...\" :c 188172 :r 7024  :d 3873 :p 331002651 :a 177275}
)"
  [json]
  (let [data-with-pop (data/data-with-pop json)
        cnt-raw-dates (count (data/raw-dates json))]
    #_(def data-with-pop data-with-pop)
    ((comp
      #_(fn [v] (def pd v) v)
      (partial apply
               map
               (fn [{:keys [population]} ;; this hashmap doesn't contain 'ccode' and 't'
                   {:keys [vaccinated]}
                   {:keys [confirmed ccode t]}
                   {:keys [recovered]}
                   {:keys [deaths]}]
                 (let [prm {:ccode ccode
                            :t     t
                            :p     population
                            :v     vaccinated
                            :a     (com/calculate-activ confirmed recovered deaths)
                            :r     recovered
                            :d     deaths
                            :c     confirmed
                            }]
                   (assoc
                    prm
                    #_(dissoc prm :c)
                    :v100k  ((com/calculate-cases-per-100k :v) prm)
                    :a100k  ((com/calculate-cases-per-100k :a) prm)
                    :r100k  ((com/calculate-cases-per-100k :r) prm)
                    :d100k  ((com/calculate-cases-per-100k :d) prm)
                    :c100k  ((com/calculate-cases-per-100k :c) prm)
                    :v-rate ((com/calc-rate :v) prm)
                    :a-rate ((com/calc-rate :a) prm)
                    :r-rate ((com/calc-rate :r) prm)
                    :d-rate ((com/calc-rate :d) prm)
                    :c-rate ((com/calc-rate :c) prm)))))
      #_(partial apply (fn [hms-population
                         hms-vaccinated
                         hms-new-confirmed
                         hms-recovered
                         hms-deaths]
                       (def hp hms-population)
                       (def hv hms-vaccinated)
                       (def hn hms-new-confirmed)
                       (def hr hms-recovered)
                       (def hd hms-deaths)
                       [hms-population
                        hms-vaccinated
                        hms-new-confirmed
                        hms-recovered
                        hms-deaths]))
      (partial map (partial sort-by (juxt :ccode :t)))
      #_(fn [v] (def xff v) v)
      #_(partial map-indexed (fn [idx hm]
                             (debugf "idx %s (count hm) %s" idx (count hm))
                             hm))
      (partial apply (fn [hms-population
                         hms-vaccinated
                         hms-new-confirmed
                         hms-recovered
                         hms-deaths]
                       ((comp
                         (partial into [hms-population hms-vaccinated]))
                        (let [default-hms ((comp
                                            set
                                            (partial map (fn [hm] (select-keys hm [:ccode :t]))))
                                           hms-population)]
                          (map (partial normalize default-hms)
                               [:confirmed :recovered :deaths]
                               [hms-new-confirmed hms-recovered hms-deaths])))))
      #_(fn [v] (def xfb v) v)
      (partial map (partial xf-for-case cnt-raw-dates data-with-pop)))
     [:population :vaccinated :confirmed :recovered :deaths])))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.api.v1)
