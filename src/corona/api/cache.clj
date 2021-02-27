;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.api.cache)

(ns corona.api.cache
  (:require [clojure.spec.alpha :as spec]
            [clojure.spec.test.alpha :as spect]
            [clojure.string :as cstr]
            [corona.macro :refer [defn-fun-id]]))

(defonce cache (atom {}))

(spec/def ::fun clojure.core/fn?)
(spec/def ::pred-fn (spec/or :nil nil? :fn clojure.core/fn?))
(spec/def ::ks (spec/coll-of keyword?))

(spec/fdef cache! :args (spec/cat :fun ::fun :ks ::ks))
(defn cache!
  "Also return the cached value for further consumption.
  First param must be a function in order to have lazy evaluation."
  [fun ks]
  ;; {:pre [(spec/valid? ::fun fun)
  ;;        (spec/valid? ::ks ks)]}
  (let [data (fun)]
    (swap! cache update-in ks (fn [_] data))
    data))
;; (spect/instrument `cache!) ;; (spect/unstrument `cache!)

(spec/fdef from-cache! :args (spec/cat :fun ::fun :ks ::ks))
(defn from-cache!
  [fun ks]
  ;; {:pre [(spec/valid? ::fun fun)
  ;;        (spec/valid? ::ks ks)]}
  (if-let [v (get-in @cache ks)]
    v
    (cache! fun ks)))
;; Instrumentation is likely to be useful at both development time and during
;; testing to discover errors in calling code. It is not recommended to use
;; instrumentation in production due to the overhead involved with checking args
;; specs.
;; Multiple functions can be instrumented without modifying their code
;; (spect/instrument `from-cache!) ;; (spect/unstrument `from-cache!)

(defn aggregation-hash
  "TODO aggregation-hash should not be here"
  []
  (cstr/join "-"
             [(get-in @cache (conj [:v1]   :json-hash))
              (get-in @cache (conj [:owid] :json-hash))]))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.api.cache)
