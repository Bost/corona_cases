;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.api.owid)

(ns corona.api.owid
  (:require clojure.stacktrace
            [corona.api.cache :as cache]
            [corona.common :as com]
            [corona.countries :as ccr]
            [corona.country-codes :as ccc]
            [corona.macro :refer [defn-fun-id debugf]])
  (:import java.text.SimpleDateFormat
           java.util.TimeZone))

(defn-fun-id json-data "" []
  (let [ks [:owid :json]]
    (when-not (get-in @cache/cache ks)
      (debugf "cache-miss %s" ks)
      #_(clojure.stacktrace/print-stack-trace (Exception.)))
    (cache/from-cache! (fn [] (com/get-json com/json-api-owid)) ks)))

#_(defn population-cnt [ccode])

(def ^SimpleDateFormat date-format
  "SimpleDateFormat"
  (let [sdf (new SimpleDateFormat "yyyy-MM-dd")]
    (.setTimeZone sdf (TimeZone/getDefault))
    sdf))

(defn date [rd] (.parse date-format rd))

(defn raw-dates [json]
  ((comp
    #_(partial take-last 4)
    (partial map :date)
    (fn [m] (get-in m [:ITA :data])))
   json))

(defn dates [raw-dates]
  ((comp
    #_(partial take-last 4)
    (partial map date))
   raw-dates))

(defn convert
  "Convert to month/day/year:
  (convert \"2021-01-20\") -> :1/20/21
  (convert \"2021-02-04\") -> :2/4/21"
  [s]
  #_(com/fmt-vaccination-date (.parse date-format s))
  ((comp
    keyword
    com/fmt-vaccination-date
    (fn [s] (.parse date-format s)))
   s))

(defn vaccination [json ccode]
  ((comp
    (partial reduce merge)
    (partial map (fn [m]
                   ((comp
                     (partial apply hash-map)
                     (juxt :date :total_vaccinations)
                     (fn [r] (update-in r [:date] convert))
                     (fn [r] (update-in r [:total_vaccinations] (fn [v] (if v (int v) 0))))
                     (partial select-keys m))
                    [:date :total_vaccinations])))
    #_(partial take-last 4)
    (fn [m]
      (let [kw-ccode (keyword (ccc/country-code-3-letter ccode))]
        (if-let [ccode-map (get m kw-ccode)]
          (get ccode-map :data)
          (let [default
                #_{kw-ccode {:data (mapv (fn [rd] {:date rd}) raw-dates)}}
                (mapv (fn [rd] {:date rd}) (raw-dates json))]
            #_(errorf "ccode %s not found in json; using %s"
                      ccode
                      default)
            default)))))
   json))

(defn vaccination-data [{:keys [raw-dates-v1 json-owid]}]
  ((comp
    (partial hash-map :vaccinated)
    (partial hash-map :locations)
    (partial map
             (comp
              (partial apply merge)
              (juxt
               (comp (partial hash-map :country)
                     ccr/country-name-aliased)
               (partial hash-map :country_code)
               (comp
                (partial hash-map :history)
                (partial merge (zipmap raw-dates-v1 (cycle [0])))
                (partial vaccination json-owid)
                #_(partial zipmap raw-dates-v1)
                #_cycle
                #_(fn [_] [245 2350 9822 18554 21775 22411 27371 32293 43317 49488 57226 59930 60302 71982 72060]))))))
   ccc/all-country-codes))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.api.owid)
