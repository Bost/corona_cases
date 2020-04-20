(ns corona.api.v1
  "Version 1 of the https://coronavirus-tracker-api.herokuapp.com/"
  (:require [clojure.core.memoize :as memo]
            [corona.common :refer [api-server time-to-live]]
            [corona.core :as c]
            [corona.countries :as cr]
            [corona.country-codes :refer :all]
            [corona.tables :as t]
            [net.cgrand.xforms :as x])
  (:import java.text.SimpleDateFormat
           java.util.TimeZone))

(defmacro dbg [body]
  `(let [x# ~body]
     (println "dbg:" '~body "=" x#)
     x#))

;; https://coronavirus-tracker-api.herokuapp.com/v2/locations?source=jhu&timelines=true

(def url (format "http://%s/all" api-server))

(defn data [] (c/get-json url))

(def data-memo (memo/ttl data {} :ttl/threshold (* time-to-live 60 1000)))

#_(require '[ clojure.inspector :as i])
#_(i/inspect (data-memo))

(defn raw-dates-unsorted []
  #_[(keyword "2/22/20") (keyword "2/2/20")]
  ((comp keys :history last :locations :confirmed) (data-memo)))

(defn keyname [key] (str (namespace key) "/" (name key)))

;; avoid creating new class each time the `fmt` function is called
(def sdf (let [sdf (new SimpleDateFormat "MM/dd/yy")]
           (.setTimeZone sdf (TimeZone/getTimeZone
                              #_"America/New_York"
                              "UTC"))
           sdf))

(defn fmt [raw-date] (.parse sdf (keyname raw-date)))

;; (defn for-case [case]
;;   (->> (get-in (data-memo) [case :locations])
;;        (filter (fn [loc]
;;                  true
;;                  #_(corona.core/in? ccs (:country_code loc))))
;;        (map (fn [loc]
;;               (let [cc (:country_code loc)]
;;                 (->> (sort-by
;;                       :f
;;                       (map (fn [[f v]] {:cc cc :f (fmt f) case v})
;;                            (:history loc)))
;;                      #_(take-last 3)))))
;;        (flatten)
;;        (group-by :f)
;;        (map (fn [[f hms]]
;;               (map (fn [[cc hms]]
;;                      {:cc cc :f f case (reduce + (map case hms))})
;;                    (group-by :cc hms))))
;;        (flatten)
;;        (sort-by :cc)))

(def ccs
  #{
    ;; "CR" "TG" "ZA" "PE" "LC" "CH" "RU" "SI" "AU" "KR" "IT" "FI" "SC" "TT" "MY"
    ;; "SY" "MN" "AM" "DZ" "UY" "TD" "DJ" "BI" "MK" "MU" "LI" "GR" "GY" "CG" "ML"
    ;; "GM" "SA" "BH" "NE" "BN" "XK" "CD" "DK" "BJ" "ME" "BO" "JO" "CV" "VE" "CI"
    ;; "UZ" "TN" "IS" "GA" "TZ" "AT" "LT" "NP" "BG" "IL" "PK" "PT" "HR" "MR" "GE"
    ;; "HU" "TW" "MM" "SR" "VA" "KW" "SE" "GB" "QQ" "VN" "CF" "PA" "VC" "JP" "IR"
    ;; "AF" "LY" "MZ" "RO" "QA" "CM" "BY" "SD" "AR" "BR" "ZW" "NZ" "FJ" "ID" "SV"
    ;; "CN" "HT" "RW" "BA" "TL" "JM" "KE" "PY" "CY" "GH" "MA" "SG" "LK" "PH" "SM"
    ;; "TR" "PS" "BZ" "CU" "AD" "DM" "LR" "OM" "SO" "DO" "AL" "FR" "GW" "BB" "CA"
    ;; "MG" "KH" "LA" "HN" "TH" "DE" "LB" "KZ" "EC" "NO" "AO" "ET" "MD" "AG" "BE"
    ;; "MV" "SZ" "CZ" "CL" "BT" "NL" "EG" "SN" "EE" "KN" "BW" "NI" "PG" "IQ" "KG"
    ;; "US" "ZM" "MC" "GT" "BF" "LU" "UA" "IE" "LV" "GD" "MW" "BS" "AZ" "SK" "GQ"
    ;; "IN" "ES" "CO" "RS" "NG" "UG" "SL" "ER" "AE" "BD" "MT" "GN" "NA" "MX" "PL"
    })

(defn xf-for-case
  "E.g.
(
  {:cc \"SK\" :f #inst \"2020-04-04T00:00:00.000-00:00\" :deaths 1}
  {:cc \"SK\" :f #inst \"2020-03-31T00:00:00.000-00:00\" :deaths 0}
  {:cc \"US\" :f #inst \"2020-04-04T00:00:00.000-00:00\" :deaths 8407}
  {:cc \"US\" :f #inst \"2020-03-31T00:00:00.000-00:00\" :deaths 3873})

  TODO see (require '[clojure.core.reducers :as r])
  "
  [case]

  (defn process-location [{:keys [country_code history]}]
    ;; (def hms hms)
    ;; (def case case)
    ;; (def f f)
    ;; (def country_code country_code)
    ;; (def history history)

    #_(->> (sort-by :f history)
           (map (fn [[f v]] {:cc country_code :f (fmt f) case v}))
           (take-last 2))

    (into [] (comp (x/sort-by :f)
                   (map (fn [[f v]] {:cc country_code :f (fmt f) case v}))
                   #_(x/take-last 2))
          history))

  (defn process-date [[f hms]]
    ;; (def hms hms)
    ;; (def case case)
    ;; (def f f)
    (into []
          ;; the xform for the `into []`
          (comp
           ;; group together provinces of the given country
           (x/by-key :cc (x/reduce conj)) ; (group-by :cc)
           (map (fn [[cc hms]] {:cc cc :f f case (reduce + (map case hms))})))
          hms)

    #_(->> (group-by :cc hms) ;; group together provinces of the given country
           (map (fn [[cc hms]] {:cc cc :f f case (reduce + (map case hms))}))))

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

  (->> (get-in (data-memo) [case :locations])
       (transduce (comp
                   (filter (fn [loc]
                             true
                             #_(corona.core/in? ccs (:country_code loc))))
                   (map process-location))
                  ;; works as flatten by 1 level
                  into [])
       (transduce (comp
                   (x/by-key :f (x/reduce conj)) ; (group-by :f)
                   (map process-date))
                  ;; works as flatten by 1 level
                  into [])
       (sort-by :cc)))

(defn pic-data
  "Returns a collection of hash-maps containing e.g.:
(
  {:cc \"SK\" :f #inst \"2020-04-04T00:00:00.000-00:00\" :c 471    :r 10    :d 1    :p 5459642   :i 460}
  {:cc \"SK\" :f #inst \"2020-03-31T00:00:00.000-00:00\" :c 363    :r 3     :d 0    :p 5459642   :i 360}
  {:cc \"US\" :f #inst \"2020-04-04T00:00:00.000-00:00\" :c 308853 :r 14652 :d 8407 :p 331002651 :i 285794}
  {:cc \"US\" :f #inst \"2020-03-31T00:00:00.000-00:00\" :c 188172 :r 7024  :d 3873 :p 331002651 :i 177275}
)"
  []
  (let [population (t/population)]
    (apply map
           (fn [{:keys [cc f confirmed] :as cm}
               {:keys [recovered] :as rm}
               {:keys [deaths] :as dm}]
             (let [prm {:cc cc :f f :c confirmed :r recovered :d deaths
                        :p (if-let [p (get population cc)]
                             p
                             (do
                               ;; TODO what it the population of Kosovo XK?
                               #_(printf "No population found for %s %s; using 0\n"
                                       (cr/country-name cc) cc)
                               0))}]
               (assoc
                prm
                #_(dissoc prm :c)
                :i (c/calculate-ill prm))))
           (map xf-for-case [:confirmed :recovered :deaths]))))
