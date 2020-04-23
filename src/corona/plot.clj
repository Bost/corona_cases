(ns corona.plot
  (:require [cljplot.build :as b]
            [cljplot.common :as plotcom]
            ;; XXX cljplot.core must be required otherwise an empty plot is
            ;; shown when released. WTF?
            [cljplot.core]
            [cljplot.render :as r]
            [clojure.set :as cset]
            [clojure2d.color :as c]
            [clojure2d.core :as c2d]
            [corona.common :as com]
            [corona.core :as cc]
            [corona.countries :as cr]
            [corona.defs :as d]
            [corona.lang :refer :all]
            [utils.core :refer :all])
  (:import [java.time LocalDate ZoneId]))

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
           (Exception. (format "Value %d must be < max %d" max-val 1e9)))))

(defn- boiler-plate [{:keys [series y-axis-formatter legend label label-conf]}]
  (-> series
      (b/preprocess-series)
      (b/add-axes :bottom)
      (b/add-axes :left)
      (b/update-scale :y :fmt y-axis-formatter)
      (b/add-legend "" legend)
      (b/add-label :top label label-conf)
      (r/render-lattice {:width 800 :height 600})
      (c2d/get-image)))

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
                   {:cc cc :f f :case :p :cnt
                    (bigint (/ (:p (first hms)) 1e3))
                    #_(reduce + (map :p hms))
                    #_(bigint (/ (get population cc) (bigint 1e3)))}
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

(defn fmt-day [day] (format "%s %s" s-day day))

(defn plot-label
  "day - day since the outbreak
  cc - country code
  stats - statistics ill, confirmed, etc. for the given country code"
  [day cc stats]
  (format "%s; %s; %s: %s"
          (fmt-day day)
          (fmt-last-date stats)
          cc/bot-name
          (format "%s %s %s"
                  s-stats
                  (cr/country-name-aliased cc)
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
  ;; TODO check if there are negative numbers in the line; if not then
  ;; {:margins {:y [0 0]}} otherwise look for the min and set the :margins
  {:margins {:y [0 0]}})

(def stroke-population (conj line-cfg {:color :red}))

(def stroke-confirmed (conj line-cfg {:color
                                      (last (c/palette-presets :ylgn-6))}))

(def stroke-sick (conj line-cfg {:color :black
                                 :stroke {:size 1.5
                                          ;; :dash [20.0] :dash-phase 10
                                          ;; :dash [5.0 2.0 2.0 2.0]
                                          ;; :dash [10.0 5.0] :join :miter
                                          :dash [4.0] :dash-phase 2.0}}))

(defn max-y-val [reducer data]
  (transduce (comp (map (fn [[k v]] v))
                   (map (fn [v] (transduce
                                identity
                                max 0
                                (map (fn [[_ n]] n) v)))))
             reducer 0 data))

(defn line-data [kw data]
  (second (transduce (filter (fn [[case vs]] (= kw case)))
                     into []
                     data)))

(defn plot-country
  "Country-specific cumulative plot of sick, recovered, deaths and sick-absolute
  cases."
  [{:keys [day cc stats]}]
  (let [base-data (stats-for-country cc stats)
        sarea-data (remove (fn [[case vs]]
                             (in? #_[:c :i :r :d] [:c :p] case))
                           base-data)
        curves (keys sarea-data)
        palette (palette-colors (count curves))]
    ;; every chart/series definition is a vector with three fields:
    ;; 1. chart type e.g. :grid, :sarea, :line
    ;; 2. data
    ;; 3. configuration hash-map

    ;; TODO annotation by value and labeling doesn't work:
    ;; :annotate? true
    ;; :annotate-fmt "%.1f"
    ;; {:label (plot-label day cc stats)}
    (boiler-plate
     {:series (b/series
               [:grid]
               [:sarea sarea-data {:palette palette}]
               #_[:line (line-data :p base-data) stroke-population]
               [:line (line-data :c base-data) stroke-confirmed]
               [:line (line-data :i base-data) stroke-sick])
      :y-axis-formatter  (metrics-prefix-formatter
                          ;; population numbers have the `max` values, all
                          ;; other numbers are derived from them

                          ;; don't display the population data for the moment
                          (max-y-val + sarea-data))
      :legend (reverse
               (conj (map #(vector :rect %2 {:color %1})
                          palette
                          (map (fn [k] (get {:i s-sick :d s-deaths :r s-recovered} k))
                               curves))
                     [:line s-confirmed     stroke-confirmed]
                     [:line s-sick-absolute stroke-sick]
                     #_[:line s-population    stroke-population]))
      :label (plot-label day cc stats)
      :label-conf (conj {:color (c/darken :steelblue)} #_{:font-size 14})})))

(defn group-below-threshold
  "Group all countries w/ the number of ill cases below the threshold under the
  `d/default-2-country-code` so that max 10 countries are displayed in the plot"
  [{:keys [case threshold stats] :as prm}]
  (let [res (map (fn [hm] (if (< (case hm) threshold)
                           (assoc hm :cc d/default-2-country-code)
                           hm))
                 stats)]
    ;; TODO implement also recalculation for when (< (count (group-by :cc res)) 6)
    ;; so no less that 6 countries appear in the plot
    (if (> (count (group-by :cc res)) 10)
      (let [raised-threshold (+ 5000 threshold)]
        (printf (str "INFO Case %s %s countries above threshold. "
                     "Raising threshold to %s and recalculating...\n")
                case
                (count (group-by :cc res))
                raised-threshold)
        (group-below-threshold (assoc prm :threshold raised-threshold)))
      {:data res :threshold threshold})))

(defn sum-all-by-date-by-case
  "Group the country stats by day and sum up the ill cases"
  [{:keys [case] :as prm}]
  (let [prm (group-below-threshold prm)
        {data :data} prm]
    (let [res (flatten (map (fn [[f hms]]
                              (map (fn [[cc hms]]
                                     {:cc cc :f f case (reduce + (map case hms))})
                                   (group-by :cc hms)))
                            (group-by :f data)))]
      (update prm :data (fn [_] res)))))

(defn fill-rest [{:keys [case] :as prm}]
  (let [date-sums (sum-all-by-date-by-case prm)
        {sum-all-by-date-by-case-threshold :data} date-sums
        countries-threshold (set (map :cc sum-all-by-date-by-case-threshold))
        res (reduce into []
                    (map (fn [[f hms]]
                           (cset/union
                            hms
                            (map (fn [cc] {:cc cc :f f case 0})
                                 (cset/difference countries-threshold
                                                  (keys (group-by :cc hms)))))
                           #_(->> (group-by :cc hms)
                                  keys
                                  (cset/difference countries-threshold)
                                  (map (fn [cc] {:cc cc :f f :i 0}))
                                  (cset/union hms)))
                         (group-by :f sum-all-by-date-by-case-threshold)))]
    (update date-sums :data (fn [_] res))))

(defn stats-all-by-case [{:keys [case] :as prm}]
  (let [fill-rest-stats (fill-rest prm)
        {data :data threshold :threshold} fill-rest-stats
        mapped-hm (plotcom/map-kv
                   (fn [entry]
                     (sort-by first
                              (map (fn [fill-rest-stats]
                                     [(to-java-time-local-date
                                       (:f fill-rest-stats))
                                      (case fill-rest-stats)])
                                   entry)))
                   (group-by :cc data))]
    #_(sort-by-country-name mapped-hm)
    (update fill-rest-stats :data (fn [_] (sort-by-last-val mapped-hm)))))

(defn plot-all-by-case
  "Case-specific plot for the sum of all countries."
  [{:keys [day case stats] :as prm}]
  (let [{json-data :data threshold :threshold} (stats-all-by-case prm)]
    (boiler-plate
     {:series (b/series [:grid] [:sarea json-data])
      :legend (reverse
               (map #(vector :rect %2 {:color %1})
                    (cycle (c/palette-presets :category20b))
                    (map
                     cr/country-alias
                     ;; XXX b/add-legend doesn't accept newline char \n
                     #_(fn [cc] (format "%s %s"
                                       cc
                                       (com/country-alias cc)))
                     (keys json-data))))
      :y-axis-formatter (metrics-prefix-formatter
                         ;; `+` means: sum up all sick/ill cases
                         (max-y-val + json-data))
      :label (format "%s; %s; %s: %s > %s"
                     (fmt-day day)
                     (fmt-last-date stats)
                     cc/bot-name
                     (case {:c s-confirmed :i s-sick-cases :r s-recovered :d s-deaths})
                     threshold)
      :label-conf {:color (c/darken :steelblue) :font-size 14}})))

(defn line-stroke [color]
  (conj line-cfg {:color color
                  :stroke {:size 3
                           ;; :dash [20.0] :dash-phase 10
                           ;; :dash [5.0 2.0 2.0 2.0]
                           ;; :dash [10.0 5.0] :join :miter
                           ;; :dash [4.0] :dash-phase 2.0
                           }}))

(defn plot-all-absolute [{:keys [day case stats] :as prm}]
  (let [{full-data :data threshold :threshold} (stats-all-by-case prm)
        data (->> full-data
                  (remove (fn [[cc _]] (= cc corona.country-codes/qq))))
        palette (cycle (c/palette-presets
                        #_:tableau-10
                        #_:tableau-10-2
                        #_:color-blind-10
                        #_:category10
                        :category20b))]
    (boiler-plate
     {:series (->> (mapv (fn [[cc cc-data] color] [:line cc-data (line-stroke color)])
                         data palette)
                   (into [[:grid]])
                   (apply b/series))
      :y-axis-formatter (metrics-prefix-formatter
                         ;; population numbers have the `max` values, all
                         ;; other numbers are derived from them

                         ;; don't display the population data for the moment
                         (max-y-val + data))
      :legend (map (fn [c r] (vector :rect r {:color c}))
                   palette
                   (map cr/country-alias (keys data)))
      :label (format
              "%s; %s; %s: %s > %s"
              (fmt-day day)
              (fmt-last-date stats)
              cc/bot-name
              (str (case {:c s-confirmed :i s-sick-cases :r s-recovered :d s-deaths})
                   " " s-absolute)
              threshold)
      :label-conf {:color (c/darken :steelblue) :font-size 14}})))
