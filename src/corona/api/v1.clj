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

;; avoid creating new class each time the `fmt` function is called
(defonce sdf (new SimpleDateFormat "MM/dd/yy"))
(defn fmt [raw-date] (.parse sdf (keyname raw-date)))

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

#_(def ccs
  #{
    "CR" "TG" "ZA" "PE" "LC" "CH" "RU" "SI" "AU" "KR" "IT" "FI" "SC" "TT" "MY"
    "SY" "MN" "AM" "DZ" "UY" "TD" "DJ" "BI" "MK" "MU" "LI" "GR" "GY" "CG" "ML"
    "GM" "SA" "BH" "NE" "BN" "XK" "CD" "DK" "BJ" "ME" "BO" "JO" "CV" "VE" "CI"
    "UZ" "TN" "IS" "GA" "TZ" "AT" "LT" "NP" "BG" "IL" "PK" "PT" "HR" "MR" "GE"
    "HU" "TW" "MM" "SR" "VA" "KW" "SE" "GB" "QQ" "VN" "CF" "PA" "VC" "JP" "IR"
    "AF" "LY" "MZ" "RO" "QA" "CM" "BY" "SD" "AR" "BR" "ZW" "NZ" "FJ" "ID" "SV"
    "CN" "HT" "RW" "BA" "TL" "JM" "KE" "PY" "CY" "GH" "MA" "SG" "LK" "PH" "SM"
    "TR" "PS" "BZ" "CU" "AD" "DM" "LR" "OM" "SO" "DO" "AL" "FR" "GW" "BB" "CA"
    "MG" "KH" "LA" "HN" "TH" "DE" "LB" "KZ" "EC" "NO" "AO" "ET" "MD" "AG" "BE"
    "MV" "SZ" "CZ" "CL" "BT" "NL" "EG" "SN" "EE" "KN" "BW" "NI" "PG" "IQ" "KG"
    "US" "ZM" "MC" "GT" "BF" "LU" "UA" "IE" "LV" "GD" "MW" "BS" "AZ" "SK" "GQ"
    "IN" "ES" "CO" "RS" "NG" "UG" "SL" "ER" "AE" "BD" "MT" "GN" "NA" "MX" "PL"
    })

(defn for-case [case]
  (->> (get-in (data-memo) [case :locations])
       (filter (fn [loc]
                 true
                 #_(corona.core/in? ccs (:country_code loc))))

       (map (fn [loc]
              (let [cc (:country_code loc)]
                (->> (:history loc)
                     (map (fn [[f v]] {:cc cc :f (fmt f) case v}))
                     (sort-by :f)
                     #_(take (count (raw-dates)))
                     #_(take-last 6)
                     ))))
       (flatten)
       (group-by :f)
       (map (fn [[f hms]]
              (->> (group-by :cc hms)
                   (map (fn [[cc hms]]
                          {:cc cc :f f case (reduce + (map case hms))})))))
       (flatten)))

(defn sum-up [init-xs]
  (loop [xs init-xs
         last-sum 0
         acc []]
    (if (seq xs)
      (let [new-last-sum (+ last-sum (first xs))]
        (recur (rest xs) new-last-sum (conj acc new-last-sum)))
      acc)))

(defn pic-data []
  (->>
   #_[:deaths]
   [:confirmed :recovered :deaths]
   (map for-case)
   (apply map
            (fn [{:keys [cc f confirmed] :as cm}
                {:keys [recovered] :as rm}
                {:keys [deaths] :as dm}]
              {:cc cc
               :f f
               :i (c/calculate-ill
                   {:cc cc :f f :c confirmed :r recovered :d deaths})}))
   #_(flatten)
   #_(sort-by :f)
   ))

#_(corona.pic/show-pic 64718)
