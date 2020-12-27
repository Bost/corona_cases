(printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.api.expdev07)

(ns corona.api.expdev07
  (:require
   [corona.common :as com]
   [corona.countries :as ccr]
   [corona.country-codes :as ccc]
   [utils.core :as utc]
   [taoensso.timbre :as timbre :refer [
                                       debugf infof
                                       warnf
                                       errorf
                                       #_fatalf
                                       ]]
   [clojure.spec.alpha :as spec]
   #_[clojure.string :as cstr]
   #_[clojure.inspector :refer :all]
   )
  (:import java.text.SimpleDateFormat))

;; (set! *warn-on-reflection* true)

(def ^:const url (format "http://%s/all" com/json-api-server))

(defonce cache (atom {}))

(spec/def ::fun clojure.core/fn?)
(spec/def ::pred-fn (spec/or :nil nil? :fn clojure.core/fn?))

(defonce cnt (atom 0))

(defn cache!
  "Also return the cached value for further consumption.
  First param must be a function in order to have lazy evaluation."
  [fun ks]
  {:pre [(spec/valid? ::fun fun)]}
  (let [msg-id "cache!"]
    #_(debugf "[%s] %s Computing %s ..." msg-id @cnt fun)
    #_(swap! cnt inc)
    (let [data (fun)]
      ;; (debugf "[%s] %s Computing ... done." msg-id fun)
      (swap! cache update-in ks (fn [_] data))
      data)))

(defn from-cache!
  [fun ks]
  {:pre [(spec/valid? ::fun fun)]}
  ;; (debugf "[from-cache!] accessing %s" ks)
  (if-let [v (get-in @cache ks)]
    v
    (cache! fun ks)))

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
  (from-cache! (fn [] (com/get-json url)) [:json]))

(def xform-raw-dates
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
                      [m d y]))))))

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
  (from-cache! (fn []
                 (transduce xform-raw-dates
                            conj []
                            ((comp
                              ;; at least 2 values needed to calc difference
                              #_(partial take-last 4)
                              keys :history last :locations :confirmed)
                             (json-data))))
               [:raw-dates]))

(defn population-cnt [ccode]
  (or (get ccr/population ccode)
      ;; world population is the sum
      ;; 7792480951
      (let [default-population 0]
        (errorf "population nr unknown; ccode: %s; using %s"
                ccode
                default-population)
        default-population)))

(def date-format (new SimpleDateFormat "MM/dd/yy"))

(defn date [rd] (.parse date-format (keyname rd)))

(defn dates [] (from-cache! (fn [] (map date (raw-dates))) [:dates]))

(defn calc-data-with-pop []
  (let [json (json-data)]
    (conj
     (->> (keys json)
          (map (fn [case-kw-full-name]
                 {case-kw-full-name
                  (let [case-m (get json case-kw-full-name)]
                    (if (contains? case-m :locations)
                      (update-in
                       case-m [:locations]
                       (fn [locs]
                         (->> locs
                              ;; here the country_code keyword comes from the json
                              (filter (fn [{:keys [country_code]}]
                                        (utc/in? ccc/all-country-codes country_code)))
                              (map (fn [m]
                                     (update-in m [:history]
                                                (fn [history]
                                                  (->> history
                                                       (filter (fn [[raw-date _]]
                                                                 (utc/in? (raw-dates) raw-date)))
                                                       (into {}))))))
                              (into []))))
                      case-m))}))
          (into {}))
     {:population
      {:locations
       (let [the-dates (raw-dates)]
         (map (fn [ccode]
                {:country (ccr/country-name-aliased ccode)
                 :country_code ccode
                 :history
                 ;; {:1/23/20 1e6 ;; start number
                 ;;    ;; other reports - calc diff
                 ;; }
                 (zipmap the-dates ((comp repeat population-cnt) ccode))})
              ccc/all-country-codes))}})))

(defn data-with-pop
  "Data with population numbers."
  []
  (from-cache! calc-data-with-pop [:data-with-pop]))

(defn get-last [coll] (first (take-last 1 coll)))

(defn get-prev [coll] (first (take-last 2 coll)))

;; TODO reload only the latest N reports. e.g. try one week
(defn sums-for-case
  "Return sums for a given `case-kw` calculated for every single report."
  [case-kw {:keys [ccode pred-fun]}]
  ;; ignore predicate for the moment
  (from-cache!
   (fn []
     (let [locations (filter pred-fun
                             ((comp :locations case-kw) (data-with-pop)))]
       ;; (debugf "locations %s" (cstr/join " " locations))
       (map (fn [raw-date]
              (if (and (empty? locations)
                       (= :recovered case-kw))
                (let [default 0]
                  #_
                  (warnf "ccode %s %s %s missing locations; defaults to %s"
                         ccode case-kw (com/fmt-date-dbg (date raw-date)) default)
                  default)
                (transduce
                 (map (comp
                       ;; https://github.com/ExpDev07/coronavirus-tracker-api/issues/41
                       ;; str com/read-number
                       (fn [history] (get history raw-date))
                       :history))
                 + 0
                 locations)))
            (raw-dates))))
   [:sums case-kw ccode]))

(defn calc-case-counts-report-by-report [pred-hm]
  (let [pcrd (mapv (fn [case-kw] (sums-for-case case-kw pred-hm))
                   [:population :confirmed :recovered :deaths])

        ;; pre-calculate active numbers - needed for com/calc-rate-active
        pcrda
        (apply
         conj pcrd
         (->> [com/calculate-active]
              (mapv (fn [fun] (apply mapv (fn [p c r d]
                                            (->> [p c r d]
                                                 (zipmap [:p :c :r :d])
                                                 (fun)))
                                     pcrd)))))]
    (zipmap com/all-cases
            (apply
             conj pcrda
             (->> [(com/calculate-cases-per-100k :a)
                   (com/calculate-cases-per-100k :r)
                   (com/calculate-cases-per-100k :d)
                   (com/calculate-cases-per-100k :c)
                   com/calc-rate-active
                   com/calc-rate-recovered
                   com/calc-rate-deaths
                   com/calc-rate-closed
                   ]
                  (mapv (fn [fun] (apply mapv (fn [p c r d a]
                                               (->> [p c r d a]
                                                    (zipmap [:p :c :r :d :a])
                                                    (fun)))
                                        pcrda))))))))

(defn case-counts-report-by-report
  "Returns a hash-map containing case-counts report-by-report. E.g.:
  ;; => ;; last 5 values
  {
   :p (... 5456362 5456362 5456362 5456362 5456362)
   :c (...    2566    2596    2599    2615    2690)
   :r (...    1861    1864    1866    1874    1884)
   :d (...      31      31      31      31      31)
   :a (...     674     701     702     710     775)}

  (get-counts (fn [_] true))
  "
  [{:keys [ccode #_pred-fun] :as pred-hm}]
  ;; ignore pred-fun for the moment
  (from-cache! (fn [] (calc-case-counts-report-by-report pred-hm))
               [:cnts (keyword ccode)]))

(defn eval-fun
  [fun pred-hm]
  (into {:t (fun (dates))}
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

(defn last-report [pred-hm] (eval-fun get-last pred-hm))

(defn last-8-reports [pred-hm] (eval-fun (partial take-last 8) pred-hm))

(defn create-pred-hm [ccode]
  {:ccode ccode
   :pred-fun (fn [loc]
               (condp = ccode
                 ccc/worldwide-2-country-code
                 true

                 ccc/default-2-country-code
                 ;; XX comes from the service
                 (= ccc/xx (:country_code loc))

                 (= ccode (:country_code loc))))})

(defn calc-stats-countries []
  (map (fn [ccode] (conj {:ccode ccode}
                        (last-report (create-pred-hm ccode))))
       ccc/all-country-codes))

(defn stats-countries [] (from-cache! calc-stats-countries [:stats]))

(defn rank-for-case [rank-kw]
  (map-indexed
   (fn [idx hm]
     (update-in (select-keys hm [:ccode]) [:rank rank-kw]
                ;; inc - ranking starts from 1, not from 0
                (fn [_] (inc idx))))
   (sort-by rank-kw >
            ;; TODO sets and set operations should be used clojure.set/difference
            (remove (fn [hm] (= (:ccode hm) ccc/zz)) (stats-countries)))))

(defn calc-all-rankings
  "TODO verify ranking for one and zero countries"
  []
  (map (fn [ccode]
         (apply utc/deep-merge
                (reduce into []
                        (map (fn [ranking]
                               (filter (fn [hm] (= (:ccode hm) ccode)) ranking))
                             (utc/transpose (map rank-for-case
                                                 com/ranking-cases))))))
       ;; TODO sets and set operations should be used clojure.set/difference
       (remove (fn [ccode] (= ccode ccc/zz)) ccc/all-country-codes)))

(defn all-rankings [] (from-cache! calc-all-rankings [:rankings]))

(printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.api.expdev07)
