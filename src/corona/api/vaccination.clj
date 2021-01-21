;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.api.vaccination)

(ns corona.api.vaccination
  (:require
   [corona.common :as com]
   [corona.api.cache :as cache]
   [corona.country-codes :as ccc])
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
    (fn [val] (cache/from-cache! (fn [] val) [:vacc :dates]))
    #_(partial take-last 4)
    (partial map date)
    (partial map :date)
    (fn [m] (get-in m [:PAK :data])))
   (json-data)))

(defn vacc [ccode]
  ((comp
    #_(fn [val] (cache/from-cache! (fn [] val) [:vacc :vacc]))
    #_(partial map (fn [m] (select-keys m [:date :total_vaccinations])))
    (partial take-last 2)
    (fn [m] (get-in m [(keyword (ccc/country-code-3-letter ccode)) :data])))
   (json-data)))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.api.vaccination)
