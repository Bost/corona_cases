;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.telegram)

;; TODO replace `->>` with `comp` https://github.com/practicalli/clojure-content/issues/160
;; TODO https://stuartsierra.com/2016/01/09/how-to-name-clojure-functions

(ns corona.telegram
  (:gen-class)
  (:require [clojure.core.async :as async]
            [clojure.string :as cstr]
            [corona.api.cache :as cache]
            [corona.api.expdev07 :as data]
            [corona.api.owid :as vac]
            [corona.api.v1 :as v1]
            [corona.commands :as cmd]
            [corona.common :as com]
            [corona.country-codes :as ccc]
            [corona.estimate :as est]
            [corona.macro :refer [defn-fun-id debugf infof warnf fatalf]]
            [corona.msg.info :as msgi]
            [corona.msg.lists :as msgl]
            [corona.msg.messages :as msg]
            [corona.plot :as plot]
            [morse.handlers :as moh]
            [morse.polling :as mop])
  (:import [java.time Instant LocalDateTime ZoneId]))

;; (set! *warn-on-reflection* true)

(defonce continue (atom true))

;; TODO use com.stuartsierra.compoment for start / stop
;; see https://github.com/miikka/avulias-botti
;; For interactive development:
(defonce initialized (atom nil))

(defonce telegram-port (atom nil))

(defn-fun-id wrap-in-hooks
  "Add :pre and :post hooks / advices around `function`
  Thanks to https://stackoverflow.com/a/10778647/5151982
  TODO doesn't work for multiarity functions. E.g.
  (defn fun ([] (fun 1)) ([x] x))"
  [{:keys [pre post]} fun]
  (fn [& args]
    (apply pre args)
    (let [result (apply fun args)]
      (apply post (cons result args)))))

;; logging alternatives - see also method advising (using multimethod):
;; https://github.com/camsaul/methodical
;; https://github.com/technomancy/robert-hooke

(defn-fun-id create-cmds "" [cmds]
  (map
   (fn [m]
     (let [{name :name fun :fun} m]
       ((comp
         (partial moh/command-fn name)
         (partial wrap-in-hooks
                  {:pre (fn [& args]
                          (infof ":pre /%s chat %s" name (:chat (first args))))
                   :post (fn [& args]
                           (let [[fn-result {:keys [chat]}] args]
                             (infof ":post /%s chat %s" name chat)
                             fn-result))}))
        (fn [m] (fun (:id (:chat m)))))))
   cmds))

(defn-fun-id create-callbacks "" [funs]
  (map
   (fn [fun]
     (->> fun
          (wrap-in-hooks
           {:pre (fn [& args]
                   (let [{:keys [data message]} (first args)]
                     (infof ":pre data %s chat %s" data (:chat message))))
            :post (fn [& args]
                    (let [[fn-result {:keys [data message]}] args]
                      (infof ":post data %s chat %s" data (:chat message))
                      fn-result))})
          (moh/callback-fn)))
   funs))

(defn-fun-id create-handlers
  "Receiving incoming updates using long polling (getUpdates method)
  https://en.wikipedia.org/wiki/Push_technology#Long_polling
  An Array of Update-objects is returned."
  []
  (let [callbacks (create-callbacks [msg/worldwide-plots])
        commands (create-cmds cmd/cmds)]
    (infof "Registering %s chatbot commands and %s callbacks ..."
           (count commands) (count callbacks))
    (into callbacks commands)))

(defn api-error-handler [] (when com/env-corona-cases? (com/system-exit 2)))

(defn-fun-id start-polling
  "Receiving incoming updates using long polling (getUpdates method)
  https://en.wikipedia.org/wiki/Push_technology#Long_polling
  An Array of Update-objects is returned.

  Starts long-polling process.
  Handler is supposed to process immediately, as it will be called in a blocking
  manner."
  [token handler]
  (let [opts {}
        channel (async/chan)]
    (debugf "Started channel %s" (com/log-obj channel))
    (let [producer (mop/create-producer channel token opts api-error-handler)]
      (infof "Created producer %s on channel %s"
             (com/log-obj producer) (com/log-obj channel))
      (debugf "Creating consumer for produced %s with handler %s ..."
                producer handler)
      (let [consumer (mop/create-consumer producer handler)]
        (infof "Created consumer %s for producer %s with handler %s"
               (com/log-obj consumer) (com/log-obj producer)
               (com/log-obj handler))
        channel))))

(defn-fun-id long-polling
  "TODO see https://github.com/Otann/morse/issues/32"
  [tgram-token]
  (infof "Starting ...")
  (if-let [polling-handlers (apply moh/handlers (create-handlers))]
    (do
      (debugf "Created polling-handlers %s" (com/log-obj polling-handlers))
      (let [port (start-polling tgram-token polling-handlers)]
        (swap! telegram-port (fn [_] port))
        (let [retval-async<!! (async/<!! port)]
          (warnf "Taking vals on port %s stopped with retval-async<! %s"
                 (com/log-obj port) (if-let [v retval-async<!!] v "nil"))
          (fatalf "Further requests may NOT be answered!!!")
          (api-error-handler))))
    (fatalf "polling-handlers not created"))
  (infof "Starting ... done"))

(defn-fun-id endlessly
  "Invoke fun and put the thread to sleep for millis in an endless loop."
  [fun ttl]
  (infof "Starting ...")
  (while @continue
    (infof "Next eval of %s scheduled at %s"
           (com/log-obj fun)
           (LocalDateTime/ofInstant
            (Instant/ofEpochMilli (+ (System/currentTimeMillis) ttl))
            (ZoneId/of ccc/zone-id)))
    (Thread/sleep ttl)
    (fun))
  (warnf "Starting ... done. Data will NOT be updated!"))

(def map-fn #_map pmap)
(def map-aggregation-fn map #_pmap)

(defn list-countries [json]
  (msgl/calc-listings com/listing-cases-absolute json
                      'corona.msg.lists/list-countries))

(defn list-per-100k [json]
  (msgl/calc-listings com/listing-cases-per-100k json
                      'corona.msg.lists/list-per-100k))

(defn-fun-id calc-cache! "" [aggegation-hash json]
  (run! (fn [fun] (fun json)) [list-countries list-per-100k])
  (com/heap-info)
  (debugf "2nd garbage collection")
  (System/gc) ;; also (.gc (Runtime/getRuntime))
  (Thread/sleep 100)
  (com/heap-info)
  (let [stats (est/estimate (v1/pic-data))
        cnt-reports (count (data/dates json))]
     ;; TODO do not call calc-functions when the `form` evaluates to true
    (when (< cnt-reports 10)
      (warnf "Some stuff may not be calculated: %s" "(< cnt-reports 10)"))
    (doall
     (map-fn (fn [ccode]
               (msgi/detailed-info ccode json com/html
                                   (data/create-pred-hm ccode))
               (plot/plot-country ccode stats cnt-reports))
             ccc/all-country-codes))
    (com/heap-info)
    (debugf "3rd garbage collection")
    (System/gc) ;; also (.gc (Runtime/getRuntime))
    (Thread/sleep 100)
    (com/heap-info)
    (doall
     (map-aggregation-fn
      (fn [aggregation-kw]
        (doall
         (map-aggregation-fn
          (fn [case-kw]
            (plot/plot-aggregation aggegation-hash aggregation-kw case-kw stats
                                   cnt-reports))
          com/absolute-cases)))
      com/aggregation-cases))))

(defn-fun-id json-changed! "" [{:keys [json-fn cache-storage] :as m}]
  (printf "%s\n" m)
  (debugf "%s" m)
   ;; TODO spec: cache-storage must be vector; json-fns must be function
  (let [hash-kws (conj cache-storage :json-hash)
         ;; (json-fn) also stores the json-data in the cache
        old-hash (get-in @cache/cache hash-kws)
        new-hash (com/hash-fn (json-fn))
        hashes-changed (not= old-hash new-hash)]
    (debugf "%s; old hash %s; new hash %s; hashes-changed: %s"
            cache-storage old-hash new-hash hashes-changed)
    (when hashes-changed
      (swap! cache/cache update-in hash-kws (fn [_] new-hash)))
    hashes-changed))

(defn-fun-id reset-cache! "" []
  ;; full cache cleanup is not really necessary
  #_(swap! cache/cache (fn [_] {}))
  (let [tbeg (System/currentTimeMillis)
        any-json-changed ((comp
                           boolean
                           (partial some true?)
                           (partial pmap json-changed!))
                          [{:json-fn data/json-data :cache-storage [:v1]}
                           {:json-fn vac/json-data  :cache-storage [:owid]}])]
    (com/heap-info)
    (System/gc) ;; also (.gc (Runtime/getRuntime))
    (debugf "1st garbage collection")
    (Thread/sleep 100)
    (com/heap-info)
    (debugf "any-json-changed %s" any-json-changed)
    (when any-json-changed
      (calc-cache! (cache/aggregation-hash) (data/json-data))
       ;; discard the intermediary results, i.e. keep only those items in the
       ;; cache which contain the final results.
      (swap! cache/cache
             (fn [_]
               (conj
                {:v1   {:json-hash (get-in @cache/cache [:v1   :json-hash])}}
                {:owid {:json-hash (get-in @cache/cache [:owid :json-hash])}}
                (select-keys
                 @cache/cache [:plot :msg :list :threshold]))))
      (debugf "Cache recalculated in %s ms"
              (- (System/currentTimeMillis) tbeg)))

     ;; non-atomically dissoc :json from under :v1 and :owid
     ;; (swap! cache/cache update-in [:v1]   dissoc :json)
     ;; (swap! cache/cache update-in [:owid] dissoc :json)
     ;; atomically dissoc :json from under :v1 and :owid
    ((comp
      (partial reset! cache/cache)
      (partial merge @cache/cache)
      (partial apply merge)
      (partial map (fn [[k v]] {k (dissoc v :json)}))
      (partial select-keys @cache/cache))
     [:v1 :owid])

    (debugf "(keys @cache/cache) %s" (keys @cache/cache))
    (debugf "Responses %s" (select-keys @cache/cache [:v1 :owid]))
    (debugf "Cache size %s B" (com/measure @cache/cache))))

(defn- p-endlessly [] (endlessly reset-cache! com/ttl))
(defn- p-long-polling [] (long-polling com/telegram-token))

(defn-fun-id start
  "Fetch api service data and only then register the telegram commands."
  []
  (if com/use-webhook?
    (infof "Starting ...")
    (infof "Starting ...\n  %s" (cstr/join "\n  " (com/show-env))))
  (reset-cache!)
  (swap! initialized (fn [_]
                        ;; TODO use morse.handler instead of true?
                       true))
  (let [funs (into [p-endlessly]
                   (when-not com/use-webhook?
                     [p-long-polling]))]
    (debugf "Parallel run %s ..." funs)
    (pmap (fn [fun] (fun)) funs))
  (infof "Starting [..] ... done"))

(defn-fun-id stop "" []
  (infof "Stopping ...")
  (run! (fn [obj-q]
          (let [obj (eval obj-q)]
            (when (= obj-q 'corona.telegram/telegram-port)
              (if-let [old-tgram-port (deref obj)]
                (do
                  (debugf "Closing old-tgram-port %s ..."
                          (com/log-obj old-tgram-port))
                  (async/close! old-tgram-port))
                (debugf "No old-tgram-port defined")))
            (swap! obj (fn [_] nil))
            (debugf "%s new value: %s"
                    obj-q (if-let [v (deref obj)] v "nil"))))
        ['corona.telegram/initialized
         'corona.api.cache/cache
         'corona.telegram/continue
         'corona.telegram/telegram-port])
  (infof "Stopping ... done"))

(defn-fun-id restart "" []
  (infof "Restarting ...")
  (when @initialized
    (stop)
    (Thread/sleep 400))
  (start)
  (infof "Restarting ... done"))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.telegram)
