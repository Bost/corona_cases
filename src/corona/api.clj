(ns corona.api
  (:require [clj-http.client :as client]
            [clj-time-ext.core :as te]
            [clojure.core.memoize :as memo]
            [clojure.data.json :as json]
            [clojure.string :as s]
            [corona.core :refer [bot-ver read-number dbg]]
            [corona.countries :as co]
            [corona.core :as c])
  (:import java.text.SimpleDateFormat))

;; TODO evaluate web services
;; https://sheets.googleapis.com/v4/spreadsheets/1jxkZpw2XjQzG04VTwChsqRnWn4-FsHH6a7UHVxvO95c/values/Dati?majorDimension=ROWS&key=AIzaSyAy6NFBLKa42yB9KMkFNucI4NLyXxlJ6jQ

;; https://github.com/iceweasel1/COVID-19-Germany
(def web-service
  {:host "coronavirus-tracker-api.herokuapp.com" :route "/all"})

(def api-service web-service)
(def host (:host api-service))
(def url
  #_"http://127.0.0.1:5000/all"
  (str "https://" host (:route api-service)))

(defn get-data [url]
  ;; TODO use monad for logging
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

(defn left-pad [s] (c/left-pad s 2))

(defn all-affected-country-codes
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
       (into co/default-affected-country-codes)
       (mapv (fn [cc] (if (= "XX" cc)
                       co/default-2-country-code
                       cc)))))

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
       (map (fn [loc]
              (->> loc :history raw-date
                   ;; https://github.com/ExpDev07/coronavirus-tracker-api/issues/41
                   ;; str read-number
                   )))
       (reduce + 0)))

(defn pred-fn [country-code]
  (fn [loc]
    ;; TODO s/upper-case is probably not needed
    (condp = (s/upper-case country-code)
      co/worldwide-2-country-code
      true

      co/default-2-country-code
      ;; XX comes from the service
      (= "XX" (:country_code loc))

      (= country-code (:country_code loc)))))

(defn sums-for-case [{:keys [case pred]}]
  (let [locations (->> (data-memo) case :locations
                       (filter pred))]
    (->> (raw-dates)
         #_(take 4)
         #_(take-last 1)
         (map (fn [raw-date] (sums-for-date locations raw-date))))))

(defn get-counts [prm]
  (let [crd (mapv (fn [case] (sums-for-case (conj prm {:case case})))
                  [:confirmed :recovered :deaths])]
    (zipmap [:c :r :d :i] (conj crd (apply mapv c/calculate-ill crd)))))

(defn confirmed [prm] (:c (get-counts prm)))
(defn deaths    [prm] (:d (get-counts prm)))
(defn recovered [prm] (:r (get-counts prm)))
(defn ill       [prm] (:i (get-counts prm)))

(defn dates []
  (let [sdf (new SimpleDateFormat "MM/dd/yy")]
    (->> (raw-dates)
         ;; :2/24/20
         (map keyname)
         (map (fn [rd] (.parse sdf rd))))))

(defn last-day [prm]
  (conj {:f (last (dates))}
        (zipmap [:c :d :r :i]
                (map last [(confirmed prm)
                           (deaths    prm)
                           (recovered prm)
                           (ill       prm)]))))
