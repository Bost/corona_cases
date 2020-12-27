(printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.telegram)

;; TODO replace `->>` with `comp` https://github.com/practicalli/clojure-content/issues/160
;; TODO replace loop-recur with functional implementation and parallelize it https://youtu.be/GvJm-eJ5o18?t=1617
;; TODO https://stuartsierra.com/2016/01/09/how-to-name-clojure-functions

(ns corona.telegram
  (:gen-class)
  (:require
   [clojure.core.async :as async]
   [corona.common :as com]
   [corona.countries :as ccr]
   [corona.commands :as cmd]
   [corona.msg.messages :as msg]
   [corona.msg.info :as msgi]
   [corona.msg.lists :as msgl]
   [morse.handlers :as moh]
   [morse.polling :as mop]
   [corona.plot :as plot]
   [taoensso.timbre :as timbre
    :refer [debugf #_info infof #_warn warnf #_errorf fatalf]]
   [corona.api.expdev07 :as data]
   [corona.api.v1 :as v1]
   [corona.country-codes :as ccc]
   [net.cgrand.xforms :as x]
   [utils.core :as u :refer [in?] :exclude [id]]
   ;; [com.stuartsierra.component :as component]
   ;; [clojure.inspector :refer :all]
   ))

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
  ([msg-id {:keys [pre post]} fun]
   (fn [& args]
     (apply pre args)
     (let [result (apply fun args)]
       (apply post (cons result args))))))

;; logging alternatives - see also method advising (using multimethod):
;; https://github.com/camsaul/methodical
;; https://github.com/technomancy/robert-hooke

(defn create-commands
  ([cmds] (create-commands "create-commands" cmds))
  ([msg-id cmds]
   (map
    (fn [{:keys [name fun]}]
      (->>
       #_(fn [prm] (fun (-> prm :chat :id)))
       (fn [{{chat-id :id} :chat}] (fun chat-id))
       (wrap-in-hooks {:pre (fn [& args]
                              (let [chat (:chat (first args))]
                                (infof "[%s] :pre /%s chat %s" msg-id name chat)))
                       :post (fn [& args]
                               (let [[fn-result {:keys [chat]}] args]
                                 (infof "[%s] :post /%s chat %s" msg-id name chat)
                                 fn-result))})
       (moh/command-fn name)))
    cmds)))

(defn create-callbacks
  ([funs] (create-callbacks "create-callbacks" funs))
  ([msg-id funs]
   (map
    (fn [fun]
      (->> fun
           (wrap-in-hooks {:pre (fn [& args]
                                  (let [{:keys [data message]} (first args)]
                                    (infof "[%s] :pre data %s chat %s"
                                           msg-id data (:chat message))))
                           :post (fn [& args]
                                   (let [[fn-result {:keys [data message]}] args]
                                     (infof "[%s] :post data %s chat %s"
                                            msg-id data (:chat message))
                                     fn-result))})
           (moh/callback-fn)))
    funs)))

(defn create-handlers
  "Receiving incoming updates using long polling (getUpdates method)
  https://en.wikipedia.org/wiki/Push_technology#Long_polling
  An Array of Update-objects is returned."
  ([] (create-handlers "create-handlers"))
  ([msg-id]
   (let [callbacks (create-callbacks [msg/worldwide-plots])
         commands (create-commands cmd/cmds)]
     (infof "[%s] registering %s chatbot commands and %s callbacks ..."
            msg-id (count commands) (count callbacks))
     (into callbacks commands))))

(defn start-polling
  "Receiving incoming updates using long polling (getUpdates method)
  https://en.wikipedia.org/wiki/Push_technology#Long_polling
  An Array of Update-objects is returned.

  Starts long-polling process.
  Handler is supposed to process immediately, as it will be called in a blocking
  manner."
  ([token handler] (start-polling "start-polling" token handler))
  ([msg-id token handler]
   (let [opts {}
         channel (async/chan)]
     (debugf "[%s] Started channel %s" msg-id channel)
     (let [producer (mop/create-producer
                     channel token opts (fn api-error-handler []
                                          (when com/env-prod?
                                            (com/system-exit 2))))]
       (infof "[%s] Created producer %s on channel %s" msg-id producer channel)
       #_(debugf "[%s] Creating consumer for produced %s with handler %s ..."
                 msg-id producer handler)
       (let [consumer (mop/create-consumer producer handler)]
         (infof "[%s] Created consumer %s for producer %s with handler %s"
                msg-id consumer producer handler)
         channel)))))

(defn long-polling
  "TODO see https://github.com/Otann/morse/issues/32"
  ([tgram-token] (long-polling "telegram" tgram-token))
  ([msg-id tgram-token]
   (infof "[%s] Starting ..." msg-id)
   (if-let [polling-handlers (apply moh/handlers (create-handlers))]
     (do
       (debugf "[%s] Created polling-handlers %s" msg-id polling-handlers)
       (let [port (start-polling tgram-token polling-handlers)]
         (swap! telegram-port (fn [_] port))
         (let [retval-async<!! (async/<!! port)]
           (warnf "[%s] Taking vals on port %s stopped with retval-async<! %s"
                  msg-id port (if-let [v retval-async<!!] v "nil"))
           (fatalf "[%s] Further requests may NOT be answered!!!" msg-id)
           (when com/env-prod?
             (com/system-exit 2)))))
     #_(do
         (debugf "[%s] Created polling-handlers %s" msg-id polling-handlers)
         (let [port (start-polling tgram-token polling-handlers)]
           (swap! telegram-port (fn [_] port))
           (async/go-loop []
             (debugf "[%s] Taking vals on port %s ..." msg-id port)
             (let [fst-retval<! (async/<! port)]
               (warnf "[%s] Taking vals on port %s stopped with fst-retval<! %s"
                      msg-id port (if-let [v fst-retval<!] v "nil"))
               (fatalf "[%s] Further telegram requests may NOT be answered!!!"
                       msg-id)
               (debugf "[%s] @initialized %s" msg-id @initialized)
               (when @initialized
                 #_com/env-prod?
                 #_(com/system-exit 2)
                 (debugf "[%s] Closing port %s ..." msg-id port)
                 (async/close! port)
                 (debugf "[%s] Closing port ... done. Current port value: %s"
                         msg-id port)
                 (let [dummy-channel-timeout 10000]
                   (debugf "[%s] Creating a dummy-channel (closes in %s msecs) ..."
                           msg-id dummy-channel-timeout)
                   (let [dummy-channel-port (async/timeout dummy-channel-timeout)]
                     (debugf "[%s] Taking vals on dummy-channel-port %s ..."
                             msg-id dummy-channel-port)
                     (let [retval-dummy-channel<! (async/<! dummy-channel-port)]
                       (debugf (str
                                "[%s] As expected (check log tstp), "
                                "taking vals on dummy-channel-port %s stopped with retval-dummy-channel<! %s")
                               msg-id dummy-channel-port retval-dummy-channel<!)
                       (let [snd-retval<! (async/<! port)]
                         #_(debugf "[%s WTF?] Taking vals on port %s stopped with snd-retval<! %s"
                                   msg-id port (if-let [v snd-retval<!] v "nil"))
                         (if (nil? snd-retval<!)
                           (do
                             #_(debugf "[%s WTF?] Closing port %s again ..."
                                       msg-id port)
                             #_(async/close! port)
                             #_(debugf "[%s WTF?] Port closed again. Current port value: %s"
                                       msg-id port)
                             (debugf "[%s WTF?] New start-polling invocation ..."
                                     msg-id)
                             (let [new-port (start-polling tgram-token polling-handlers)]
                               (swap! telegram-port (fn [_] new-port))
                               (debugf "[%s WTF?] Recuring the go-loop on a new-port %s ..." msg-id new-port)
                               (recur)))
                           (do
                             (debugf "[%s WTF?] Recuring the go-loop on the old port %s ..." msg-id port)
                             (recur)))))))))))
         #_(let [sleep-time Long/MAX_VALUE]
             (warnf "[%] async/go-loop will sleep forever, i.e. %s msecs"
                    msg-id sleep-time)
             (Thread/sleep sleep-time)))
     (fatalf "[%s] polling-handlers not created" msg-id))
   (infof "[%s] Starting ... done" msg-id)))

(defn endlessly
  "Invoke fun and put the thread to sleep for millis in an endless loop."
  ([fun ttl] (endlessly "endlessly" fun ttl))
  ([msg-id fun ttl]
   (infof "[%s] Starting ..." msg-id)
   (while @continue
     (Thread/sleep ttl)
     (fun))
   (debugf "[%s] Starting ... done" msg-id)
   (warnf "[%s] Displayed data will NOT be updated!" msg-id)))

(defn estimate-recov
  [reports all-stats]
  (apply map
         ;; reducing two values into one... TODO identify here the transducer
         (fn [confirmed deaths] (- confirmed deaths))
         (map (comp
               (fn [case-kw-stats] (into (drop-last reports case-kw-stats)
                                        (repeat reports 0)))
               (fn [case-kw] (map case-kw all-stats)))
              [:c :d])))

(defn estimate-recov-for-country
  "Seems like different countries have different recovery reporting policies:
  * Germany  - 14 days/reports
  * Slovakia - 23 days/reports

  Warning: lucky coincidence of 1 report per 1 day!"
  [[ccode stats-country-unsorted]]
  (let [stats-country (sort-by :t stats-country-unsorted)]
    (mapv (fn [est-rec stats-hm]
            (conj stats-hm {:e est-rec}))
          (estimate-recov
           14
           #_(+ 3 (* 2 7)) stats-country)
          stats-country)))

(defn calc-listings [fun case-kws]
  (run! (fn [case-kw]
         (let [coll (sort-by case-kw < (data/stats-countries))
               ;; Split the long list of all countries into smaller sub-parts
               sub-msgs (partition-all (/ (count coll)
                                          #_com/cnt-messages-in-listing
                                          7) coll)
               options {:parse_mode com/html}
               prm (conj options {:cnt-msgs (count sub-msgs)
                                  :cnt-reports (count (data/dates))
                                  :pred-hm (msg/create-pred-hm (ccr/get-country-code ccc/worldwide))})]
           (doall
            (map-indexed (fn [idx sub-msg]
                           (fun case-kw (inc idx) (conj prm {:data sub-msg})))
                         sub-msgs))))
       case-kws))

(defn reset-cache!
  ([] (reset-cache! "reset-cache!"))
  ([msg-id]
   ;; full cache cleanup is not really necessary
   #_(swap! data/cache (fn [_] {}))
   (let [tbeg (System/currentTimeMillis)]
     ;; enforce evaluation; can't be done by (force (all-rankings))
     #_(doall
        #_(data/stats-countries)
        #_(data/dates)
        #_(data/json-data))
     (doall
      (run! (fn [prms] (apply calc-listings prms))
            [[msgl/list-countries com/listing-cases-absolute]
             [msgl/list-per-100k com/listing-cases-per-100k]]))
     (let [stats (->> (v1/pic-data)
                      (transduce (comp
                                  ;; group together provinces of the given country
                                  (x/by-key :ccode (x/reduce conj)) ; (group-by :ccode)
                                  (map estimate-recov-for-country))
                                 ;; the xform for the `into []`
                                 into [])
                      (sort-by :ccode))
           cnt-reports (count (data/dates))
           form '(< (count (corona.api.expdev07/raw-dates)) 10)]
       ;; TODO do not call calc-functions when the `form` evaluates to true
       (if (eval form)
         (warnf "Some stuff may not be calculated: %s" form))
       (doall
        (pmap (fn [ccode]
                (msgi/detailed-info ccode com/html
                                    (data/create-pred-hm ccode))
                (plot/plot-country ccode stats cnt-reports)
                #_(pmap (fn [fun] (fun))
                      [(fn [] (msgi/detailed-info ccode com/html
                                                 (data/create-pred-hm ccode)))
                       (fn [] (plot/plot-country ccode stats cnt-reports))]))
              ccc/all-country-codes))
       (doall
        (pmap (fn [plot-fn]
                (doall
                 (pmap (fn [case-kw]
                         #_(debugf "Calculating %s %s" plot-fn case-kw)
                         (plot-fn case-kw stats cnt-reports))
                       com/absolute-cases)))
              [plot/plot-sum plot/plot-absolute])))
     ;; discard the intermediary results, i.e. keep only those items in the
     ;; cache which contain the final results.
     (swap! data/cache (fn [_]
                         (select-keys @data/cache [:plot :msg :list :threshold])))

     (debugf "[%s] %s chars cached in %s ms"
             msg-id
             (count (str @data/cache)) (- (System/currentTimeMillis) tbeg)))))

(defn start
  "Fetch api service data and only then register the telegram commands."
  ([] (start com/env-type))
  ([env-type] (start "telegram-start" env-type))
  ([msg-id env-type]
   (infof "[%s] Starting version %s in environment %s ..."
          msg-id com/botver env-type)
   (reset-cache!)
   (swap! initialized (fn [_]
                        ;; TODO use morse.handler instead of true?
                        true))
   (let [funs (into [(fn p-endlessly [] (endlessly reset-cache! com/ttl))]
                    (when-not com/use-webhook?
                      [(fn p-long-polling [] (long-polling com/telegram-token))]))]
     (debugf "[%s] Parallel run: %s ..." msg-id funs)
     (pmap (fn [fun] (fun)) funs))
   (infof "[%s] Starting [..] ... done" msg-id)))

(defn stop
  ([] (stop "long-poll-stop"))
  ([msg-id]
   (infof "[%s] Stopping ..." msg-id)
   (run! (fn [obj-q]
           (let [obj (eval obj-q)]
             (if (= obj-q 'corona.telegram/telegram-port)
               (if-let [old-tgram-port (deref obj)]
                 (do
                   (debugf "[%s] Closing old-tgram-port %s ..."
                           msg-id old-tgram-port)
                   (async/close! old-tgram-port))
                 (debugf "[%s] No old-tgram-port defined" msg-id))
               (do
                 #_(debugf "[%s] obj-q %s is not the %s"
                           msg-id obj-q 'corona.telegram/telegram-port)))
             (swap! obj (fn [_] nil))
             (debugf "[%s] %s new value: %s"
                     msg-id
                     obj-q (if-let [v (deref obj)] v "nil"))))
         ['corona.telegram/initialized
          'corona.api.expdev07/cache
          'corona.telegram/continue
          'corona.telegram/telegram-port])
   (infof "[%s] Stopping ... done" msg-id)))

(defn restart
  ([] (restart "long-poll-restart"))
  ([msg-id]
   (infof "[%s] Restarting ..." msg-id)
   (when @initialized
     (stop)
     (Thread/sleep 400))
   (start)
   (infof "[%s] Restarting ... done" msg-id)))

(printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.telegram)
