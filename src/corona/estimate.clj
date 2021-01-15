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
      (mapv (fn [estim stats-hm] (conj stats-hm {kw-estim estim}))
            (apply map (fn [& prm]
                         ((comp
                           (fn [prm] (apply calculate-fun prm)))
                          prm))
                   (map (comp
                         (fn [{:keys [vs shift]}] (into (drop-last shift vs)
                                                        (repeat shift 0)))
                         (fn [{:keys [kw shift]}] {:vs (map kw stats-country)
                                                  :shift shift}))
                        kw-shift-maps))
            stats-country))))

(defn estimate [pic-data]
  ((comp
    (partial sort-by :ccode)
    flatten
    (partial map (estim-for-country-fn com/calculate-activ
                                       :ea [{:kw :c  :shift 0}
                                            {:kw :er :shift 0}
                                            {:kw :d  :shift shift-deaths}]))
    (partial group-by :ccode)
    flatten
    (partial map (estim-for-country-fn com/calculate-recov
                                       :er [{:kw :c :shift shift-recovery}
                                            {:kw :d :shift shift-deaths}]))
    (partial group-by :ccode))
   pic-data))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.estimate)
