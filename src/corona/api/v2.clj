(ns corona.api.v2
  (:require [clojure.core.memoize :as memo]
            [clojure.set :as cset]
            [corona.core :as c :refer [read-number]]
            [corona.defs :as d]
            [java-time :as jt]
            [clj-time.local :as tl]
            [clj-time.coerce :as tc]
            )
  (:import java.text.SimpleDateFormat))

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

(defn url [cc] (str url-all
                    ;; "&country_code=" cc
                    ))

(def time-to-live "In minutes" (* 24 60)) ;; the whole day - I'm deving...

(defn data [] (c/get-json (url "SK")))

(def data-memo (memo/ttl data {} :ttl/threshold (* time-to-live 60 1000)))

(def cnt-countries
  #_3
  (count (set (map :country_code (:locations (data-memo))))))

(def cnt-days
  5
  #_((comp count :timeline :confirmed :timelines first :locations)
   (data-memo)))

(defn pic-data
  "
  (
  {:cc \"AF\", :f \"2020-02-03T00:00:00Z\", :i 0}
  {:cc \"AL\", :f \"2020-02-03T00:00:00Z\", :i 0}
  {:cc \"DZ\", :f \"2020-02-03T00:00:00Z\", :i 0}
  {:cc \"AF\", :f \"2020-02-08T00:00:00Z\", :i 0}
  {:cc \"AL\", :f \"2020-02-08T00:00:00Z\", :i 0}
  {:cc \"DZ\", :f \"2020-02-08T00:00:00Z\", :i 0}
  {:cc \"AF\", :f \"2020-02-18T00:00:00Z\", :i 0}
  {:cc \"AL\", :f \"2020-02-18T00:00:00Z\", :i 0}
  {:cc \"DZ\", :f \"2020-02-18T00:00:00Z\", :i 0}
  {:cc \"AF\", :f \"2020-03-07T00:00:00Z\", :i 1}
  {:cc \"AL\", :f \"2020-03-07T00:00:00Z\", :i 0}
  {:cc \"DZ\", :f \"2020-03-07T00:00:00Z\", :i 17}
  {:cc \"AF\", :f \"2020-03-25T00:00:00Z\", :i 82}
  {:cc \"AL\", :f \"2020-03-25T00:00:00Z\", :i 141}
  {:cc \"DZ\", :f \"2020-03-25T00:00:00Z\", :i 281})
  "
  []
  (->>
   (data-memo)
       :locations
       (take cnt-countries)
       (map (fn [location]
              (let [cc (:country_code location)
                    c-timeline (->> location :timelines :confirmed :timeline)
                    r-timeline (->> location :timelines :recovered :timeline)
                    d-timeline (->> location :timelines :deaths    :timeline)]
                (map (fn [[f c] [_ r] [_ d]]
                       {:cc cc :f (tc/to-date (tl/to-local-date-time (name f)))
                        :i (c/calculate-ill c r d)})
                     (->> c-timeline (take cnt-days))
                     (if (empty? (->> r-timeline (take cnt-days))) (repeat cnt-days [nil 0]))
                     (->> d-timeline (take cnt-days)))
                )))
       (flatten)
       #_(sort-by :f)))
