;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.telegram)

;; TODO replace `->>` with `comp` https://github.com/practicalli/clojure-content/issues/160
;; TODO https://stuartsierra.com/2016/01/09/how-to-name-clojure-functions

(ns corona.telegram
  (:gen-class)
  (:require [clojure.core.async :as async]
            [corona.api.expdev07 :as data]
            [corona.api.vaccination :as vac]
            [corona.api.cache :as cache]
            [corona.api.v1 :as v1]
            [corona.commands :as cmd]
            [corona.common :as com]
            [corona.countries :as ccr]
            [corona.country-codes :as ccc]
            [corona.msg.info :as msgi]
            [corona.msg.lists :as msgl]
            [corona.msg.messages :as msg]
            [corona.estimate :as est]
            [corona.plot :as plot]
            [morse.handlers :as moh]
            [morse.polling :as mop]
            [taoensso.timbre :as timbre :refer [debugf fatalf infof warnf]]))

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
     (infof "[%s] registering %s chatbot commands and %s callbacks ..."
            fun-id (count commands) (count callbacks))
     (into callbacks commands))))

(defn api-error-handler [] (when com/env-prod? (com/system-exit 2)))

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
  ([tgram-token] (long-polling "telegram" tgram-token))
  ([fun-id tgram-token]
   (infof "[%s] Starting ..." fun-id)
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
   (infof "[%s] Starting ... done" fun-id)))

(defn calc-listings [json fun case-kws]
  (run! (fn [case-kw]
          (let [coll (sort-by case-kw < (data/stats-countries json))
               ;; Split the long list of all countries into smaller sub-parts
               ;; TODO move message partitioning to the corona.msg.lists
                sub-msgs (partition-all (/ (count coll)
                                           cmd/cnt-messages-in-listing) coll)
                options {:parse_mode com/html}
                prm (conj options {:cnt-msgs (count sub-msgs)
                                   :cnt-reports (count (data/dates json))
                                   :pred-hm (msg/create-pred-hm (ccr/get-country-code ccc/worldwide))})]
            (doall
             (map-indexed (fn [idx sub-msg]
                            (fun case-kw (inc idx) (conj prm {:data sub-msg})))
                          sub-msgs))))
        case-kws))

(defn endlessly
  "Invoke fun and put the thread to sleep for millis in an endless loop."
  ([fun ttl] (endlessly "endlessly" fun ttl))
  ([fun-id fun ttl]
   (infof "[%s] Starting ..." fun-id)
   (while @continue
     (Thread/sleep ttl)
     (fun))
   (debugf "[%s] Starting ... done" fun-id)
   (warnf "[%s] Displayed data will NOT be updated!" fun-id)))

(def map-fn #_map pmap)
(def map-aggregation-fn map #_pmap)

(def json-hash-v1 [:json-hash :v1])
(def json-hash-owid [:json-hash :owid])
(def json-v1 [:json :v1])
(def json-owid [:json :owid])

(defn do-reset-cache!
  ([new-hash tbeg] (do-reset-cache! "do-reset-cache!" new-hash tbeg))
  ([fun-id new-hash tbeg]
   (swap! cache/cache update-in json-hash-v1 (fn [_] new-hash))
   (let [json (data/json-data)]
     (doall
      (run! (fn [prms] (apply (partial calc-listings json) prms))
            [[msgl/list-countries com/listing-cases-absolute]
             [msgl/list-per-100k com/listing-cases-per-100k]]))
     (let [stats (est/estimate (v1/pic-data))
           cnt-reports (count (data/dates json))]
     ;; TODO do not call calc-functions when the `form` evaluates to true
       (when (< cnt-reports 10)
         (warnf "Some stuff may not be calculated: %s" "(< cnt-reports 10)"))
       (doall
        (map-fn (fn [ccode]
                  (msgi/detailed-info ccode com/html
                                      (data/create-pred-hm ccode))
                  (plot/plot-country ccode stats cnt-reports))
                ccc/all-country-codes))
       (com/heap-info)
       (Thread/sleep 100)
       (System/gc) ;; also (.gc (Runtime/getRuntime))
       (com/heap-info)
       (doall
        (map-aggregation-fn
         (fn [aggregation-kw]
         ;; TODO delete picture from telegram servers
           (doall
            (map-aggregation-fn
             (fn [case-kw]
               (plot/plot-aggregation new-hash aggregation-kw case-kw stats cnt-reports))
             com/absolute-cases)))
         com/aggregation-cases))))
   ;; discard the intermediary results, i.e. keep only those items in the
   ;; cache which contain the final results.
   (swap! cache/cache
          (fn [_] (select-keys
                   @cache/cache [:json-hash :plot :msg :list :threshold])))
   (debugf "[%s] %s chars cached in %s ms"
           fun-id
           (com/measure @cache/cache)
           ((comp count str) @cache/cache) (- (System/currentTimeMillis) tbeg))))

(defn reset-cache!
  ([] (reset-cache! "reset-cache!"))
  ([fun-id]
   ;; full cache cleanup is not really necessary
   #_(swap! cache/cache (fn [_] {}))
   (let [tbeg (System/currentTimeMillis)]
     ;; enforce evaluation; can't be done by (force (all-rankings json))
     (let [new-hash-v1 (com/hash-fn (data/json-data))]
       (debugf "[%s] (get-in @cache/cache json-hash-v1): %s new hash: %s equal: %s"
               fun-id
               (get-in @cache/cache json-hash-v1)
               new-hash-v1
               (= (get-in @cache/cache json-hash-v1) new-hash-v1))
       (when-not (= (get-in @cache/cache json-hash-v1) new-hash-v1)
         (do-reset-cache! new-hash-v1 tbeg))
       ;; :json introduced by the (data/json-data)
       (swap! cache/cache (fn [_]
                            (update-in @cache/cache [:json] dissoc :v1))))
     (let [new-hash-owid (com/hash-fn (vac/json-data))]
       (debugf "[%s] (get-in @cache/cache json-hash-owid): %s new hash: %s equal: %s"
               fun-id
               (get-in @cache/cache json-hash-owid)
               new-hash-owid
               (= (get-in @cache/cache json-hash-owid) new-hash-owid))
       (when-not (= (get-in @cache/cache json-hash-owid) new-hash-owid)
         (do-reset-cache! new-hash-owid tbeg))
       ;; :json introduced by the (vac/json-data)
       (swap! cache/cache (fn [_]
                            (update-in @cache/cache [:json] dissoc :owid)))))))

(defn start
  "Fetch api service data and only then register the telegram commands."
  ([] (start com/env-type))
  ([env-type] (start "telegram-start" env-type))
  ([fun-id env-type]
   (infof "[%s] Starting version %s in environment %s ..."
          fun-id com/botver env-type)
   (reset-cache!)
   (swap! initialized (fn [_]
                        ;; TODO use morse.handler instead of true?
                        true))
   (let [funs (into [(fn p-endlessly [] (endlessly reset-cache! com/ttl))]
                    (when-not com/use-webhook?
                      [(fn p-long-polling [] (long-polling com/telegram-token))]))]
     (debugf "[%s] Parallel run: %s ..." fun-id funs)
     (pmap (fn [fun] (fun)) funs))
   (infof "[%s] Starting [..] ... done" fun-id)))

(defn stop
  ([] (stop "long-poll-stop"))
  ([fun-id]
   (infof "[%s] Stopping ..." fun-id)
   (run! (fn [obj-q]
           (let [obj (eval obj-q)]
             (if (= obj-q 'corona.telegram/telegram-port)
               (if-let [old-tgram-port (deref obj)]
                 (do
                   (debugf "[%s] Closing old-tgram-port %s ..."
                           fun-id old-tgram-port)
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
   (infof "[%s] Stopping ... done" fun-id)))

(defn restart
  ([] (restart "long-poll-restart"))
  ([fun-id]
   (infof "[%s] Restarting ..." fun-id)
   (when @initialized
     (stop)
     (Thread/sleep 400))
   (start)
   (infof "[%s] Restarting ... done" fun-id)))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.telegram)
