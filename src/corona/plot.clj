(ns corona.plot
  (:require [cljplot.build :as b]
            [cljplot.common :as plotcom]
            [cljplot.render :as r]
            ;; XXX cljplot.core must be required otherwise an empty plot is
            ;; shown. WTF?
            [cljplot.core]
            [clojure.set :as cset]
            [clojure2d.color :as c]
            [clojure2d.core :as c2d]
            [corona.api.v1 :as v1]
            [corona.common :as com]
            [corona.core :as cc]
            [corona.defs :as d])
  (:import [java.time LocalDate ZoneId]))

(defn metrix-prefix-unit
  "Show 1K instead of 1000; i.e. kilo, mega etc.
  See https://en.wikipedia.org/wiki/Metric_prefix#List_of_SI_prefixes"
  [v]
  (int v)
  #_(str (/ v 1000) "k")
  #_(str (/ v (* 1000 1000)) "M"))

(defn to-java-time-local-date [java-util-date]
  (LocalDate/ofInstant (.toInstant java-util-date)
                       (ZoneId/systemDefault)
                       #_(ZoneId/of"Europe/Berlin")))

(defn sort-by-country-name [mapped-hm]
  (sort-by first (comp - compare)
           mapped-hm))

(defn sort-by-last-val [mapped-hm]
  (let [order (->> mapped-hm
                   (map (fn [[cc hms]] [cc (second (last hms))]))
                   (sort-by second)
                   (reverse)
                   (map first))]
    (transduce (map (fn [cc] {cc (get mapped-hm cc)}))
               into []
               order)))

(def data
  (v1/pic-data)
  #_(v2/pic-data))

(defn sum-for-pred
  "Calculate sums for a given country code or all countries if the country code
  is unspecified"
  [{:keys [cc]}]
  (let [pred-fn (fn [hm] (if cc (= cc (:cc hm)) true))]
    (->> data
         #_(v1/pic-data)
         (filter pred-fn)
         (group-by :f)
         (map (fn [[f hms]]
                  [
                   ;; confirmed cases is the sum of all others
                   {:cc cc :f f :case :c :cnt (reduce + (map :c hms))}
                   {:cc cc :f f :case :r :cnt (reduce + (map :r hms))}
                   {:cc cc :f f :case :d :cnt (reduce + (map :d hms))}
                   {:cc cc :f f :case :i :cnt (reduce + (map :i hms))}]))
         flatten)))

(defn stats-for-country [prm]
  (let [hm (group-by :case (sum-for-pred prm))
        mapped-hm (plotcom/map-kv
                   (fn [entry]
                     (sort-by first
                              (map (fn [{:keys [f cnt]}]
                                     [(to-java-time-local-date f) cnt])
                                   entry)))
                   hm)]
    ;; sort - keep the "color order" of cases fixed; don't
    ;; recalculate it
    (reverse (transduce (map (fn [case] {case (get mapped-hm case)}))
                        into []
                        [:i :r :d :c]))))

(defn fmt-last-date []
  ((comp com/fmt-date :f last) (sort-by :f data)))

(defn label [cc]
  (format
   "%s; %s: %s"
   (fmt-last-date)
   cc/bot-name
   (if cc
     (format "Stats for %s %s"
             (com/country-name-aliased cc)
             (com/encode-cmd cc))
     (format "Stats %s %s"
             (com/country-name-aliased d/worldwide-2-country-code)
             (com/encode-cmd d/worldwide-2-country-code)))))

(defn plot-country
  "Colors: https://clojure2d.github.io/clojure2d/docs/static/palettes.html"
  [{:keys [cc] :as prm}]
  (let [json-data (stats-for-country prm)
        sarea-data (->> json-data
                        (remove (fn [[case vs]] (= :c case))))

        palette (cycle
                 (
                  #_identity
                  reverse
                  (take-last 3
                        (c/palette-presets
                         :gnbu-6
                         #_:accent
                         #_:pubu-3
                         #_:set2
                         #_:rdbu-3
                         #_:greens-3
                         #_:brbg-3
                         #_:ylgnbu-3
                         #_:category20b))))
        stroke-size 1.5
        stroke-confirmed {:color (last (c/palette-presets :ylgn-6)) :stroke {:size stroke-size}}
        stroke-sick {:color :black #_(last (c/palette-presets :gnbu-9))
                     :stroke {:size stroke-size
                              ;; :dash [20.0] :dash-phase 10
                              ;; :dash [5.0 2.0 2.0 2.0]
                              ;; :dash [10.0 5.0] :join :miter
                              :dash [4.0] :dash-phase 2.0}}
        legend
        (reverse
         (conj (map #(vector :rect %2 {:color %1}) palette
                    (map (fn [k] (get {:i "Sick" :d "Deaths" :r "Recovered"} k))
                         (keys sarea-data)))
               [:line "Confirmed"     stroke-confirmed]
               [:line "Sick absolute" stroke-sick]))]
    (let [sick-line-data (->> json-data
                              (filter (fn [[case vs]] (= :i case)))
                              (reduce into [])
                              (second))
          confirmed-line-data (->> json-data
                                   (filter (fn [[case vs]] (= :c case)))
                                   (reduce into [])
                                   (second))
          render-res
          (-> (b/series [:grid]
                        [:sarea sarea-data {:palette palette}]
                        [:line confirmed-line-data stroke-confirmed]
                        [:line sick-line-data stroke-sick])
              (b/preprocess-series)
              (b/update-scale :y :fmt metrix-prefix-unit)
              (b/add-axes :bottom)
              (b/add-axes :left)
              #_(b/add-label :bottom "Date")
              #_(b/add-label :left "Sick")
              (b/add-label :top (label cc)
                           {:color (c/darken :steelblue) :font-size 14})
              (b/add-legend "" legend)
              (r/render-lattice {:width 800 :height 600}))]
      #_(def sick-line sick-line-data)
      #_(def sarea sarea-data)
      #_(-> render-res
            (save "/tmp/stacked-area.png")
            #_(show))
      (-> render-res (c2d/get-image)))))


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

(defn stats-all-countries-ill [threshold]
  (let [hm (group-by :cn
                     (map (fn [{:keys [cc] :as hm}]
                            #_hm
                            (assoc hm :cn cc)
                            #_(assoc hm :cn (com/country-alias cc)))
                          (fill-rest threshold)))
        mapped-hm (plotcom/map-kv
                   (fn [entry]
                     (sort-by first
                              (map (fn [{:keys [f i]}]
                                     [(to-java-time-local-date f) i])
                                   entry)))
                   hm)]
    (sort-by-last-val
     #_sort-by-country-name
     (transduce
      (map (fn [[cc v]] {(com/country-alias cc) v}))
      into {}
      mapped-hm))))

(defn plot-all-countries-ill [threshold]
  (let [json-data (stats-all-countries-ill threshold)
        pal (cycle (c/palette-presets :category20b))
        ;; TODO add country codes (on a new line)
        ;; TODO rename Others -> Rest
        legend (reverse (map #(vector :rect %2 {:color %1}) pal
                             (keys json-data)))
        render-res
        (-> (b/series [:grid]
                      [:sarea json-data])
            (b/preprocess-series)
            (b/update-scale :y :fmt metrix-prefix-unit)
            (b/add-axes :bottom)
            (b/add-axes :left)
            #_(b/add-label :bottom "Date")
            #_(b/add-label :left "Sick")
            (b/add-label :top (format
                               "%s; %s: Sic cases > %s"
                               (fmt-last-date)
                               cc/bot-name
                               threshold)
                         {:color (c/darken :steelblue) :font-size 14})
            (b/add-legend "" legend)
            (r/render-lattice {:width 800 :height 600}))]
    #_(-> render-res
          (save "/tmp/stacked-area.png")
          #_(show))
    (-> render-res (c2d/get-image))))
