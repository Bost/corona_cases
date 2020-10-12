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
   [clojure.core.memoize :as memo]
   )
  (:import java.text.SimpleDateFormat))

;; (debugf "Loading namespace %s" *ns*)

(def ^:const url (format "http://%s/all" com/api-server))

(defonce cache (atom {}))

(defn request! []
  (doall
   (let [tbeg (System/currentTimeMillis)]
     (let [response (com/get-json url)]
       (swap! cache (fn [_] response))
       (debugf "[request!] %s chars cached in %s ms"
               (count (str @cache)) (- (System/currentTimeMillis) tbeg))))))

(defn reset-cache! []
  (swap! cache (fn [_] {}))
  (request!))

(defn cache! [data ks]
  (swap! cache update-in ks (fn [_] data))
  #_(debugf "%s elems cached in %s" (count data) ks)
  data)

(defn data-memo [] @cache)

(defn keyname [key] (str (namespace key) "/" (name key)))

(defn left-pad [s] (com/left-pad s 2))

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

(defn raw-dates-calc []
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
   #_[(keyword "2/22/20") (keyword "2/2/20")]
   (keys (:history
          (last
           (:locations
            (:confirmed (data-memo))))))))

(defn raw-dates
  "Size:
  (apply + (map (fn [rd] (count (str rd))) (get-in @cache [:raw-dates])))
  ;; 2042 chars

  (time ...) measurement:
  (apply + rd-calc)  ;; no cache used
  ;; 3250.596 msecs
  (count rd-calc)
  ;; 1010 items

  (apply + rd-cached) ;; read from cache
  ;; 4.040 msecs
  (count rd-cached)
  ;; 1010 items"
  []
  (let [ks [:raw-dates]]
    (if-let [rd (get-in @cache ks)]
      rd
      (cache! (raw-dates-calc) ks))))

(defn population-cnt [country-code]
  (or (get ccr/population country-code)
      ;; world population is the sum
      ;; 7792480951
      (let [default-population 0]
        (error (format "population nr unknown; country-code: %s; using %s"
                       country-code
                       default-population))
        default-population)))

(defn dates []
  (let [ks [:dates]]
    (if-let [d (get-in @cache ks)]
      d
      (let [d
            (let [sdf (new SimpleDateFormat "MM/dd/yy")]
              (map (fn [rd] (.parse sdf (keyname rd)))
                   (raw-dates)))]
        (cache! d ks)))))

(defn data-with-pop
  "Data with population numbers"
  []
  (let [ks [:data-with-pop]]
    (if-let [dwp (get-in @cache ks)]
      dwp
      (let [dwp
            (conj
             (data-memo)
             {:population
              {:locations
               (let [the-dates (raw-dates)]
                 (mapv (fn [country-code]
                         {
                          :country (ccr/country-name-aliased country-code)
                          :country_code country-code
                          :history
                          ;; {:1/23/20 1e6 ;; start number
                          ;;    ;; other days - calc diff
                          ;; }
                          (let [pop-cnt (population-cnt country-code)]
                            (zipmap the-dates (repeat pop-cnt)))})
                       ccc/all-country-codes))}})]
        (cache! dwp ks)))))

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
  "
  [case-kw {:keys [cc pred]}]
  ;; ignore predicate for the moment
  (if-let [sfc (get-in @cache [:sums case-kw cc])]
    sfc
    (let [sfc
          (let [locations (filter pred
                                  ((comp :locations case-kw)
                                   (data-with-pop)))]
            (map (fn [raw-date]
                   (sums-for-date case-kw locations raw-date))
                 (raw-dates)))]
      (cache! sfc [:sums case-kw]))))

;; TODO reload only the latest N reports. e.g. try one week

;; TODO country-plots are <= 45kB; world-plots are <= 68kb
;; 8 top-10-plots, 1 world-plot + 252 countries;
;; (+ (* 2 (+ 1 252)) (* 252 45) (* 68 8)) => 12390 kB ~12.5MB
;; TODO listing-message-size: ~?kB
(defn get-counts-memo
  "Returns a hash-map containing case-counts day-by-day. E.g.:
  ;; => ;; last 5 values
  {
   :p (... 5456362 5456362 5456362 5456362 5456362)
   :c (...    2566    2596    2599    2615    2690)
   :r (...    1861    1864    1866    1874    1884)
   :d (...      31      31      31      31      31)
   :i (...     674     701     702     710     775)}

  (get-counts (fn [_] true))
  "
  [{:keys [cc pred] :as pred-hm}]
  ;; ignore predicate for the moment
  #_(debugf "cc %s" cc)
  (let [ks [:cnts (keyword cc)]]
    (if-let [cnts (get-in @cache ks)]
      cnts
      (let [cnts
            (let [pcrd (mapv (fn [case-kw] (sums-for-case case-kw pred-hm))
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
                                                pcrd)))))))]
        (cache! cnts ks)))))

(s/def ::fun clojure.core/fn?)
(s/def ::pred-fn (s/or :nil nil? :fn clojure.core/fn?))

(defn eval-fun
  [fun pred-hm]
  #_{:pre [(s/valid? ::fun fun)
         (s/valid? ::pred-fn pred)]}
  ;; (debugf "dates %s" (count (dates)))
  (into {:f (fun (dates))}
        (map (fn [[k v]] {k (fun v)})
             (get-counts-memo pred-hm))))

(defn delta
  [pred-hm]
  (->> [get-prev get-last]
       (map (fn [fun]
              (eval-fun fun pred-hm)))
       (apply (fn [prv lst]
                (map (fn [k]
                       {k (- (k lst) (k prv))})
                     com/all-cases)))
       (reduce into {})))

(defn last-nn-day
  [pred-hm]
  (eval-fun get-last pred-hm))

(defn last-nn-8-reports
  [pred-hm]
  (eval-fun (fn [coll] (take-last 8 coll)) pred-hm))

(defn last-8-reports
  [pred-hm]
  (eval-fun (fn [coll] (take-last 8 coll)) pred-hm))

(defn old-pred-fn [country-code]
  (fn [loc]
    (condp = country-code
      ccc/worldwide-2-country-code
      true

      ccc/default-2-country-code
      ;; XX comes from the service
      (= ccc/xx (:country_code loc))

      (= country-code (:country_code loc)))))

(defn create-pred-hm [country-code]
  {:cc country-code
   :pred (old-pred-fn country-code)
   })

(defn stats-countries
  []
  (if-let [stats (get-in @cache [:stats])]
    stats
    (let [stats
          (map (fn [cc] (conj {:cc cc}
                             (last-nn-day (create-pred-hm cc))))
               ccc/all-country-codes)]
      (cache! stats [:stats]))))
