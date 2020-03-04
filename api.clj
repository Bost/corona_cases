(ns coronavirus.api
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clj-time
             [format :as tf]
             [core :as time]
             [coerce :as tc]]
            [coronavirus.csv :as csv])
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

(def url
  (let [{host :host route :route} (nth web-services 0)]
    (str "https://" host route)))

(defn get-data [url]
  (as-> url $
    (client/get $ {:accept :json})
    (:body $)
    (json/read-json $)))

(defn fix-octal-val
  "(read-string s-day \"08\") produces a NumberFormatException
  https://clojuredocs.org/clojure.core/read-string#example-5ccee021e4b0ca44402ef71a"
  [s]
  (clojure.string/replace s #"^0+" ""))

(defn read-number [v]
  (if (or (empty? v) (= "0" v))
    0
    (-> v fix-octal-val read-string)))

(defn data [] (get-data url))
#_(require '[ clojure.inspector :as i])
#_(i/inspect data)

(defn raw-dates []
  (->> (data) :recovered :locations last :history keys sort))

(defn sums-for-case [case]
  (defn sums-for-date [date]
    (->> data case :locations
         #_(take-last 1)
         (map (fn [loc]
                (->> loc
                     :history
                     date
                     read-number)))
         (apply +)))
  (->> raw-dates
       #_(take-last 2)
       (map sums-for-date)))

(defn get-counts []
  (let [crd (mapv sums-for-case [:confirmed :deaths :recovered])
        i (apply map (fn [c r d] (csv/calculate-ill c r d)) crd)]
    (zipmap [:c :d :r :i]
            (conj crd i))))

(defn keyname [key] (str (namespace key) "/" (name key)))

(defn dates []
  #_(map (fn [hm] (.parse (new SimpleDateFormat "MM-dd-yyyy")
                       (subs (:f hm) 0 10)))
         (get-counts))
  (let [sdf (new SimpleDateFormat "MM/dd/yy")]
    (->> (raw-dates)
         ;; :2/24/20
         (map (fn [rd] (->> rd keyname (.parse sdf)))))))
