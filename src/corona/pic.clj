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

(defn partition-in-chunks
  [col]
  (let [[grouped separate] (partition-all (- (count col) 7) col)]
    (into [grouped] (partition-all 1 separate))))

(defn group [prm]
(let [total (data/last-day {:pred (fn [_] true)})
      total-i (:i total)]
    (let [[head & tail] (->> (msg/stats-all-affected-countries {})
                             (sort-by :i <)
                             partition-in-chunks)]
      (->> (into [(->> head
                       (map (fn [country] (select-keys country [:c :d :r :i])))
                       (apply merge-with +)
                       (into {:cc d/default-2-country-code :cn d/others
                              :f (->> head first :f)}))]
                 tail)
           (map (fn [hm]
                  (assoc hm :rate (/ total-i (:i hm)))))))))

(def group-memo
  (memo/ttl group {} :ttl/threshold (* 60 60 1000)))

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

    (transduce
     (map-indexed (fn [idx _]
                    (map (fn [cc]
                           (let [$2 (data/eval-fun {:fun (fn [coll] (nth coll idx))
                                                    :pred (data/pred-fn cc)})]
                             #_{:cc cc :f (:f $2) :i (:i $2)}
                             (into {:cc cc} (select-keys $2 [:f :i]))))
                         country-codes)))
     into []
     (data/dates-memo
      #_{:limit-fn (fn [coll] (take 10 coll))}))

    #_(->> (data/dates-memo
          #_{:limit-fn (fn [coll] (take 10 coll))})
         (map-indexed (fn [idx _]
                        (->> country-codes
                             (map (fn [cc]
                                    (as-> {:fun (fn [coll] (nth coll idx))
                                           :pred (data/pred-fn cc)} $
                                      (data/eval-fun $)
                                      {:cc cc :f (:f $) :i (:i $)}
                                      #_(select-keys $ [:f :i])
                                      #_(into {:cc cc} $)))))))
         (reduce into [])
         #_(sort-by :f))))

(def data-memo (memo/memo data))

(defn add-up [coll]
  (loop [vs coll
         last-max 0
         acc []]
    (if (empty? vs)
      acc
      (let [new-last-max
            (first vs)
            #_last-max
            #_(max last-max (first vs))]
        (recur (rest vs)
               new-last-max
               (conj acc new-last-max))))))

(defn below [threshold hms]
  (->> hms
       (map (fn [{:keys [i] :as hm}] (if (< i threshold)
                                       (assoc hm :cc d/default-2-country-code)
                                       hm)))))

(defn sum-below [threshold]
  ;; the previous day maximum must be checked; make sure the default mapping of ships wont' get lost
  (let [data (data-memo)]
    (println "Splitting according to threshold")
    (let [below-threshold (below threshold data)]
      (println "Grouping below threshold")
      (->> below-threshold
           (group-by :f)
           (map (fn [[f hms]]
                  (->> (group-by :cc hms)
                       (map (fn [[cc hms]]
                              {:cc cc :f f :i (reduce + (map :i hms))})))))
           ;; flatten
           ;; ;; (sort-by :f)
           ;; (group-by :cc)
           ;; (mapv (fn [[_ hms]]
           ;;         (map (fn [orig-hm i] (assoc orig-hm :i i))
           ;;              hms
           ;;              (add-up (map :i hms)))))
           flatten
           #_(sort-by :f)
       ))))

(defn fill-rest [threshold]
  (let [sum-below-threshold (sum-below threshold)
        countries-threshold (->> sum-below-threshold
                                 (map :cc)
                                 distinct
                                 set)]
    (->> (group-by :f sum-below-threshold)
         (map (fn [[f hms]]
                (->> (group-by :cc hms) keys
                     (cset/difference countries-threshold)
                     (map (fn [cc] {:cc cc :f f :i 0}))
                     (cset/union hms))))
         (reduce into [])
         #_(sort-by :f))))

(defn calc-json-data [threshold]
  (->> (fill-rest threshold)
       (map (fn [{:keys [cc] :as hm}] (assoc hm :cn (com/country-alias cc))))
       (group-by :cn)
       (map-kv (fn [entry]
                 (->> entry
                      (map (fn [{:keys [f i]}]
                             [(to-java-time-local-date f) i]))
                      (sort-by first))))
       (sort-by first (comp - compare))))

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
        (b/add-label :bottom "date")
        (b/add-label :left "Cases")
        (b/add-legend (str "Country > " threshold)
                      legend)
        (r/render-lattice {:width 800 :height 600})
        (save "results/vega/stacked-area.jpg")
        (show))))
