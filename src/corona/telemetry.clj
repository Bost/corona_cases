(ns corona.telemetry
  (:require
   [clj-memory-meter.core :as meter]
   [taoensso.timbre :as timbre]
   [clojure.string :as cstr]
   [clojure.algo.monads :refer [domonad state-m]]))

(defmacro defn-fun-id
  "Docstring is mandatory!"
  [name docstr args & exprs]
  `(def ~name
     ~docstr
     (fn ~args (let [~'fun-id (str (quote ~name))]
                 ~@exprs))))

#_(defn-fun-id f "f-docs" [x]
  {:pre [(number? x)]}
  (printf "[%s] hi\n" fun-id)
  (inc x))

#_(defn-fun-id g "f-docs" [x]
  (printf "[%s] hi\n" fun-id)
  (inc (f x)))

(def sb "")
(def se " %")

(defn system-time [] (System/currentTimeMillis))

(defmacro debugf [s & exprs] `(timbre/debugf (str "%s%s%s " ~s) sb ~'fun-id se ~@exprs))
(defmacro infof  [s & exprs] `(timbre/infof  (str "%s%s%s " ~s) sb ~'fun-id se ~@exprs))
(defmacro warnf  [s & exprs] `(timbre/warnf  (str "%s%s%s " ~s) sb ~'fun-id se ~@exprs))
(defmacro errorf [s & exprs] `(timbre/errorf (str "%s%s%s " ~s) sb ~'fun-id se ~@exprs))
(defmacro fatalf [s & exprs] `(timbre/fatalf (str "%s%s%s " ~s) sb ~'fun-id se ~@exprs))

(defmacro system-ok?
  "Do not add (:require [corona.common]) since this is a macro. Otherwise a
  circular dependency is created"
  []
  `((comp
     (fn [~'v] (timbre/infof "%s%s%s system-ok? %s" sb ~'fun-id se ~'v)
       ~'v))
    (if (or (= ~'fun-id "-main")
            (= corona.common/env-type :devel))
      (do
        #_
        (timbre/infof
         (str "[%s] do-check"
              " (= ~'fun-id \"-main\"): %s;"
              " (= corona.common/env-type :devel): %s")
         ~'fun-id
         (= ~'fun-id "-main")
         (= corona.common/env-type :devel))
        (timbre/infof
         "%s%s%s\n  %s" sb ~'fun-id se (cstr/join "\n  " (corona.common/show-env)))
        (let [~'dbase-ok? (corona.models.migration/migrated?)]
          (timbre/infof "%s%s%s dbase-ok? %s" sb ~'fun-id se ~'dbase-ok?)
          ~'dbase-ok?))
      (do
        #_
        (timbre/infof
         (str "[%s] no-check"
              " (= ~'fun-id \"-main\"): %s;"
              " (= corona.common/env-type :devel): %s")
         ~'fun-id
         (= ~'fun-id "-main")
         (= corona.common/env-type :devel))
        true))))

(defn- count-chars [object]
  ((comp (fn [v] (format "%s chars" v)) count str) object))

(defn-fun-id measure "" [object & prm]
  (try (apply (partial meter/measure object) prm)
       (catch java.lang.reflect.InaccessibleObjectException e
         #_(timbre/warnf "Caught %s. Returning count of chars." e)
         (count-chars object))
       (catch java.lang.reflect.InvocationTargetException e
         #_(timbre/warnf "Caught %s. Returning count of chars." e)
         (count-chars object))
       (catch Exception e
         (timbre/warnf "Caught %s. Rethrowing..." e)
         (throw e))))

(defn- format-bytes
  "Nicely format `num-bytes` as kilobytes/megabytes/etc.
    (format-bytes 1024) ; -> 2.0 KB
  See https://github.com/metabase/metabase metabase.util/format-bytes"
  [num-bytes]
  (loop [n num-bytes [suffix & more] ["B" "kB" "MB" "GB"]]
    (if (and (seq more)
             (>= n 1024))
      (recur (/ n 1024) more)
      (format "%.1f %s" (float n) suffix))))

(defn add-calc-time
  "Returns a state-monad function that assumes the state to be a map.

  TODO turn this to macro so that `var-name` doesn't have to be specified and
  the namespace is of the plain-val"
  [var-name plain-val]
  (fn [state]
    (let [accumulator (get state :acc)
          time-begin (get state :tbeg)
          calc-time (- (system-time) (+ (apply + accumulator) time-begin))]
      (timbre/debugf
       "%s%s%s %s obtained in %s ms. Available heap %s"
       sb var-name se (if (nil? plain-val) "nil-value" (measure plain-val))
       calc-time
       ((comp
         format-bytes
         (fn [{:keys [size max free]}] (+ (- max size) free)))
        (let [runtime (Runtime/getRuntime)]
          {:size (.totalMemory runtime) ;; current size of heap in bytes

           ;; max size of heap in bytes. The heap cannot grow beyond this size.
           ;; Any attempt will result in an OutOfMemoryException.
           :max (.maxMemory runtime)

           ;; amount of free memory within the heap in bytes. This size will
           ;; increase after garbage collection and decrease as new objects are
           ;; created.
           :free (.freeMemory runtime)})))
      ((domonad state-m [mvv (m-result plain-val)] mvv)
       (update-in state [:acc]
                  ;; the acc-value is ignored so `comp` can't be used
                  (fn [_] (vec (concat accumulator (vector calc-time)))))))))

(defn- fmap
  "See clojure.algo.generic.functor/fmap"
  [f m]
  (into (empty m) (for [[k v] m] [k (f v)])))

(defn-fun-id heap-info
  "See https://github.com/metrics-clojure/metrics-clojure"
  []
  ((comp
    (fn [v] (debugf "%s" v)) ;; debugf is a macro
    (partial fmap format-bytes))
   (let [runtime (Runtime/getRuntime)]
     {:size (.totalMemory runtime) ;; current size of heap in bytes

      ;; max size of heap in bytes. The heap cannot grow beyond this size. Any
      ;; attempt will result in an OutOfMemoryException.
      :max (.maxMemory runtime)

      ;; amount of free memory within the heap in bytes. This size will increase
      ;; after garbage collection and decrease as new objects are created.
      :free (.freeMemory runtime)})))

;; (defn-fun-id foo "docstr" []
;;   (debugf "some %s text %s" 'a 1)
;;   (debugf "%s" {:a 1 :b 2})
;;   1)

#_(defmacro debugf [s & exprs] `((quote ~'debugf) (str "[%s] " ~s) ~'fun-id ~@exprs))

#_(defmacro defn-fun-id2
  "Docstring is mandatory!"
  [name docstr args & exprs]
  `(def ~name
     ~docstr
     (fn ~args
       (let [fun-id# (str (quote ~name))]
         (fn debugf [s & exprs] `(timbre/debugf (str "[%s] " ~s) fun-id# ~@exprs))
         (fn infof  [s & exprs] `(timbre/infof  (str "[%s] " ~s) fun-id# ~@exprs))
         (fn warnf  [s & exprs] `(timbre/warnf  (str "[%s] " ~s) fun-id# ~@exprs))
         (fn errorf [s & exprs] `(timbre/errorf (str "[%s] " ~s) fun-id# ~@exprs))
         (fn fatalf [s & exprs] `(timbre/fatalf (str "[%s] " ~s) fun-id# ~@exprs))
         ~@exprs))))

;; https://clojurians.zulipchat.com/#narrow/stream/151168-clojure/topic/macro.20for.20transforming.20let.20into.20def/near/215975427
;; https://clojurians.zulipchat.com/#narrow/stream/151168-clojure/topic/core.20nuggets/near/218333667
