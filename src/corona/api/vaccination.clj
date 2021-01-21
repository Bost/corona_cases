;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.api.vaccination)

(ns corona.api.vaccination
  (:require
   [corona.common :as com]
   [corona.api.cache :as cache]
   [corona.country-codes :as ccc]
   [corona.countries :as ccr])
  (:import java.text.SimpleDateFormat))

(def ^:const url
  "https://covid.ourworldindata.org/data/owid-covid-data.json")

(defn json-data []
  (cache/from-cache! (fn [] (com/get-json url)) [:vacc :json]))

#_(defn population-cnt [ccode])

(def date-format (new SimpleDateFormat "YYYY-mm-dd"))

(defn date [rd] (.parse date-format rd))

(defn dates []
  ((comp
    ;; (fn [val] (cache/from-cache! (fn [] val) [:vacc :dates]))
    #_(partial take-last 4)
    (partial map date)
    (partial map :date)
    (fn [m] (get-in m [:ITA :data])))
   (json-data)))

     ;; :8/12/20 130718,
     ;; :8/12/20 130718,
     ;; :8/12/20 130718,


(defn vaccination [ccode]
  ((comp
    #_(fn [val] (cache/from-cache! (fn [] val) [:vacc :vacc]))
    (partial map (fn [m]
                   (if-let [v (:total_vaccinations m)]
                     (int v)
                     0)
                   #_(seleqct-keys m [:date :total_vaccinations])))
    #_(partial take-last 4)
    (fn [m] (get-in m [(keyword (ccc/country-code-3-letter ccode)) :data])))
   (json-data)))

(defn vaccination-data [raw-dates]
  #_(def rds raw-dates)
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
                (partial zipmap raw-dates)
                cycle
                (fn [_] [245 2350 9822 18554 21775 22411 27371 32293 43317 49488 57226 59930 60302 71982 72060]))))))
   ccc/all-country-codes))


;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.api.vaccination)
