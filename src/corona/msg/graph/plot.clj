;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.msg.graph.plot)

(ns corona.msg.graph.plot
  (:require [cljplot.build :as b]
            [cljplot.common :as plotcom]
            [cljplot.render :as r]
            [clojure.set :as cset]
            [clojure2d.color :as c]
            [clojure2d.core :as c2d]
            [corona.api.cache :as cache]
            [corona.common :as com :refer
             [kcco krec kact kdea krep kpop kclo knew ktst
              krep kabs ker_ kea_
              kcase-kw
              sum makelense]]
            [corona.cases :as cases]
            [corona.countries :as ccr]
            [corona.country-codes :as ccc]
            [corona.lang :as lang]
            ;; XXX cljplot.core must be required otherwise an empty plot is
            ;; shown when released. WTF?
            cljplot.core
            [corona.macro :refer [defn-fun-id debugf infof]]
            [utils.core :refer [in?]]
            [corona.models.dbase :as dbase]
            [clojure.inspector :as insp :refer [inspect-table inspect-tree]]
            )
  (:import java.awt.image.BufferedImage
           java.io.ByteArrayOutputStream
           [java.time LocalDate ZoneId]
           java.time.format.DateTimeFormatter
           javax.imageio.ImageIO))

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

(defn- boiler-plate
  [{:keys [series x-axis-formatter y-axis-formatter legend label label-conf]}]
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

(defn sort-by-last-val [hm]
  ((comp (partial reduce into [])
         (partial map (comp (partial select-keys hm)
                            vector
                            first))
         reverse
         (partial sort-by second)
         (partial map (juxt first (comp second last second))))
   hm))

(defn stats-for-country-case-kw [ccode stats case-kw]
  ((comp
    (partial vector case-kw)
    (partial take-last com/nr-of-days)
    (partial sort-by first)
    (partial
     map
     (fn [[tst hms]]
       [(to-java-time-local-date tst)
        (condp = case-kw
          kpop (bigint (/ (get (first hms) kpop) 1e3))
          (sum
           (or (get com/stats-for-country-case--lense-map case-kw)
               (com/basic-lense case-kw))
           hms))]))
    (partial group-by ktst))
   stats))

(defn stats-for-country
  [ccode stats]
  (let [relevant-stats
        (filter (fn [hm] (in? [ccc/worldwide-2-country-code
                               (get hm kcco)]
                              ccode))
                stats)]
    ((comp
      (partial map (fn [[k v]] (clojure.lang.MapEntry/create k v)))
      reverse
      (partial keep (partial stats-for-country-case-kw ccode relevant-stats)))
     [kact krec kdea knew kpop ker_ kea_])))

(defn fmt-report [report] (format "%s %s" lang/report report))

(defn plot-label
  "report-nr - Nth report since the outbreak
  ccode - country code
  stats - statistics active, confirmed, etc. for the given country code"
  [report-nr ccode stats last-date]
  (format "%s; %s; %s: %s"
          (fmt-report report-nr)
          (com/fmt-date last-date)
          com/bot-name
          (format "%s %s %s"
                  lang/stats
                  (ccr/country-name-aliased ccode)
                  (com/encode-cmd ccode))))

(defn palette-colors
  "Infinite sequence.
 Palette https://clojure2d.github.io/clojure2d/docs/static/palettes.html"
  [n]
  ((comp cycle reverse (partial take-last n) c/palette-presets) :gnbu-6))

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
  (conj line-cfg {:color :olive #_:green
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
  active-absolute cases.. E.g.:
(message-img ccode stats last-date report)
"
  [ccode stats last-date report]
  (when-not (in? ccc/excluded-country-codes ccode)
    (let [base-data (stats-for-country ccode stats)
          sarea-data (remove (fn [[case-kw _]]
                               (in?
                                #_[knew kact krec kdea]
                                [knew kpop ker_ kea_] case-kw))
                             base-data)
          curves (keys sarea-data)
          ;; let is a macro, thus the palette-colors returning infinite sequence
          ;; doesn't get pre-computed.
          palette (palette-colors (count curves))]
      ;; every chart/series definition is a vector with three fields:
      ;; 1. chart type e.g. :grid, :sarea, :line
      ;; 2. data
      ;; 3. configuration hash-map

      ;; TODO annotation by value and labeling doesn't work:
      ;; :annotate? true
      ;; :annotate-fmt "%.1f"
      ;; {:label (plot-label report ccode stats last-date)}
      (boiler-plate
       {:series (b/series
                 [:grid]
                 [:sarea sarea-data {:palette palette}]
                 #_[:line (line-data kpop base-data) stroke-population]
                 [:line (line-data knew base-data) stroke-confir]
                 [:line (line-data kact base-data) (stroke-active)]
                 [:line (line-data ker_ base-data) (stroke-estim-recov)]
                 [:line (line-data kea_ base-data) (stroke-estim-activ)])
        :x-axis-formatter date-fmt-fn
        :y-axis-formatter (metrics-prefix-formatter
                           ;; population numbers have the `max` values, all
                           ;; other numbers are derived from them
                           ;; don't display the population data for the moment
                           (max-y-val + sarea-data))
        :legend (reverse
                 (conj (map (fn [color rect]
                              (def color color)
                              (def rect rect)
                              (vector :rect rect {:color color}))
                            palette
                            (let [legend-hm {kact lang/active
                                             kdea lang/deaths
                                             krec lang/recovered
                                             ker_ lang/recov-estim
                                             kea_ lang/activ-estim}]
                              (map (partial get legend-hm) curves)))
                       [:line lang/confirmed       stroke-confir]
                       [:line lang/active-absolute (stroke-active)]
                       [:line lang/recov-estim     (stroke-estim-recov)]
                       [:line lang/activ-estim     (stroke-estim-activ)]
                       #_[:line lang/people    stroke-population]))
        :label (plot-label report ccode stats last-date)
        :label-conf (conj {:color (c/darken :steelblue)} #_{:font-size 14})}))))

(defn-fun-id message
  "Country-specific cumulative plot of active, recovered, deaths and
  active-absolute cases."
  [ccode stats last-date report]
  ((comp
    to-byte-array-auto-closable
    message-img)
   ccode stats last-date report))

(defn message-kw [ccode] [:plot (keyword ccode)])

(defn-fun-id group-below-threshold
  "Group all countries with the nr of active cases below the threshold under the
  `ccc/default-2-country-code` so that max 10 countries are plotted.

  Too many invocations lead to 'OutOfMemoryError: Java heap space'. Garbage
  Collection on every 4th invocation doesn't help. Tail-Call invocation might
  help to counter this problem."
  [{:keys [threshold threshold-increase stats] :as prm}]
  (let [case-kw (get prm kcase-kw)
        max-plot-lines 10
        l-fun (condp = case-kw
                kact (makelense kact krep kabs)
                krec (makelense krec krep kabs)
                (com/basic-lense case-kw))
        res
        ((comp
          (partial map (fn [hm] (if (< (get-in hm l-fun) threshold)
                                  (assoc hm kcco ccc/default-2-country-code)
                                  hm)))
          (partial sort-by ktst))
         stats)]
    ;; TODO implement recalculation for decreasing case-kw numbers (e.g. active cases)
    (let [cnt-countries (count (group-by kcco res))]
      (if (> cnt-countries max-plot-lines)
        (let [raised-threshold (+ threshold-increase threshold)]
          (infof "%s; %s countries above threshold %s. Raise to %s"
                 case-kw cnt-countries threshold raised-threshold)
          (swap! cache/cache
                 update-in [:threshold case-kw] (fn [_] raised-threshold))
          (dbase/upsert-threshold! {:kw case-kw
                                    :inc threshold-increase
                                    :val raised-threshold})
          (group-below-threshold (assoc prm :threshold raised-threshold)))
        {:data res :threshold threshold}))))

(defn stats-all-by-case "" [prm]
  (let [case-kw (get prm kcase-kw)]
    (update
     (group-below-threshold prm)
     :data
     (fn [data]
       (let [countries-threshold ((comp set (partial map kcco)) data)
             new-lensed-case-kw
             (condp = case-kw
               kact (makelense kact krep kabs)
               krec (makelense krec krep kabs)
               (com/basic-lense case-kw))
             simple-lensed-case-kw (com/makelense case-kw)]
         ((comp
           sort-by-last-val
           (partial plotcom/map-kv
                    (comp
                     (partial sort-by first)
                     (partial map
                              ;; TODO use juxt
                              ;; (comp (partial apply vector)
                              ;;       (juxt ...))
                              (fn [hm]
                                [((comp to-java-time-local-date
                                        (fn [m] (get m ktst))) hm)
                                 (get-in hm simple-lensed-case-kw)]))))
           (partial reduce into {})
           (partial map
                    ;; (fn [[ccode hms1]] {ccode (sort-by ktst hms1)})
                    (comp
                     (partial apply hash-map) ;; TODO this may be not needed?
                     (juxt first (comp (partial sort-by ktst) second))))
           (partial group-by kcco)
           (partial reduce into [])
           (partial map
                    (fn [[t hms]]
                      ((comp
                        (partial cset/union hms)
                        set
                        ;; fill the rest with zeros
                        (partial map (partial hash-map ktst t case-kw 0 kcco))
                        (partial cset/difference countries-threshold)
                        set
                        keys
                        (partial group-by kcco))
                       hms)))
           (partial group-by ktst)
           flatten
           (partial map (fn [[t hms0]]
                          ((comp
                            (partial map
                                     (fn [[ccode hms]]
                                       ((comp
                                         (partial hash-map kcco ccode ktst t case-kw)
                                         (partial sum new-lensed-case-kw))
                                        hms)))
                            (partial group-by kcco))
                           hms0)))
           (partial group-by ktst))
          data))))))

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
  [report stats last-date case-kw threshold postfix]
  (format "%s; %s; %s: %s > %s"
          (fmt-report report)
          (com/fmt-date last-date)
          com/bot-name
          ((comp
            (fn [s] (str s postfix))
            (partial cases/text-for-case case-kw))
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

(defn-fun-id aggregation-img "
(aggregation-img thresholds stats last-date cnt-reports aggregation-kw case-kw)"
  [thresholds stats last-date cnt-reports aggregation-kw case-kw]
  (let [sabc
        (stats-all-by-case
         (conj
          {:report cnt-reports
           :stats stats
           kcase-kw case-kw}
          (let [th ((comp
                     first
                     (partial filter (comp
                                      (partial = case-kw)
                                      (fn [m] (get m :kw)))))
                    thresholds)]
            {:threshold (cache/from-cache! (fn [] (:val th))
                                           [:threshold aggregation-kw case-kw])
             :threshold-increase (:inc th)})))
        data (:data sabc)
        threshold-recalced (:threshold sabc)]
    (boiler-plate
     {:series (condp = aggregation-kw
                :abs ((comp
                       (partial apply b/series)
                       (partial into [[:grid]])
                       (partial mapv (fn [[_ ccode-data] color]
                                       [:line ccode-data (line-stroke color)])))
                      data (cycle (c/palette-presets :category20b)))
                :sum (b/series [:grid] [:sarea data]))
      :legend ((comp (condp = aggregation-kw
                       :abs identity
                       :sum reverse)
                     legend)
               data)
      :x-axis-formatter date-fmt-fn
      :y-axis-formatter (y-axis-formatter data)
      :label (label-str cnt-reports stats last-date case-kw threshold-recalced
                        (condp = aggregation-kw
                          :abs (str " " lang/absolute)
                          :sum ""))
      :label-conf label-conf})))

(defn aggregation! ""
  ([id aggregation-kw case-kw]
   {:pre [(string? id)]}
   (get-in @cache/cache [:plot (keyword id) aggregation-kw case-kw]))
  ([thresholds stats last-date cnt-reports id aggregation-kw case-kw]
   {:pre [(string? id)]}
   (taoensso.timbre/debugf "[aggregation!] case-kw %s aggregation-kw %s" case-kw aggregation-kw)
   (cache/cache! (fn []
                   ((comp
                     to-byte-array-auto-closable)
                    (aggregation-img
                     thresholds stats last-date cnt-reports
                     aggregation-kw case-kw)))
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
