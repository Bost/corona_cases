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
            [corona.msg.info :as msgi]
            [corona.msg.lists :as msgl]
            [corona.msg.messages :as msg]
            [corona.plot :as plot]
            [morse.handlers :as moh]
            [morse.polling :as mop]
            [taoensso.timbre :as timbre :refer [debugf fatalf infof warnf]])
  (:import [java.time Instant LocalDateTime ZoneId]))

;; (set! *warn-on-reflection* true)

(defonce continue (atom true))

;; TODO use com.stuartsierra.compoment for start / stop
;; see https://github.com/miikka/avulias-botti
;; For interactive development:
(defonce initialized (atom nil))

(defonce telegram-port (atom nil))

(defn wrap-in-hooks
  "Add :pre and :post hooks / advices around `function`
  Thanks to https://stackoverflow.com/a/10778647/5151982
  TODO doesn't work for multiarity functions. E.g.
  (defn fun ([] (fun 1)) ([x] x))"
  ([prm fun] (wrap-in-hooks "wrap-in-hooks" prm fun))
  ([fun-id {:keys [pre post]} fun]
   (fn [& args]
     (apply pre args)
     (let [result (apply fun args)]
       (apply post (cons result args))))))

;; logging alternatives - see also method advising (using multimethod):
;; https://github.com/camsaul/methodical
;; https://github.com/technomancy/robert-hooke

(defn create-cmds
  ([cmds] (create-cmds "create-cmds" cmds))
  ([fun-id cmds]
   (map
    (fn [m]
      (let [{name :name fun :fun} m]
        ((comp
          (partial moh/command-fn name)
          (partial wrap-in-hooks {:pre (fn [& args]
                                         (let [chat (:chat (first args))]
                                           (infof "[%s] :pre /%s chat %s" fun-id name chat)))
                                  :post (fn [& args]
                                          (let [[fn-result {:keys [chat]}] args]
                                            (infof "[%s] :post /%s chat %s" fun-id name chat)
                                            fn-result))}))
         (fn [m] (fun (:id (:chat m)))))))
    cmds)))

(defn create-callbacks
  ([funs] (create-callbacks "create-callbacks" funs))
  ([fun-id funs]
   (map
    (fn [fun]
      (->> fun
           (wrap-in-hooks {:pre (fn [& args]
                                  (let [{:keys [data message]} (first args)]
                                    (infof "[%s] :pre data %s chat %s"
                                           fun-id data (:chat message))))
                           :post (fn [& args]
                                   (let [[fn-result {:keys [data message]}] args]
                                     (infof "[%s] :post data %s chat %s"
                                            fun-id data (:chat message))
                                     fn-result))})
           (moh/callback-fn)))
    funs)))

(defn create-handlers
  "Receiving incoming updates using long polling (getUpdates method)
  https://en.wikipedia.org/wiki/Push_technology#Long_polling
  An Array of Update-objects is returned."
  ([] (create-handlers "create-handlers"))
  ([fun-id]
   (let [callbacks (create-callbacks [msg/worldwide-plots])
         commands (create-cmds cmd/cmds)]
     (infof "[%s] Registering %s chatbot commands and %s callbacks ..."
            fun-id (count commands) (count callbacks))
     (into callbacks commands))))

(defn api-error-handler [] (when com/env-corona-cases? (com/system-exit 2)))

(defn start-polling
  "Receiving incoming updates using long polling (getUpdates method)
  https://en.wikipedia.org/wiki/Push_technology#Long_polling
  An Array of Update-objects is returned.

  Starts long-polling process.
  Handler is supposed to process immediately, as it will be called in a blocking
  manner."
  ([token handler] (start-polling "start-polling" token handler))
  ([fun-id token handler]
   (let [opts {}
         channel (async/chan)]
     (debugf "[%s] Started channel %s" fun-id (com/log-obj channel))
     (let [producer (mop/create-producer channel token opts api-error-handler)]
       (infof "[%s] Created producer %s on channel %s"
              fun-id (com/log-obj producer) (com/log-obj channel))
       #_(debugf "[%s] Creating consumer for produced %s with handler %s ..."
                 fun-id producer handler)
       (let [consumer (mop/create-consumer producer handler)]
         (infof "[%s] Created consumer %s for producer %s with handler %s"
                fun-id (com/log-obj consumer) (com/log-obj producer)
                (com/log-obj handler))
         channel)))))

(defn long-polling
  "TODO see https://github.com/Otann/morse/issues/32"
  ([tgram-token] (long-polling "long-polling" tgram-token))
  ([fun-id tgram-token]
   (let [msg (format "[%s] Starting" fun-id)]
     (infof "%s ..." msg)
     (if-let [polling-handlers (apply moh/handlers (create-handlers))]
       (do
         (debugf "[%s] Created polling-handlers %s"
                 fun-id (com/log-obj polling-handlers))
         (let [port (start-polling tgram-token polling-handlers)]
           (swap! telegram-port (fn [_] port))
           (let [retval-async<!! (async/<!! port)]
             (warnf "[%s] Taking vals on port %s stopped with retval-async<! %s"
                    fun-id
                    (com/log-obj port) (if-let [v retval-async<!!] v "nil"))
             (fatalf "[%s] Further requests may NOT be answered!!!" fun-id)
             (api-error-handler))))
       (fatalf "[%s] polling-handlers not created" fun-id))
     (infof "%s ... done" msg))))

(defn endlessly
  "Invoke fun and put the thread to sleep for millis in an endless loop."
  ([fun ttl] (endlessly "endlessly" fun ttl))
  ([fun-id fun ttl]
   (let [msg (format "[%s] Starting" fun-id)]
     (infof "%s ..." msg)
     (while @continue
       (infof "[%s] Next eval of %s scheduled at %s" fun-id
              (com/log-obj fun)
              (LocalDateTime/ofInstant
               (Instant/ofEpochMilli (+ (System/currentTimeMillis) ttl))
               (ZoneId/of ccc/zone-id)))
       (Thread/sleep ttl)
       (fun))
     (warnf "%s ... done. Data will NOT be updated!" msg))))

(def map-fn #_map pmap)
(def map-aggregation-fn map #_pmap)

(defn calc-cache!
  ([aggegation-hash json] (calc-cache! "calc-cache!" aggegation-hash json))
  ([fun-id aggegation-hash json]
   (run! (fn [prms] (apply (partial msgl/calc-listings json) prms))
         [[msgl/list-countries com/listing-cases-absolute]
          [msgl/list-per-100k com/listing-cases-per-100k]])
   (com/heap-info)
   (Thread/sleep 100)
   (debugf "[%s] 2nd garbage collection" fun-id)
   (System/gc) ;; also (.gc (Runtime/getRuntime))
   (com/heap-info)
   (let [stats (est/estimate (v1/pic-data))
         cnt-reports (count (data/dates json))]
     ;; TODO do not call calc-functions when the `form` evaluates to true
     (when (< cnt-reports 10)
       (warnf "[%s] Some stuff may not be calculated: %s" "(< cnt-reports 10)"
              fun-id))
     (doall
      (map-fn (fn [ccode]
                (msgi/detailed-info ccode json com/html
                                    (data/create-pred-hm ccode))
                (plot/plot-country ccode stats cnt-reports))
              ccc/all-country-codes))
     (com/heap-info)
     (Thread/sleep 100)
     (debugf "[%s] 3rd garbage collection" fun-id)
     (System/gc) ;; also (.gc (Runtime/getRuntime))
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
       com/aggregation-cases)))))

(defn- json-changed!
  ([m] (json-changed! "json-changed!" m))
  ([fun-id {:keys [json-fn cache-storage] :as m}]
   (debugf "[%s] %s" fun-id m)
   ;; TODO spec: cache-storage must be vector; json-fns must be function
   (let [hash-kws (conj cache-storage :json-hash)
         ;; (json-fn) also stores the json-data in the cache
         old-hash (get-in @cache/cache hash-kws)
         new-hash (com/hash-fn (json-fn))
         hashes-changed (not= old-hash new-hash)]
     (debugf "[%s] %s; old hash %s; new hash %s; hashes-changed: %s"
             fun-id cache-storage old-hash new-hash hashes-changed)
     (when hashes-changed
       (swap! cache/cache update-in hash-kws (fn [_] new-hash)))
     hashes-changed)))

(defn reset-cache!
  ([] (reset-cache! "reset-cache!"))
  ([fun-id]
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
     (Thread/sleep 100)
     (System/gc) ;; also (.gc (Runtime/getRuntime))
     (debugf "[%s] 1st garbage collection" fun-id)
     (com/heap-info)
     (debugf "[%s] any-json-changed %s" fun-id any-json-changed)
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
       (debugf "[%s] Cache recalculated in %s ms"
               fun-id (- (System/currentTimeMillis) tbeg)))

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

     (debugf "[%s] (keys @cache/cache) %s"
             fun-id (keys @cache/cache))
     (debugf "[%s] Responses %s"
             fun-id (select-keys @cache/cache [:v1 :owid]))
     (debugf "[%s] Cache size %s B"
             fun-id (com/measure @cache/cache)))))

(defn- p-endlessly [] (endlessly reset-cache! com/ttl))
(defn- p-long-polling [] (long-polling com/telegram-token))

(defn start
  "Fetch api service data and only then register the telegram commands."
  ([] (start "start"))
  ([fun-id]
   (let [msg (format "[%s] Starting" fun-id)]
     (if com/use-webhook?
       (infof "%s ..." msg)
       (infof "%s ...\n  %s" msg (cstr/join "\n  " (com/show-env))))
     (reset-cache!)
     (swap! initialized (fn [_]
                        ;; TODO use morse.handler instead of true?
                          true))
     (let [funs (into [p-endlessly]
                      (when-not com/use-webhook?
                        [p-long-polling]))]
       (debugf "[%s] Parallel run %s ..." fun-id funs)
       (pmap (fn [fun] (fun)) funs))
     (infof "%s [..] ... done" msg))))

(defn stop
  ([] (stop "stop"))
  ([fun-id]
   (let [msg (format "[%s] Starting" fun-id)]
     (infof "%s ..." msg)
     (run! (fn [obj-q]
             (let [obj (eval obj-q)]
               (when (= obj-q 'corona.telegram/telegram-port)
                 (if-let [old-tgram-port (deref obj)]
                   (do
                     (debugf "[%s] Closing old-tgram-port %s ..."
                             fun-id (com/log-obj old-tgram-port))
                     (async/close! old-tgram-port))
                   (debugf "[%s] No old-tgram-port defined" fun-id)))
               (swap! obj (fn [_] nil))
               (debugf "[%s] %s new value: %s"
                       fun-id
                       obj-q (if-let [v (deref obj)] v "nil"))))
           ['corona.telegram/initialized
            'corona.api.cache/cache
            'corona.telegram/continue
            'corona.telegram/telegram-port])
     (infof "%s ... done" msg))))

(defn restart
  ([] (restart "restart"))
  ([fun-id]
   (let [msg (format "[%s] Restarting" fun-id)]
     (infof "%s ..." msg)
     (when @initialized
       (stop)
       (Thread/sleep 400))
     (start)
     (infof "%s ... done" msg))))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.telegram)
