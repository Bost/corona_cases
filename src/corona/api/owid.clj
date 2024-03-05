;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.api.owid)

(ns corona.api.owid
  (:require
   [clojure.stacktrace]
   [corona.api.cache :as cache]
   [corona.common :as com]
   [corona.countries :as ccr]
   [corona.country-codes :as ccc]
   [corona.telemetry :refer [debugf defn-fun-id]]
   [corona.utils.core :as cutc]
   [utils.core :as utc]))

(defn-fun-id json-data "" []
  (com/get-json com/json-api-owid))

(defn- vaccination
  "
  (vaccination json desired-dates-hm ccode)
  "
  [json desired-dates-hm ccode]
  ((comp
    (partial reduce merge)
    (partial map (fn [m]
                   ((comp
                     (partial apply hash-map)
                     (juxt :date :total_vaccinations)
                     (partial cutc/update-in :ks [:date]
                              ;; Convert to month/day/year:
                              ;; "2021-01-20" -> :1/20/21
                              ;; "2021-02-04" -> :2/4/21
                              :f (comp
                                  keyword
                                  com/fmt-vaccination-date
                                  (fn [s] (.parse com/date-format s))))
                     (partial cutc/update-in :ks [:total_vaccinations]
                              ;; can't use (int ...) conversion.
                              ;; The `v` is > Integer.MAX_VALUE
                              :f (fn [v] (if v (long v) 0)))
                     (partial select-keys m))
                    [:date :total_vaccinations])))
    (fn [m]
      (if-let [ccode-map ((comp
                           (partial get m)
                           keyword
                           ccc/country-code-3-letter)
                          ccode)]
        (get ccode-map :data)
        desired-dates-hm)))
   json))

(defn vaccination-data
  "
  (vaccination-data raw-dates-v1 desired-dates-v1 json-owid)
  "
  [raw-dates-v1 desired-dates-v1 json-owid]
  (let [all-dates-owid ((comp
                         #_(partial map (partial cutc/select-keys :ks [:date]))
                         (partial map (partial cutc/get-in :ks [:date]))
                         (partial cutc/get-in :ks [:ITA :data]))
                        json-owid)

        desired-dates-owid (filter (fn [elem] (utc/in? all-dates-owid elem)) desired-dates-v1)
        desired-dates-hm (map hash-map (repeat :date) desired-dates-owid)]
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
                  (partial vaccination json-owid desired-dates-hm))))))
     ccc/relevant-country-codes)))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.api.owid)
