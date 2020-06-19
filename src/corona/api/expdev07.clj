(ns corona.api.expdev07
  (:require [clojure.core.memoize :as memo]
            [clojure.set :as cset]
            [corona.common :as com :refer [api-server time-to-live]]
            [corona.core :as c :refer [read-number]]
            [corona.countries :as cr]
            [corona.country-codes :refer :all]
            [corona.defs :as d]
            ;; for debugging
            ;; [utils.core :refer :all :exclude [id]]
            )
  (:import java.text.SimpleDateFormat))

(def url (format "http://%s/all" api-server))

(defn data [] (c/get-json url))

(def data-memo (memo/ttl data {} :ttl/threshold (* time-to-live 60 1000)))

(defn raw-dates-unsorted []
  #_[(keyword "2/22/20") (keyword "2/2/20")]
  (->> (data-memo) :confirmed :locations last :history keys))

(defn keyname [key] (str (namespace key) "/" (name key)))

(defn left-pad [s] (c/left-pad s 2))

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
            (transduce (comp (map read-number)
                             (interpose "/"))
                       str
                       [m d y])))))
   conj []
   (raw-dates-unsorted)))

(defn data-with-pop []
  (conj
   (data-memo)
   {:population
    {:locations
     (let [dates (raw-dates)]
       (->> (cr/all-country-codes)
            ;; (take 0)
            (mapv (fn [country-code]
                    {
                     :country (cr/country-name-aliased country-code)
                     :country_code country-code
                     :history
                     ;; {:1/23/20 1e6 ;; start number
                     ;;    ;; other days - calc diff
                     ;; }
                     (let [population (or (->> country-code
                                               (cr/country-code--country)
                                               (get cr/population))
                                          (->> country-code
                                               (cr/country-name-aliased)
                                               (get cr/population))
                                          ;; world population is the sum
                                          ;; 7792480951
                                          0)]
                       (zipmap dates (repeat population)))}))))}}))

(def data-with-pop-memo (memo/ttl data-with-pop {} :ttl/threshold (* time-to-live 60 1000)))

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
     (transduce (comp (map (fn [cc] (if (= xx cc)
                                     d/default-2-country-code
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

(def dates-memo (memo/ttl dates {} :ttl/threshold (* time-to-live 60 1000)))

(defn get-last [coll] (first (take-last 1 coll)))

(defn get-prev [coll] (first (take-last 2 coll)))

(defn sums-for-date [case locations raw-date]
  (if (and (empty? locations)
           (= :recovered case))
    0
    (transduce (map (comp
                     ;; https://github.com/ExpDev07/coronavirus-tracker-api/issues/41
                     ;; str read-number
                     raw-date
                     :history))
               + 0
               locations)))

(defn sums-for-case
  "Return sums for a given `case` calculated for every single day
  E.g.
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
  {
   :p (                              ...) #_TODO
   :c (48288 49684 50931 51614 52383 ...)
   :r (    0     0     0     0     0 ...)
   :d ( 4814  4854  4874  4874  4891 ...)
   :i [43474 44830 46057 46740 47492 ...]}

  Invocation e.g.:
  (get-counts {:pred (fn [_] true)})
  (get-counts {:pred (pred-fn sk)})
  "
  [prm]
  (let [pcrd (mapv (fn [case] (sums-for-case (conj prm {:case case})))
                   [:population :confirmed :recovered :deaths])]
    (zipmap com/all-pcrdi-cases
            (conj pcrd (apply mapv c/calculate-ill pcrd)))))

(def get-counts-memo
  (memo/ttl get-counts {} :ttl/threshold (* time-to-live 60 1000)))

(defn population [prm] (:p (get-counts-memo prm)))
(defn confirmed [prm]  (:c (get-counts-memo prm)))
(defn deaths    [prm]  (:d (get-counts-memo prm)))
(defn recovered [prm]  (:r (get-counts-memo prm)))
(defn ill       [prm]  (:i (get-counts-memo prm)))

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
                     com/all-pcrdi-cases)))
       (reduce into {})))

(defn last-day
  "E.g.:
  (last-day {:pred (pred-fn sk)})
  (last-day {:pred (fn [_] true)})"
  [prm]
  #_(println "last-day" "prm" prm)
  (eval-fun (assoc prm :fun get-last)))

(defn pred-fn [country-code]
  (fn [loc]
    (condp = country-code
      d/worldwide-2-country-code
      true

      d/default-2-country-code
      ;; XX comes from the service
      (= xx (:country_code loc))

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
