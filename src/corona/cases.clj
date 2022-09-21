;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.cases)

(ns corona.cases
  (:require
   [corona.keywords :refer :all]
   [corona.models.dbase :as dbase]
   [corona.telemetry :refer [debugf defn-fun-id infof warnf]]
   [taoensso.timbre :as timbre]
   [utils.core :as utc]))

(defmacro tore
  "->>-or-eduction. In fact both have the same performance.
  See also:
  - my explanations at https://clojuredocs.org/clojure.core/eduction
  - https://github.com/rplevy/swiss-arrows"
  [coll & fns]
  `(->> ~coll ~@fns)
  #_`(sequence (eduction ~@fns ~coll)))

(def threshold-defaults
  "Recovery data is not provided anymore. So:

On the master branch:
postgres=# select * from thresholds order by kw;
On corona-cases-bot:
  kw |  inc  |   val    |     updated_at
 ----+-------+----------+---------------------
  a  | 10000 |  1900390 | 2022-02-09 06:16:56
  d  |  1000 |   145000 | 2022-01-27 04:07:03
  n  | 50000 | 13510000 | 2022-02-09 06:16:08
  r  | 10000 | 10857000 | 2022-02-09 06:16:30
  "
  ((comp (partial sort-by :kw))
   [{:kw kvac :inc (int 1e6) :val (int 1e7)}
    {:kw kpop :inc (int 1e6) :val (int 1e7)}
    {:kw knew :inc 50000     :val (int 13510e3)}
    {:kw krec :inc 10000     :val (int 12677e3)}
    {:kw kdea :inc 1000      :val (int   153e3)}
    {:kw kact :inc 10000     :val (int  2520e3)}]))

(defn case-kw-val [case-kw]
  ((comp
    (fn [m] (get m :val))
    first)
   (filter (comp (partial = case-kw) :kw)
           threshold-defaults)))

(defn threshold-defaults-sql
  "Sql commands for threshold-defaults"
  []
  (printf "
insert into \"thresholds\" (kw,inc,val,updated_at) values ('a',10000,%s,cast('now()' as timestamp(0)));
insert into \"thresholds\" (kw,inc,val,updated_at) values ('d',1000,%s,cast('now()' as timestamp(0)));
insert into \"thresholds\" (kw,inc,val,updated_at) values ('n',50000,%s,cast('now()' as timestamp(0)));
insert into \"thresholds\" (kw,inc,val,updated_at) values ('r',10000,%s,cast('now()' as timestamp(0)));
\n
update \"thresholds\" set val = %s, updated_at = cast('now()' as timestamp(0)) where kw = 'a';
update \"thresholds\" set val = %s, updated_at = cast('now()' as timestamp(0)) where kw = 'd';
update \"thresholds\" set val = %s, updated_at = cast('now()' as timestamp(0)) where kw = 'n';
update \"thresholds\" set val = %s, updated_at = cast('now()' as timestamp(0)) where kw = 'r';
select * from thresholds order by kw;
"
          (case-kw-val kact) (case-kw-val kdea) (case-kw-val knew) (case-kw-val krec)
          (case-kw-val kact) (case-kw-val kdea) (case-kw-val knew) (case-kw-val krec)))

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
  [
   ;; absolute values
   {:idx  0 :kw kvac}
   {:idx  1 :kw kpop}
   {:idx  2 :kw knew}
   {:idx  3 :kw krec :listing-idx 1}
   {:idx  4 :kw kdea :listing-idx 2}
   {:idx  5 :kw kact :listing-idx 0}

   ;; TODO the order matters: it must be the same as in the info-message
   ;; Incidence per 1e5
   {:idx  6 :kw kv1e5}
   {:idx  7 :kw ka1e5}
   {:idx  8 :kw kr1e5}
   {:idx  9 :kw kd1e5}
   {:idx 10 :kw kc1e5}  ;; closed-per-1e5

   ;; rates
   {:idx 11 :kw :v%}
   {:idx 12 :kw :a%}
   {:idx 13 :kw :r%}
   {:idx 14 :kw :d%}
   {:idx 15 :kw :c%}     ;; closed-rate
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
        (filter (fn [m] (utc/in? [0 1 2 3 4 5 #_6 7 8 9 10] (:idx m))))
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

(defn text-for-case
  "(text-for-case case-kw texts)"
  [case-kw texts]
  ((comp (partial nth texts)
         first
         (partial keep-indexed (fn [i k] (when (= k case-kw) i))))
   basic-cases))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.cases)
