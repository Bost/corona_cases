;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.estimate)

(ns corona.estimate
  (:require [corona.common :as com]))

(def ^:const shift-recovery
  "Mean number of days/reports between symptoms outbreak and full recovery. (Lucky
  coincidence of 1 report per 1 day!)

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

(defn estim-for-country-fn [calculate-fun kw-estim kw-shift-maps]
  (fn [[_ stats-country-unsorted]]
    (let [stats-country (sort-by :t stats-country-unsorted)]
      #_(def stats-country stats-country)
      (mapv (fn [estim stats-hm] (conj stats-hm {kw-estim estim}))
            (apply map calculate-fun
                   (map (comp
                         (fn [{:keys [vs shift]}] (into (drop-last shift vs)
                                                       (repeat shift 0)))
                         (fn [{:keys [kw shift]}] {:vs (map kw stats-country)
                                                  :shift shift}))
                        kw-shift-maps))
            stats-country))))

(defn estimate [pic-data]
  ((comp
    ;; unsorted [estim] 75.1 MiB
    ;; sorted   [estim] 144.0 MiB
    #_(partial sort-by (juxt :ccode :t))
    #_flatten
    #_(partial map (estim-for-country-fn com/calculate-closed
                                       :ec [{:kw :n  :shift 0}
                                            {:kw :er :shift 0}
                                            {:kw :d  :shift shift-deaths}]))
    #_(partial group-by :ccode)
    flatten
    (partial map
             (fn [[ccode hms]]
               (let [population ((comp :p first) hms)]
                 ((estim-for-country-fn (comp (fn [place] (com/per-1e5 place population))
                                              com/calculate-activ)
                                        :ea100k [{:kw :n  :shift 0}
                                                 {:kw :er :shift 0}
                                                 {:kw :d  :shift shift-deaths}])
                  [ccode hms]))))
    (partial group-by :ccode)

    ;;;;;;;;;;;;;;;;;;;;;;;;;;;
    flatten
    (partial map (estim-for-country-fn com/calculate-activ
                                       :ea [{:kw :n  :shift 0}
                                            {:kw :er :shift 0}
                                            {:kw :d  :shift shift-deaths}]))
    (partial group-by :ccode)
    ;;;;;;;;;;;;;;;;;;;;;;;;;;;
    flatten
    (partial map
             (fn [[ccode hms]]
               (let [population ((comp :p first) hms)]
                 ((estim-for-country-fn (comp (fn [place] (com/per-1e5 place population))
                                              com/calculate-recov)
                                        :er100k [{:kw :n :shift shift-recovery}
                                                 {:kw :d :shift shift-deaths}])
                  [ccode hms]))))
    (partial group-by :ccode)
    ;;;;;;;;;;;;;;;;;;;;;;;;;;
    flatten
    (partial map (estim-for-country-fn com/calculate-recov
                                       :er [{:kw :n :shift shift-recovery}
                                            {:kw :d :shift shift-deaths}]))
    (partial group-by :ccode)

    #_(fn [v] (def m1 v) v)
    #_(partial sort-by (juxt :ccode :t))
    #_(fn [v] (def m0 v) v))
   pic-data))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.estimate)
