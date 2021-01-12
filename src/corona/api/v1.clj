;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.api.v1)

(ns corona.api.v1
  "Version 1 of the https://coronavirus-tracker-api.herokuapp.com/"
  (:refer-clojure :exclude [pr])
  (:require
   [corona.common :as com]
   [corona.country-codes :as ccc]
   [corona.api.expdev07 :as srvc]
   [net.cgrand.xforms :as x]
   #_[taoensso.timbre :as timbre :refer [debug debugf info infof warn errorf fatalf]]
   )
  (:import java.text.SimpleDateFormat
           java.util.TimeZone))

;; (set! *warn-on-reflection* true)

;; avoid creating new class each time the `fmt` function is called
(def sdf
  "SimpleDateFormat"
  (let [sdf (new SimpleDateFormat "MM/dd/yy")]
    (.setTimeZone sdf (TimeZone/getDefault))
    sdf))

(defn fmt [raw-date] (.parse ^SimpleDateFormat sdf (srvc/keyname raw-date)))

;; (defn for-case [case-kw]
;;   (->> (get-in (json-data) [case-kw :locations])
;;        (filter (fn [loc]
;;                  true
;;                  #_(utils.core/in? ccodes (:country_code loc))))
;;        (map (fn [loc]
;;               (let [ccode (:country_code loc)]
;;                 (->> (sort-by
;;                       :t
;;                       (map (fn [[t v]] {:ccode ccode :t (fmt t) case-kw v})
;;                            (:history loc)))
;;                      #_(take-last 3)))))
;;        (flatten)
;;        (group-by :t)
;;        (map (fn [[t hms]]
;;               (map (fn [[ccode hms]]
;;                      {:ccode ccode :t t case-kw (reduce + (map case-kw hms))})
;;                    (group-by :ccode hms))))
;;        (flatten)
;;        (sort-by :ccode)))

(def ^:const ccodes
  #{
    "DE"
    ;; "CR" "TG" "ZA" "PE" "LC" "CH" "RU" "SI" "AU" "KR" "IT" "FI" "SC" "TT"
    ;; "MY" "SY" "MN" "AM" "DZ" "UY" "TD" "DJ" "BI" "MK" "MU" "LI" "GR" "GY"
    ;; "CG" "ML" "GM" "SA" "BH" "NE" "BN" "XK" "CD" "DK" "BJ" "ME" "BO" "JO"
    ;; "CV" "VE" "CI" "UZ" "TN" "IS" "GA" "TZ" "AT" "LT" "NP" "BG" "IL" "PK"
    ;; "PT" "HR" "MR" "GE" "HU" "TW" "MM" "SR" "VA" "KW" "SE" "GB" "QQ" "VN"
    ;; "CF" "PA" "VC" "JP" "IR" "AF" "LY" "MZ" "RO" "QA" "CM" "BY" "SD" "AR"
    ;; "BR" "ZW" "NZ" "FJ" "ID" "SV" "CN" "HT" "RW" "BA" "TL" "JM" "KE" "PY"
    ;; "CY" "GH" "MA" "SG" "LK" "PH" "SM" "TR" "PS" "BZ" "CU" "AD" "DM" "LR"
    ;; "OM" "SO" "DO" "AL" "FR" "GW" "BB" "CA" "MG" "KH" "LA" "HN" "TH" "DE"
    ;; "LB" "KZ" "EC" "NO" "AO" "ET" "MD" "AG" "BE" "MV" "SZ" "CZ" "CL" "BT"
    ;; "NL" "EG" "SN" "EE" "KN" "BW" "NI" "PG" "IQ" "KG" "US" "ZM" "MC" "GT"
    ;; "BF" "LU" "UA" "IE" "LV" "GD" "MW" "BS" "AZ" "SK" "GQ" "IN" "ES" "CO"
    ;; "RS" "NG" "UG" "SL" "ER" "AE" "BD" "MT" "GN" "NA" "MX" "PL"
    })

(defn xf-for-case
  "E.g.
(
  {:ccode \"SK\" :t #inst \"2020-04-04T00:00:00.000-00:00\" :deaths 1}
  {:ccode \"SK\" :t #inst \"2020-03-31T00:00:00.000-00:00\" :deaths 0}
  {:ccode \"US\" :t #inst \"2020-04-04T00:00:00.000-00:00\" :deaths 8407}
  {:ccode \"US\" :t #inst \"2020-03-31T00:00:00.000-00:00\" :deaths 3873})

  TODO see (require '[clojure.core.reducers :as r])
  "
  [case-kw]

  (defn process-location [{:keys [country_code history]}]
    ;; (def hms hms)
    ;; (def case-kw case-kw)
    ;; (def t t)
    ;; (def country_code country_code)
    ;; (def history history)

    #_(->> (sort-by :t history)
           (map (fn [[t v]] {:ccode country_code :t (fmt t) case-kw v}))
           (take-last 2))

    (into [] (comp (x/sort-by :t)
                   (map (fn [[t v]] {:ccode country_code :t (fmt t) case-kw v}))
                   #_(x/take-last 28))
          history))

  (defn process-date [[t hms]]
    ;; (def hms hms)
    ;; (def case-kw case-kw)
    ;; (def t t)
    (into []
          ;; the xform for the `into []`
          (comp
           ;; group together provinces of the given country
           (x/by-key :ccode (x/reduce conj)) ; (group-by :ccode)
           (map (fn [[ccode hms]] {:ccode ccode :t t case-kw (reduce + (map case-kw hms))})))
          hms)

    #_(->> (group-by :ccode hms) ;; group together provinces of the given country
           (map (fn [[ccode hms]] {:ccode ccode :t t case-kw (reduce + (map case-kw hms))}))))

  ;; TODO see: "A transducer for clojure.core.flatten"
  ;; https://groups.google.com/forum/#!topic/clojure-dev/J442k0GsWoY
  ;;
  ;; - `flatten` does not provide a transducer, but `cat` and `mapcat`
  ;;   transducers cover most cases.
  ;; - also "remove flatmap in favor of mapcat"
  ;;   https://clojure.atlassian.net/browse/CLJ-1494
  ;;
  ;; Resulting PR `tree-seq` instead of `flatten`:
  ;; https://github.com/cgrand/xforms/issues/20

  ;; Transducers: how-to
  ;; https://www.astrecipes.net/blog/2016/11/24/transducers-how-to/

  (->> (get-in (srvc/data-with-pop) [case-kw :locations])
       (transduce (comp
                   (filter
                    (fn [loc]
                      true
                      #_(utils.core/in? ccodes (:country_code loc))))
                   (map process-location))
                  ;; works as flatten by 1 level
                  into [])
       (transduce (comp
                   (x/by-key :t (x/reduce conj)) ; (group-by :t)
                   (map process-date))
                  ;; works as flatten by 1 level
                  into [])
       (sort-by :ccode)))

(defn pic-data
  "Returns a collection of hash-maps containing e.g.:
(
  {:ccode \"SK\" :t #inst \"2020-04-04T00:00:00.000-00:00\" :c 471    :r 10    :d 1    :p 5459642   :a 460}
  {:ccode \"SK\" :t #inst \"2020-03-31T00:00:00.000-00:00\" :c 363    :r 3     :d 0    :p 5459642   :a 360}
  {:ccode \"US\" :t #inst \"2020-04-04T00:00:00.000-00:00\" :c 308853 :r 14652 :d 8407 :p 331002651 :a 285794}
  {:ccode \"US\" :t #inst \"2020-03-31T00:00:00.000-00:00\" :c 188172 :r 7024  :d 3873 :p 331002651 :a 177275}
)"
  []
  (apply map
         (fn [{:keys [population]}
              {:keys [ccode t confirmed]}
              {:keys [recovered]}
              {:keys [deaths]}]
           (let [prm {:ccode ccode :t t
                      :c confirmed
                      :r recovered
                      :d deaths
                      :p population
                      :a (com/calculate-activ confirmed recovered deaths)}]
             (assoc
              prm
              #_(dissoc prm :c)
              :a100k ((com/calculate-cases-per-100k :a) prm)
              :r100k ((com/calculate-cases-per-100k :r) prm)
              :d100k ((com/calculate-cases-per-100k :d) prm)
              :a-rate (com/calc-rate-active prm)
              :r-rate (com/calc-rate-recovered prm)
              :d-rate (com/calc-rate-deaths prm)
              :c-rate (com/calc-rate-closed prm))))

         (map xf-for-case [:population :confirmed :recovered :deaths])))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.api.v1)
