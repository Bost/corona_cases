;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.api.cache)

(ns corona.api.cache
  (:require [clojure.spec.alpha :as spec]
            [clojure.string :as cstr]
            [corona.macro :refer [defn-fun-id]]))

(defonce cache (atom {}))

(spec/def ::fun clojure.core/fn?)
(spec/def ::pred-fn (spec/or :nil nil? :fn clojure.core/fn?))

(defonce cnt (atom 0))

(defn-fun-id cache!
  "Also return the cached value for further consumption.
  First param must be a function in order to have lazy evaluation."
  [fun ks]
  {:pre [(spec/valid? ::fun fun)]}
  #_(debugf "[%s] %s Computing %s ..." fun-id @cnt fun)
  #_(swap! cnt inc)
  (let [data (fun)]
      ;; (debugf "[%s] %s Computing ... done." fun-id fun)
    (swap! cache update-in ks (fn [_] data))
    data))

(defn from-cache!
  [fun ks]
  {:pre [(spec/valid? ::fun fun)]}
  ;; (debugf "[from-cache!] accessing %s" ks)
  (if-let [v (get-in @cache ks)]
    v
    (cache! fun ks)))

(defn aggregation-hash
  "TODO aggregation-hash should not be here"
  []
  (cstr/join "-"
             [(get-in @cache (conj [:v1]   :json-hash))
              (get-in @cache (conj [:owid] :json-hash))]))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.api.cache)
