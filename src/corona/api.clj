(ns corona.api
  (:require [clj-http.client :as client]
            [clj-time-ext.core :as te]
            [clojure.core.memoize :as memo]
            [clojure.data.json :as json]
            [corona.core :refer [bot-ver read-number]]
            [corona.csv :refer [calculate-ill]])
  (:import java.text.SimpleDateFormat))

(defmacro dbg [body]
  `(let [x# ~body]
     (println "dbg:" '~body "=" x#)
     x#))

(def web-services
  [
   {:host "coronavirus-tracker-api.herokuapp.com"
    :route "/all"
    :mtr-report
    {:tstp "2020-03-04T16:42:57+0100"
     :data "
  1.|-- fritz.box                  0.0%    10    0.4   0.4   0.4   0.4   0.0
  2.|-- 62.52.200.221              0.0%    10   21.9  21.8  21.7  22.2   0.2
  3.|-- bundle-ether14.0001.dbrx.  0.0%    10   22.2  22.4  22.2  22.6   0.1
  4.|-- ae2-0.0002.corx.02.fra.de  0.0%    10   22.3  22.6  21.7  25.3   1.2
  5.|-- bundle-ether6.0001.dbrx.0  0.0%    10   22.4  22.3  22.0  22.5   0.2
  6.|-- bundle-ether1.0004.prrx.0  0.0%    10   22.8  22.4  22.1  22.8   0.2
  7.|-- 99.82.182.196              0.0%    10   21.5  22.0  21.5  22.5   0.3
  8.|-- 52.93.23.214               0.0%    10   22.5  23.2  22.5  25.3   0.8
  9.|-- 54.239.106.149             0.0%    10   21.8  23.3  21.7  27.9   2.2
 10.|-- ???                       100.0    10    0.0   0.0   0.0   0.0   0.0
 11.|-- 52.93.133.214              0.0%    10   45.2  45.3  44.9  46.5   0.6
 12.|-- 150.222.240.144            0.0%    10   45.8  46.3  45.0  50.0   1.7
 13.|-- ???                       100.0    10    0.0   0.0   0.0   0.0   0.0
 14.|-- ???                       100.0    10    0.0   0.0   0.0   0.0   0.0
 15.|-- ???                       100.0    10    0.0   0.0   0.0   0.0   0.0
 16.|-- ???                       100.0    10    0.0   0.0   0.0   0.0   0.0
 17.|-- 150.222.241.216            0.0%    10   48.2  48.7  47.9  53.7   1.7
 18.|-- ???                       100.0    10    0.0   0.0   0.0   0.0   0.0
 19.|-- 52.93.6.182                0.0%    10   45.8  47.2  45.6  55.3   3.2
 20.|-- 52.93.101.29               0.0%    10   45.2  45.2  44.8  47.0   0.7
 21.|-- 52.93.101.12               0.0%    10   46.9  46.6  45.7  49.0   1.0
 22.|-- 52.93.7.51                 0.0%    10   47.4  47.9  47.4  48.9   0.4
 23.|-- ???                       100.0    10    0.0   0.0   0.0   0.0   0.0
"}}
   {:host "covid2019-api.herokuapp.com"
    :mtr-report
    {:tstp "2020-03-04T16:43:19+0100"
     :data "
  1.|-- fritz.box                  0.0%    10    0.4   0.4   0.4   0.4   0.0
  2.|-- 62.52.200.221              0.0%    10   21.7  21.9  21.6  22.3   0.2
  3.|-- bundle-ether14.0001.dbrx.  0.0%    10   22.3  22.2  21.7  22.7   0.3
  4.|-- bundle-ether1.0001.prrx.0  0.0%    10   22.8  22.2  21.7  22.8   0.3
  5.|-- 176.52.252.32              0.0%    10   22.0  25.6  21.8  42.8   7.5
  6.|-- 176.52.248.230             0.0%    10  106.0 105.8 105.4 106.2   0.3
  7.|-- 84.16.13.138               0.0%    10  110.3 110.4 109.6 111.1   0.4
  8.|-- 213.140.55.195             0.0%    10  117.4 113.7 111.1 117.9   3.0
  9.|-- 54.239.108.38              0.0%    10  126.6 129.7 114.0 154.0  10.7
 10.|-- 52.93.114.1                0.0%    10  109.6 109.5 108.8 112.1   1.0
 11.|-- ???                       100.0    10    0.0   0.0   0.0   0.0   0.0
 12.|-- ???                       100.0    10    0.0   0.0   0.0   0.0   0.0
 13.|-- ???                       100.0    10    0.0   0.0   0.0   0.0   0.0
 14.|-- 52.93.28.174               0.0%    10  123.9 114.8 112.1 123.9   4.1
 15.|-- ???                       100.0    10    0.0   0.0   0.0   0.0   0.0
"
     }}
   {:host "corona-api.herokuapp.com"
    :mtr-report
    {:tstp "2020-03-04T16:43:50+0100"
     :data "
  1.|-- fritz.box                  0.0%    10    0.4   0.4   0.3   0.4   0.0
  2.|-- 62.52.200.221              0.0%    10   21.6  21.9  21.6  22.5   0.3
  3.|-- bundle-ether14.0002.dbrx.  0.0%    10   22.6  22.4  22.0  22.7   0.2
  4.|-- bundle-ether2.0001.prrx.0  0.0%    10   22.2  22.4  22.2  23.0   0.2
  5.|-- 176.52.252.32              0.0%    10   21.8  27.0  21.6  50.6  10.5
  6.|-- ???                       100.0    10    0.0   0.0   0.0   0.0   0.0
  7.|-- 94.142.125.253             0.0%    10  109.7 110.1 109.7 110.4   0.2
  8.|-- 213.140.55.193             0.0%    10  111.2 111.4 111.2 111.7   0.2
  9.|-- 54.239.108.40              0.0%    10  134.3 135.1 125.9 142.8   5.1
 10.|-- 52.93.114.15               0.0%    10  111.2 111.4 110.9 112.1   0.4
 11.|-- ???                       100.0    10    0.0   0.0   0.0   0.0   0.0
 12.|-- ???                       100.0    10    0.0   0.0   0.0   0.0   0.0
 13.|-- ???                       100.0    10    0.0   0.0   0.0   0.0   0.0
 14.|-- 52.93.28.140               0.0%    10  110.5 112.1 110.1 120.3   3.8
 15.|-- ???                       100.0    10    0.0   0.0   0.0   0.0   0.0
"}
    }])

(def api-service (nth web-services 0))
(def host (:host api-service))
(def url (str "https://" host (:route api-service)))

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

(def countries
  (->> [:confirmed :deaths :recovered]
       (map (fn [case] (->> (data-memo) case :locations (map :country) set)))
       (reduce clojure.set/union)
       #_(count)))

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

(defn sums-for-case [case]
  (let [locations (->> (data-memo) case :locations)]
    (->> (raw-dates)
         #_(take-last 1)
         (map (fn [raw-date] (sums-for-date locations raw-date))))))

(defn get-counts []
  (let [crd (mapv sums-for-case [:confirmed :recovered :deaths])
        i (apply mapv calculate-ill crd)]
    (zipmap [:c :r :d :i] (conj crd i))))

(defn confirmed [] (:c (get-counts)))
(defn deaths    [] (:d (get-counts)))
(defn recovered [] (:r (get-counts)))
(defn ill       [] (:i (get-counts)))

(defn dates []
  (let [sdf (new SimpleDateFormat "MM/dd/yy")]
    (->> (raw-dates)
         ;; :2/24/20
         (map keyname)
         (map (fn [rd] (.parse sdf rd))))))

(defn last-day  []
  (conj {:f (last (dates))}
        (zipmap [:c :d :r :i]
                (map last [(confirmed) (deaths) (recovered) (ill)]))))
