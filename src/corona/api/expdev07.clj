(printf "Current-ns [%s] loading %s\n" *ns* 'corona.api.expdev07)

(ns corona.api.expdev07
  (:require
   [corona.common :as com]
   [corona.countries :as ccr]
   [corona.country-codes :as ccc :refer :all]
   [utils.core :as utc :refer [in?]]
   [taoensso.timbre :as timbre :refer [
                                       ;; debug debugf info infof warn
                                       errorf
                                       #_fatalf
                                       ]]
   [clojure.spec.alpha :as s]
   #_[clojure.inspector :refer :all]
   )
  (:import java.text.SimpleDateFormat))

(def ^:const url (format "http://%s/all" com/api-server))

(defonce cache (atom {}))

(defn cache!
  "Also return the cached value for further consumption."
  [calc-data-fn ks]
  (let [data (calc-data-fn)]
    (swap! cache update-in ks (fn [_] data))
    data))

(defn from-cache [ks calc-data-fn]
  #_(debugf "[from-cache] accessing %s" ks)
  (if-let [v (get-in @cache ks)]
    v
    (cache! calc-data-fn ks)))

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

(defn json-data []
  (from-cache [:json] (fn [] (com/get-json url))))

(defn calc-raw-dates-fn []
  (->> (transduce
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
                 (:confirmed (json-data)))))))
       #_(take-last 8)))

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
  (from-cache [:raw-dates] calc-raw-dates-fn))

(defn population-cnt [country-code]
  (or (get ccr/population country-code)
      ;; world population is the sum
      ;; 7792480951
      (let [default-population 0]
        (errorf "population nr unknown; country-code: %s; using %s"
                country-code
                default-population)
        default-population)))

(defn calc-dates-fn []
  #_(debugf "calc-dates-fn")
  (let [sdf (new SimpleDateFormat "MM/dd/yy")]
    (map (fn [rd] (.parse sdf (keyname rd)))
         (raw-dates))))

(defn dates []
  (from-cache [:dates] calc-dates-fn))

(defn calc-data-with-pop-fn []
  (conj
   (->> (keys (json-data))
        (map (fn [case-kw-full-name]
               {case-kw-full-name
                (let [case-m (get (json-data) case-kw-full-name)]
                  (if (contains? case-m :locations)
                    (update-in
                     case-m [:locations]
                     (fn [locs]
                       #_(debugf "%s" (type locs))
                       (->> locs
                            #_(map (fn [m] (select-keys m [:country :country_code :history])))
                            (filter (fn [{:keys [country_code]}]
                                      #_true
                                      (in? ccc/all-country-codes country_code)))
                            (map (fn [m]
                                   #_(debugf "%s" (keys m))
                                   (update-in m [:history]
                                              (fn [history]
                                                (->> history
                                                     (filter (fn [[raw-date _]]
                                                               #_true
                                                               (in? (raw-dates) raw-date)))
                                                     (into {}))))))
                            (into []))))
                    case-m))}))
        (into {}))
   {:population
      {:locations
       (let [the-dates (raw-dates)]
         (map (fn [country-code]
                {
                 :country (ccr/country-name-aliased country-code)
                 :country_code country-code
                 :history
                 ;; {:1/23/20 1e6 ;; start number
                 ;;    ;; other days - calc diff
                 ;; }
                 (let [pop-cnt (population-cnt country-code)]
                   (zipmap the-dates (repeat pop-cnt)))})
              ccc/all-country-codes))}}))

(defn data-with-pop
  "Data with population numbers."
  []
  (from-cache [:data-with-pop] calc-data-with-pop-fn))

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

(defn calc-sums-for-case-fn [case-kw pred-fn]
  #_(debugf "calc-sums-for-case-fn")
  (let [locations (filter pred-fn
                          ((comp :locations case-kw)
                           (data-with-pop)))]
    (map (fn [raw-date]
           (sums-for-date case-kw locations raw-date))
         (raw-dates))))

(defn sums-for-case
  "Return sums for a given `case-kw` calculated for every single day."
  [case-kw {:keys [cc pred]}]
  ;; ignore predicate for the moment
  (from-cache [:sums case-kw cc] (fn [] (calc-sums-for-case-fn case-kw pred))))

;; TODO reload only the latest N reports. e.g. try one week

;; TODO country-plots are <= 45kB; world-plots are <= 68kb
;; 8 top-10-plots, 1 world-plot + 252 countries;
;; (+ (* 2 (+ 1 252)) (* 252 45) (* 68 8)) => 12390 kB ~12.5MB
;; TODO listing-message-size: ~?kB

(defn calc-case-counts-report-by-report-fn [pred-hm]
  #_(debugf "calc-case-counts-report-by-report-fn")
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
                                       pcrd))))))))

(defn case-counts-report-by-report
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
  (from-cache [:cnts (keyword cc)] (fn [] (calc-case-counts-report-by-report-fn pred-hm))))

(s/def ::fun clojure.core/fn?)
(s/def ::pred-fn (s/or :nil nil? :fn clojure.core/fn?))

(defn eval-fun
  [fun pred-hm]
  #_{:pre [(s/valid? ::fun fun)
         (s/valid? ::pred-fn pred)]}
  ;; (debugf "dates %s" (count (dates)))
  (into {:f (fun (dates))}
        (map (fn [[k v]] {k (fun v)})
             (case-counts-report-by-report pred-hm))))

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

(defn calc-stats-countries-fn []
  #_(debugf "calc-stats-countries-fn")
  (map (fn [cc] (conj {:cc cc}
                      (last-nn-day (create-pred-hm cc))))
       ccc/all-country-codes))

(defn stats-countries []
  (from-cache [:stats] calc-stats-countries-fn))

(defn deep-merge
  "Recursively merges maps. TODO see https://github.com/weavejester/medley
Thanks to https://gist.github.com/danielpcox/c70a8aa2c36766200a95#gistcomment-2711849"
  [& maps]
  (apply merge-with (fn [& args]
                      (if (every? map? args)
                        (apply deep-merge args)
                        (last args)))
         maps))

(defn rank-for-case [rank-kw]
  (map-indexed
   (fn [idx hm]
     (update-in (select-keys hm [:cc]) [:rank rank-kw]
                ;; inc - ranking starts from 1, not from 0
                (fn [_] (inc idx))))
   (sort-by rank-kw >
            (stats-countries))))

(defn calc-all-rankings-fn
  "TODO verify ranking for one and zero countries"
  []
  #_(debugf "calc-all-rankings-fn")
  (map (fn [affected-cc]
         (apply deep-merge
                (reduce into []
                        (map (fn [ranking]
                               (filter (fn [{:keys [cc]}]
                                         (= cc affected-cc))
                                       ranking))
                             (utc/transpose (map rank-for-case
                                                 com/ranking-cases))))))
       ccc/all-country-codes))

(defn all-rankings []
  (from-cache [:rankings] calc-all-rankings-fn))
