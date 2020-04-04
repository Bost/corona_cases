(ns corona.api.v2
  (:require [clj-time.coerce :as tc]
            [clj-time.local :as tl]
            [clojure.core.memoize :as memo]
            [corona.core :as c]))

(def source
  "jhu"

  ;; csbs throws:
  ;; Execution error (ArityException) at cljplot.impl.line/eval34748$fn (line.clj:155).
  ;; Wrong number of args (0) passed to: cljplot.common/fast-max
  #_"csbs"
  )
(def url-all
  (format "http://%s/v2/locations?source=%s&timelines=true"
          "localhost:8000"
          #_"covid-tracker-us.herokuapp.com"
          #_"coronavirus-tracker-api.herokuapp.com"
          source))

;; https://coronavirus-tracker-api.herokuapp.com/v2/locations?source=jhu&timelines=true

(defn url [] (str url-all
                    #_"&country_code=US"))

(def time-to-live "In minutes" 15)

(defn data [] (c/get-json (url)))

(def data-memo
  #_data
  (memo/ttl data {} :ttl/threshold (* time-to-live 60 1000)))

(def cnt-days
  #_5
  ((comp count :timeline :confirmed :timelines first :locations)
   (data-memo)))

(defn pic-data
  "
  (
  {:cc \"AF\", :f \"2020-02-03T00:00:00Z\", :i 0}
  {:cc \"AL\", :f \"2020-02-03T00:00:00Z\", :i 0}
  {:cc \"AF\", :f \"2020-02-08T00:00:00Z\", :i 0}
  {:cc \"AL\", :f \"2020-02-08T00:00:00Z\", :i 0}
  {:cc \"AF\", :f \"2020-02-18T00:00:00Z\", :i 0}
  {:cc \"AL\", :f \"2020-02-18T00:00:00Z\", :i 0}
  {:cc \"AF\", :f \"2020-03-07T00:00:00Z\", :i 1}
  {:cc \"AL\", :f \"2020-03-07T00:00:00Z\", :i 0}
  {:cc \"AF\", :f \"2020-03-25T00:00:00Z\", :i 82}
  {:cc \"AL\", :f \"2020-03-25T00:00:00Z\", :i 141})
  "
  []
  (->> (data-memo)
       :locations
       #_(filter (fn [loc] (corona.core/in? ["US" "DE" "CN"]
                                           (:country_code loc))))
       (map (fn [loc]
              (let [cc (:country_code loc)
                    c-timeline (->> loc :timelines :confirmed :timeline)
                    r-timeline (->> loc :timelines :recovered :timeline)
                    d-timeline (->> loc :timelines :deaths    :timeline)]
                (map (fn [[f c] [_ r] [_ d]]
                       {:cc cc :f (tc/to-date (tl/to-local-date-time (name f)))
                        :i (c/calculate-ill c r d)})
                     (->> c-timeline (take cnt-days))
                     (if-let [norm-timeline (seq (->> r-timeline (take cnt-days)))]
                       norm-timeline
                       (repeat cnt-days [nil 0]))
                     (->> d-timeline (take cnt-days)))
                )))
       (flatten)
       #_(sort-by :f)))
