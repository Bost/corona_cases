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
  (com/get-json com/json-api-owid))

(def ^SimpleDateFormat date-format
  "SimpleDateFormat"
  (let [sdf (new SimpleDateFormat "yyyy-MM-dd")]
    (.setTimeZone sdf (TimeZone/getDefault))
    sdf))

(defn vaccination [json ccode]
  ((comp
    (partial reduce merge)
    (partial map (fn [m]
                   ((comp
                     (partial apply hash-map)
                     (juxt :date :total_vaccinations)
                     (fn [r] (update-in r [:date]
                                       ;; Convert to month/day/year:
                                       ;; "2021-01-20" -> :1/20/21
                                       ;; "2021-02-04" -> :2/4/21
                                       (comp
                                        keyword
                                        com/fmt-vaccination-date
                                        (fn [s] (.parse date-format s)))))
                     (fn [r] (update-in r [:total_vaccinations]
                                       (fn [v] (if v (int v) 0))))
                     (partial select-keys m))
                    [:date :total_vaccinations])))
    (fn [m]
      (if-let [ccode-map ((comp
                           (partial get m)
                           keyword
                           ccc/country-code-3-letter)
                          ccode)]
        (get ccode-map :data)
        ;; to obtain all dates pick any country (e.g. Italy) and read its dates
        ((comp
          (partial map (fn [m] (select-keys m [:date])))
          (fn [m] (get-in m [:ITA :data])))
         m))))
   json))

(defn vaccination-data [raw-dates-v1 json-owid]
  ((comp
    (partial hash-map :vaccinated)
    (partial map
             (comp
              (partial apply merge)
              (juxt
               (comp
                (partial hash-map :country)
                ccr/country-name-aliased)
               (partial hash-map :country_code)
               (comp
                (partial hash-map :history)
                (partial merge (zipmap raw-dates-v1 (cycle [0])))
                (partial vaccination json-owid))))))
   ccc/relevant-country-codes))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.api.owid)
