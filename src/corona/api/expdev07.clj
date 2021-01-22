;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.api.expdev07)

(ns corona.api.expdev07
  (:require
   [corona.common :as com]
   [corona.countries :as ccr]
   [corona.country-codes :as ccc]
   [corona.api.vaccination :as vac]
   [utils.core :as utc]
   [taoensso.timbre :as timbre :refer [debugf infof
                                       warnf
                                       errorf
                                       #_fatalf]]
   [corona.api.cache :as cache])
  (:import java.text.SimpleDateFormat))

;; (set! *warn-on-reflection* true)

(def ^:const url (format "http://%s/all" com/json-api-server))

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
  (let [ks [:json :v1]]
    (if (get-in @cache/cache ks)
      (debugf "[%s] cache-hit %s" "json-data" ks)
      (debugf "[%s] cache-miss %s" "json-data" ks))
    (cache/from-cache! (fn [] (com/get-json url)) ks)))

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

;; TODO raw-dates should have json-data as a param
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
  [json]
  (cache/from-cache!
   (fn []
     (transduce xform-raw-dates
                conj []
                ((comp
                  ;; at least 2 values needed to calc difference
                  #_(partial take-last 4)
                  keys :history last :locations :confirmed)
                 json)))
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

(defn dates [json]
  ((comp
    (fn [val] (cache/from-cache! (fn [] val) [:dates]))
    (partial map date))
   (raw-dates json)))

(defn population-data [raw-dates]
  ((comp
    (partial hash-map :population)
    (partial hash-map :locations)
    (partial map
             (comp
              (partial apply merge)
              (juxt (comp (partial hash-map :country)
                          ccr/country-name-aliased)
                    (partial hash-map :country_code)
                    (comp (partial hash-map :history)
                          (partial zipmap raw-dates)
                          repeat
                          population-cnt)))))
   ccc/all-country-codes))

(defn vaccination-data [raw-dates]
  ((comp
    (partial hash-map :vaccinated)
    (partial hash-map :locations)
    (partial map
             (comp
              (partial apply merge)
              (juxt (comp (partial hash-map :country)
                          ccr/country-name-aliased)
                    (partial hash-map :country_code)
                    (comp (partial hash-map :history)
                          (partial zipmap raw-dates)
                          repeat
                          #_(fn [n] (int (/ n 2)))
                          (fn [n] (int (/ n 10)))
                          population-cnt)))))
   ccc/all-country-codes))

(defn corona-data [json]
  ((comp
    (partial into {})
    (partial map
             (fn [case-kw-full-name]
               ((comp
                 (partial hash-map case-kw-full-name)
                 (fn [case-m]
                   (if (contains? case-m :locations)
                     (update-in
                      case-m [:locations]
                      (comp
                       (partial into [])
                       (partial map (fn [m]
                                      (update-in
                                       m [:history]
                                       (comp
                                        (partial into {})
                                        (partial filter
                                                 (comp
                                                  (partial utc/in? (raw-dates json))
                                                  first))))))
                       ;; here the country_code keyword comes from the json
                       (partial filter
                                (comp
                                 (partial utc/in? ccc/all-country-codes)
                                 :country_code))))
                     case-m))
                 (partial get json))
                case-kw-full-name)))
    keys)
   json))

(defn calc-data-with-pop [json]
  (conj (corona-data json)
        (vac/vaccination-data {:raw-dates-v1
                               (raw-dates (json-data))
                               :raw-dates-owid
                               (vac/raw-dates (vac/json-data))})
        (population-data (raw-dates json))))

(defn data-with-pop
  "Data with population numbers."
  []
  (cache/from-cache! (fn [] (calc-data-with-pop (json-data)))
                     [:data-with-pop]))

(defn get-last [coll] (first (take-last 1 coll)))

(defn get-prev [coll] (first (take-last 2 coll)))

;; TODO reload only the latest N reports. e.g. try one week
(defn sums-for-case
  "Return sums for a given `case-kw` calculated for every single report."
  [case-kw {:keys [ccode pred-fun]}]
  ;; ignore predicate for the moment
  (cache/from-cache!
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
            (raw-dates (json-data)))))
   [:sums case-kw ccode]))

(defn calc-case-counts-report-by-report [pred-hm]
  (let [vpcrd (mapv (fn [case-kw] (sums-for-case case-kw pred-hm))
                    [:vaccinated :population :confirmed :recovered :deaths])

        ;; pre-calculate active numbers - needed for com/calc-rate-active
        vpcrda
        (apply
         conj vpcrd
         (mapv (fn [fun] (apply mapv (fn [_ _ c r d] (fun c r d)) vpcrd))
               [com/calculate-activ]))]
    (zipmap com/all-cases
            (apply
             conj vpcrda
             (->> [(com/calculate-cases-per-100k :v)
                   (com/calculate-cases-per-100k :a)
                   (com/calculate-cases-per-100k :r)
                   (com/calculate-cases-per-100k :d)
                   (com/calculate-cases-per-100k :c)
                   (com/calc-rate :v)
                   (com/calc-rate :a)
                   (com/calc-rate :r)
                   (com/calc-rate :d)
                   (com/calc-rate :c)]
                  (mapv (fn [fun] (apply mapv (fn [v p c r d a]
                                                (->> [v p c r d a]
                                                     (zipmap [:v :p :c :r :d :a])
                                                     (fun)))
                                         vpcrda))))))))

(defn case-counts-report-by-report
  "Returns a hash-map containing case-counts report-by-report. E.g.:
  ;; => ;; last 5 values
  {
   :v (...  545636  545636  545636  545636  545636)
   :p (... 5456362 5456362 5456362 5456362 5456362)
   :c (...    2566    2596    2599    2615    2690)
   :r (...    1861    1864    1866    1874    1884)
   :d (...      31      31      31      31      31)
   :a (...     674     701     702     710     775)}

  (get-counts (fn [_] true))
  "
  [{:keys [ccode #_pred-fun] :as pred-hm}]
  ;; ignore pred-fun for the moment
  (cache/from-cache! (fn [] (calc-case-counts-report-by-report pred-hm))
                     [:cnts (keyword ccode)]))

(defn eval-fun
  [fun pred-hm json]
  (into {:t (fun (dates json))}
        (map (fn [[k v]] {k (fun v)})
             (case-counts-report-by-report pred-hm))))

(defn delta
  [pred-hm json]
  (->> [get-prev get-last]
       (map (fn [fun]
              (eval-fun fun pred-hm json)))
       (apply (fn [prv lst]
                (map (fn [k]
                       {k (- (k lst) (k prv))})
                     com/all-cases)))
       (reduce into {}))
  #_((comp
      (partial reduce into {})
      (partial apply (fn [prv lst] (map (fn [k] {k (- (k lst) (k prv))}) com/all-cases)))
      (partial map (fn [fun] (eval-fun fun pred-hm))))
     [get-prev get-last]))

(defn last-report [pred-hm json]
  (let [ret (eval-fun get-last pred-hm json)]
    #_(debugf "[%s] ret %s" "last-report" (select-keys ret [:a-rate :v-rate]))
    ret))

(defn last-8-reports [pred-hm json] (eval-fun (partial take-last 8) pred-hm json))

(defn create-pred-hm [ccode]
  {:ccode ccode
   :pred-fun (fn [loc]
               (condp = ccode
                 ccc/worldwide-2-country-code
                 true

                 ccc/default-2-country-code
                 ;; XX comes from the service
                 (= "XX" (:country_code loc))

                 (= ccode (:country_code loc))))})

(defn calc-stats-countries [json]
  (map (fn [ccode] (conj {:ccode ccode}
                        (last-report (create-pred-hm ccode) json)))
       ccc/all-country-codes))

(defn stats-countries [json] (cache/from-cache! (fn [] (calc-stats-countries json)) [:stats]))

(defn rank-for-case [rank-kw json]
  (map-indexed
   (fn [idx hm]
     (update-in (select-keys hm [:ccode]) [:rank rank-kw]
                ;; inc - ranking starts from 1, not from 0
                (fn [_] (inc idx))))
   (sort-by rank-kw >
            ;; TODO sets and set operations should be used clojure.set/difference
            (remove (fn [hm] (= (:ccode hm) "ZZ")) (stats-countries json)))))

(defn calc-all-rankings
  "TODO verify ranking for one and zero countries"
  [json]
  (map (fn [ccode]
         (apply utc/deep-merge
                (reduce into []
                        (map (fn [ranking]
                               (filter (fn [hm] (= (:ccode hm) ccode)) ranking))
                             (utc/transpose (map (fn [case-kw] (rank-for-case case-kw json))
                                                 com/ranking-cases))))))
       ;; TODO sets and set operations should be used clojure.set/difference
       (remove (fn [ccode] (= ccode "ZZ")) ccc/all-country-codes)))

(defn all-rankings [json] (cache/from-cache! (fn [] (calc-all-rankings json)) [:rankings]))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.api.expdev07)
