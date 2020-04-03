(ns corona.api.expdev07
  (:require [clj-http.client :as client]
            [clojure.core.memoize :as memo]
            [clojure.data.json :as json]
            [clojure.set :as cset]
            [corona.core :as c :refer [read-number]]
            [corona.defs :as d])
  (:import java.text.SimpleDateFormat))

;; TODO evaluate web services
;; https://sheets.googleapis.com/v4/spreadsheets/1jxkZpw2XjQzG04VTwChsqRnWn4-FsHH6a7UHVxvO95c/values/Dati?majorDimension=ROWS&key=AIzaSyAy6NFBLKa42yB9KMkFNucI4NLyXxlJ6jQ

;; https://github.com/iceweasel1/COVID-19-Germany
#_(def api-service {:host "coronavirus-tracker-api.herokuapp.com" :route "/all"})
#_(def host (:host api-service))
#_(def url
  #_"http://127.0.0.1:5000/all"
  (str "https://" host (:route api-service)))

(defn data []
  (json/read-json (:body (client/get "https://coronavirus-tracker-api.herokuapp.com/all" {:accept :json}))))

(def data-memo (memo/ttl data {}
                         ;; 15 minutes:
                         :ttl/threshold (* 15 60 1000)))

#_(require '[ clojure.inspector :as i])
#_(i/inspect (data-memo))

(defn raw-dates-unsorted []
  #_[(keyword "2/22/20") (keyword "2/2/20")]
  (->> (data-memo) :confirmed :locations last :history keys))

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
       (reduce cset/union)
       sort
       vec
       #_(into cr/default-affected-country-codes)
       (mapv (fn [cc] (if (= "XX" cc)
                       d/default-2-country-code
                       cc)))
       distinct))

(comment
  (data-memo)
  
  (all-affected-country-codes)

  )


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

(defn sums-for-date [case locations raw-date]
  (if (and (empty? locations)
           (= :recovered case))
    0
    (->> locations
         (map (fn [loc]
                (->> loc :history raw-date
                     ;; https://github.com/ExpDev07/coronavirus-tracker-api/issues/41
                     ;; str read-number
                     )))
         (reduce + 0))))

(defn pred-fn [country-code]
  (fn [loc]
    (condp = country-code
      d/worldwide-2-country-code
      true

      d/default-2-country-code
      ;; XX comes from the service
      (= "XX" (:country_code loc))

      (= country-code (:country_code loc)))))

(defn sums-for-case [{:keys [case pred]}]
  (let [locations (->> (data-memo) case :locations
                       (filter pred))]
    (->> (raw-dates)
         #_(take 4)
         #_(take-last 1)
         (map (fn [raw-date] (sums-for-date case locations raw-date))))))

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

(defn get-last [coll]
  (->> coll
       (take-last 1)
       (first)))

(defn get-prev [coll]
  (->> coll
       (take-last 2)
       (first)))

(defn eval-fun [{:keys [fun] :as prm}]
  (conj {:f (fun (dates))}
        (zipmap [:c :d :r :i]
                (map fun [(confirmed prm)
                          (deaths    prm)
                          (recovered prm)
                          (ill       prm)]))))

(defn delta
  "Example (delta {:pred (pred-fn \"CN\")})"
  [prm]
  (->> [get-prev get-last]
       (map (fn [fun] (eval-fun (assoc prm :fun fun))))
       (apply (fn [prv lst]
                (map (fn [k] k
                       {k (- (k lst) (k prv))})
                     [:c :d :r :i])
                ))
       (reduce into {})))

(defn last-day
  "Example (last-day {:pred (fn [_] true)})"
  [prm] (eval-fun (assoc prm :fun get-last)))
