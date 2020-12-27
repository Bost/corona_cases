(printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.plot)

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
   [corona.lang :as lang]
   [utils.core :refer [in?] :exclude [id]]
   [taoensso.timbre :as timbre :refer [debugf
                                       #_info infof
                                       ;; warn errorf fatalf
                                       ]]
   [corona.api.expdev07 :as data])
  (:import java.awt.image.BufferedImage
           java.io.ByteArrayOutputStream
           javax.imageio.ImageIO
           [java.time LocalDate ZoneId]))

;; (set! *warn-on-reflection* true)

(defn- threshold
  "See also https://github.com/rplevy/swiss-arrows"
  [case-kw]
  #_
  (->> case-params
       (filter (fn [m] (= (:kw m) case-kw)))
       (map :threshold)
       (first))
  #_
  (transduce (comp
              (filter (fn [m] (= (:kw m) case-kw)))
              (map :threshold))
             ;; there's only one element so we can use the net.cgrand.xforms.rfs/last
             net.cgrand.xforms.rfs/last []
             case-params)
  (first
   (com/tore
    com/case-params
    (filter (fn [m] (= (:kw m) case-kw)))
    (map :threshold))))

(defn min-threshold
  "Countries with the number of cases less than the threshold are grouped into
  \"Rest\"."
  [case-kw]
  (data/from-cache! (fn [] ((comp :val threshold) case-kw))
                    [:threshold case-kw]))

(defn threshold-increase
  "Case-dependent threshold recalculation increase."
  [case-kw]
  ((comp :inc threshold) case-kw))

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
  (->> stats
       (filter (fn [hm] (in? [ccc/worldwide-2-country-code (:ccode hm)] ccode)))
       (group-by :t)
       (map (fn [[t hms]]
              [;; confirmed cases is the sum of all others
               {:ccode ccode :t t :case-kw :p :cnt
                (bigint (/ (:p (first hms)) 1e3))
                #_(reduce + (map :p hms))
                #_(bigint (/ (get population ccode) (bigint 1e3)))}
               {:ccode ccode :t t :case-kw :e :cnt (reduce + (map :e hms))}
               {:ccode ccode :t t :case-kw :c :cnt (reduce + (map :c hms))}
               {:ccode ccode :t t :case-kw :r :cnt (reduce + (map :r hms))}
               {:ccode ccode :t t :case-kw :d :cnt (reduce + (map :d hms))}
               {:ccode ccode :t t :case-kw :a :cnt (reduce + (map :a hms))}]))
       (flatten)))

(defn stats-for-country [ccode stats]
  (let [mapped-hm (plotcom/map-kv
                   (fn [entry]
                     (sort-by first
                              (map (fn [{:keys [t cnt]}]
                                     [(to-java-time-local-date t) cnt])
                                   entry)))
                   (group-by :case-kw (sum-for-pred ccode stats)))]
    ;; sort - keep the "color order" of cases fixed; don't
    ;; recalculate it
    ;; TODO try (map {:a 1 :b 2 :c 3 :d 4} [:a :d]) ;;=> (1 4)
    (reverse (transduce (map (fn [case-kw] (select-keys mapped-hm [case-kw])))
                        into []
                        [:a :r :d :c :p :e]))))

(defn fmt-last-date [stats]
  ((comp com/fmt-date :t last) (sort-by :t stats)))

(defn fmt-report [report] (format "%s %s" lang/report report))

(defn plot-label
  "report-nr - Nth report since the outbreak
  ccode - country code
  stats - statistics active, confirmed, etc. for the given country code"
  [report-nr ccode stats]
  (format "%s; %s; %s: %s"
          (fmt-report report-nr)
          (fmt-last-date stats)
          com/bot-name
          (format "%s %s %s"
                  lang/stats
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

(def ^:const stroke-estimated
  (conj line-cfg {:color :red
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
  (second (transduce (filter (fn [[case-kw _]] (= kw case-kw)))
                     into []
                     data)))

(defn toByteArrayAutoClosable
  "Thanks to https://stackoverflow.com/a/15414490"
  [^BufferedImage image]
  (with-open [out (new ByteArrayOutputStream)]
    (let [fmt-name "png"] ;; png is an informal format name.
      (ImageIO/write image fmt-name out)
      (.toByteArray out))))

(defn calc-plot-country
  "Country-specific cumulative plot of sick, recovered, deaths and sick-absolute
  cases."
  [ccode & [stats report]]
  (when-not (in? ccc/excluded-country-codes ccode)
    (let [base-data (stats-for-country ccode stats)
          sarea-data (remove (fn [[case-kw _]]
                               (in? #_[:c :a :r :d] [:c :p :e] case-kw))
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
      ;; {:label (plot-label report ccode stats)}
      (let [img
            (boiler-plate
             {:series (b/series
                       [:grid]
                       [:sarea sarea-data {:palette palette}]
                       #_[:line (line-data :p base-data) stroke-population]
                       [:line (line-data :c base-data) stroke-confirmed]
                       [:line (line-data :a base-data) stroke-sick]
                       [:line (line-data :e base-data) stroke-estimated])
              :y-axis-formatter (metrics-prefix-formatter
                                 ;; population numbers have the `max` values, all
                                 ;; other numbers are derived from them
                                 ;; don't display the population data for the moment
                                 (max-y-val + sarea-data))
              :legend (reverse
                       (conj (map #(vector :rect %2 {:color %1})
                                  palette
                                  (map (fn [k] (get {:a lang/active :d lang/deaths :r lang/recovered
                                                     :e lang/recov-estim} k))
                                       curves))
                             [:line lang/confirmed     stroke-confirmed]
                             [:line lang/sick-absolute stroke-sick]
                             [:line lang/recov-estim   stroke-estimated]
                             #_[:line lang/people    stroke-population]))
              :label (plot-label report ccode stats)
              :label-conf (conj {:color (c/darken :steelblue)} #_{:font-size 14})})]
        (let [img-byte-array (toByteArrayAutoClosable img)]
          (debugf "[plot-country] ccode %s img-size %s" ccode (count img-byte-array))
          img-byte-array)))))

(defn plot-country
  "The optional params `stats`, `report` are used only for the first calculation"
  [ccode & [stats report]]
  (let [ks [:plot (keyword ccode)]]
    (if (and stats report)
      (data/cache! (fn [] (calc-plot-country ccode stats report))
                   ks)
      (get-in @data/cache ks))))

(defn group-below-threshold
  "Group all countries w/ the number of active cases below the threshold under the
  `ccc/default-2-country-code` so that max 10 countries are displayed in the
  plot"
  [{:keys [case-kw threshold threshold-increase stats] :as prm}]
  (let [max-plot-lines 10
        res (map (fn [hm] (if (< (get hm case-kw) threshold)
                           (assoc hm :ccode ccc/default-2-country-code)
                           hm))
                 stats)]
    ;; TODO implement recalculation for decreasing case-kw numbers (e.g. sics)
    (if (> (count (group-by :ccode res)) max-plot-lines)
      (let [raised-threshold (+ threshold-increase threshold)]
        (infof "Case %s; %s countries above threshold. Raise to %s"
               case-kw (count (group-by :ccode res)) raised-threshold)
        (swap! data/cache update-in [:threshold case-kw] (fn [_] raised-threshold))
        (group-below-threshold (assoc prm :threshold raised-threshold)))
      {:data res :threshold threshold})))

(defn sum-all-by-date-by-case
  "Group the country stats by report and sum up the active cases"
  [{:keys [case-kw] :as prm-orig}]
  (let [prm (group-below-threshold prm-orig)
        res
        (->> (:data prm)
             (group-by :t)
             (map (fn [[t hms]]
                    (->> (group-by :ccode hms)
                         (map (fn [[ccode hms]]
                                {:ccode ccode :t t case-kw (reduce + (map case-kw hms))})))))
             (flatten))
        #_(flatten (map (fn [[t hms]]
                          (map (fn [[ccode hms]]
                                 {:ccode ccode :t t case-kw (reduce + (map case-kw hms))})
                               (group-by :ccode hms)))
                        (group-by :t (:data prm))))]
    (update prm :data (fn [_] res))))

(defn fill-rest [{:keys [case-kw] :as prm}]
  (let [date-sums (sum-all-by-date-by-case prm)
        {sum-all-by-date-by-case-threshold :data} date-sums
        countries-threshold (set (map :ccode sum-all-by-date-by-case-threshold))
        res (reduce into []
                    (map (fn [[t hms]]
                           (cset/union
                            hms
                            (map (fn [ccode] {:ccode ccode :t t case-kw 0})
                                 (cset/difference countries-threshold
                                                  (keys (group-by :ccode hms)))))
                           #_(->> (group-by :ccode hms)
                                  (keys)
                                  (cset/difference countries-threshold)
                                  (map (fn [ccode] {:ccode ccode :t t :a 0}))
                                  (cset/union hms)))
                         (group-by :t sum-all-by-date-by-case-threshold)))]
    (update date-sums :data (fn [_] res))))

(defn stats-all-by-case [{:keys [case-kw] :as prm}]
  (let [fill-rest-stats (fill-rest prm)
        data (:data fill-rest-stats)
        mapped-hm (plotcom/map-kv
                   (fn [entry]
                     (sort-by first
                              (map (fn [fill-rest-stats]
                                     [(to-java-time-local-date
                                       (:t fill-rest-stats))
                                      (get fill-rest-stats case-kw)])
                                   entry)))
                   (group-by :ccode data))]
    #_(sort-by-country-name mapped-hm)
    (update fill-rest-stats :data (fn [_] (sort-by-last-val mapped-hm)))))

(defn calc-sum
  "Case-specific plot for the sum of all countries."
  ([case-kw stats report] (calc-sum "calc-sum" case-kw stats report))
  ([msg-id case-kw stats report]
   (let [threshold (min-threshold case-kw)
         prm {:report report
              :stats stats
              :threshold threshold
              :threshold-increase (threshold-increase case-kw)
              :case-kw case-kw}

         {json-data :data threshold-recaltulated :threshold}
         (stats-all-by-case prm)]
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
                            (fmt-report report)
                            (fmt-last-date stats)
                            com/bot-name
                            (com/text-for-case
                             case-kw
                             [lang/confirmed lang/recovered lang/deaths lang/active-cases])
                            #_(->> [lang/confirmed lang/recovered lang/deaths lang/active-cases]
                                   (zipmap com/basic-cases)
                                   case-kw)
                            #_(case-kw {:c lang/confirmed :a lang/active-cases :r lang/recovered :d lang/deaths})
                            threshold-recaltulated)
             :label-conf {:color (c/darken :steelblue) :font-size 14}})]
       (let [img-byte-array (toByteArrayAutoClosable img)]
         (debugf "[%s] %s img-size %s" msg-id case-kw (count img-byte-array))
         img-byte-array)))))

(defn plot-sum
  "The optional params `stats`, `report` are used only for the first calculation"
  [case-kw & [stats report]]
  (let [ks [:plot :sum case-kw]]
    (if (and stats report)
      (data/cache! (fn [] (calc-sum case-kw stats report))
                   ks)
      (get-in @data/cache ks))))

(defn line-stroke [color]
  (conj line-cfg {:color color
                  :stroke {:size 3
                           ;; :dash [20.0] :dash-phase 10
                           ;; :dash [5.0 2.0 2.0 2.0]
                           ;; :dash [10.0 5.0] :join :miter
                           ;; :dash [4.0] :dash-phase 2.0
                           }}))

(defn calc-absolute
  ([case-kw stats report] (calc-absolute "calc-absolute" case-kw stats report))
  ([msg-id case-kw stats report]
   (let [threshold (min-threshold case-kw)
         prm {:report report
              :stats stats
              :threshold threshold
              :threshold-increase (threshold-increase case-kw)
              :case-kw case-kw}

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
                     (fmt-report report)
                     (fmt-last-date stats)
                     com/bot-name
                     (str (case-kw {:c lang/confirmed :a lang/active-cases
                                    :r lang/recovered :d lang/deaths})
                          " " lang/absolute)
                     threshold)
             :label-conf {:color (c/darken :steelblue) :font-size 14}})]
       (let [img-byte-array (toByteArrayAutoClosable img)]
         (debugf "[%s] %s img-size %s" msg-id  case-kw (count img-byte-array))
         img-byte-array)))))

(defn plot-absolute
  "The optional params `stats`, `report` are used only for the first calculation"
  [case-kw & [stats report]]
  (let [ks [:plot :abs case-kw]]
    (if (and stats report)
      (data/cache! (fn [] (calc-absolute case-kw stats report))
                   ks)
      (get-in @data/cache ks))))

(printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.plot)
