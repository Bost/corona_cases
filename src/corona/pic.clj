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

(defn dates
  "
  (
  #inst \"2020-01-21T23:00:00.000-00:00\"
  #inst \"2020-01-22T23:00:00.000-00:00\"
  #inst \"2020-01-23T23:00:00.000-00:00\"
  #inst \"2020-01-24T23:00:00.000-00:00\"
  #inst \"2020-01-25T23:00:00.000-00:00\"
  #inst \"2020-01-26T23:00:00.000-00:00\")
  "
  []
  (->> (data/dates
        #_{:limit-fn (fn [coll] (take 20 coll))})
       #_(sort)
       #_(reverse)
       #_(drop 3)))

(def dates-memo (memo/memo dates))

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
  (let [dates (dates-memo)
        country-codes
        #_["AU" "AT" "AE"]
        (data/all-affected-country-codes
         #_{:limit-fn (fn [coll] (take 10 coll))})]

    (->> dates
         (map-indexed (fn [idx _]
                        (->> country-codes
                             (map (fn [cc]
                                    (as-> {:fun (fn [coll] (nth coll idx))
                                           :pred (data/pred-fn cc)} $
                                      (data/eval-fun $)
                                      (select-keys $ [:f :i])
                                      (into {:cc cc} $)))))))
         (reduce into [])
         (sort-by :f))))

(def data-memo (memo/memo data))

(def json
  {:$schema "https://vega.github.io/schema/vega-lite/v4.json"
   :width 300 :height 200
   :data {:url "data/unemployment-across-industries.json"}
   :mark "area"
   :encoding
   {:x     {:timeUnit "yearmonth" :field "date" :type "temporal" :axis {:format "%Y"}}
    :y     {:aggregate "sum" :field "count" :type "quantitative"}
    :color {:field "series" :type "nominal" :scale {:scheme "category20b"}}}})

#_(def unemployment (read-json "resources/unemployment-across-industries.json"))

#_(def unemployment-area (->>
                        (data)
                        (group-by :cc)
                        (map-kv (fn [entry]
                                  (map (fn [{:keys [f i]}]
                                         #_(println "type " (type (dt/local-date year month)))
                                         [(to-java-time-local-date f) i])
                                       entry)))
                        (sort-by first (comp - compare))))

(defn normalize-by-f [f hms]
  #_hms
  [{:cc d/default-2-country-code :f f
    :i (->> hms
            (map (fn [hm] (select-keys hm [:i])))
            (apply merge-with +)
            :i)}])

(defn normalize-by-cc [hms]
  #_hms
  (->> hms
       (map (fn [hm] (select-keys hm [:i])))
       (apply merge-with +)
       :i))

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

(defn below [treshold hms]
  (->> hms
       (map (fn [{:keys [i] :as hm}] (if (< i treshold)
                                       (assoc hm :cc d/default-2-country-code)
                                       hm)))))

(defn sum-below [treshold]
  ;; the previous day maximum must be checked; make sure the default mapping of ships wont' get lost
  (->> (data-memo)
       (below treshold)
       #_(drop (* 3 4))
       (group-by :f)
       (mapv (fn [[f hms]]
               (->> (group-by :cc hms)
                    (mapv (fn [[cc hms]]
                            #_(normalize-by-cc hms)
                            {:cc cc :f f :i (normalize-by-cc hms)})))))
       flatten
       (sort-by :f)
       (group-by :cc)
       (mapv (fn [[cc hms]]
               (map (fn [orig-hm i]
                      (assoc orig-hm :i i))
                    hms
                    (->> hms (map :i) add-up))))
       flatten
       ))

(defn countries [treshold]
  (->> (sum-below treshold)
       (map (fn [hm] (select-keys hm [:cc])))
       distinct
       (map vals)
       (reduce into [])))

(defn fill-rest [treshold]
  (let [dates (dates)
        sum-below-treshold (sum-below treshold)
        countries-treshold (countries treshold)]
    (->> (group-by :f sum-below-treshold)
         (map (fn [[k hms]]
                (->>
                 (clojure.set/difference (set countries-treshold) (->> (group-by :cc hms)
                                                                       keys
                                                                       set))
                 (mapv (fn [cc] {:cc cc :f k :i 0}))
                 set
                 (clojure.set/union (set hms)))))
         (reduce into [])
         (sort-by :f))))

(defn calc-json-data [treshold]
  (->> (fill-rest treshold)
       (map (fn [{:keys [cc] :as hm}] (assoc hm :cn (com/country-alias cc))))
       (group-by :cn)
       (map-kv (fn [entry]
                 (->> entry
                      (map (fn [{:keys [f i]}]
                             [(to-java-time-local-date f) i]))
                      (sort-by first))))
       (sort-by first (comp - compare))))

#_(def unemployment-area (->>
                        unemployment
                        #_(take 300)
                        (group-by :series)
                        (map-kv (fn [entry]
                                  (map (fn [{:keys [year month count]}]
                                         #_(println "type " (type (dt/local-date year month)))
                                         [(dt/local-date year month) count])
                                       entry)))
                        (sort-by first (comp - compare))))
#_(def json-data unemployment-area)

(defn show-pic [treshold]
  (let [json-data (calc-json-data treshold)
        pal (cycle (c/palette-presets :category20b))
        legend (reverse (map #(vector :rect %2 {:color %1}) pal (keys json-data)))]
    (-> (b/series [:grid] [:sarea json-data])
        (b/preprocess-series)
        (b/update-scale :y :fmt int)
        (b/add-axes :bottom)
        (b/add-axes :left)
        (b/add-label :bottom "date")
        (b/add-label :left "Cases")
        (b/add-legend (str "Country > " treshold)
                      legend)
        (r/render-lattice {:width 800 :height 600})
        (save "results/vega/stacked-area.jpg")
        (show))))
