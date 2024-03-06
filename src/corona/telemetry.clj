;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.telemetry)

(ns corona.telemetry
  (:require
   [clj-memory-meter.core :as meter]
   [taoensso.timbre :as timbre]
   [taoensso.timbre.appenders.core :as appenders]
   [clojure.string :as cstr]
   [environ.core :as env]
   [clojure.algo.monads :refer [domonad state-m]]))

;; (set! *warn-on-reflection* true)

(def env-type
  "When developing, check the value of:
      echo $CORONA_ENV_TYPE
  When testing locally via `heroku local --env=.custom.env` check
  the file .custom.env

  TODO env-type priority could / should be:
  1. command line parameter
  2. some config/env file - however not the .custom.env
  3. environment variable
  "
  ((comp keyword cstr/lower-case :corona-env-type) env/env))

(def log-level-map ^:const {:debug :dbg :info :inf :warn :wrn :error :err})

(def fst-beg "[") ;; the very first beginning
(def sb "") ;; intermediary beginning
(def intermediary-end "") ;; intermediary end
(def se "]") ;; the very last end

(defn log-output
  "Default (fn [data]) -> string output fn.
    Use`(partial log-output <opts-map>)` to modify default opts."
  ([     data] (log-output nil data))
  ([opts data] ; For partials
   (let [{:keys [no-stacktrace? #_stacktrace-fonts]} opts
         {:keys [level ?err #_vargs msg_ ?ns-str ?file #_hostname_
                 timestamp_ ?line]} data]
     (str
      (force timestamp_)
      " "
      ;; #?(:clj (force hostname_))  #?(:clj " ")
      ((comp
        (fn [s] (subs s 0 1))
        cstr/upper-case
        name)
       (or (level log-level-map) level))
      " " fst-beg
      (or ((comp
            (fn [n] (subs ?ns-str n))
            inc
            (fn [s] (.indexOf s ".")))
           ?ns-str)
          ?file "?")
      ;; line number indication doesn't work from the defn-fun-id macro
      ;; ":" (or ?line "?")
      intermediary-end " "
      (force msg_)
      (when-not no-stacktrace?
        (when-let [err ?err]
          (str taoensso.encore/system-newline
               (timbre/stacktrace err opts))))))))

(def ^:const zone-id "Europe/Berlin")

#_(timbre/set-config! timbre/default-config)
(timbre/merge-config!
 (conj
  {:output-fn log-output #_timbre/default-output-fn
   :timestamp-opts
   (conj {:timezone (java.util.TimeZone/getTimeZone zone-id)}
         {:pattern "HH:mm:ss.SSSX"})}
  (when-not (= env-type :devel)
    ;; TODO log only last N days
    {:appenders {:spit (appenders/spit-appender {:fname "corona.log"})}})))

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

(defn system-time [] (System/currentTimeMillis))

(defmacro debugf [s & exprs] `(timbre/debugf (str "%s%s%s " ~s) sb ~'fun-id se ~@exprs))
(defmacro infof  [s & exprs] `(timbre/infof  (str "%s%s%s " ~s) sb ~'fun-id se ~@exprs))
(defmacro warnf  [s & exprs] `(timbre/warnf  (str "%s%s%s " ~s) sb ~'fun-id se ~@exprs))
(defmacro errorf [s & exprs] `(timbre/errorf (str "%s%s%s " ~s) sb ~'fun-id se ~@exprs))
(defmacro fatalf [s & exprs] `(timbre/fatalf (str "%s%s%s " ~s) sb ~'fun-id se ~@exprs))

(def is-devel-env? (= env-type :devel))

(defmacro system-ok?
  "Do not add (:require [corona.common]) since this is a macro. Otherwise a
  circular dependency is created"
  []
  `((comp
     (fn [~'v] (timbre/infof "%s%s%s system-ok? %s" sb ~'fun-id se ~'v)
       ~'v))
    (if (or (= ~'fun-id "-main") is-devel-env?)
      (do
        #_
        (timbre/infof
         (str "%s%s%s do-check (= ~'fun-id \"-main\"): %s; is-devel-env?: %s")
         sb ~'fun-id se (= ~'fun-id "-main") is-devel-env?)
        (timbre/infof
         "%s%s%s\n  %s" sb ~'fun-id se (cstr/join "\n  " (corona.common/show-env)))
        (let [~'dbase-ok? (corona.models.migration/migrated?)]
          (timbre/infof "%s%s%s dbase-ok? %s" sb ~'fun-id se ~'dbase-ok?)
          ~'dbase-ok?))
      (do
        #_
        (timbre/infof
         (str "%s%s%s no-check (= ~'fun-id \"-main\"): %s; is-devel-env?: %s")
         sb ~'fun-id se (= ~'fun-id "-main") is-devel-env?)
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

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.telemetry)
