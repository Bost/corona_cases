;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.estimate)

(ns corona.estimate
  (:require [corona.common :as com :refer [lense kc kact kp kr kn kd krep kest kabs k1e5 k%%%]]
            [corona.macro :refer [defn-fun-id debugf infof warnf]]))

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

(defn-fun-id estim-new-country-fn "" [calculate-fun kw-estim kw-shift-maps]
  (fn [[ccode hms-stats-country-unsorted]]
    [ccode
     (let [stats-country (sort-by :t hms-stats-country-unsorted)]
       #_(def stats-country stats-country)
       (map
        (fn [estim stats-hm]
          #_(debugf "estim %s" estim)
          (conj stats-hm
                (update-in stats-hm kw-estim (fn [_] estim))))
        (apply map calculate-fun
               (map (comp
                     (fn [{:keys [vs shift]}] (into (drop-last shift vs)
                                                   (repeat shift 0)))
                     (fn [{:keys [kw shift]}] {:vs (map (fn [stats] (get-in stats kw))
                                                       stats-country)
                                              :shift shift}))
                    kw-shift-maps))
        stats-country))]))

(defn estimate-new "" [pic-data]
  #_(def pic-data pic-data)
  ((comp
    #_(fn [v] (def sorted-flat-hms v) v)
    #_(partial sort-by (juxt com/kccode com/kt))
    #_(fn [v] (def flat-hms v) v)
    flatten
    #_(fn [v] (def hms v) v)
    (partial map (fn [[ccode hms]] hms))
    ;; #_(fn [v] (def ec1e5 v) v)
    (partial map
             (fn [[ccode hms]]
               (let [population ((comp com/kp first) hms)]
                 ((estim-new-country-fn (comp (fn [place] (com/per-1e5 place population))
                                              com/calc-closed)
                                        (lense kc kest k1e5)
                                        [{:kw (lense kr kest kabs) :shift 0} ;; from kest
                                         {:kw (lense kd krep kabs) :shift shift-deaths}])
                  [ccode hms]))))
    ;; #_(fn [v] (def ec v) v)
    (partial map (estim-new-country-fn com/calc-closed
                                       (lense kc kest kabs)
                                       [{:kw (lense kr kest kabs) :shift 0} ;; from kest
                                        {:kw (lense kd krep kabs) :shift shift-deaths}]))
    ;; #_(fn [v] (def ea1e5 v) v)
    (partial map
             (fn [[ccode hms]]
               (let [population ((comp com/kp first) hms)]
                 ((estim-new-country-fn (comp (fn [place] (com/per-1e5 place population))
                                              com/calc-active)
                                        (lense kact kest k1e5)
                                        [{:kw (lense kn krep kabs) :shift 0}
                                         {:kw (lense kr kest kabs) :shift 0} ;; from kest
                                         {:kw (lense kd krep kabs) :shift shift-deaths}])
                  [ccode hms]))))
    ;; #_(fn [v] (def ea v) v)
    (partial map (estim-new-country-fn com/calc-active
                                       (lense kact kest kabs)
                                       [{:kw (lense kn krep kabs) :shift 0}
                                        {:kw (lense kr kest kabs) :shift 0} ;; from kest
                                        {:kw (lense kd krep kabs) :shift shift-deaths}]))
    ;; #_(fn [v] (def er1e5 v) v)
    (partial map
             (fn [[ccode hms]]
               (let [population ((comp com/kp first) hms)]
                 ((estim-new-country-fn (comp (fn [place] (com/per-1e5 place population))
                                              com/calc-recov)
                                        (lense kr kest k1e5)
                                        [{:kw (lense kn krep kabs) :shift shift-recovery}
                                         {:kw (lense kd krep kabs) :shift shift-deaths}])
                  [ccode hms]))))
    ;; #_(fn [v] (def er v) v)
    (partial map (estim-new-country-fn com/calc-recov
                                       (lense kr kest kabs)
                                       [{:kw (lense kn krep kabs) :shift shift-recovery}
                                        {:kw (lense kd krep kabs) :shift shift-deaths}]))
    #_(fn [v] (def gc v) v)
    (partial group-by com/kccode)
    )
   pic-data))

(defn estim-for-country-fn [calculate-fun kw-estim kw-shift-maps]
  (fn [[ccode hms-stats-country-unsorted]]
    [ccode
     (let [stats-country (sort-by :t hms-stats-country-unsorted)]
       #_(def stats-country stats-country)
       (map
        (fn [estim stats-hm] (conj stats-hm {kw-estim estim}))
        (apply map calculate-fun
               (map (comp
                     (fn [{:keys [vs shift]}] (into (drop-last shift vs)
                                                    (repeat shift 0)))
                     (fn [{:keys [kw shift]}] {:vs (map (fn [stats] (get-in stats kw))
                                                        stats-country)
                                               :shift shift}))
                    kw-shift-maps))
        stats-country))]))

(defn estimate [pic-data]
  #_(def pic-data pic-data)
  ((comp
    ;; "134.0 MiB"
    #_(fn [v] (def sorted-flat-hms v) v)
    #_(partial sort-by (juxt :ccode :t))
    ;; "52.8 MiB"
    #_(fn [v] (def flat-hms v) v)
    flatten
    ;; "52.8 MiB"
    #_(fn [v] (def hms v) v)
    (partial map (fn [[ccode hms]] hms))
    ;; "52.8 MiB"
    #_(fn [v] (def ec1e5 v) v)
    (partial map
             (fn [[ccode hms]]
               (let [population ((comp :p first) hms)]
                 ((estim-for-country-fn (comp (fn [place] (com/per-1e5 place population))
                                              com/calc-closed)
                                        :ec1e5 [{:kw (lense :er) :shift 0}
                                                {:kw (lense :d)  :shift shift-deaths}])
                  [ccode hms]))))
    ;; "52.9 MiB"
    #_(fn [v] (def ec v) v)
    (partial map (estim-for-country-fn com/calc-closed
                                       :ec [{:kw (lense :er) :shift 0}
                                            {:kw (lense :d)  :shift shift-deaths}]))
    ;; "52.8 MiB"
    #_(fn [v] (def ea1e5 v) v)
    (partial map
             (fn [[ccode hms]]
               (let [population ((comp :p first) hms)]
                 ((estim-for-country-fn (comp (fn [place] (com/per-1e5 place population))
                                              com/calc-active)
                                        :ea1e5 [{:kw (lense :n)  :shift 0}
                                                {:kw (lense :er) :shift 0}
                                                {:kw (lense :d)  :shift shift-deaths}])
                  [ccode hms]))))
    ;; "52.7 MiB"
    #_(fn [v] (def ea v) v)
    (partial map (estim-for-country-fn com/calc-active
                                       :ea [{:kw (lense :n)  :shift 0}
                                            {:kw (lense :er) :shift 0}
                                            {:kw (lense :d)  :shift shift-deaths}]))
    ;; "52.6 MiB"
    #_(fn [v] (def er1e5 v) v)
    (partial map
             (fn [[ccode hms]]
               (let [population ((comp :p first) hms)]
                 ((estim-for-country-fn (comp (fn [place] (com/per-1e5 place population))
                                              com/calc-recov)
                                        :er1e5 [{:kw (lense :n) :shift shift-recovery}
                                                {:kw (lense :d) :shift shift-deaths}])
                  [ccode hms]))))
    ;; "52.6 MiB"
    #_(fn [v] (def er v) v)
    (partial map (estim-for-country-fn com/calc-recov
                                       :er [{:kw [:n] :shift shift-recovery}
                                            {:kw [:d] :shift shift-deaths}]))
    ;; "51.2 MiB"
    #_(fn [v] (def gc v) v)
    (partial group-by :ccode)
    ;; "56.3 MiB"
    )
   pic-data))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.estimate)
