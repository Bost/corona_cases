(ns corona.plot
  (:require [cljplot.build :as b]
            [cljplot.common :as plotcom]
            [cljplot.render :as r]
            [clojure.set :as cset]
            [corona.tables :as t]
            [clojure2d.color :as c]
            [clojure2d.core :as c2d]
            [corona.common :as com]
            [corona.core :as cc]
            [corona.countries :as cr]
            [corona.lang :refer :all]
            [corona.country-codes :refer :all]
            ;; XXX cljplot.core must be required otherwise an empty plot is
            ;; shown. WTF?
            [cljplot.core]
            [corona.core :refer [in?]]
            [corona.defs :as d])
  (:import [java.time LocalDate ZoneId]))

(defmacro dbg [body]
  `(let [x# ~body]
     (println "dbg:" '~body "=" x#)
     x#))

(defn metrics-prefix-formatter [max-val]
  "Show 1k instead of 1000; i.e. kilo, mega etc.
      1 400 -> 1400
     14 000 ->   14k
    140 000 ->  140k
  1 400 000 -> 1400k
  See https://en.wikipedia.org/wiki/Metric_prefix#List_of_SI_prefixes"
  (cond
    (< max-val (dec (bigint 1e4)))  (fn [v] (str (bigint (/ v 1e0)) ""))
    (< max-val (dec (bigint 1e7)))  (fn [v] (str (bigint (/ v 1e3)) "k"))
    (< max-val (dec (bigint 1e10))) (fn [v] (str (bigint (/ v 1e6)) "M"))
    (< max-val (dec (bigint 1e13))) (fn [v] (str (bigint (/ v 1e9)) "G"))
    :else (throw
           (Exception. (format "Value %d must be < max %d)"
                               (fn [v] (str (/ v (bigint 1e9)) "G")))))))

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

(defn sum-for-pred
  "Calculate sums for a given country code or all countries if the country code
  is unspecified."
  [cc stats]
  (let [pred-fn (fn [hm] (if (= cc d/worldwide-2-country-code)
                          true
                          (= cc (:cc hm))))]
    (->> stats
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

(defn stats-for-country [cc stats]
  (let [mapped-hm (plotcom/map-kv
                   (fn [entry]
                     (sort-by first
                              (map (fn [{:keys [f cnt]}]
                                     [(to-java-time-local-date f) cnt])
                                   entry)))
                   (group-by :case (sum-for-pred cc stats)))]
    ;; sort - keep the "color order" of cases fixed; don't
    ;; recalculate it
    (reverse (transduce (map (fn [case] {case (get mapped-hm case)}))
                        into []
                        [:i :r :d :c :p]))))

(defn fmt-last-date [stats]
  ((comp com/fmt-date :f last) (sort-by :f stats)))

(defn plot-label
  "TODO add day number since the outbreak"
  [cc stats]
  (format
   "%s; %s: %s"
   (fmt-last-date stats)
   cc/bot-name
   (format "%s %s %s" s-stats (cr/country-name-aliased cc)
           (com/encode-cmd cc))))

(defn palette-colors [n]
  "Palette https://clojure2d.github.io/clojure2d/docs/static/palettes.html"
  (->> (c/palette-presets :gnbu-6)
       (take-last n)
       (reverse)
       (cycle)))

(def line-cfg
  "By default line-margins are 5%. Setting them to [0 0] may not make up
  for 100% alignment with the axes. There is also some margin in
  canvas, or some other factors as rounding, aligning, java2d rendering
  and aligning etc. See
  https://clojurians.zulipchat.com/#narrow/stream/197967-cljplot-dev/topic/using.20cljplot.20for.20work/near/193681905"
  {:margins {:y [0 0]}})

(def stroke-confirmed (conj line-cfg
                            {:color (last (c/palette-presets :ylgn-6)) }))

(def stroke-sick (conj line-cfg
                       {:color :black #_(last (c/palette-presets :gnbu-9))
                        :stroke {:size 1.5
                                 ;; :dash [20.0] :dash-phase 10
                                 ;; :dash [5.0 2.0 2.0 2.0]
                                 ;; :dash [10.0 5.0] :join :miter
                                 :dash [4.0] :dash-phase 2.0}}))

(defn max-y-val [reducer data]
  (let [xform (comp
               (map (fn [[k v]] v))
               (map (fn [v] (transduce
                             identity
                             max 0
                             (map (fn [[_ n]] n) v)))))]
    (transduce xform reducer 0 data)))

(defn line-data [kw data]
  (second
   (transduce
    (filter (fn [[case vs]] (= kw case)))
    into []
    data)))

(defn plot-country [cc stats]
  (let [json-data (stats-for-country cc stats)
        sarea-data (->> json-data
                        (remove (fn [[case vs]]
                                  (in?
                                   [:c :i :r :d]
                                   #_[:c :p]
                                   case))))
        curves (keys sarea-data)
        palette (palette-colors (count curves))

        legend
        (reverse
         (conj (map #(vector :rect %2 {:color %1})
                    palette
                    (map (fn [k] (get {:i s-sick :d s-deaths :r s-recovered} k))
                         curves))
               [:line s-confirmed     stroke-confirmed]
               [:line s-sick-absolute stroke-sick]
               [:line s-population    stroke-population]))]
    (let [y-axis-formatter (metrics-prefix-formatter
                            ;; population numbers have the `max` values, all
                            ;; other numbers are derived from them
                            (max-y-val max json-data))]
      ;; every chart/series definition is a vector with three fields:
      ;; 1. chart type e.g. :grid, :sarea, :line
      ;; 2. data
      ;; 3. configuration hash-map
      (-> (b/series
           [:grid]
           [:sarea sarea-data {:palette palette}]
           #_[:line (line-data :p json-data) stroke-population]
           #_[:line  (line-data :c json-data) stroke-confirmed]
           #_[:line  (line-data :i json-data) stroke-sick])
          (b/preprocess-series)
          (b/update-scale :y :fmt y-axis-formatter)
          (b/add-axes :bottom)
          (b/add-axes :left)
          #_(b/add-label :bottom "Date")
          #_(b/add-label :left "Sick")
          (b/add-label :top (plot-label cc stats)
                       {:color (c/darken :steelblue) :font-size 14})
          (b/add-legend "" legend)
          (r/render-lattice {:width 800 :height 600})
          (c2d/get-image)))))

(defn group-below-threshold
  "Group all countries w/ the number of ill cases below the threshold under the
  `d/default-2-country-code`"
  [threshold stats]
  (map (fn [{:keys [i] :as hm}] (if (< i threshold)
                                  (assoc hm :cc d/default-2-country-code)
                                  hm))
       stats))

(defn sum-ills-by-date
  "Group the country stats by day and sum up the ill cases"
  [threshold stats]
  (flatten (map (fn [[f hms]]
                  (map (fn [[cc hms]]
                         {:cc cc :f f :i (reduce + (map :i hms))})
                       (group-by :cc hms)))
                (group-by :f (group-below-threshold threshold stats)))))

(defn fill-rest [threshold stats]
  (let [sum-ills-by-date-threshold (sum-ills-by-date threshold stats)
        countries-threshold (set (map :cc sum-ills-by-date-threshold))]
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
                 (group-by :f sum-ills-by-date-threshold)))))

(defn stats-all-countries-ill [threshold stats]
  (let [hm (group-by :cn
                     (map (fn [{:keys [cc] :as hm}]
                            #_hm
                            (assoc hm :cn cc)
                            #_(assoc hm :cn (com/country-alias cc)))
                          (fill-rest threshold stats)))
        mapped-hm (plotcom/map-kv
                   (fn [entry]
                     (sort-by first
                              (map (fn [{:keys [f i]}]
                                     [(to-java-time-local-date f) i])
                                   entry)))
                   hm)]
    #_(sort-by-country-name mapped-hm)
    (sort-by-last-val mapped-hm)))

(defn plot-all-countries-ill [threshold stats]
  (let [json-data (stats-all-countries-ill threshold stats)
        palette (cycle (c/palette-presets :category20b))
        legend (reverse
                (map #(vector :rect %2 {:color %1})
                     palette
                     (map
                      cr/country-alias
                      ;; XXX b/add-legend doesn't accept newline char \n
                      #_(fn [cc] (format "%s %s"
                                       cc
                                       (com/country-alias cc)))
                      (keys json-data))))
        y-axis-formatter (metrics-prefix-formatter
                          ;; `+` means: sum up all sick/ill cases
                          (max-y-val + json-data))]
    (-> (b/series [:grid]
                  [:sarea json-data])
        (b/preprocess-series)
        (b/update-scale :y :fmt y-axis-formatter)
        (b/add-axes :bottom)
        (b/add-axes :left)
        #_(b/add-label :bottom "Date")
        #_(b/add-label :left "Sick")
        (b/add-label :top (format
                           "%s; %s: %s > %s"
                           (fmt-last-date stats)
                           cc/bot-name
                           s-sick-cases
                           threshold)
                     {:color (c/darken :steelblue) :font-size 14})
        (b/add-legend "" legend)
        (r/render-lattice {:width 800 :height 600})
        (c2d/get-image))))
