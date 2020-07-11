(ns corona.api.v2
  (:require
   [clj-time.coerce :as tc]
   [clj-time.local :as tl]
   [clojure.core.memoize :as memo]
   [corona.common :as co]
   [corona.country-codes :refer :all]
   [utils.core :refer [] :exclude [id]]
   ))

;; https://coronavirus-tracker-api.herokuapp.com/v2/locations?source=jhu&timelines=true

;; TODO fix provinces of Denmark:
;; Denmark http://localhost:8000/v2/locations/92
;; Denmark http://localhost:8000/v2/locations/93
;; Denmark http://localhost:8000/v2/locations/94

(def url (format "http://%s/v2/locations?source=%s&timelines=true"
                 co/api-server co/api-data-source))

(defn data [] (co/get-json url))

(def data-memo
  #_data
  (memo/ttl data {} :ttl/threshold (* co/time-to-live 60 1000)))

(defn cnt-days []
  #_5
  ((comp count :timeline :confirmed :timelines first :locations)
   (data-memo)))

(def ccs
  #{
    sk de
    ;; cr tg za pe lc ch ru si au kr it fi sc tt my sy mn am dz uy td dj bi mk
    ;; mu li gr gy cg ml gm sa bh ne bn xk cd dk bj me bo jo cv ve ci uz tn is
    ;; ga tz at lt np bg il pk pt hr mr ge hu tw mm sr va kw se gb qq vn cf pa
    ;; vc jp ir af ly mz ro qa cm by sd ar br zw nz fj id sv cn ht rw ba tl jm
    ;; ke py cy gh ma sg lk ph sm tr ps bz cu ad dm lr om so do al fr gw bb ca
    ;; mg kh la hn th de lb kz ec no ao et md ag be mv sz cz cl bt nl eg sn ee
    ;; kn bw ni pg iq kg us zm mc gt bf lu ua ie lv gd mw bs az sk gq in es co
    ;; rs ng ug sl er ae bd mt gn na mx pl
    })

(defn sum-up-to
  "Nth elem of the returned collection is a sum of it's predecessors in
  the `coll`. E.g.:
  (sum-up-to [1 1 1 1])
  ;; => [1 2 3 4]"
  [coll]
  (loop [init-coll coll
         acc-coll []
         acc-val 0]
    (if (empty? init-coll)
      acc-coll
      (let [[head & tail] init-coll
            last-val (+ head acc-val)]
        (recur tail
               (conj acc-coll last-val)
               last-val)))))

;; TODO use utils.core/transpose-matrix
(defn transpose-matrix
  "Transpose matrix. E.g.:
  (transpose-matrix [[:a :b :c] [0 1 2]])

  See also https://github.com/mikera/core.matrix
  "
  [m]
  (if (seq m)
    (apply mapv vector m)
    m))

(defn timeline [case loc]
  (let [hms (some->> loc :timelines case :timeline
                     (take (cnt-days)))
        v (->> hms
               (transpose-matrix))
        [fs vs] v
        ;; sum-up-to-vs (sum-up-to vs)
        sum-up-to-vs vs
        ]
    hms))

(defn pic-data
  "
  Unusable for the moment - nr. of recoveries is 0
  [{:cc \"AF\", :f \"2020-02-03T00:00:00Z\", :i 0}
   {:cc \"AL\", :f \"2020-02-03T00:00:00Z\", :i 0}
   {:cc \"AF\", :f \"2020-02-08T00:00:00Z\", :i 0}
   {:cc \"AL\", :f \"2020-02-08T00:00:00Z\", :i 0}
   {:cc \"AF\", :f \"2020-02-18T00:00:00Z\", :i 0}
   {:cc \"AL\", :f \"2020-02-18T00:00:00Z\", :i 0}
   {:cc \"AF\", :f \"2020-03-07T00:00:00Z\", :i 1}
   {:cc \"AL\", :f \"2020-03-07T00:00:00Z\", :i 0}
   {:cc \"AF\", :f \"2020-03-25T00:00:00Z\", :i 82}
   {:cc \"AL\", :f \"2020-03-25T00:00:00Z\", :i 141}]
  "
  []
  (->> (data-memo)
       :locations
       #_(take 1)
       (filter (fn [loc]
                 #_true
                 (in? ccs (:country_code loc))))
       (mapv (fn [loc]
              (map (fn [[f c] [_ r] [_ d]]
                     {:cc (:country_code loc)
                      :f (->> f
                              (name)
                              (tl/to-local-date-time)
                              (tc/to-date))
                      :c c
                      :r r
                      :d d
                      :i (co/calculate-active c r d)})
                   (timeline :confirmed loc)
                   (if-let [norm-timeline (seq (timeline :recovered loc))]
                     norm-timeline
                     (repeat (cnt-days) [nil 0]))
                   (timeline :deaths loc))))
       (flatten)))

(defn population []
  (->> (data-memo)
       :locations
       #_(take 1)
       (filter (fn [loc]
                 true
                 #_(in? ccs (:country_code loc))))
       (mapv (fn [loc]
               {:cc (:country_code loc)
                :cp (:country_population loc)}))))
