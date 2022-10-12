;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.api.expdev07)

(ns corona.api.expdev07
  (:require
   [clojure.stacktrace]
   [corona.api.cache :as cache]
   [corona.api.owid :as vac]
   [corona.common :as com]
   [corona.countries :as ccr]
   [corona.country-codes :as ccc]
   [corona.telemetry :refer [debugf defn-fun-id errorf]]
   [utils.core :as utc])
  (:import
   (java.text SimpleDateFormat)))

;; (set! *warn-on-reflection* true)

(defn keyname [key] (str (namespace key) "/" (name key)))

(defn left-pad [s] (com/left-pad s 2))

(defn xf-sort
  "A sorting transducer. Mostly a syntactic improvement to allow composition of
  sorting with the standard transducers, but also provides a slight performance
  increase over transducing, sorting, and then continuing to transduce.

  Thanks to https://gist.github.com/matthewdowney/380dd28c1046d4919a8c59a523f804fd.js
  "
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

(defn-fun-id json-data "Iterate over the list of com/json-apis-v1" []
  (loop [[url & urls] com/json-apis-v1]
    (when url
      (if-let [res (com/get-json url)]
        res
        (recur urls)))))

(def xform-raw-dates
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
           (transduce (comp (map com/read-number)
                            (interpose "/"))
                      str
                      [m d y]))))))

(defn raw-dates
  "Size:
  (apply + (map (fn [rd] (count (str rd))) (get-in @cache [:v1 :raw-dates])))
  ;; 2042 chars

  (time ...) measurement:
  (apply + rd-calc)  ;; no cache used
  ;; 3250.596 msecs
  (count rd-calc)
  ;; 1010 items

  (apply + rd-cached) ;; read from cache
  ;; 4.040 msecs
  (count rd-cached)
  ;; 1010 items"
  [json]
  (cache/from-cache!
   (fn []
     (transduce xform-raw-dates
                conj []
                ((comp
                  ;; at least 2 values needed to calc difference
                  #_(partial take-last 4)
                  #_(fn [v] (debugf "count %s" (count v)) v)
                  keys :history
                  #_(fn [v] (debugf "country %s" (:country v)) v)
                  ;; `first` is probably faster that `last`
                  first #_last :locations :confirmed)
                 json)))
   [:v1 :raw-dates]))

(defn-fun-id population-cnt "" [ccode]
  (or (get ccr/population ccode)
      ;; world population is the sum
      ;; 7792480951
      (let [default-population 0]
        (errorf "ccode %s unknown population size. Using %s"
                ccode
                default-population)
        default-population)))

(def date-format (new SimpleDateFormat "MM/dd/yy"))

(defn date
  "(date (keyword \"4/26/20\")) => #inst \"2020-04-26T00:00:00.000-00:00\""
  [raw-date] (.parse date-format (keyname raw-date)))

(defn dates
  "(dates (map keyword [\"4/26/20\" \"4/27/20\"]))
  ;; => (#inst \"2020-04-26T00:00:00.000-00:00\"
  ;;     #inst \"2020-04-27T00:00:00.000-00:00\")"
  [raw-dates-v1]
  ((comp
    (fn [val] (cache/from-cache! (fn [] val) [:v1 :dates]))
    (partial map date))
   raw-dates-v1))

(defn population-data [raw-dates-v1]
  ((comp
    (partial hash-map :population)
    (partial map
             (comp
              (partial apply merge)
              (juxt
               (comp
                (partial hash-map :country)
                ccr/country-name-aliased)
               (partial hash-map :country_code)
               (comp
                (partial hash-map :history)
                (partial zipmap raw-dates-v1)
                repeat
                population-cnt)))))
   ccc/relevant-country-codes))

(defn corona-data [raw-dates-v1 json-v1]
  ((comp
    (partial reduce into {})
    (partial map
             (fn [case-kw-full-name]
               ((comp
                 (partial hash-map case-kw-full-name)
                 (partial into [])
                 (partial map (fn [m]
                                (update-in
                                 m [:history]
                                 (comp
                                  (partial into {})
                                  (partial filter
                                           (comp
                                            (partial utc/in? raw-dates-v1)
                                            first))))))
                 ;; here the country_code keyword comes from the json
                 (partial filter
                          (comp
                           (partial utc/in? ccc/relevant-country-codes)
                           :country_code))
                 (partial get-in json-v1)
                 (fn [kw] [kw :locations]))
                case-kw-full-name)))
    (partial filter (partial utc/in? [:confirmed :deaths :recovered]))
    keys)
   json-v1))

(defn data-with-pop
  "(data-with-pop raw-dates-v1 json-v1 json-owid)"
  [raw-dates-v1 json-v1 json-owid]
  (conj (corona-data raw-dates-v1 json-v1)
        (vac/vaccination-data raw-dates-v1 json-owid)
        (population-data raw-dates-v1)))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.api.expdev07)
