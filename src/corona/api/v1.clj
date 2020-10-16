(printf "Current-ns [%s] loading %s\n" *ns* 'corona.api.v1)

(ns corona.api.v1
  "Version 1 of the https://coronavirus-tracker-api.herokuapp.com/"
  (:refer-clojure :exclude [pr])
  (:require
   [corona.common :as com]
   [corona.country-codes :refer :all]
   #_[utils.core :refer [in?] :exclude [id]]
   [corona.api.expdev07 :as srvc]
   [net.cgrand.xforms :as x]
   #_[taoensso.timbre :as timbre :refer [debug debugf info infof warn errorf fatalf]]
   )
  (:import java.text.SimpleDateFormat
           java.util.TimeZone))

;; avoid creating new class each time the `fmt` function is called
(def sdf
  "SimpleDateFormat"
  (let [sdf (new SimpleDateFormat "MM/dd/yy")]
    (.setTimeZone sdf (TimeZone/getDefault))
    sdf))

(defn fmt [raw-date] (.parse sdf (srvc/keyname raw-date)))

;; (defn for-case [case]
;;   (->> (get-in (json-data) [case :locations])
;;        (filter (fn [loc]
;;                  true
;;                  #_(in? ccs (:country_code loc))))
;;        (map (fn [loc]
;;               (let [cc (:country_code loc)]
;;                 (->> (sort-by
;;                       :f
;;                       (map (fn [[f v]] {:cc cc :f (fmt f) case v})
;;                            (:history loc)))
;;                      #_(take-last 3)))))
;;        (flatten)
;;        (group-by :f)
;;        (map (fn [[f hms]]
;;               (map (fn [[cc hms]]
;;                      {:cc cc :f f case (reduce + (map case hms))})
;;                    (group-by :cc hms))))
;;        (flatten)
;;        (sort-by :cc)))

(def ^:const ccs
  #{
    ;; cr tg za pe lc ch ru si au kr it fi sc tt my sy mn am dz uy td dj bi mk
    ;; mu li gr gy cg ml gm sa bh ne bn xk cd dk bj me bo jo cv ve ci uz tn is
    ;; ga tz at lt np bg il pk pt hr mr ge hu tw mm sr va kw se gb qq vn cf pa
    ;; vc jp ir af ly mz ro qa cm by sd ar br zw nz fj id sv cn ht rw ba tl jm
    ;; ke py cy gh ma sg lk ph sm tr ps bz cu ad dm lr om so do al fr gw bb ca
    ;; mg kh la hn th de lb kz ec no ao et md ag be mv sz cz cl bt nl eg sn ee
    ;; kn bw ni pg iq kg us zm mc gt bf lu ua ie lv gd mw bs az sk gq in es co
    ;; rs ng ug sl er ae bd mt gn na mx pl
    })

(defn xf-for-case
  "E.g.
(
  {:cc \"SK\" :f #inst \"2020-04-04T00:00:00.000-00:00\" :deaths 1}
  {:cc \"SK\" :f #inst \"2020-03-31T00:00:00.000-00:00\" :deaths 0}
  {:cc \"US\" :f #inst \"2020-04-04T00:00:00.000-00:00\" :deaths 8407}
  {:cc \"US\" :f #inst \"2020-03-31T00:00:00.000-00:00\" :deaths 3873})

  TODO see (require '[clojure.core.reducers :as r])
  "
  [case]

  (defn process-location [{:keys [country_code history]}]
    ;; (def hms hms)
    ;; (def case case)
    ;; (def f f)
    ;; (def country_code country_code)
    ;; (def history history)

    #_(->> (sort-by :f history)
           (map (fn [[f v]] {:cc country_code :f (fmt f) case v}))
           (take-last 2))

    (into [] (comp (x/sort-by :f)
                   (map (fn [[f v]] {:cc country_code :f (fmt f) case v}))
                   #_(x/take-last 2))
          history))

  (defn process-date [[f hms]]
    ;; (def hms hms)
    ;; (def case case)
    ;; (def f f)
    (into []
          ;; the xform for the `into []`
          (comp
           ;; group together provinces of the given country
           (x/by-key :cc (x/reduce conj)) ; (group-by :cc)
           (map (fn [[cc hms]] {:cc cc :f f case (reduce + (map case hms))})))
          hms)

    #_(->> (group-by :cc hms) ;; group together provinces of the given country
           (map (fn [[cc hms]] {:cc cc :f f case (reduce + (map case hms))}))))

  ;; TODO see: "A transducer for clojure.core.flatten"
  ;; https://groups.google.com/forum/#!topic/clojure-dev/J442k0GsWoY
  ;;
  ;; - `flatten` does not provide a transducer, but `cat` and `mapcat`
  ;;   transducers cover most cases.
  ;; - also "remove flatmap in favor of mapcat"
  ;;   https://clojure.atlassian.net/browse/CLJ-1494
  ;;
  ;; Resulting PR `tree-seq` instead of `flatten`:
  ;; https://github.com/cgrand/xforms/issues/20

  ;; Transducers: how-to
  ;; https://www.astrecipes.net/blog/2016/11/24/transducers-how-to/

  (->> (get-in (srvc/data-with-pop) [case :locations])
       (transduce (comp
                   (filter (fn [loc]
                             true
                             #_(in? ccs (:country_code loc))))
                   (map process-location))
                  ;; works as flatten by 1 level
                  into [])
       (transduce (comp
                   (x/by-key :f (x/reduce conj)) ; (group-by :f)
                   (map process-date))
                  ;; works as flatten by 1 level
                  into [])
       (sort-by :cc)))

(defn pic-data
  "Returns a collection of hash-maps containing e.g.:
(
  {:cc \"SK\" :f #inst \"2020-04-04T00:00:00.000-00:00\" :c 471    :r 10    :d 1    :p 5459642   :i 460}
  {:cc \"SK\" :f #inst \"2020-03-31T00:00:00.000-00:00\" :c 363    :r 3     :d 0    :p 5459642   :i 360}
  {:cc \"US\" :f #inst \"2020-04-04T00:00:00.000-00:00\" :c 308853 :r 14652 :d 8407 :p 331002651 :i 285794}
  {:cc \"US\" :f #inst \"2020-03-31T00:00:00.000-00:00\" :c 188172 :r 7024  :d 3873 :p 331002651 :i 177275}
)"
  []
  (apply map
         (fn [
               {:keys [population]}
               {:keys [cc f confirmed]}
               {:keys [recovered]}
               {:keys [deaths]}]
             (let [prm {:cc cc :f f :c confirmed :r recovered :d deaths
                        :p population
                        }]
               (assoc
                prm
                #_(dissoc prm :c)
                :i (com/calculate-active prm)
                :i100k ((com/calculate-cases-per-100k :i) prm)
                :r100k ((com/calculate-cases-per-100k :r) prm)
                :d100k ((com/calculate-cases-per-100k :d) prm))))
         (map xf-for-case [:population :confirmed :recovered :deaths])))
