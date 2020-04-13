(ns corona.api.expdev07
  (:require [clojure.core.memoize :as memo]
            [clojure.set :as cset]
            [corona.common :as com :refer [api-server time-to-live]]
            [corona.core :as c :refer [read-number]]
            ;; for debugging
            [corona.country-codes :refer :all]
            [corona.defs :as d])
  (:import java.text.SimpleDateFormat))

(def url (format "http://%s/all" api-server))

(defn data [] (c/get-json url))

(def data-memo (memo/ttl data {} :ttl/threshold (* time-to-live 60 1000)))

#_(require '[ clojure.inspector :as i])
#_(i/inspect (data-memo))

(defn raw-dates-unsorted []
  #_[(keyword "2/22/20") (keyword "2/2/20")]
  (->> (data-memo) :confirmed :locations last :history keys))

(defn keyname [key] (str (namespace key) "/" (name key)))

(defn left-pad [s] (c/left-pad s 2))

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

(defn all-affected-country-codes
  "Countries with some confirmed, deaths or recovered cases"
  ([] (all-affected-country-codes {:limit-fn identity}))
  ([{:keys [limit limit-fn] :as prm}]
   (let [coll (transduce (map (fn [case]
                                #_(set (map :country_code (:locations (case (data-memo)))))
                                (transduce (map :country_code)
                                           conj #{}
                                           ((comp :locations case) (data-memo)))))
                         cset/union #{}
                         [:confirmed :deaths :recovered])]
     (transduce (comp (map (fn [cc] (if (= "XX" cc)
                                     d/default-2-country-code
                                     cc)))
                      (distinct)
                      limit-fn)
                conj []
                coll))

   #_(->> [:confirmed :deaths :recovered]
        (map (fn [case] (->> (data-memo)
                            case :locations
                            (map :country_code)
                            set)))
        (reduce cset/union)
        (mapv (fn [cc] (if (= "XX" cc)
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
  (sums-for-case {:case :confirmed :pred (pred-fn sk})
  "
  [{:keys [case pred]}]
  (let [locations (filter pred
                          ((comp :locations case) (data-memo)))]
    (map (fn [raw-date]
           (sums-for-date case locations raw-date))
         (raw-dates))))

(defn get-counts
  "Examples:
  (get-counts {:pred (fn [_] true)})
  (get-counts {:pred (pred-fn sk)})
  "
  [prm]
  (let [crd (mapv (fn [case] (sums-for-case (conj prm {:case case})))
                  [:confirmed :recovered :deaths])]
    (zipmap [:c :r :d :i] (conj crd (apply mapv c/calculate-ill crd)))))

(def get-counts-memo (memo/ttl get-counts {} :ttl/threshold (* time-to-live 60 1000)))

(defn confirmed [prm] (:c (get-counts-memo prm)))
(defn deaths    [prm] (:d (get-counts-memo prm)))
(defn recovered [prm] (:r (get-counts-memo prm)))
(defn ill       [prm] (:i (get-counts-memo prm)))

(defn eval-fun
  "
  (eval-fun {:fun get-last :pred (pred-fn SK)})
  "
  [{:keys [fun date] :as prm}]
  (into {:f (fun (dates-memo))}
        (map (fn [[k v]] {k (fun v)})
             (get-counts-memo prm))))

(defn delta
  "Example (delta {:pred (pred-fn cn)})"
  [prm]
  (->> [get-prev get-last]
       (map (fn [fun] (eval-fun (assoc prm :fun fun))))
       (apply (fn [prv lst]
                (map (fn [k]
                       {k (- (k lst) (k prv))})
                     [:c :d :r :i])
                ))
       (reduce into {})))

(defn last-day
  "Example (last-day {:pred (fn [_] true)})"
  [prm] (eval-fun (assoc prm :fun get-last)))

(defn pred-fn [country-code]
  (fn [loc]
    (condp = country-code
      d/worldwide-2-country-code
      true

      d/default-2-country-code
      ;; XX comes from the service
      (= "XX" (:country_code loc))

      (= country-code (:country_code loc)))))

(defn all-affected-country-names
  []
  (->> (all-affected-country-codes)
       (map com/country-name)))

(defn stats-per-country [{:keys [cc] :as prm}]
  (->> (assoc prm :pred (pred-fn cc))
       (last-day)
       (conj {:cn (com/country-name-aliased cc)
              :cc cc})))

(defn stats-all-affected-countries [prm]
  (->> (all-affected-country-codes)
       (map (fn [cc]
              (stats-per-country (assoc prm :cc cc))))))

