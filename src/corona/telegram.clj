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
            [corona.countries :as ccr]
            [corona.estimate :as est]
            [corona.macro :as macro :refer [defn-fun-id debugf infof warnf fatalf]]
            [taoensso.timbre :as timbre]
            [corona.msg.text.details :as msgi]
            [corona.msg.text.lists :as msgl]
            [corona.msg.text.messages :as msg]
            [corona.msg.text.common :as msgc]
            [corona.msg.graph.plot :as plot]
            [morse.handlers :as moh]
            [morse.polling :as mop]
            ;; needed for the 'ok?' macro
            corona.models.migration
            [corona.models.dbase :as dbase]
            [clojure.algo.monads
             :refer
             [
              monad domonad with-monad defmonadfn
              m-reduce m-lift
              identity-m ;; sequence-m maybe-m writer-m m-lift
              state-m
              m-result
              fetch-val fetch-state
              set-val set-state
              ]]
            [corona.lang :as lang])
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
                            (let [chat-prm (:chat (first args))]
                              (infof ":pre /%s chat %s" name chat-prm)
                              (cond
                                (= name lang/start)
                                (when-not (dbase/chat-exists? chat-prm)
                                  (dbase/insert-chat! chat-prm)))))
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
        commands (create-cmds cmd/all-handlers)]
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

(defn-fun-id calc-cache!
  "TODO regarding garbage collection - see object finalization:
https://clojuredocs.org/clojure.core/reify#example-60252402e4b0b1e3652d744c"
  [aggegation-hash json]
  (let [;; tbeg must be captured before the function composition
        init-state {:tbeg (com/system-time) :acc []}]
    ((comp
      first
      (domonad
       state-m
       [
        dates       (m-result (data/dates json))
        cnt-reports (m-result (count dates))
        prm-json    (m-result {:json json})

        prm-json-footer-reports
        (m-result (assoc prm-json
                         :footer (msgc/footer com/html)
                         :cnt-reports cnt-reports))

        _ (com/add-calc-time "prm-json-footer-reports" prm-json-footer-reports)

        stats-countries (m-result (data/stats-countries json))
        _ (com/add-calc-time "stats-countries" stats-countries)

        listings (m-result
                  (do
                    #_(com/heap-info)
                    #_(System/gc) ;; also (.gc (Runtime/getRuntime))
                    #_(debugf "1st garbage collection")
                    #_(Thread/sleep 100)
                    #_(com/heap-info)
                    (let [pred-hm ((comp
                                    data/create-pred-hm
                                    ccr/get-country-code)
                                   ccc/worldwide)
                          prm-json-hm prm-json-footer-reports
                          prm
                          ((comp
                            #_(partial assoc prm-json-footer-reports
                                     :stats stats-countries
                                     :header)
                            (partial assoc (conj pred-hm prm-json-hm) :header)
                            (partial msgc/header com/html)
                            :t
                            data/last-report
                            (partial conj prm-json))
                           pred-hm)]
                      (run! (fn [[case-kws listing-fun]]
                              (msgl/calc-listings case-kws stats-countries (assoc prm :fun listing-fun)))
                            [[com/listing-cases-absolute 'corona.msg.text.lists/absolute-vals]
                             [com/listing-cases-per-100k 'corona.msg.text.lists/per-100k]]))
                    #_(com/heap-info)
                    #_(debugf "2nd garbage collection")
                    #_(System/gc) ;; also (.gc (Runtime/getRuntime))
                    #_(Thread/sleep 100)
                    #_(com/heap-info))
                  )
        _ (com/add-calc-time "calc-listings" listings)

        _ (m-result
           ;; TODO don't exec all-ccode-messages when (< cnt-reports 10)
           (when (< cnt-reports 10)
             (warnf "Some stuff may not be calculated: %s"
                    "(< cnt-reports 10)")))

        stats (m-result (est/estimate (v1/pic-data json)))
        _ (com/add-calc-time "estimate" stats)

        all-ccode-messages
        (m-result
         (doall
          (let [prm-json-hm (assoc prm-json-footer-reports
                                   :dates dates)]
            (map-fn
             (fn [ccode]
               (let [pred-hm (data/create-pred-hm ccode)]
                 ((comp
                   (partial msgi/message! ccode)
                   (partial assoc (conj pred-hm prm-json-hm) :header)
                   (partial msgc/header com/html)
                   :t
                   data/last-report
                   (partial conj prm-json))
                  pred-hm))

               (plot/message! ccode stats cnt-reports))

             ccc/all-country-codes))))
        _ (com/add-calc-time "all-ccode-messages" all-ccode-messages)
        ;; i (m-result (do (Thread/sleep 30) (inc 1)))
        ;; _ (com/add-calc-time "sleep30" i)

        cleanups
        (m-result
         (do
           (com/heap-info)
           (debugf "3rd garbage collection")
           (System/gc) ;; also (.gc (Runtime/getRuntime))
           (Thread/sleep 100)
           (com/heap-info)))
        _ (com/add-calc-time "cleanups" cleanups)
        all-aggregations
        (m-result (doall
                   (map-aggregation-fn
                    (fn [aggregation-kw]
                      (doall
                       (map-aggregation-fn
                        (fn [case-kw]
                          (plot/aggregation! aggegation-hash aggregation-kw case-kw stats
                                             cnt-reports))
                        com/absolute-cases)))
                    com/aggregation-cases)))
        _ (com/add-calc-time "all-aggregations" all-aggregations)

        ;; discard the intermediary results, i.e. keep only those items in the
        ;; cache which contain the final results.
        calc-result
        (m-result
         (swap! cache/cache
                (fn [_]
                  (conj
                   {:v1   {:json-hash (get-in @cache/cache [:v1   :json-hash])}}
                   {:owid {:json-hash (get-in @cache/cache [:owid :json-hash])}}
                   (select-keys
                    @cache/cache [:plot :msg :list :threshold])))))]
       calc-result))
     init-state)))

(defn-fun-id json-changed! "" [{:keys [json-fn cache-storage] :as m}]
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

(defn-fun-id clear-cache! "" []
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
   [:v1 :owid]))

(defn-fun-id reset-cache! "" []
  ;; full cache cleanup is not really necessary
  #_(swap! cache/cache (fn [_] {}))
  ((comp
    (fn [any-json-changed]
      (debugf "any-json-changed %s" any-json-changed)
      (when any-json-changed
        (let [;; tbeg must be captured before the function composition
              init-state {:tbeg (com/system-time) :acc []}]
          ((comp
            first
            (domonad state-m
                     [calc-result (m-result
                                   (calc-cache! (cache/aggregation-hash)
                                                (data/json-data)))
                      _ (com/add-calc-time "calc-cache!" calc-result)
                      ;; j (m-result (do (Thread/sleep 30) (inc i)))
                      ;; _ (com/add-calc-time "sleep30" i)
                      ]
                     calc-result))
           init-state))))
    boolean
    (partial some true?)
    (partial pmap json-changed!))
   [{:json-fn data/json-data :cache-storage [:v1]}
    {:json-fn vac/json-data  :cache-storage [:owid]}])

  (clear-cache!)
  (debugf "(keys @cache/cache) %s" (keys @cache/cache))
  (debugf "Responses %s" (select-keys @cache/cache [:v1 :owid]))
  (debugf "Cache size %s" (com/measure @cache/cache)))

(defn- p-endlessly [] (endlessly reset-cache! com/ttl))
(defn- p-long-polling [] (long-polling com/telegram-token))

(defn-fun-id start
  "Fetch api service data and only then register the telegram commands."
  []
  (infof "Starting ...")
  (macro/system-ok?)
  (reset-cache!)
  (swap! initialized (fn [_]
                       ;; TODO use morse.handler instead of true?
                       true))
  (let [funs (into [p-endlessly]
                   (when-not com/use-webhook?
                     [p-long-polling]))]
    (debugf "Parallel run %s ..." funs)
    (pmap (fn [fun] (fun)) funs))
  (infof "Starting ... done"))

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
