;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.api.v1)

(ns corona.api.v1
  "Version 1 of the https://coronavirus-tracker-api.herokuapp.com/"
  (:refer-clojure :exclude [pr])
  (:require [corona.api.expdev07 :as data]
            [corona.common :as com :refer
             [ktst krep kest kabs k1e5 k%%% knew kvac kact krec kdea kclo kpop
              kcco sum]]
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

(defn normalize "" [default-hms k hms]
  (let [hms-set ((comp
                  set
                  (partial map (fn [hmc] (select-keys hmc [kcco ktst]))))
                 hms)]
    ((comp
      (partial concat hms)
      (partial keep (fn [dhm] (when-not (contains? hms-set dhm)
                               (assoc dhm k 0)))))
     default-hms)))

(defn xf-for-case "" [cnt-raw-dates data-with-pop case-kw]
  (let [lensed-case-kw (com/makelense case-kw)]
  ((comp
    #_(partial sort-by (juxt kcco ktst))
    flatten
    (partial map
             (fn [[ccode hms]]
               ((comp
                 #_(partial take-last 2)
                 (fn [ms] (subvec ms (dec (- (count ms) com/nr-of-days))))
                 vec
                 (partial sort-by ktst))
                hms)))
    (partial group-by kcco)

    flatten
    (partial map (fn [[t hms]] ;; process-date
                   ((comp
                     #_(fn [ms] (debugf "(count ms) %s" (count ms)) ms)
                     (fn [ms]
                       (conj ms
                             ((comp
                               (partial hash-map
                                        kcco ccc/worldwide-2-country-code
                                        ktst t
                                        case-kw)
                               (partial sum lensed-case-kw))
                              ms)))
                     ;; group together provinces of the given country
                     (partial map
                              (fn [[ccode hms]]
                                ((comp
                                  (partial hash-map kcco ccode ktst t case-kw)
                                  (partial sum lensed-case-kw))
                                 hms)))
                     (partial group-by kcco))
                    hms)))
    (partial group-by ktst)
    flatten
    (partial map (fn [{:keys [country_code history]}] ;;  process-location
                   ((comp
                     #_(partial take-last 4)
                     ;; The reason for `take-last` is the
                     ;; https://github.com/owid/covid-19-data/issues/1113
                     (partial take-last cnt-raw-dates)
                     (partial sort-by ktst)
                     (partial map (fn [[t v]] {kcco country_code
                                              ktst (fmt t) case-kw v})))
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
   case-kw)))

(defn-fun-id pic-data "" [cnt-raw-dates data-with-pop]
  ((comp
    (partial apply
             map
             (fn [{:keys [population]} ;; this hashmap doesn't contain 'ccode' and 't'
                  {:keys [vaccinated]}
                  {:keys [confirmed] :as hm-confirmed}
                  {:keys [recovered]}
                  {:keys [deaths]}]
               (let [new-confirmed confirmed
                     ccode         (kcco hm-confirmed)
                     t             (ktst hm-confirmed)
                     prm {kcco ccode
                          ktst   t
                          kpop   population
                          kvac   vaccinated
                          kact   (com/calc-active new-confirmed recovered deaths)
                          krec   recovered
                          kdea   deaths
                          knew   new-confirmed
                          kclo   (com/calc-closed deaths recovered)}
                     kws [knew kvac kact krec kdea kclo]]
                 ((comp
                   (partial conj
                            {kcco ccode ktst t kpop population})
                   (partial zipmap kws)
                   (partial map (partial hash-map krep))
                   (partial map
                            (partial zipmap [kabs k1e5 k%%%]))
                   (partial map (fn [case-kw]
                                  [((identity case-kw) prm)
                                   ((com/calc-per-1e5 case-kw) prm)
                                   ((com/calc-rate case-kw) prm)])))
                  kws))))
    ;; unsorted [pic-data] 99.2 MiB obtained in 7614 ms
    ;; sorted   [pic-data] 46.4 MiB
    (partial map (partial sort-by (juxt kcco ktst)))
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
                                                      hm [kcco ktst]))))
                                         hms-population)]
                        (map (partial normalize default-hms)
                             [:confirmed :recovered :deaths]
                             [hms-new-confirmed hms-recovered hms-deaths])))))
    (partial map (partial xf-for-case cnt-raw-dates data-with-pop)))
   [:population :vaccinated :confirmed :recovered :deaths]))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.api.v1)
