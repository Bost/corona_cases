(printf "Current-ns [%s] loading %s\n" *ns* 'corona.api.expdev07)

(ns corona.api.expdev07
  (:require
   [clojure.set :as cset]
   [corona.common :as com]
   [corona.countries :as ccr]
   [corona.country-codes :as ccc :refer :all]
   [utils.core :refer [dbgv dbgi] :exclude [id]]
   [taoensso.timbre :as timbre :refer :all]
   [clojure.spec.alpha :as s]
   )
  (:import java.text.SimpleDateFormat))

;; (debugf "Loading namespace %s" *ns*)

(def ^:const url (format "http://%s/all" com/api-server))

(defn data [] (com/get-json url))

(defonce cache (atom nil))

(defn request! []
  (doall
   (let [tbeg (System/currentTimeMillis)]
     (let [response (data)]
       (swap! cache (fn [_] response))
       (debugf "[request!] %s chars cached in %s ms"
               (count (str @cache)) (- (System/currentTimeMillis) tbeg))))))

(def data-memo
  (fn [] @cache)
  #_(com/memo-ttl data))

(defn raw-dates-unsorted []
  #_[(keyword "2/22/20") (keyword "2/2/20")]
  (keys (:history (last (:locations (:confirmed (data-memo)))))))

(defn keyname [key] (str (namespace key) "/" (name key)))

(defn left-pad [s] (com/left-pad s 2))

#_(require '[ clojure.inspector :as i])
#_(i/inspect
   (data-with-pop-memo)
   #_(data-memo))

(defn xf-sort
  "A sorting transducer. Mostly a syntactic improvement to allow composition of
  sorting with the standard transducers, but also provides a slight performance
  increase over transducing, sorting, and then continuing to transduce.

  Thanks to https://gist.github.com/matthewdowney/380dd28c1046d4919a8c59a523f804fd.js
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
            (transduce (comp (map com/read-number)
                             (interpose "/"))
                       str
                       [m d y])))))
   conj []
   (raw-dates-unsorted)))

(defn population-cnt [country-code]
  (or (get ccr/population country-code)
      ;; world population is the sum
      ;; 7792480951
      (let [default-population 0]
        (error (format "population nr unknown; country-code: %s; using %s"
                       country-code
                       default-population))
        default-population)))

(defn data-with-pop
  "Data with population numbers"
  []
  (conj
   (data-memo)
   {:population
    {:locations
     (let [dates (raw-dates)]
       (->> (ccc/all-country-codes)
            ;; (take 0)
            (mapv (fn [country-code]
                    {
                     :country (ccr/country-name-aliased country-code)
                     :country_code country-code
                     :history
                     ;; {:1/23/20 1e6 ;; start number
                     ;;    ;; other days - calc diff
                     ;; }
                     (let [pop-cnt (population-cnt country-code)]
                       (zipmap dates (repeat pop-cnt)))}))))}}))

(def data-with-pop-memo
  (com/memo-ttl data-with-pop))

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
     (transduce (comp (map (fn [cc] (if (= ccc/xx cc)
                                     ccc/default-2-country-code
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

(def all-affected-country-codes-memo
  (com/memo-ttl all-affected-country-codes))

(defn dates
  ([] (dates {:limit-fn identity}))
  ([{:keys [limit-fn] :as prm}]
   #_(debug "dates" {:limit-fn limit-fn})
   (let [sdf (new SimpleDateFormat "MM/dd/yy")]
     (map (fn [rd] (.parse sdf (keyname rd)))
          (limit-fn (raw-dates))))))

(def dates-memo
  (com/memo-ttl dates))

(defn get-last [coll] (first (take-last 1 coll)))

(defn get-prev [coll] (first (take-last 2 coll)))

(defn sums-for-date [case locations raw-date]
  (if (and (empty? locations)
           (= :recovered case))
    0
    (transduce (map (comp
                     ;; https://github.com/ExpDev07/coronavirus-tracker-api/issues/41
                     ;; str com/read-number
                     raw-date
                     :history))
               + 0
               locations)))

(defn sums-for-case
  "Return sums for a given `case-kw` calculated for every single day. E.g.
  (sums-for-case :confirmed (pred-fn sk))
  "
  [case-kw pred]
  (let [locations (filter pred
                          ((comp :locations case-kw)
                           (data-with-pop-memo)
                           #_(data-memo)))]
    (map (fn [raw-date]
           (sums-for-date case-kw locations raw-date))
         (raw-dates))))

(defn get-counts
  "Returns a hash-map containing case-counts day-by-day. E.g.:
  (get-counts (pred-fn sk))
  ;; => ;; last 5 values
  {
   :p (... 5456362 5456362 5456362 5456362 5456362)
   :c (...    2566    2596    2599    2615    2690)
   :r (...    1861    1864    1866    1874    1884)
   :d (...      31      31      31      31      31)
   :i (...     674     701     702     710     775)}

  (get-counts (fn [_] true))
  "
  [pred]
  (let [pcrd (mapv (fn [case-kw] (sums-for-case case-kw pred))
                   [:population :confirmed :recovered :deaths])]
    (zipmap com/all-cases
            (apply
             conj pcrd
             (->> [com/calculate-active
                   (com/calculate-cases-per-100k :i)
                   (com/calculate-cases-per-100k :r)
                   (com/calculate-cases-per-100k :d)
                   (com/calculate-cases-per-100k :c)]
                  #_(mapv (fn [f] (apply mapv f pcrd)))
                  (mapv (fn [f] (apply mapv (fn [p c r d]
                                             (->> [p c r d]
                                                  (zipmap [:p :c :r :d])
                                                  (f)))
                                      pcrd))))))))

(def get-counts-memo
  #_get-counts
  (com/memo-ttl get-counts))

(s/def ::fun clojure.core/fn?)
(s/def ::pred-fn (s/or :nil nil? :fn clojure.core/fn?))

(defn eval-fun
  "E.g.:
  (eval-fun get-last (pred-fn sk))
  (eval-fun get-last (fn [_] true))"
  [fun pred]
  {:pre [(s/valid? ::fun fun)
         (s/valid? ::pred-fn pred)]}
  (into {:f (fun (dates-memo))}
        (map (fn [[k v]] {k (fun v)})
             (get-counts-memo pred))))
(defn delta
  "E.g.:
  (delta {:pred-q '(pred-fn cn)  :pred (pred-fn cn)})
  (delta {:pred-q '(fn [_] true) :pred (fn [_] true)})"
  [prm]
  (->> [get-prev get-last]
       (map (fn [fun]
              (eval-fun fun (:pred prm))))
       (apply (fn [prv lst]
                (map (fn [k]
                       {k (- (k lst) (k prv))})
                     com/all-cases)))
       (reduce into {})))

(defn last-nn-day
  "E.g.:
  (last-nn-day (pred-fn sk))
  (last-nn-day (fn [_] true))"
  [pred]
  (eval-fun get-last pred))

(defn last-8-reports
  "E.g.:
  (last-8-reports {:pred-q '(pred-fn sk) :pred (pred-fn sk)})
  (last-8-reports {:pred-q '(fn [_] true) :pred (fn [_] true)})"
  [{:keys [pred] :as prm}]
  (eval-fun (fn [coll] (take-last 8 coll)) pred))

(defn pred-fn [country-code]
  (fn [loc]
    (condp = country-code
      ccc/worldwide-2-country-code
      true

      ccc/default-2-country-code
      ;; XX comes from the service
      (= ccc/xx (:country_code loc))

      (= country-code (:country_code loc)))))

(defn stats-per-country [{:keys [cc] :as prm}]
  (conj
   (last-nn-day (pred-fn cc))
   #_{:cn (ccr/country-name-aliased cc)}
   {:cc cc}))

(defn stats-all-affected-countries [prm]
  (map (fn [cc]
         (stats-per-country (assoc prm :cc cc)))
       (all-affected-country-codes-memo)))

(def stats-all-affected-countries-memo
  #_stats-all-affected-countries
  (com/memo-ttl stats-all-affected-countries))
