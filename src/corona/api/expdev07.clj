(ns corona.api.expdev07
  (:require
   [clojure.core.memoize :as memo]
   [clojure.set :as cset]
   [corona.common :as co]
   [corona.countries :as cr]
   [corona.country-codes :as cc :refer :all]
   [utils.core :refer [dbgv dbgi] :exclude [id]]
   )
  (:import java.text.SimpleDateFormat))

(def url (format "http://%s/all" co/api-server))

(defn data [] (co/get-json url))

(def data-memo (memo/ttl data {} :ttl/threshold (* co/time-to-live 60 1000)))

(defn raw-dates-unsorted []
  #_[(keyword "2/22/20") (keyword "2/2/20")]
  (->> (data-memo) :confirmed :locations last :history keys))

(defn keyname [key] (str (namespace key) "/" (name key)))

(defn left-pad [s] (co/left-pad s 2))

#_(require '[ clojure.inspector :as i])
#_(i/inspect
   (data-with-pop-memo)
   #_(data-memo))

(defn xf-sort
  "A sorting transducer. Mostly a syntactic improvement to allow composition of
  sorting with the standard transducers, but also provides a slight performance
  increase over transducing, sorting, and then continuing to transduce.

  Thanx to https://gist.github.com/matthewdowney/380dd28c1046d4919a8c59a523f804fd.js
  "
  ([]
   (xf-sort compare))
  ([cmp]
   (fn [rf]
     (let [temp-list (java.util.ArrayList.)]
       (fn
         ([]
          (rf))
         ([xs]
          (reduce rf xs (sort cmp (vec (.toArray temp-list)))))
         ([xs x]
          (.add temp-list x)
          xs))))))

(defn raw-dates []
  (transduce
   (comp
    (map keyname)
    (map (fn [date] (re-find (re-matcher #"(\d+)/(\d+)/(\d+)" date))))
    (map (fn [[_ m d y]]
           (transduce (comp (map left-pad)
                            (interpose "/"))
                      str
                      [y m d])))
    (xf-sort)
    (map (fn [kw] (re-find (re-matcher #"(\d+)/(\d+)/(\d+)" kw))))
    (map (fn [[_ y m d]]
           (keyword
            (transduce (comp (map co/read-number)
                             (interpose "/"))
                       str
                       [m d y])))))
   conj []
   (raw-dates-unsorted)))

(defn population-cnt [country-code]
  (or (->> country-code
           (cr/country-code--country)
           (get cr/population))
      (->> country-code
           (cr/country-name-aliased)
           (get cr/population))
      ;; world population is the sum
      ;; 7792480951
      (let [default-population 0]
        (printf "ERROR population undefined; country-code: %s; using %s\n"
                country-code
                default-population)
        default-population)))

(defn data-with-pop
  "Data with population numbers"
  []
  (conj
   (data-memo)
   {:population
    {:locations
     (let [dates (raw-dates)]
       (->> (cc/all-country-codes)
            ;; (take 0)
            (mapv (fn [country-code]
                    {
                     :country (cr/country-name-aliased country-code)
                     :country_code country-code
                     :history
                     ;; {:1/23/20 1e6 ;; start number
                     ;;    ;; other days - calc diff
                     ;; }
                     (let [pop-cnt (population-cnt country-code)]
                       (zipmap dates (repeat pop-cnt)))}))))}}))

(def data-with-pop-memo
  (memo/ttl data-with-pop {} :ttl/threshold (* co/time-to-live 60 1000)))

(defn all-affected-country-codes
  "Countries with some confirmed, deaths or recovered cases"
  ([] (all-affected-country-codes {:limit-fn identity}))
  ([{:keys [limit limit-fn] :as prm}]
   (let [coll (transduce (map (fn [case]
                                #_(set (map :country_code (:locations (case
                                                                          (data-with-pop-memo)
                                                                          #_(data-memo)))))
                                (transduce (map :country_code)
                                           conj #{}
                                           ((comp :locations case)
                                            (data-with-pop-memo)
                                            #_(data-memo)))))
                         cset/union #{}
                         [:population :confirmed :deaths :recovered])]
     (transduce (comp (map (fn [cc] (if (= cc/xx cc)
                                     cc/default-2-country-code
                                     cc)))
                      (distinct)
                      limit-fn)
                conj []
                coll))

   #_(->> [:population :confirmed :deaths :recovered]
          (map (fn [case] (->>
                            (data-with-pop-memo)
                            #_(data-memo)
                            case :locations
                            (map :country_code)
                            set)))
        (reduce cset/union)
        (mapv (fn [cc] (if (= xx cc)
                        d/default-2-country-code
                        cc)))
        (distinct)
        (limit-fn)
        )))

(defn dates
  ([] (dates {:limit-fn identity}))
  ([{:keys [limit-fn] :as prm}]
   (let [sdf (new SimpleDateFormat "MM/dd/yy")]
     (map (fn [rd] (.parse sdf (keyname rd)))
          (limit-fn (raw-dates))))))

(def dates-memo
  (memo/ttl dates {} :ttl/threshold (* co/time-to-live 60 1000)))

(defn get-last [coll] (first (take-last 1 coll)))

(defn get-prev [coll] (first (take-last 2 coll)))

(defn sums-for-date [case locations raw-date]
  (if (and (empty? locations)
           (= :recovered case))
    0
    (transduce (map (comp
                     ;; https://github.com/ExpDev07/coronavirus-tracker-api/issues/41
                     ;; str co/read-number
                     raw-date
                     :history))
               + 0
               locations)))

(defn sums-for-case
  "Return sums for a given `case` calculated for every single day. E.g.
  (sums-for-case {:case :confirmed :pred (pred-fn sk)})
  "
  [{:keys [case pred]}]
  (let [locations (filter pred
                          ((comp :locations case)
                           (data-with-pop-memo)
                           #_(data-memo)))]
    (map (fn [raw-date]
           (sums-for-date case locations raw-date))
         (raw-dates))))

(defn get-counts
  "Returns a hash-map containing case-counts day-by-day. E.g.:
  (get-counts {:pred (pred-fn sk)})
  ;; => ;; last 5 values
  {
   :p (... 5456362 5456362 5456362 5456362 5456362)
   :c (...    2566    2596    2599    2615    2690)
   :r (...    1861    1864    1866    1874    1884)
   :d (...      31      31      31      31      31)
   :i (...     674     701     702     710     775)}

  (get-counts {:pred (fn [_] true)})
  "
  [prm]
  (let [pcrd (mapv (fn [case] (sums-for-case (conj prm {:case case})))
                   [:population :confirmed :recovered :deaths])]
    (zipmap co/all-cases
            (conj pcrd
                  (apply mapv co/calculate-active pcrd)
                  (apply mapv co/calculate-active-per-100k    pcrd)
                  (apply mapv co/calculate-recovered-per-100k pcrd)
                  (apply mapv co/calculate-deaths-per-100k    pcrd)))))

(def get-counts-memo
  #_get-counts
  (memo/ttl get-counts {} :ttl/threshold (* co/time-to-live 60 1000)))

(defn population [prm] (:p (get-counts-memo prm)))
(defn confirmed [prm]  (:c (get-counts-memo prm)))
(defn deaths    [prm]  (:d (get-counts-memo prm)))
(defn recovered [prm]  (:r (get-counts-memo prm)))
(defn active    [prm]  (:i (get-counts-memo prm)))
(defn active-per-100k    [prm] (:i100k (get-counts-memo prm)))
(defn recovered-per-100k [prm] (:r100k (get-counts-memo prm)))
(defn deaths-per-100k    [prm] (:d100k (get-counts-memo prm)))

(defn eval-fun
  "E.g.:
  (eval-fun {:fun get-last :pred (pred-fn sk)})
  (eval-fun {:fun get-last :pred (fn [_] true)})
  "
  [{:keys [fun date] :as prm}]
  (into {:f (fun (dates-memo))}
        (map (fn [[k v]] {k (fun v)})
             (get-counts-memo prm))))

(defn delta
  "E.g.:
  (delta {:pred (pred-fn cn)})
  (delta {:pred (fn [_] true)})"
  [prm]
  (->> [get-prev get-last]
       (map (fn [fun] (eval-fun (assoc prm :fun fun))))
       (apply (fn [prv lst]
                (map (fn [k]
                       {k (- (k lst) (k prv))})
                     co/all-cases)))
       (reduce into {})))

(defn last-day
  "E.g.:
  (last-day {:pred (pred-fn sk)})
  (last-day {:pred (fn [_] true)})"
  [prm]
  #_(println "last-day" "prm" prm)
  (eval-fun (assoc prm :fun get-last)))

(defn last-8-reports
  "E.g.:
  (last-8-reports {:pred (pred-fn sk)})
  (last-8-reports {:pred (fn [_] true)})"
  [prm]
  #_(println "last-day" "prm" prm)
  (eval-fun (assoc prm :fun (fn [coll] (take-last 8 coll)))))

(defn pred-fn [country-code]
  (fn [loc]
    (condp = country-code
      cc/worldwide-2-country-code
      true

      cc/default-2-country-code
      ;; XX comes from the service
      (= cc/xx (:country_code loc))

      (= country-code (:country_code loc)))))

(defn stats-per-country [{:keys [cc] :as prm}]
  (->> (assoc prm :pred (pred-fn cc))
       (last-day)
       (conj {:cn (cr/country-name-aliased cc)
              :cc cc})))

(defn stats-all-affected-countries [prm]
  (->> (all-affected-country-codes)
       (map (fn [cc]
              (stats-per-country (assoc prm :cc cc))))))
