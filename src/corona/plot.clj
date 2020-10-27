(printf "Current-ns [%s] loading %s\n" *ns* 'corona.plot)

(ns corona.plot
  (:require
   [cljplot.build :as b]
   [cljplot.common :as plotcom]
   ;; XXX cljplot.core must be required otherwise an empty plot is
   ;; shown when released. WTF?
   [cljplot.core]
   [cljplot.render :as r]
   [clojure.set :as cset]
   [clojure2d.color :as c]
   [clojure2d.core :as c2d]
   [corona.common :as com]
   [corona.countries :as ccr]
   [corona.country-codes :as ccc]
   [corona.lang :as l]
   [utils.core :refer [in?] :exclude [id]]
   [taoensso.timbre :as timbre :refer [debugf
                                       #_info infof
                                       ;; warn errorf fatalf
                                       ]]
   [corona.api.expdev07 :as data]
   )
  (:import java.awt.image.BufferedImage
           java.io.ByteArrayOutputStream
           javax.imageio.ImageIO
           [java.time LocalDate ZoneId]))

;; (set! *warn-on-reflection* true)

(defn metrics-prefix-formatter
  "Show 1k instead of 1000; i.e. kilo, mega etc.
      1 400 -> 1400
     14 000 ->   14k
    140 000 ->  140k
  1 400 000 -> 1400k
  See https://en.wikipedia.org/wiki/Metric_prefix#List_of_SI_prefixes"
  [max-val]
  (cond
    (< max-val (dec (bigint 1e4)))  (fn [v] (str (bigint (/ v 1e0)) ""))
    (< max-val (dec (bigint 1e7)))  (fn [v] (str (bigint (/ v 1e3)) "k"))
    (< max-val (dec (bigint 1e10))) (fn [v] (str (bigint (/ v 1e6)) "M"))
    (< max-val (dec (bigint 1e13))) (fn [v] (str (bigint (/ v 1e9)) "G"))
    :else (throw
           (Exception. (format "Value %d must be < max %d" max-val 1e9)))))

(defn- boiler-plate [{:keys [series y-axis-formatter legend label label-conf]}]
  ;; (-> series
  ;;     (b/preprocess-series)
  ;;     (b/add-axes :bottom)
  ;;     (b/add-axes :left)
  ;;     (b/update-scale :y :fmt y-axis-formatter)
  ;;     (b/add-legend "" legend)
  ;;     (b/add-label :top label label-conf)
  ;;     (r/render-lattice {:width 800 :height 600})
  ;;     (c2d/get-image))
  (c2d/get-image
   (r/render-lattice
    (b/add-label
     (b/add-legend
      (b/update-scale
       (b/add-axes
        (b/add-axes
         (b/preprocess-series series)
         :bottom)
        :left)
       :y :fmt y-axis-formatter)
      "" legend)
     :top label label-conf)
    {:width 800 :height 600})))

(defn to-java-time-local-date [^java.util.Date java-util-date]
  (LocalDate/ofInstant (.toInstant java-util-date) (ZoneId/systemDefault)))

(defn sort-by-country-name [mapped-hm]
  (sort-by first (comp - compare)
           mapped-hm))

(defn sort-by-last-val [mapped-hm]
  (let [order (->> mapped-hm
                   (map (fn [[ccode hms]] [ccode (second (last hms))]))
                   (sort-by second)
                   (reverse)
                   (map first))]
    (transduce (map (fn [ccode] {ccode (get mapped-hm ccode)}))
               into []
               order)))

(defn sum-for-pred
  "Calculate sums for a given country code or all countries if the country code
  is unspecified."
  [ccode stats]
  (let [pred-fn (fn [hm] (if (= ccode ccc/worldwide-2-country-code)
                          true
                          (= ccode (:cc hm))))]
    (->> stats
         (filter pred-fn)
         (group-by :t)
         (map (fn [[t hms]]
                  [
                   ;; confirmed cases is the sum of all others
                   {:cc ccode :t t :case :p :cnt
                    (bigint (/ (:p (first hms)) 1e3))
                    #_(reduce + (map :p hms))
                    #_(bigint (/ (get population ccode) (bigint 1e3)))}
                   {:cc ccode :t t :case :c :cnt (reduce + (map :c hms))}
                   {:cc ccode :t t :case :r :cnt (reduce + (map :r hms))}
                   {:cc ccode :t t :case :d :cnt (reduce + (map :d hms))}
                   {:cc ccode :t t :case :i :cnt (reduce + (map :i hms))}]))
         flatten)))

(defn stats-for-country [ccode stats]
  (let [mapped-hm (plotcom/map-kv
                   (fn [entry]
                     (sort-by first
                              (map (fn [{:keys [t cnt]}]
                                     [(to-java-time-local-date t) cnt])
                                   entry)))
                   (group-by :case (sum-for-pred ccode stats)))]
    ;; sort - keep the "color order" of cases fixed; don't
    ;; recalculate it
    (reverse (transduce (map (fn [case-kw] {case-kw (get mapped-hm case-kw)}))
                        into []
                        [:i :r :d :c :p]))))

(defn fmt-last-date [stats]
  ((comp com/fmt-date :t last) (sort-by :t stats)))

(defn fmt-day [day] (format "%s %s" l/day day))

(defn plot-label
  "report-nr - Nth report since the outbreak
  ccode - country code
  stats - statistics active, confirmed, etc. for the given country code"
  [report-nr ccode stats]
  (format "%s; %s; %s: %s"
          (fmt-day report-nr)
          (fmt-last-date stats)
          com/bot-name
          (format "%s %s %s"
                  l/stats
                  (ccr/country-name-aliased ccode)
                  (com/encode-cmd ccode))))

(defn palette-colors
  "Palette https://clojure2d.github.io/clojure2d/docs/static/palettes.html"
  [n]
  (->> (c/palette-presets :gnbu-6)
       (take-last n)
       (reverse)
       (cycle)))

(def ^:const line-cfg
  "By default line-margins are 5%. Setting them to [0 0] may not make up
  for 100% alignment with the axes. There is also some margin in
  canvas, or some other factors as rounding, aligning, java2d rendering
  and aligning etc. See
  https://clojurians.zulipchat.com/#narrow/stream/197967-cljplot-dev/topic/using.20cljplot.20for.20work/near/193681905"
  ;; TODO check if there are negative numbers in the line; if not then
  ;; {:margins {:y [0 0]}} otherwise look for the min and set the :margins
  {:margins {:y [0 0]}})

(def ^:const stroke-population (conj line-cfg {:color :red}))

(def ^:const stroke-confirmed
  (conj line-cfg {:color
                  (last (c/palette-presets :ylgn-6))}))

(def ^:const stroke-sick
  (conj line-cfg {:color :black
                  :stroke {:size 1.5
                           ;; :dash [20.0] :dash-phase 10
                           ;; :dash [5.0 2.0 2.0 2.0]
                           ;; :dash [10.0 5.0] :join :miter
                           :dash [4.0] :dash-phase 2.0}}))

(defn max-y-val [reducer data]
  (transduce (comp (map (fn [[_ v]] v))
                   (map (fn [v] (transduce
                                identity
                                max 0
                                (map (fn [[_ n]] n) v)))))
             reducer 0 data))

(defn line-data [kw data]
  (second (transduce (filter (fn [[case _]] (= kw case)))
                     into []
                     data)))

(defn toByteArrayAutoClosable
  "Thanks to https://stackoverflow.com/a/15414490"
  [^BufferedImage image]
  (with-open [out (new ByteArrayOutputStream)]
    (let [fmt-name "png"] ;; png is an informal format name.
      (ImageIO/write image fmt-name out)
      (.toByteArray out))))

(defn calc-plot-country-fn
  "Country-specific cumulative plot of sick, recovered, deaths and sick-absolute
  cases."
  [ccode & [stats day]]
  (let [base-data (stats-for-country ccode stats)
        sarea-data (remove (fn [[case-kw _]]
                             (in? #_[:c :i :r :d] [:c :p] case-kw))
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
    ;; {:label (plot-label day ccode stats)}
    (let [img
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
                                (map (fn [k] (get {:i l/active :d l/deaths :r l/recovered} k))
                                     curves))
                           [:line l/confirmed     stroke-confirmed]
                           [:line l/sick-absolute stroke-sick]
                           #_[:line l/people    stroke-population]))
            :label (plot-label day ccode stats)
            :label-conf (conj {:color (c/darken :steelblue)} #_{:font-size 14})})]
      (let [img-byte-array (toByteArrayAutoClosable img)]
        (debugf "[plot-country] ccode %s; img-size %s" ccode (count img-byte-array))
        img-byte-array))))

(defn plot-country
  "The optional params `stats`, `day` are used only for the first calculation
  See http://clojure-goes-fast.com/ https://github.com/clojure-goes-fast/
  TODO https://github.com/clojure-goes-fast/clj-async-profiler
  "
  [country-code & [stats day]]
  (data/from-cache (fn [] (calc-plot-country-fn country-code stats day))
                   [:plot (keyword country-code)]))

(defn group-below-threshold
  "Group all countries w/ the number of active cases below the threshold under the
  `ccc/default-2-country-code` so that max 10 countries are displayed in the
  plot"
  [{:keys [case threshold threshold-increase stats] :as prm}]
  (let [max-plot-lines 10
        res (map (fn [hm] (if (< (case hm) threshold)
                           (assoc hm :cc ccc/default-2-country-code)
                           hm))
                 stats)]
    ;; TODO implement recalculation for decreasing case numbers (e.g. sics)
    (if (> (count (group-by :cc res)) max-plot-lines)
      (let [raised-threshold (+ threshold-increase threshold)]
        (infof "Case %s; %s countries above threshold. Raise to %s"
               case (count (group-by :cc res)) raised-threshold)
        (group-below-threshold (assoc prm :threshold raised-threshold)))
      {:data res :threshold threshold})))

(defn sum-all-by-date-by-case
  "Group the country stats by day and sum up the active cases"
  [{:keys [case] :as prm-orig}]
  (let [prm (group-below-threshold prm-orig)]
    (let [res (flatten (map (fn [[t hms]]
                              (map (fn [[ccode hms]]
                                     {:cc ccode :t t case (reduce + (map case hms))})
                                   (group-by :cc hms)))
                            (group-by :t (:data prm))))]
      (update prm :data (fn [_] res)))))

(defn fill-rest [{:keys [case] :as prm}]
  (let [date-sums (sum-all-by-date-by-case prm)
        {sum-all-by-date-by-case-threshold :data} date-sums
        countries-threshold (set (map :cc sum-all-by-date-by-case-threshold))
        res (reduce into []
                    (map (fn [[t hms]]
                           (cset/union
                            hms
                            (map (fn [ccode] {:cc ccode :t t case 0})
                                 (cset/difference countries-threshold
                                                  (keys (group-by :cc hms)))))
                           #_(->> (group-by :cc hms)
                                  keys
                                  (cset/difference countries-threshold)
                                  (map (fn [ccode] {:cc ccode :t t :i 0}))
                                  (cset/union hms)))
                         (group-by :t sum-all-by-date-by-case-threshold)))]
    (update date-sums :data (fn [_] res))))

(defn stats-all-by-case [{:keys [case] :as prm}]
  (let [fill-rest-stats (fill-rest prm)
        data (:data fill-rest-stats)
        mapped-hm (plotcom/map-kv
                   (fn [entry]
                     (sort-by first
                              (map (fn [fill-rest-stats]
                                     [(to-java-time-local-date
                                       (:t fill-rest-stats))
                                      (case fill-rest-stats)])
                                   entry)))
                   (group-by :cc data))]
    #_(sort-by-country-name mapped-hm)
    (update fill-rest-stats :data (fn [_] (sort-by-last-val mapped-hm)))))

(defn calc-plot-sum-by-case-fn
  "Case-specific plot for the sum of all countries."
  [case-kw stats day]
  (let [
        prm {
             :day day
             :stats stats
             :threshold (com/min-threshold case-kw)
             :threshold-increase (com/threshold-increase case-kw)
             :case case-kw
             }

        {json-data :data threshold-recaltulated :threshold} (stats-all-by-case prm)]
    (let [img
          (boiler-plate
           {:series (b/series [:grid] [:sarea json-data])
            :legend (reverse
                     (map #(vector :rect %2 {:color %1})
                          (cycle (c/palette-presets :category20b))
                          (map
                           ccr/country-alias
                           ;; XXX b/add-legend doesn't accept newline char \n
                           #_(fn [ccode] (format "%s %s"
                                             ccode
                                             (com/country-alias ccode)))
                           (keys json-data))))
            :y-axis-formatter (metrics-prefix-formatter
                               ;; `+` means: sum up all active cases
                               (max-y-val + json-data))
            :label (format "%s; %s; %s: %s > %s"
                           (fmt-day day)
                           (fmt-last-date stats)
                           com/bot-name
                           (->> [l/confirmed l/recovered l/deaths l/active-cases ]
                                (zipmap com/basic-cases)
                                case-kw)
                           #_(case-kw {:c l/confirmed :i l/active-cases :r l/recovered :d l/deaths})
                           threshold-recaltulated)
            :label-conf {:color (c/darken :steelblue) :font-size 14}})]
      (let [img-byte-array (toByteArrayAutoClosable img)]
        (debugf "[plot-sum-by-case] case %s; img-size %s" case-kw (count img-byte-array))
        img-byte-array))))

(defn plot-sum-by-case
  "The optional params `stats`, `day` are used only for the first calculation"
  [case-kw & [stats day]]
  (data/from-cache (fn [] (calc-plot-sum-by-case-fn case-kw stats day))
                   [:plot :sum case-kw]))

(defn line-stroke [color]
  (conj line-cfg {:color color
                  :stroke {:size 3
                           ;; :dash [20.0] :dash-phase 10
                           ;; :dash [5.0 2.0 2.0 2.0]
                           ;; :dash [10.0 5.0] :join :miter
                           ;; :dash [4.0] :dash-phase 2.0
                           }}))

(defn calc-plot-absolute-by-case-fn
  [case-kw stats day]
  (let [
        threshold (com/min-threshold case-kw)
        prm {
             :day day
             :stats stats
             :threshold threshold
             :threshold-increase (com/threshold-increase case-kw)
             :case case-kw
             }

        json-data (->> (:data (stats-all-by-case prm))
                       #_(remove (fn [[ccode _]] (= ccode ccc/qq))))
        palette (cycle (c/palette-presets
                        #_:tableau-10
                        #_:tableau-10-2
                        #_:color-blind-10
                        #_:category10
                        :category20b))]
    (let [img
          (boiler-plate
           {:series (->> (mapv (fn [[_ ccode-data] color]
                                 [:line ccode-data (line-stroke color)])
                               json-data
                               palette)
                         (into [[:grid]])
                         (apply b/series))
            :y-axis-formatter (metrics-prefix-formatter
                               ;; population numbers have the `max` values, all
                               ;; other numbers are derived from them

                               ;; don't display the population json-data for the moment
                               (max-y-val + json-data))
            :legend (map (fn [c r] (vector :rect r {:color c}))
                         palette
                         (map ccr/country-alias (keys json-data)))
            :label (format
                    "%s; %s; %s: %s > %s"
                    (fmt-day day)
                    (fmt-last-date stats)
                    com/bot-name
                    (str (case-kw {:c l/confirmed :i l/active-cases :r l/recovered :d l/deaths})
                         " " l/absolute)
                    threshold)
            :label-conf {:color (c/darken :steelblue) :font-size 14}})]
      (let [img-byte-array (toByteArrayAutoClosable img)]
        (debugf "[plot-absolute-by-case] case %s; img-size %s" case-kw (count img-byte-array))
        img-byte-array))))

(defn plot-absolute-by-case
  "The optional params `stats`, `day` are used only for the first calculation"
  [case-kw & [stats day]]
  (data/from-cache (fn [] (calc-plot-absolute-by-case-fn case-kw stats day))
                   [:plot :abs case-kw]))
