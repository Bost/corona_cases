(ns corona.pic
  (:require [cljplot.render :as r]
            [cljplot.build :as b]
            [cljplot.common :refer :all]
            [fastmath.interpolation :as in]
            [fastmath.stats :as stats]

            [corona.api.expdev07 :as data]
            [corona.messages :as msg]
            [corona.common :as com]

            [clojure2d.color :as c]
            [cljplot.scale :as s]
            [fastmath.core :as m]
            [cljplot.core :refer :all]
            [java-time :as dt]

            [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clj-time.local :as tl]
            [corona.core :refer [in?]]
            [clojure.set :as cset]

            [clojure.core.memoize :as memo]
            [fastmath.random :as rnd]
            [corona.defs :as d])
  (:import
   #_[java.util Date]
   [java.time LocalDate]
   [java.time ZoneId]))

(defn to-java-time-local-date [java-util-date]
  (LocalDate/ofInstant (.toInstant java-util-date) (ZoneId/of
                                                    #_"Brazil/East"
                                                    "UTC"
                                                    #_"Europe/Berlin")))

(defn fmt
  "
  2020-01-23T23:00:00.000-00:00
  2006-10-01T07:00:00.000Z
  "
  [date]
  (tf/unparse (tf/with-zone
                (tf/formatter "dd MMM")
                #_(tf/formatters :date-time)
                (t/default-time-zone))
              #_(tf/formatters :date-time)
              (->> date tc/from-date)))

(defn data
  "
  [
  {:cc \"AU\", :f #inst \"2020-01-21T23:00:00.000-00:00\", :i 0}
  {:cc \"AT\", :f #inst \"2020-01-21T23:00:00.000-00:00\", :i 0}
  {:cc \"AE\", :f #inst \"2020-01-21T23:00:00.000-00:00\", :i 0}
  ...
  {:cc \"AU\", :f #inst \"2020-01-26T23:00:00.000-00:00\", :i 5}
  {:cc \"AT\", :f #inst \"2020-01-26T23:00:00.000-00:00\", :i 0}
  {:cc \"AE\", :f #inst \"2020-01-26T23:00:00.000-00:00\", :i 0}]
  "
  []
  (let [country-codes
        #_["AU" "AT" "AE"]
        (data/all-affected-country-codes
         #_{:limit-fn (fn [coll] (take 10 coll))})]

    (reduce
     into []
     (map-indexed
      (fn [idx _]
        (map (fn [cc]
               (let [$2 (data/eval-fun {:fun (fn [coll] (nth coll idx))
                                        :pred (data/pred-fn cc)})]
                 #_{:cc cc :f (:f $2) :i (:i $2)}
                 (into {:cc cc} (select-keys $2 [:f :i]))))
             country-codes))
      (data/dates-memo
       #_{:limit-fn (fn [coll] (take 10 coll))})))))

(def data-memo (memo/memo data))

(defn below [threshold hms]
  (map (fn [{:keys [i] :as hm}] (if (< i threshold)
                                  (assoc hm :cc d/default-2-country-code)
                                  hm))
       hms))

(defn sum-below [threshold]
  (flatten (map (fn [[f hms]]
                  (map (fn [[cc hms]]
                         {:cc cc :f f :i (reduce + (map :i hms))})
                       (group-by :cc hms)))
            (group-by :f (below threshold (data-memo))))))

(defn fill-rest [threshold]
  (let [sum-below-threshold (sum-below threshold)
        countries-threshold (set (map :cc sum-below-threshold))]
    (reduce into []
            (map (fn [[f hms]]
                   (cset/union
                    hms
                    (map (fn [cc] {:cc cc :f f :i 0})
                         (cset/difference countries-threshold
                                          (keys (group-by :cc hms)))))
                   #_(->> (group-by :cc hms)
                          keys
                          (cset/difference countries-threshold)
                          (map (fn [cc] {:cc cc :f f :i 0}))
                          (cset/union hms)))
                 (group-by :f sum-below-threshold)))))

(defn calc-json-data [threshold]
  (let [hm (group-by :cn
                     (map (fn [{:keys [cc] :as hm}] (assoc hm :cn (com/country-alias cc)))
                          (fill-rest threshold)))
        mapped-hm (map-kv (fn [entry]
                            (sort-by first
                                     (map (fn [{:keys [f i]}] [(to-java-time-local-date f) i])
                                          entry)))
                          hm)]
    (sort-by first (comp - compare)
             mapped-hm)))

#_(def json
  {:$schema "https://vega.github.io/schema/vega-lite/v4.json"
   :width 300 :height 200
   :data {:url "data/unemployment-across-industries.json"}
   :mark "area"
   :encoding
   {:x     {:timeUnit "yearmonth" :field "date" :type "temporal" :axis {:format "%Y"}}
    :y     {:aggregate "sum" :field "count" :type "quantitative"}
    :color {:field "series" :type "nominal" :scale {:scheme "category20b"}}}})

(defn show-pic [threshold]
  (let [json-data (calc-json-data threshold)
        pal (cycle (c/palette-presets :category20b))
        legend (reverse (map #(vector :rect %2 {:color %1}) pal (keys json-data)))]
    (-> (b/series [:grid] [:sarea json-data])
        (b/preprocess-series)
        (b/update-scale :y :fmt int)
        (b/add-axes :bottom)
        (b/add-axes :left)
        (b/add-label :bottom "Date")
        (b/add-label :left "Sick")
        (b/add-legend ""
                      #_(str "Country > " threshold)
                      legend)
        (r/render-lattice {:width 800 :height 600})
        (save "results/vega/stacked-area.jpg")
        (show))))
