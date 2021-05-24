;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.telegram)

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
            [corona.macro :as macro :refer
             [defn-fun-id debugf infof warnf fatalf]]
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
            [clojure.algo.monads :refer [domonad state-m m-result]]
            [corona.lang :as lang])
  (:import [java.time Instant LocalDateTime ZoneId]))

;; (set! *warn-on-reflection* true)

(defonce continue (atom true))

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

(defn-fun-id calc-cache!
  "TODO regarding garbage collection - see object finalization:
https://clojuredocs.org/clojure.core/reify#example-60252402e4b0b1e3652d744c"
  [aggregation-hash json-owid json-v1]
  (let [;; tbeg must be captured before the function composition
        init-state {:tbeg (com/system-time) :acc []}]
    ((comp
      first
      (domonad
       state-m
       [
        raw-dates-v1 ((comp m-result data/raw-dates) json-v1)
        dates        ((comp m-result data/dates) raw-dates-v1)
        last-date    ((comp m-result last (partial sort-by :t)) dates)
        cnt-reports  ((comp m-result count) dates)

        has-estim-vals? (m-result true)
        ;; some-recove?
        ;; ((comp (partial some pos?))
        ;;  (last-7
        ;;   ;; the 'original' value does or does not contain recovered cases
        ;;   (com/ident-fun :r)
        ;;   last-8))
        ;; (and some-recove? (not (msgc/worldwide? ccode)))

        prm-base    (m-result {:header (msgc/header com/html last-date)
                               :footer (msgc/footer com/html has-estim-vals?)
                               :cnt-reports cnt-reports})
        _ (com/add-calc-time "prm-base" prm-base)

        garbage-coll
        (m-result
         (do
           (debugf "(System/gc)")
           (System/gc) ;; also (.gc (Runtime/getRuntime))
           (Thread/sleep 100)))
        _ (com/add-calc-time "garbage-coll" garbage-coll)

        estim ((comp m-result
                     #_(fn [v] (def es v) v)
                     est/estimate
                     #_(fn [v] (def pd v) v)
                     (partial v1/pic-data cnt-reports)
                     (fn [x]
                       (def cnt-reports cnt-reports)
                       (def raw-dates-v1 raw-dates-v1)
                       (def json-v1 json-v1)
                       (def json-owid json-owid)
                       x)
                     data/data-with-pop)
               raw-dates-v1 json-v1 json-owid)
        _ (com/add-calc-time "estim" estim)

        ;; WTF!?! Size of estim and stats-countries is too big. See filtering of
        ;; sorted / unsorted data
        ;; [pic-data] 46.9 MiB
        ;; [estim] 151.7 MiB
        ;; [stats-countries] 151.7 MiB; should be just ~184.0 KiB
        stats-countries
        ((comp
          m-result
          #_(fn [v] (def sc v) v)
          (partial filter (fn [hm] (= (:t hm) last-date)))
          #_(partial sort-by (juxt :ccode :t)))
        estim)
        _ (com/add-calc-time "stats-countries" stats-countries)

        garbage-coll
        (m-result
         (do
           (debugf "(System/gc)")
           (System/gc) ;; also (.gc (Runtime/getRuntime))
           (Thread/sleep 100)))
        _ (com/add-calc-time "garbage-coll" garbage-coll)

        ;; TODO always use estimated vals since there is at
        ;; least 1 country not reporting recovered cases
        lense-fun (m-result com/estim-fun)

        all-calc-listings
        (let [prm (assoc prm-base
                         :ccode (ccr/get-country-code ccc/worldwide)
                         :lense-fun lense-fun)]
          ((comp
            m-result doall
            (partial map (partial apply msgl/calc-listings! stats-countries prm)))
           [[com/listing-cases-absolute 'corona.msg.text.lists/absolute-vals]
            [com/listing-cases-per-1e5 'corona.msg.text.lists/per-1e5]]))
        _ (com/add-calc-time "all-calc-listings" all-calc-listings)

        _ (m-result
           ;; TODO don't exec all-ccode-messages when (< cnt-reports 10)
           (when (< cnt-reports 10)
             (warnf "Some stuff may not be calculated. Too few %s: %s"
                    'cnt-reports cnt-reports)))

        garbage-coll
        (m-result
         (do
           (debugf "(System/gc)")
           (System/gc) ;; also (.gc (Runtime/getRuntime))
           (Thread/sleep 100)))
        _ (com/add-calc-time "garbage-coll" garbage-coll)

        rankings
        ((comp m-result)
         (msgi/all-rankings lense-fun stats-countries))
        _ (com/add-calc-time "rankings" rankings)

        garbage-coll
        (m-result
         (do
           (debugf "(System/gc)")
           (System/gc) ;; also (.gc (Runtime/getRuntime))
           (Thread/sleep 100)))
        _ (com/add-calc-time "garbage-coll" garbage-coll)

        all-ccode-messages
        ;; pmap 16499ms, map 35961ms
        (let [prm (assoc prm-base
                         :dates dates
                         :rankings rankings
                         :estim estim)]
          ((comp
            m-result doall
            (partial map ;; pmap is faster however it eats too much memory
                     (fn [ccode]
                       [(cache/cache! (fn [] (msgi/message ccode prm))
                                      (msgi/message-kw ccode))
                        (debugf "msgi/message ccode %s done" ccode)
                        (cache/cache! (fn [] (plot/message ccode
                                                          estim cnt-reports))
                                      (plot/message-kw ccode))
                        (debugf "plot/message ccode %s done" ccode)])))
           ;; here also "ZZ" worldwide messages
           ccc/all-country-codes))
        _ (com/add-calc-time "all-ccode-messages" all-ccode-messages)

        garbage-coll
        (m-result
         (do
           (debugf "(System/gc)")
           (System/gc) ;; also (.gc (Runtime/getRuntime))
           (Thread/sleep 100)))
        _ (com/add-calc-time "garbage-coll" garbage-coll)

        all-aggregations
        ((comp
          m-result doall
          ;; map is faster than pmap!!!
          ;; 1. map 4100ms, pmap 8737ms
          ;; 2. map 3982ms
          ;; 3. map 3779ms
          (partial map (partial apply plot/aggregation!
                                estim cnt-reports aggregation-hash)))
         (for [a com/aggregation-cases
               b com/absolute-cases]
           [a b]))
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
                    @cache/cache [:plot :msg :list :threshold])))))

        garbage-coll
        (m-result
         (do
           (debugf "(System/gc)")
           (System/gc) ;; also (.gc (Runtime/getRuntime))
           (Thread/sleep 100)))
        _ (com/add-calc-time "garbage-coll" garbage-coll)
        ]
       calc-result))
     init-state)))

(defn-fun-id json-changed! "" [{:keys [json-fn cache-storage]}]
  ;; TODO spec: cache-storage must be vector; json-fns must be function
  (let [json (json-fn)
        hash-kws (conj cache-storage :json-hash)
        old-hash (get-in @cache/cache hash-kws)
        new-hash (com/hash-fn json)
        hashes-changed (not= old-hash new-hash)]
    (debugf "%s; old hash %s; new hash %s; hashes-changed: %s"
            cache-storage old-hash new-hash hashes-changed)
    (when hashes-changed
      (swap! cache/cache update-in hash-kws (fn [_] new-hash))
      #_(do
          (debugf "(System/gc)")
          (System/gc) ;; also (.gc (Runtime/getRuntime))
          (Thread/sleep 100)
          #_(com/heap-info))
      json)))

(defn-fun-id reset-cache! "" []
  ;; full cache cleanup is not really necessary
  #_(swap! cache/cache (fn [_] {}))
  ((comp
    (partial
     apply
     (fn [json-owid json-v1]
       (let [any-json-changed (some boolean [json-owid json-v1])]
         (debugf "any-json-changed %s" any-json-changed)
         (when any-json-changed
           #_(calc-cache! (cache/aggregation-hash)
                        json-v1
                        json-owid)
           (let [;; tbeg must be captured before the function composition
                 init-state {:tbeg (com/system-time) :acc []}]
             ((comp
               first
               (domonad state-m
                        [calc-result
                         (m-result
                          (calc-cache! (cache/aggregation-hash)
                                       json-owid
                                       json-v1))
                         _ (com/add-calc-time "calc-cache!" calc-result)]
                        calc-result))
              init-state))))))
    (partial pmap json-changed!))
   [{:json-fn vac/json-data  :cache-storage [:owid]}
    {:json-fn data/json-data :cache-storage [:v1]}])

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
