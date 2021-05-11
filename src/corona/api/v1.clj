;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.api.v1)

(ns corona.api.v1
  "Version 1 of the https://coronavirus-tracker-api.herokuapp.com/"
  (:refer-clojure :exclude [pr])
  (:require [corona.api.expdev07 :as data]
            [corona.common :as com :refer [sum]]
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

(defn xf-for-case "" [cnt-raw-dates data-with-pop case-kw]
  (let [lensed-case-kw (com/tmp-lense case-kw)]
  ((comp
    #_(partial sort-by (juxt :ccode :t))
    #_(partial take-last 1)
    flatten
    (partial map
             (fn [[ccode hms]]
               ((comp
                 (fn [[fst rst]]
                   (conj rst
                         [{:ccode ccode
                           :t ((comp :t last) fst)
                           case-kw ((comp case-kw last) fst)}]))
                 ;; TODO 365 is the count of days in the plot
                 ;; See also corona.msg.graph.plot/stats-for-country
                 (fn [ms] (split-at (- (count ms) 365) ms))
                 (partial sort-by :t))
                hms)))
    (partial group-by :ccode)

    flatten
    (partial map (fn [[t hms]] ;; process-date
                   ((comp
                     #_(fn [ms] (debugf "(count ms) %s" (count ms)) ms)
                     (fn [ms]
                       (conj ms
                             ((comp
                               (partial hash-map :ccode "ZZ" :t t case-kw)
                               (partial sum lensed-case-kw))
                              ms)))
                     ;; group together provinces of the given country
                     (partial map
                              (fn [[ccode hms]]
                                ((comp
                                  (partial hash-map :ccode ccode :t t case-kw)
                                  (partial sum lensed-case-kw))
                                 hms)))
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
    (partial get data-with-pop))
   case-kw)
  ))

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

(defn-fun-id pic-data "" [json]
  #_(def json json)
  (let [data-with-pop (data/data-with-pop json)
        cnt-raw-dates (count (data/raw-dates json))]
    #_(def data-with-pop data-with-pop)
    #_(def cnt-raw-dates cnt-raw-dates)
    ((comp
      #_(fn [v] (def pd v) v)
      (partial apply
               map
               (fn [{:keys [population]} ;; this hashmap doesn't contain 'ccode' and 't'
                   {:keys [vaccinated]}
                   {:keys [confirmed ccode t]}
                   {:keys [recovered]}
                   {:keys [deaths]}]
                 #_(let [new-confirmed confirmed
                       prm {:ccode ccode
                            :t     t
                            :p     population
                            :v     vaccinated
                            :a     (com/calc-active new-confirmed recovered deaths)
                            :r     recovered
                            :d     deaths
                            :n     new-confirmed
                            :c     (com/calc-closed deaths recovered)}
                       kws [#_:v :a :r :d :c]]
                   ((comp
                     (partial conj
                              {:ccode ccode :t t :p population :v vaccinated :n new-confirmed})
                     (partial zipmap [:abs :1e5 :%%%])
                     (partial map (partial zipmap kws))
                     utc/transpose
                     (partial map (fn [case-kw]
                                    [((identity case-kw) prm)
                                     ((com/calc-per-1e5 case-kw) prm)
                                     ((com/calc-rate case-kw) prm)])))
                    kws))
                 (let [new-confirmed confirmed
                       prm {:ccode ccode
                            :t     t
                            :p     population
                            :v     vaccinated
                            :a     (com/calc-active new-confirmed recovered deaths)
                            :r     recovered
                            :d     deaths
                            :n     new-confirmed
                            :c     (com/calc-closed deaths recovered)}]
                   ((comp
                     #_(partial
                      conj
                      (let [kws [:a :r :d :c]]
                        {
                         ;; :ccode ccode :t t :p p :v v :n n
                         :abs prm
                         #_((comp
                             (partial into {})
                             (partial map (fn [kw] {kw (kw prm)})))
                            kws)
                         :1e5
                         ((comp
                           (partial into {})
                           (partial map (fn [kw] {kw ((com/calc-per-1e5 kw) prm)})))
                          kws)
                         :%
                         ((comp
                           (partial into {})
                           (partial map (fn [kw] {kw ((com/calc-rate kw) prm)})))
                          kws)}))
                     (partial conj prm)
                     (partial reduce into {})
                     (partial map (fn [rate-kw per-1e5-kw case-kw]
                                    {per-1e5-kw ((com/calc-per-1e5 case-kw) prm)
                                     rate-kw ((com/calc-rate case-kw) prm)})))
                    [:v% :a% :r% :d% :c%]
                    [:v1e5 :a1e5 :r1e5 :d1e5 :c1e5]
                    [:v :a :r :d :c]))))
      ;; unsorted [pic-data] 99.2 MiB obtained in 7614 ms
      ;; sorted   [pic-data] 46.4 MiB
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
                                            (partial map
                                                     (fn [hm]
                                                       (select-keys
                                                        hm [:ccode :t]))))
                                           hms-population)]
                          (map (partial normalize default-hms)
                               [:confirmed :recovered :deaths]
                               [hms-new-confirmed hms-recovered hms-deaths])))))
      #_(fn [v] (def xfb v) v)
      (partial map (partial xf-for-case cnt-raw-dates data-with-pop)))
     [:population :vaccinated :confirmed :recovered :deaths])))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.api.v1)
