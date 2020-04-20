(ns corona.api.v2
  (:require [clj-time.coerce :as tc]
            [clj-time.local :as tl]
            [clojure.core.memoize :as memo]
            [corona.common :refer [api-data-source api-server time-to-live]]
            [corona.core :as c]))

;; https://coronavirus-tracker-api.herokuapp.com/v2/locations?source=jhu&timelines=true

(def url (format "http://%s/v2/locations?source=%s&timelines=true"
                 api-server api-data-source))

(defn data [] (c/get-json url))

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
