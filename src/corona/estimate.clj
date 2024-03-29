;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.estimate)

(ns corona.estimate
  (:require
   [clojure.inspector :as insp :refer [inspect-table inspect-tree]]
   [corona.common :as com]
   [corona.keywords :refer :all]
   [corona.telemetry :refer [debugf defn-fun-id infof warnf]]))

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

(defn-fun-id estim-country-fn "" [calculate-fun kw-estim kw-shift-maps]
  (fn [[ccode hms-stats-country-unsorted]]
    [ccode
     (let [stats-country (sort-by ktst hms-stats-country-unsorted)]
       ;; the map-function operates on two collections
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

(defn estimate "" [pic-data]
  (let [shift (max corona.estimate/shift-recovery
                   corona.estimate/shift-deaths)]
    ((comp
      flatten
      (partial map (fn [[ccode hms]]
;;; TODO check against off-by-1
;;; TODO prevent the IndexOutOfBoundsException as in the corona.api.v1/xf-for-case
;;; the drop must be done after estimation
                     (drop shift hms)))
      (partial map
               (fn [[ccode hms]]
                 (let [population ((comp kpop first) hms)]
                   ((estim-country-fn (comp (fn [place] (com/per-1e5 place population))
                                            com/calc-closed)
                                      (basic-lense kc1e5)
                                      [{:kw (basic-lense krec) :shift 0}
                                       {:kw (basic-lense kdea) :shift shift-deaths}])
                    [ccode hms]))))
      (partial map (estim-country-fn com/calc-closed
                                     (basic-lense kclo)
                                     [{:kw (basic-lense krec) :shift 0}
                                      {:kw (basic-lense kdea) :shift shift-deaths}]))
      (partial map
               (fn [[ccode hms]]
                 (let [population ((comp kpop first) hms)]
                   ((estim-country-fn (comp (fn [place] (com/per-1e5 place population))
                                            com/calc-active)
                                      (basic-lense ka1e5)
                                      [{:kw (basic-lense knew) :shift 0}
                                       {:kw (basic-lense krec) :shift 0}
                                       {:kw (basic-lense kdea) :shift shift-deaths}])
                    [ccode hms]))))
      (partial map (estim-country-fn com/calc-active
                                     (basic-lense kact)
                                     [{:kw (basic-lense knew) :shift 0}
                                      {:kw (basic-lense krec) :shift 0}
                                      {:kw (basic-lense kdea) :shift shift-deaths}]))
      (partial map
               (fn [[ccode hms]]
                 (let [population ((comp kpop first) hms)]
                   ((estim-country-fn (comp (fn [place] (com/per-1e5 place population))
                                            com/calc-recov)
                                      (basic-lense kr1e5)
                                      [{:kw (basic-lense knew) :shift shift-recovery}
                                       {:kw (basic-lense kdea) :shift shift-deaths}])
                    [ccode hms]))))
      (partial map (estim-country-fn com/calc-recov
                                     (basic-lense krec)
                                     [{:kw (basic-lense knew) :shift shift-recovery}
                                      {:kw (basic-lense kdea) :shift shift-deaths}]))
      (partial group-by kcco))
     pic-data)))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.estimate)
