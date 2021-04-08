;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.msg.graph.plot)

(ns corona.msg.graph.plot
  (:require [cljplot.build :as b]
            [cljplot.common :as plotcom]
            [cljplot.render :as r]
            [clojure.set :as cset]
            [clojure2d.color :as c]
            [clojure2d.core :as c2d]
            [corona.api.cache :as cache]
            [corona.common :as com]
            [corona.countries :as ccr]
            [corona.country-codes :as ccc]
            [corona.lang :as lang]
            ;; XXX cljplot.core must be required otherwise an empty plot is
            ;; shown when released. WTF?
            cljplot.core
            [corona.macro :refer [defn-fun-id debugf infof]]
            [utils.core :refer [in?]])
  (:import java.awt.image.BufferedImage
           java.io.ByteArrayOutputStream
           [java.time LocalDate ZoneId]
           java.time.format.DateTimeFormatter
           javax.imageio.ImageIO))

;; (set! *warn-on-reflection* true)

(defn- threshold
  "See also https://github.com/rplevy/swiss-arrows"
  [case-kw]
(first
   (com/tore
    com/case-params
    (filter (fn [m] (= (:kw m) case-kw)))
    (map :threshold))))

(defn min-threshold
  "Countries with the number of cases less than the threshold are grouped into
  \"Rest\"."
  [aggregation-kw case-kw]
  (cache/from-cache! (fn [] ((comp :val threshold) case-kw))
                    [:threshold aggregation-kw case-kw]))

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

(defn- boiler-plate [{:keys [series x-axis-formatter y-axis-formatter legend label label-conf]}]
  ((comp
    c2d/get-image
    (fn [s] (r/render-lattice s {:width 800 :height 600}))
    (fn [s] (b/add-label s :top label label-conf))
    (fn [s] (b/add-legend s "" legend))
    (fn [s] (b/update-scale s :x :fmt x-axis-formatter))
    (fn [s] (b/update-scale s :y :fmt y-axis-formatter))
    (fn [s] (b/add-axes s :left))
    (fn [s] (b/add-axes s :bottom))
    (fn [s] (b/preprocess-series s)))
   series))

(defn to-java-time-local-date [^java.util.Date java-util-date]
  (LocalDate/ofInstant (.toInstant java-util-date) (ZoneId/systemDefault)))

(defn sort-by-country-name [hmap] (sort-by first (comp - compare) hmap))

(defn sort-by-last-val [hm]
  ((comp (partial reduce into [])
         (partial map (comp (fn [key] {key (get hm key)})
                            first))
         reverse
         (partial sort-by second)
         (partial map (juxt first (comp second last second))))
   hm))

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
               {:ccode ccode :t t :case-kw :er :cnt (reduce + (map :er hms))}
               {:ccode ccode :t t :case-kw :ea :cnt (reduce + (map :ea hms))}
               {:ccode ccode :t t :case-kw :c :cnt (reduce + (map :c hms))}
               {:ccode ccode :t t :case-kw :r :cnt (reduce + (map :r hms))}
               {:ccode ccode :t t :case-kw :d :cnt (reduce + (map :d hms))}
               {:ccode ccode :t t :case-kw :a :cnt (reduce + (map :a hms))}]))
       (flatten)))

(defn stats-for-country [ccode stats]
  (let [mapped-hm
        ((comp
          #_(partial take-last (/ 365 2)))
         (plotcom/map-kv
          (fn [entry]
            ((comp
              (partial take-last (/ 365 1 #_2)))
             (sort-by first
                      (map (fn [{:keys [t cnt]}]
                             [(to-java-time-local-date t) cnt])
                           entry))))
          (group-by :case-kw (sum-for-pred ccode stats))))]
    ;; sort - keep the "color order" of cases fixed; don't
    ;; recalculate it
    ;; TODO try (map {:a 1 :b 2 :c 3 :d 4} [:a :d]) ;;=> (1 4)
    ((comp
      reverse)
     (transduce (map (fn [case-kw] (select-keys mapped-hm [case-kw])))
                into []
                [:a :r :d :c :p :er :ea]))))

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

(def ^:const stroke-confir
  (conj line-cfg {:color
                  (last (c/palette-presets :ylgn-6))}))

(defn stroke-active []
  (conj line-cfg {:color :black
                  :stroke {:size 1.5
                           ;; :dash [20.0] :dash-phase 10
                           ;; :dash [5.0 2.0 2.0 2.0]
                           ;; :dash [10.0 5.0] :join :miter
                           :dash [2.0] :dash-phase 2.0
                           }}))

(defn stroke-estim-recov []
  (conj line-cfg {:color :red
                  :stroke {:size 1.5
                           ;; :dash [20.0] :dash-phase 10
                           ;; :dash [5.0 2.0 2.0 2.0]
                           ;; :dash [10.0 5.0] :join :miter
                           :dash [2.0] :dash-phase 2.0
                           }}))

(defn stroke-estim-activ []
  (conj line-cfg {:color :green
                  :stroke {:size 1.5
                           ;; :dash [20.0] :dash-phase 10
                           ;; :dash [5.0 2.0 2.0 2.0]
                           ;; :dash [10.0 5.0] :join :miter
                           :dash [2.0] :dash-phase 2.0
                           }}))

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

(defn to-byte-array-auto-closable
  "Thanks to https://stackoverflow.com/a/15414490"
  [^BufferedImage image]
  (when image
    (with-open [out (new ByteArrayOutputStream)]
      (let [fmt-name "png"] ;; png is an informal format name.
        (ImageIO/write image fmt-name out)
        (.toByteArray out)))))

(defn date-fmt-fn [d]
  (.format (DateTimeFormatter/ofPattern "dd.MM") d))

(defn-fun-id message-img
  "Country-specific cumulative plot of active, recovered, deaths and
  active-absolute cases."
  [ccode & [stats report]]
  (when-not (in? ccc/excluded-country-codes ccode)
    #_(def stats stats)
    #_(def report report)
    (let [base-data (stats-for-country ccode stats)
          sarea-data (remove (fn [[case-kw _]]
                               (in? #_[:c :a :r :d] [:c :p :er :ea] case-kw))
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
      (boiler-plate
       {:series (b/series
                 [:grid]
                 [:sarea sarea-data {:palette palette}]
                 #_[:line (line-data :p base-data) stroke-population]
                 [:line (line-data :c base-data) stroke-confir]
                 [:line (line-data :a base-data) (stroke-active)]
                 [:line (line-data :er base-data) (stroke-estim-recov)]
                 [:line (line-data :ea base-data) (stroke-estim-activ)])
        :x-axis-formatter date-fmt-fn
        :y-axis-formatter (metrics-prefix-formatter
                                 ;; population numbers have the `max` values, all
                                 ;; other numbers are derived from them
                                 ;; don't display the population data for the moment
                           (max-y-val + sarea-data))
        :legend (reverse
                 (conj (map #(vector :rect %2 {:color %1})
                            palette
                            (map (fn [k] (get {:a lang/active
                                              :d lang/deaths
                                              :r lang/recovered
                                              :er lang/recov-estim
                                              :ea lang/activ-estim} k))
                                 curves))
                       [:line lang/confirmed       stroke-confir]
                       [:line lang/active-absolute (stroke-active)]
                       [:line lang/recov-estim     (stroke-estim-recov)]
                       [:line lang/activ-estim     (stroke-estim-activ)]
                       #_[:line lang/people    stroke-population]))
        :label (plot-label report ccode stats)
        :label-conf (conj {:color (c/darken :steelblue)} #_{:font-size 14})}))))

(defn-fun-id message
  "Country-specific cumulative plot of active, recovered, deaths and
  active-absolute cases."
  [ccode & [stats report]]
  ((comp
    (fn [arr]
      (debugf "ccode %s size %s" ccode (if arr (com/measure arr) 0))
      arr)
    to-byte-array-auto-closable
    (fn [prms] (apply message-img prms)))
   [ccode stats report]))

(defn message-kw [ccode] [:plot (keyword ccode)])

(defn-fun-id group-below-threshold
  "Group all countries w/ the number of active cases below the threshold under the
  `ccc/default-2-country-code` so that max 10 countries are displayed in the
  plot"
  [{:keys [case-kw threshold threshold-increase stats] :as prm}]
  (let [max-plot-lines 10
        res (map (fn [hm] (if (< (get hm case-kw) threshold)
                           (assoc hm :ccode ccc/default-2-country-code)
                           hm))
                 stats)]
    ;; TODO implement recalculation for decreasing case-kw numbers (e.g. active cases)
    (if (> (count (group-by :ccode res)) max-plot-lines)
      (let [raised-threshold (+ threshold-increase threshold)]
        (infof "Case %s; %s countries above threshold. Raise to %s"
               case-kw (count (group-by :ccode res)) raised-threshold)
        (swap! cache/cache update-in [:threshold case-kw] (fn [_] raised-threshold))
        (group-below-threshold (assoc prm :threshold raised-threshold)))
      {:data res :threshold threshold})))

(defn-fun-id sum-all-by-date-by-case
  "Group the country stats by report and sum up the cases"
  [{:keys [case-kw] :as prm-orig}]
  (let [prm (group-below-threshold prm-orig)
        res
        ((comp
          flatten
          (partial map (fn [[t hms]]
                         (->> (group-by :ccode hms)
                              (map (fn [[ccode hms]]
                                     {:ccode ccode :t t case-kw (reduce + (map case-kw hms))})))))
          (partial group-by :t)
          :data)
         prm)
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
                            (set
                             (map (fn [ccode] {:ccode ccode :t t case-kw 0})
                                  (cset/difference countries-threshold
                                                   (set (keys (group-by :ccode hms)))))))
                           #_(->> (group-by :ccode hms)
                                  (keys)
                                  (set)
                                  (cset/difference countries-threshold)
                                  (map (fn [ccode] {:ccode ccode :t t :a 0}))
                                  (set)
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


(defn legend [json-data]
  (map (fn [c r] (vector :rect r {:color c}))
       (cycle (c/palette-presets :category20b))
       (map ccr/country-alias
            ;; XXX b/add-legend doesn't accept newline char \n
            #_(fn [ccode] (format "%s %s"
                                  ccode
                                  (com/country-alias ccode)))
            (keys json-data))))

(defn y-axis-formatter [json-data]
   ;; `+` means: sum up all active cases
  (metrics-prefix-formatter (max-y-val + json-data)))

(def label-conf {:color (c/darken :steelblue) :font-size 14})

(defn label-str
  [report stats case-kw threshold postfix]
  (format "%s; %s; %s: %s > %s"
          (fmt-report report)
          (fmt-last-date stats)
          com/bot-name
          ((comp
            (fn [s] (str s postfix))
            (partial com/text-for-case case-kw))
           [lang/confirmed lang/recovered lang/deaths lang/active-cases])
          threshold))

(defn line-stroke [color]
  (conj line-cfg {:color color
                  :stroke {:size 3
                           ;; :dash [20.0] :dash-phase 10
                           ;; :dash [5.0 2.0 2.0 2.0]
                           ;; :dash [10.0 5.0] :join :miter
                           ;; :dash [4.0] :dash-phase 2.0
                           }}))

(defn-fun-id aggregation-img ""
  [stats report aggregation-kw case-kw]
  (let [{json-data :data threshold-recalced :threshold}
        (stats-all-by-case {:report report
                            :stats stats
                            :threshold (min-threshold aggregation-kw case-kw)
                            :threshold-increase (threshold-increase case-kw)
                            :case-kw case-kw})]
    (boiler-plate
     {:series (condp = aggregation-kw
                :abs (->> (mapv (fn [[_ ccode-data] color]
                                  [:line ccode-data (line-stroke color)])
                                json-data
                                (cycle (c/palette-presets :category20b)))
                          (into [[:grid]])
                          (apply b/series))
                :sum (b/series [:grid] [:sarea json-data]))
      :legend ((comp (condp = aggregation-kw
                       :abs identity
                       :sum reverse)
                     legend)
               json-data)
      :x-axis-formatter date-fmt-fn
      :y-axis-formatter (y-axis-formatter json-data)
      :label (label-str report stats case-kw threshold-recalced
                        (condp = aggregation-kw
                          :abs (str " " lang/absolute)
                          :sum ""))
      :label-conf label-conf})))

(defn aggregation!
  "The optional params `stats`, `report` are used only for the first
  calculation"
  ([id aggregation-kw case-kw]
   {:pre [(string? id)]}
   (get-in @cache/cache [:plot (keyword id) aggregation-kw case-kw]))
  ([stats report id aggregation-kw case-kw]
   {:pre [(string? id)]}
   (cache/cache! (fn []
                   ((comp
                     #_(fn [arr] (com/heap-info) arr)
                     to-byte-array-auto-closable)
                    (aggregation-img stats report aggregation-kw case-kw)))
                 [:plot (keyword id) aggregation-kw case-kw])))

;;;; lazy-evaluation CPS (Continuation Passing Style)
;; (defonce ch (atom {}))

;; (defn ch! [ks form]
;;   (let [data (eval form)]
;;     (swap! ch update-in ks (fn [_] data))
;;     data))

;; ((comp
;;   (partial ch! [:ch])
;;   #_(fn [form] (eval form))
;;   (fn [form] `~form))
;;  '((comp str inc)
;;    (+ 1 2)))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.msg.graph.plot)
