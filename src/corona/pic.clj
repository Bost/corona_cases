(ns corona.pic
  (:require [cljplot.build :as b]
            [cljplot.common :refer :all]
            [cljplot.core :refer :all]
            [cljplot.render :as r]
            [clojure.set :as cset]
            [clojure2d.color :as c]
            [corona.api.v1 :as v1]
            [corona.common :as com]
            [corona.core :as cc]
            [corona.defs :as d])
  (:import [java.time LocalDate ZoneId]))

(defn to-java-time-local-date [java-util-date]
  (LocalDate/ofInstant (.toInstant java-util-date)
                       (ZoneId/systemDefault)
                       #_(ZoneId/of"Europe/Berlin")))

(defn below [threshold hms]
  (map (fn [{:keys [i] :as hm}] (if (< i threshold)
                                  (assoc hm :cc d/default-2-country-code)
                                  hm))
       hms))

(def data
  (v1/pic-data)
  #_(v2/pic-data))

(defn sum-below [threshold]
  (flatten (map (fn [[f hms]]
                  (map (fn [[cc hms]]
                         {:cc cc :f f :i (reduce + (map :i hms))})
                       (group-by :cc hms)))
                (group-by :f (below threshold data)))))

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

(defn sort-by-country-name [mapped-hm]
  (sort-by first (comp - compare)
           mapped-hm))

(defn sort-by-last-val [mapped-hm]
  (let [order (->> mapped-hm
                   (map (fn [[country hms]] [country (second (last hms))]))
                   (sort-by second)
                   (reverse)
                   (map first))]
    (->> order
         (map (fn [country]
                {country (get mapped-hm country)}))
         (reduce into []))))

(defn calc-json-data [threshold]
  (let [hm (group-by :cn
                     (map (fn [{:keys [cc] :as hm}]
                            (assoc hm :cn (com/country-alias cc)))
                          (fill-rest threshold)))
        mapped-hm
        (map-kv (fn [entry]
                  (sort-by first
                           (map (fn [{:keys [f i]}]
                                  [(to-java-time-local-date f) i])
                                entry)))
                hm)]
    #_(sort-by-country-name mapped-hm)
    (sort-by-last-val mapped-hm)))

#_(def json
  {:$schema "https://vega.github.io/schema/vega-lite/v4.json"
   :width 300 :height 200
   :data {:url "data/unemployment-across-industries.json"}
   :mark "area"
   :encoding
   {:x     {:timeUnit "yearmonth" :field "date" :type "temporal" :axis
            {:format "%Y"}}
    :y     {:aggregate "sum" :field "count" :type "quantitative"}
    :color {:field "series" :type "nominal" :scale {:scheme "category20b"}}}})

(defn show-pic [threshold]
  (let [json-data (calc-json-data threshold)
        pal (cycle (c/palette-presets :category20b))
        legend (reverse (map #(vector :rect %2 {:color %1}) pal
                             (keys json-data)))]
    #_(println (pr-str json-data))
    (let [res (-> (b/series [:grid] [:sarea json-data])
                  (b/preprocess-series)
                  (b/update-scale :y :fmt int)
                  (b/add-axes :bottom)
                  (b/add-axes :left)
                  #_(b/add-label :bottom "Date")
                  #_(b/add-label :left "Sick")
                  (b/add-label :top (format
                                     "%s; %s: Sic cases > %s"
                                     ((comp com/fmt-date :f last) data)
                                     cc/bot-name
                                     threshold)
                               {:color (c/darken :steelblue) :font-size 14})
                  (b/add-legend "" legend)
                  (r/render-lattice {:width 800 :height 600})
                  (save com/temp-file)
                  #_(show))]
      res)))
