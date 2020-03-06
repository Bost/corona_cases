(ns corona.api
  (:require [clj-http.client :as client]
            [clj-time-ext.core :as te]
            [clojure.core.memoize :as memo]
            [clojure.data.json :as json]
            [clojure.string :as s]
            [corona.core :refer [bot-ver read-number]]
            [corona.csv :refer [calculate-ill]])
  (:import java.text.SimpleDateFormat))

(defmacro dbg [body]
  `(let [x# ~body]
     (println "dbg:" '~body "=" x#)
     x#))

(def web-service
  {:host "coronavirus-tracker-api.herokuapp.com" :route "/all"})

(def api-service web-service)
(def host (:host api-service))
(def url
  #_"http://127.0.0.1:5000/all"
  (str "https://" host (:route api-service)))

(defn get-data [url]
  (let [tbeg (te/tnow)]
    (println (str "[" tbeg "           " " " bot-ver " /" "get-data: " url "]"))
    (let [r (as-> url $
              (client/get $ {:accept :json})
              (:body $)
              (json/read-json $))]
      (println (str "[" tbeg ":" (te/tnow) " " bot-ver " /" "get-data: " url "]"))
      r)))

(defn data [] (get-data url))

(def time-to-live "In minutes" 15)

(def data-memo (memo/ttl data {} :ttl/threshold (* time-to-live 60 1000)))

#_(require '[ clojure.inspector :as i])
#_(i/inspect (data-memo))

(defn raw-dates-unsorted []
  #_[(keyword "2/22/20") (keyword "2/2/20")]
  (->> (data-memo) :recovered :locations last :history keys))

(defn keyname [key] (str (namespace key) "/" (name key)))

(defn left-pad [s] (.replaceAll (format "%2s" s) " " "0"))

(defn affected-countries
  "Countries with some confirmed, deaths or recovered cases"
  []
  (->> [:confirmed :deaths :recovered]
       (map (fn [case] (->> (data-memo)
                            case :locations
                            (map :country_code)
                            set)))
       (reduce clojure.set/union)
       sort
       vec
       #_(take 2)))

(defn raw-dates []
  (->> (raw-dates-unsorted)
       (map keyname)
       (map (fn [date] (re-find (re-matcher #"(\d+)/(\d+)/(\d+)" date))))
       (map (fn [[_ m d y]]
              (->> [y m d]
                   (map left-pad)
                   (interpose "/")
                   (apply str))))
       sort
       (map (fn [kw] (re-find (re-matcher #"(\d+)/(\d+)/(\d+)" kw))))
       (map (fn [[_ y m d]]
              (->> [m d y]
                   (map read-number)
                   (interpose "/")
                   (apply str)
                   (keyword))))))

(defn sums-for-date [locations raw-date]
  (->> locations
       #_(take-last 1)
       (map (fn [loc] (->> loc :history raw-date read-number)))
       (apply +)))

(defn sums-for-case [case pred]
  (let [locations (->> (data-memo) case :locations
                       (filter pred))]
    (->> (raw-dates)
         #_(take-last 1)
         (map (fn [raw-date] (sums-for-date locations raw-date))))))

(defn get-counts [pred]
  (let [crd (mapv (fn [case] (sums-for-case case pred))
                  [:confirmed :recovered :deaths])
        i (apply mapv calculate-ill crd)]
    (zipmap [:c :r :d :i] (conj crd i))))

(defn confirmed [pred] (:c (get-counts pred)))
(defn deaths    [pred] (:d (get-counts pred)))
(defn recovered [pred] (:r (get-counts pred)))
(defn ill       [pred] (:i (get-counts pred)))

(defn dates []
  (let [sdf (new SimpleDateFormat "MM/dd/yy")]
    (->> (raw-dates)
         ;; :2/24/20
         (map keyname)
         (map (fn [rd] (.parse sdf rd))))))

(defn last-day [pred]
  (conj {:f (last (dates))}
        (zipmap [:c :d :r :i]
                (map last [(confirmed pred)
                           (deaths    pred)
                           (recovered pred)
                           (ill       pred)]))))
