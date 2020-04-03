(ns corona.api.v1
  (:require [clojure.core.memoize :as memo]
            [clojure.set :as cset]
            [corona.core :as c :refer [read-number]]
            [corona.defs :as d]
            [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clj-time.local :as tl]

            )
  (:import java.text.SimpleDateFormat))

(def url-all
  "http://127.0.0.1:8000/all"
  #_(str "https://" host (:route api-service)))

(defn url [] (str url-all))

(def time-to-live "In minutes" (* 24 60)) ;; the whole day - I'm deving...

(defn data [] (c/get-json (url)))

(def data-memo (memo/ttl data {} :ttl/threshold (* time-to-live 60 1000)))

#_(require '[ clojure.inspector :as i])
#_(i/inspect (data-memo))

(defn raw-dates-unsorted []
  #_[(keyword "2/22/20") (keyword "2/2/20")]
  (->> (data-memo) :confirmed :locations last :history keys))

(defn keyname [key] (str (namespace key) "/" (name key)))

(defn left-pad [s] (c/left-pad s 2))

(defn xf-sort
  "A sorting transducer. Mostly a syntactic improvement to allow composition of
  sorting with the standard transducers, but also provides a slight performance
  increase over transducing, sorting, and then continuing to transduce."
  ([]
   (xf-sort compare))
  ([cmp]
   (fn [rf]
     (let [temp-list (java.util.ArrayList.)]
       (fn
         ([]
          (rf))
         ([xs]
          (reduce rf xs (sort cmp (vec (.toArray temp-list)))))
         ([xs x]
          (.add temp-list x)
          xs))))))

(defn raw-dates []
  (transduce
   (comp
    (map keyname)
    (map (fn [date] (re-find (re-matcher #"(\d+)/(\d+)/(\d+)" date))))
    (map (fn [[_ m d y]]
           (transduce (comp (map left-pad)
                            (interpose "/"))
                      str
                      [y m d])))
    (xf-sort)
    (map (fn [kw] (re-find (re-matcher #"(\d+)/(\d+)/(\d+)" kw))))
    (map (fn [[_ y m d]]
           (keyword
            (transduce (comp (map read-number)
                             (interpose "/"))
                       str
                       [m d y])))))
   conj []
   (raw-dates-unsorted)))

(def cnt-days
  #_2
  (count (raw-dates)))

(defn for-case [case]
  (transduce
   (map (fn [loc]
          (let [cc (:country_code loc)
                timeline (:history loc)]
            (transduce
             (map (fn [[f v]] {:cc cc :f f case v}))
             conj []
             (if-let [norm-timeline (seq
                                     #_(take-last 1 timeline)
                                     (take cnt-days timeline))]
               norm-timeline
               (repeat cnt-days [nil 0]))))))
   into []
   (filter (fn [loc]
             true
             #_(corona.core/in?
              #_["CN"]
              ["CN" "FR" "DE" "US" "IR" "IT" "ES" "UK"]
              (:country_code loc)))
           (get-in (data-memo) [case :locations]))))


(defn pic-data []
  ;; avoid creating new class each time the `fmt` function is called
  (def sdf (new SimpleDateFormat "MM/dd/yy"))
  (defn fmt [raw-date] (.parse sdf (keyname raw-date)))

  (apply map
         (fn [{:keys [cc f confirmed] :as cm}
             {:keys [recovered] :as rm}
             {:keys [deaths] :as dm}]
           {:cc cc
            :f (fmt f)
            :i (c/calculate-ill
                {:cc cc :f f :c confirmed :r recovered :d deaths})})
         (map for-case [:confirmed :recovered :deaths])))

#_(corona.pic/show-pic 64718)
