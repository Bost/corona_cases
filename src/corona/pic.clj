(ns corona.pic
  (:require [cljplot.build :as b]
            [cljplot.common :refer :all]
            [cljplot.core :refer :all]
            [cljplot.render :as r]
            [clojure.set :as cset]
            [clojure2d.color :as c]
            [corona.common :as com]
            [corona.defs :as d]
            [corona.api.v1]
            [corona.api.v2])
  (:import [java.time LocalDate ZoneId]))

(defn to-java-time-local-date [java-util-date]
  (LocalDate/ofInstant (.toInstant java-util-date) (ZoneId/of
                                                    #_"Brazil/East"
                                                    "UTC"
                                                    #_"Europe/Berlin")))

(def data-memo
  #_corona.api.v2/pic-data
  corona.api.v1/pic-data
  #_(memo/memo data))

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
        mapped-hm
        (map-kv (fn [entry]
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
    #_(println (pr-str json-data))
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
