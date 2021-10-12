;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.cases)

(ns corona.cases
  (:require
   [utils.core :as utc]
   [corona.models.dbase :as dbase]
   [taoensso.timbre :as timbre]
   [corona.macro :refer [defn-fun-id debugf infof warnf]]
   ))

(defmacro tore
  "->>-or-eduction. In fact both have the same performance.
  See also:
  - my explanations at https://clojuredocs.org/clojure.core/eduction
  - https://github.com/rplevy/swiss-arrows"
  [coll & fns]
  `(->> ~coll ~@fns)
  #_`(sequence (eduction ~@fns ~coll)))

#_
(printf "
update \"thresholds\" set val = %s, updated_at = cast('now()' as timestamp(0)) where kw = 'a';
update \"thresholds\" set val = %s, updated_at = cast('now()' as timestamp(0)) where kw = 'd';
update \"thresholds\" set val = %s, updated_at = cast('now()' as timestamp(0)) where kw = 'n';
update \"thresholds\" set val = %s, updated_at = cast('now()' as timestamp(0)) where kw = 'r';
select * from thresholds order by kw;
" 5159000 132000 5310000 3667000)
(def threshold-defaults
  "Recovery data is not provided anymore. So:
Old values on corona-cases:
kw |  inc  |   val   |     updated_at
----+-------+---------+---------------------
a  | 10000 | 5119000 | 2021-09-13 05:31:32
d  |  1000 |  134000 | 2021-06-17 05:21:14
n  | 50000 | 5260000 | 2021-09-10 04:25:13
r  | 10000 | 4087000 | 2021-07-30 05:24:33

Old values on hokuspokus:
kw |  inc  |   val   |     updated_at
----+-------+---------+---------------------
a  | 10000 | 5119000 | 2021-09-12 21:26:43
d  |  1000 |  134000 | 2021-06-23 05:23:34
n  | 50000 | 5260000 | 2021-09-09 17:27:41
r  | 10000 | 3867000 | 2021-07-27 05:22:40"
  ((comp (partial sort-by :kw))
   [{:kw :v :inc (int 1e6) :val (int 1e7)}
    {:kw :p :inc (int 1e6) :val (int 1e7)}
    {:kw :n :inc 50000     :val (int 3660e3)}
    {:kw :r :inc 10000     :val (int 2937e3)}
    {:kw :d :inc 1000      :val (int 87e3)}
    {:kw :a :inc 10000     :val (int 1029e3)}]))

(defn norm [raw-ths]
  ((comp
    (fn [ths] (def ths ths) ths)
    (partial map
             (comp
              (fn [m] (update-in m [:kw] keyword))
              (fn [m]
                (clojure.set/rename-keys
                 m
                 {:thresholds/kw :kw
                  :thresholds/inc :inc
                  :thresholds/val :val
                  :thresholds/updated_at :updated_at}))))
    (fn [ths] (def raw-ths ths) ths))
   raw-ths))

(def case-params
  [{:idx  0 :kw :v}
   {:idx  1 :kw :p}
   {:idx  2 :kw :n}
   {:idx  3 :kw :r :listing-idx 1}
   {:idx  4 :kw :d :listing-idx 2}
   {:idx  5 :kw :a :listing-idx 0}

   ;; TODO the order matters: it must be the same as in the info-message
   {:idx  6 :kw :v1e5}
   {:idx  7 :kw :a1e5}
   {:idx  8 :kw :r1e5}
   {:idx  9 :kw :d1e5}
   {:idx 10 :kw :c1e5}  ;; closed-per-1e5

   {:idx 11 :kw :v%}
   {:idx 12 :kw :a%}
   {:idx 13 :kw :r%}
   {:idx 14 :kw :d%}
   {:idx 15 :kw :c%}     ;; closed-rate
   {:idx 16 :kw :ea}     ;; estimate-active
   {:idx 17 :kw :er}     ;; estimate-recovered
   {:idx 18 :kw :ea1e5} ;; estimate-active-per-1e5
   {:idx 19 :kw :er1e5} ;; estimate-recovered-per-1e5
   {:idx 20 :kw :c}      ;; closed
   {:idx 21 :kw :ec}     ;; estimate-closed
   {:idx 22 :kw :ec1e5} ;; estimate-closed-per-1e5
   ])

(def aggregation-params
  ":idx - defines an order in appearance"
  [{:idx  0 :kw :sum}
   {:idx  1 :kw :abs}])

(def aggregation-cases
  (tore aggregation-params
        (filter (fn [m] (utc/in? [0 1] (:idx m))))
        (map :kw)))

(def absolute-cases
  (tore case-params
        (filter (fn [m] (utc/in? [2 3 4 5] (:idx m))))
        (map :kw)))

(def cartesian-product-all-case-types
  (for [a aggregation-cases
        b absolute-cases]
    [a b]))

(def basic-cases
  (tore case-params
        (filter (fn [m] (utc/in? [2 3 4 5 #_6 7 8 9 10] (:idx m))))
        (map :kw)))

(def all-report-cases
  (tore case-params
        (filter (fn [m] (utc/in? [0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15] (:idx m))))
        (map :kw)))

(def all-cases
  (tore case-params
        (map :kw)))

(def ranking-cases
  (tore case-params
        (filter (fn [m] (utc/in? [1 6 7 8 9 10] (:idx m))))
        (mapv :kw)))

(def listing-cases-per-1e5
  (tore case-params
        (filter (fn [m] (utc/in? [7 8 9] (:idx m))))
        (map :kw)))

(def listing-cases-absolute
  ((comp
    (partial map :kw)
    (partial sort-by :listing-idx)
    (partial filter (fn [m] (utc/in? [0 1 2] (:listing-idx m)))))
   case-params))

(defn text-for-case [case-kw texts]
  ((comp (partial nth texts)
         first
         (partial keep-indexed (fn [i k] (when (= k case-kw) i))))
   basic-cases))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.cases)
