;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.estimate)

(ns corona.estimate
  (:require
   [clojure.inspector :as insp :refer [inspect-table inspect-tree]]
   [corona.api.expdev07 :as data]
   [corona.common :as com]
   [corona.keywords :refer :all]
   [corona.telemetry :refer [debugf defn-fun-id infof warnf measure]]
   [corona.utils.core :as cutc]
   [taoensso.timbre :as timbre]
   ))

(def ^:const shift-recovery
  "Mean number of days/reports between symptoms outbreak and full recovery.
 (Lucky coincidence of 1 report per 1 day!)

  Seems like different countries have different recovery reporting policies:
  * Germany  - 14 days/reports
  * Slovakia - 23 days/reports"
  14
  #_(+ 3 (* 2 7)))

(def ^:const shift-deaths
  "https://www.spiegel.de/wissenschaft/medizin/coronavirus-infizierte-genesene-tote-alle-live-daten-a-242d71d5-554b-47b6-969a-cd920e8821f1
  Mean number of days/reports between symptoms outbreak and death. (Lucky
  coincidence of 1 report per 1 day!)"
  18)

(def max-shift (max shift-recovery shift-deaths))

(defn-fun-id estim-country-fn
  "E.g.:
(estim-country-fn com/calc-active
                  (basic-lense kact)
                  [{:kw (basic-lense knew) :shift 0}
                   {:kw (basic-lense krec) :shift 0}
                   {:kw (basic-lense kdea) :shift shift-deaths}])
"
  [calculate-fun kw-estim kw-shift-maps]
  (fn
    [[ccode hms-stats-country-unsorted]]
    [ccode
     (let [stats-country (sort-by ktst hms-stats-country-unsorted)]
       (map
        (fn [estim stats-hm]
          (update-in stats-hm kw-estim (fn [_] estim)))
        ;; 1st collection
        (apply map calculate-fun
               (map (comp
                     (fn [{:keys [vs shift]}] (into (drop-last shift vs)
                                                    (repeat shift 0)))
                     (fn [{:keys [kw shift]}] {:vs (map (fn [stats] (get-in stats kw))
                                                        stats-country)
                                               :shift shift}))
                    kw-shift-maps))
        ;; 2nd collection
        stats-country))]))

(defn drop-shift-flatten
  "`coll` is a collection of pairs:
   [[\"CN\" [{...} ... {...}]]
    ...
    [\"DE\" [{...} ... {...}]]]"
  [coll]
  ((comp
;;; TODO: prevent the IndexOutOfBoundsException as in the
;;; corona.api.v1/xf-for-case the drop must be done after estimation
    flatten
    (partial map (fn [[ccode hms]]
                   (drop max-shift hms)))
    #_
    (partial map (comp
                  (partial drop max-shift)
                  second))) ;; obtain the collection of hash-maps
   coll))

(def last-estim-edn-file "last-estim.edn")

(def last-estim
  ((comp
    #_measure
    read-string
    #_(fn [s] (println "count:" (count s)) s)
    slurp)
   last-estim-edn-file))

(defn zero-sum
  "The first report is dated to January, 22nd 2022, i.e. (we must assume) for any
  country any sum from the past contains just zero-values. I.e. 0 new cases, 0
  recoveries, 0 deaths, 0 percents (of any ratio) etc.

  E.g.:
  (zero-sum \"CN\")
  (initial-sum \"DE\")
  "
  ([ccode kw] (get-in (zero-sum ccode) (basic-lense kw)))
  ([ccode]
   {
    kcco ccode
    ktst #inst "2020-01-21T00:00:00.000-00:00"
    kpop (data/population-cnt ccode)
    kact {krep {kabs 0 k1e5 0 k%%% 0} kest {kabs 0 k1e5 0 k%%% 0}}
    kclo {krep {kabs 0 k1e5 0 k%%% 0} kest {kabs 0 k1e5 0 k%%% 0}}
    kdea {krep {kabs 0 k1e5 0 k%%% 0}                            } ;; reported
    knew {krep {kabs 0 k1e5 0 k%%% 0}                            } ;; reported
    krec {krep {kabs 0 k1e5 0 k%%% 0} kest {kabs 0 k1e5 0 k%%% 0}}
;;; vaccination data come from owid-json not from v1-json; they are reported are reported
;;; vaccination in Germany "DE" began on 2020-12-26
    kvac {krep {kabs 0 k1e5 0 k%%% 0}                            }
    }))

(defn initial-sum
  "E.g.:
  (initial-sum \"CN\" kpop) => 1404614720
  (initial-sum \"DE\" kact) => 0
  "
  ([ccode kw] (get-in (initial-sum ccode) (basic-lense kw)))
  ([ccode] ((comp
             first
             (partial filter (fn [stats-country]
                               (= (get-in stats-country [kcco]) ccode))))
            last-estim)))

(defn-fun-id estim-country-fn
  "
   `previous-sum-fun` is either `initial-sum` or `zero-sum`

((estim-country-fn calculate-fun previous-sum-fun kw-estim kw-shift-maps)
 [ccode hms-stats-country-unsorted])

E.g.:
(estim-country-fn com/calc-active
                   initial-sum
                   kact
                   [{:kw (basic-lense knew) :shift 0}
                    {:kw (basic-lense krec) :shift 0}
                    {:kw (basic-lense kdea) :shift shift-deaths}])
"
  [calculate-fun previous-sum-fun kw-estim kw-shift-maps]
  (def calculate-fun calculate-fun)
  (def previous-sum-fun previous-sum-fun)
  (def kw-estim kw-estim)
  (def kw-shift-maps kw-shift-maps)
  (fn
;;; TODO: create examples of evaluation of this function
    [[ccode hms-stats-country-unsorted]]
    (def ccode ccode)
    (def hms-stats-country-unsorted hms-stats-country-unsorted)
    (vector
     ccode
     (let [
           ;; this sorting should be done in advance
           stats-country (sort-by ktst hms-stats-country-unsorted)
           previous (previous-sum-fun ccode kw-estim)
           ]
       ;; the map-function operates on two collections
       (map
        (fn [estim stats-hm]
          (
           #_cutc/update-in
           update-in
           stats-hm (basic-lense kw-estim) (fn [_] estim)))
        ;; 1st collection provides values for the estim parameter
        (apply map calculate-fun
               #_
               (list
                (previous previous previous previous previous ...)
                ;; shifted list of new-reported cases
                (list 0 0 n1 n2 n3 ...)
                ;; shifted list of death cases
                (list 0 0 d1 d2 d3 ...))
               (conj ;; for lists conj does prepending
                (map (comp
                      ;; extract just the (list ...) and adjust according the shift
                      (fn [{:keys [vs shift]}] (into (drop-last shift vs)
                                                     (repeat shift 0)))
                      ;; returns {:vs (list ...) :shift ...}
                      (fn [{:keys [kw shift] :as shift-map}]
                        ;; TODO: instead of {:vs ... :shift ...} use:
                        assoc-in
                        ;; (assoc-in (select-keys shift-map [:shift])
                        ;;           :vs (map (partial cutc/get-in :ks (basic-lense kw) :m)
                        ;;                    stats-country))
                        {:vs    (map
                                 #_(partial cutc/get-in :ks (basic-lense kw))
                                 (fn [stats] (get-in stats (basic-lense kw)))
                                 stats-country)
                         :shift shift}))
                     kw-shift-maps)
                (repeat (count stats-country) previous)))
        ;; 2nd collection provides values for the stats-hm parameter
        stats-country)))))

(defn estimate
  "First `shift` elements of the estimation are dropped.
  `sum-previous-estims` will ultimately come from the dbase
  E.g.:
  (estimate previous-sum-fun pic-data)
  (estimate zero-sum pic-data)"
  [previous-sum-fun pic-data]
  ((comp
    drop-shift-flatten
    (partial map
             (fn [[_ hms]]
               ((estim-country-fn (comp (fn [numerator]
                                          ((comp
                                            (partial com/per-1e5 numerator)
                                            kpop
                                            first)
                                           hms))
                                        com/calc-closed)
                                  previous-sum-fun
                                  kc1e5
                                  [{:kw krec :shift 0}
                                   {:kw kdea :shift shift-deaths}])
                [_ hms])))
    (partial map (estim-country-fn com/calc-closed
                                   previous-sum-fun
                                   kclo
                                   [{:kw krec :shift 0}
                                    {:kw kdea :shift shift-deaths}]))
    (partial map
             (fn [[_ hms]]
               ((estim-country-fn (comp (fn [numerator]
                                          ((comp
                                            (partial com/per-1e5 numerator)
                                            kpop
                                            first)
                                           hms))
                                        com/calc-active)
                                  previous-sum-fun
                                  ka1e5
                                  [{:kw knew :shift 0}
                                   {:kw krec :shift 0}
                                   {:kw kdea :shift shift-deaths}])
                [_ hms])))
    (partial map (estim-country-fn com/calc-active
                                   previous-sum-fun
                                   kact
                                   [{:kw knew :shift 0}
                                    {:kw krec :shift 0}
                                    {:kw kdea :shift shift-deaths}]))
    (partial map
             (fn [[_ hms]]
               ((estim-country-fn (comp (fn [numerator]
                                          ((comp
                                            (partial com/per-1e5 numerator)
                                            kpop
                                            first)
                                           hms))
                                        com/calc-recov)
                                  previous-sum-fun
                                  kr1e5
                                  [{:kw knew :shift shift-recovery}
                                   {:kw kdea :shift shift-deaths}])
                [_ hms])))
    (partial map (estim-country-fn com/calc-recov
                                   previous-sum-fun
                                   krec
                                   [{:kw knew :shift shift-recovery}
                                    {:kw kdea :shift shift-deaths}]))
    (partial group-by kcco))
   pic-data))

(defn estimate-initial-sum
  "Create `last-estim-edn-file`"
  [pic-data]
  ((comp
    (fn [m]
      (clojure.pprint/pprint
       ((comp
         (partial sort-by kcco)
         (partial mapv (fn [[_ hms]] (last hms))))
        m)
       (clojure.java.io/writer last-estim-edn-file)))
    (fn [pd] (estimate pd zero-sum)))
   pic-data))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.estimate)
