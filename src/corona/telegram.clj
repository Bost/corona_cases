(printf "Current-ns [%s] loading %s\n" *ns* 'corona.telegram)

(ns corona.telegram
  (:gen-class)
  (:require
   [clojure.core.async :as async]
   [corona.common :as com]
   [corona.commands :as cmd]
   [clojure.set :as cset]
   [corona.messages :as msg]
   [morse.handlers :as h]
   [morse.polling :as p]
   [corona.plot :as plot]
   [taoensso.timbre :as timbre
    :refer [debugf #_info infof #_warn warnf #_errorf fatalf]]
   [corona.api.expdev07 :as data]
   [corona.api.v1 :as v1]
   [corona.country-codes :as ccc]
   [net.cgrand.xforms :as x]
   ;; [com.stuartsierra.component :as component]
   [clojure.inspector :refer :all]
   [utils.num :as utn]
   ))

;; (set! *warn-on-reflection* true)

(defonce continue (atom true))

;; TODO use com.stuartsierra.compoment for start / stop
;; For interactive development:
(defonce component (atom nil))

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
      (->> (fn [prm] (fun (-> prm :chat :id)))
           (wrap-in-hooks {:pre (fn [& args]
                                  (let [chat (:chat (first args))]
                                    (infof "[%s] :pre /%s chat %s" msg-id name chat)))
                           :post (fn [& args]
                                   (let [[fn-result {:keys [chat]}] args]
                                     (infof "[%s] :post /%s chat %s" msg-id name chat)
                                     fn-result))})
           (h/command-fn name)))
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
                                     #_(debugf "fn-result %s; size %s"
                                               fn-result (count (str fn-result)))
                                     fn-result))})
           (h/callback-fn)))
    funs)))

(defn create-handlers
  "Receiving incoming updates using long polling (getUpdates method)
  https://en.wikipedia.org/wiki/Push_technology#Long_polling
  An Array of Update-objects is returned."
  ([] (create-handlers "create-handlers"))
  ([msg-id]
   (let [callbacks (create-callbacks [msg/worldwide-plots])
         commands (create-commands cmd/cmds)]
     (infof "[%s] registering %s chatbot commands and %s callbacks..."
            msg-id (count commands) (count callbacks))
     (apply h/handlers (into callbacks commands)))))

(defn start-polling
  "
  Receiving incoming updates using long polling (getUpdates method)
  https://en.wikipedia.org/wiki/Push_technology#Long_polling
  An Array of Update-objects is returned.

  TODO switch to webhooks: https://github.com/Otann/morse/issues/44

  Starts long-polling process.
  Handler is supposed to process immediately, as it will be called in a blocking
  manner."
  ([token handler] (start-polling "start-polling" token handler))
  ([msg-id token handler]
   (let [opts {}
         channel (async/chan)]
     (debugf "[%s] Started channel %s" msg-id channel)
     (let [producer (p/create-producer
                     channel token opts (fn api-error-handler []
                                          (when com/env-prod?
                                            (com/system-exit 2))))]
       (infof "[%s] Created producer %s on channel %s" msg-id producer channel)
       #_(debugf "[%s] Creating consumer for produced %s with handler %s ..."
                 msg-id producer handler)
       (let [consumer (p/create-consumer producer handler)]
         (infof "[%s] Created consumer %s for producer %s with handler %s"
                msg-id consumer producer handler)
         channel)))))

(defn telegram
  "TODO see https://github.com/Otann/morse/issues/32"
  ([tgram-token] (telegram "telegram" tgram-token))
  ([msg-id tgram-token]
   (infof "[%s] Starting..." msg-id)
   (if-let [tgram-handlers (create-handlers)]
     (do
       (debugf "[%s] Created tgram-handlers %s" msg-id tgram-handlers)
       (let [port (start-polling tgram-token tgram-handlers)]
         (swap! telegram-port (fn [_] port))
         (let [retval-async<!! (async/<!! port)]
           (warnf "[%s] Taking vals on port %s stopped with retval-async<! %s"
                  msg-id port (if-let [v retval-async<!!] v "nil"))
           (fatalf "[%s] Further requests may NOT be answered!!!" msg-id)
           (when com/env-prod?
             (com/system-exit 2)))))
     #_(do
         (debugf "[%s] Created tgram-handlers %s" msg-id tgram-handlers)
         (let [port (start-polling tgram-token tgram-handlers)]
           (swap! telegram-port (fn [_] port))
           (async/go-loop []
             (debugf "[%s] Taking vals on port %s..." msg-id port)
             (let [fst-retval<! (async/<! port)]
               (warnf "[%s] Taking vals on port %s stopped with fst-retval<! %s"
                      msg-id port (if-let [v fst-retval<!] v "nil"))
               (fatalf "[%s] Further telegram requests may NOT be answered!!!"
                       msg-id)
               (debugf "[%s] @component %s" msg-id @component)
               (when @component
                 #_com/env-prod?
                 #_(com/system-exit 2)
                 (debugf "[%s] Closing port %s..." msg-id port)
                 (async/close! port)
                 (debugf "[%s] Closing port... done. Current port value: %s"
                         msg-id port)
                 (let [dummy-channel-timeout 10000]
                   (debugf "[%s] Creating a dummy-channel (closes in %s msecs)..."
                           msg-id dummy-channel-timeout)
                   (let [dummy-channel-port (async/timeout dummy-channel-timeout)]
                     (debugf "[%s] Taking vals on dummy-channel-port %s..."
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
                             #_(debugf "[%s WTF?] Closing port %s again..."
                                       msg-id port)
                             #_(async/close! port)
                             #_(debugf "[%s WTF?] Port closed again. Current port value: %s"
                                       msg-id port)
                             (debugf "[%s WTF?] New start-polling invocation..."
                                     msg-id)
                             (let [new-port (start-polling tgram-token tgram-handlers)]
                               (swap! telegram-port (fn [_] new-port))
                               (debugf "[%s WTF?] Recuring the go-loop on a new-port %s..." msg-id new-port)
                               (recur)))
                           (do
                             (debugf "[%s WTF?] Recuring the go-loop on the old port %s..." msg-id port)
                             (recur)))))))))))
         #_(let [sleep-time Long/MAX_VALUE]
             (warnf "[%] async/go-loop will sleep forever, i.e. %s msecs"
                    msg-id sleep-time)
             (Thread/sleep sleep-time)))
     (fatalf "[%s] tgram-handlers not created" msg-id))
   (infof "[%s] Starting... done" msg-id)))

(defn endlessly
  "Invoke fun and put the thread to sleep for millis in an endless loop.
  TODO have a look at `repeatedly`"
  ([fun ttl] (endlessly "endlessly" fun ttl))
  ([msg-id fun ttl]
   (infof "[%s] Starting..." msg-id)
   (while @continue
     (Thread/sleep ttl)
     (fun))
   (debugf "[%s] Starting... done" msg-id)
   (warnf "[%s] Displayed data will NOT be updated!" msg-id)))

(defn estimate-recov
  "Warning: lucky coincidence of 1 report per 1 day!"
  [days all-stats]
  (apply map
         ;; reducing two values into one... TODO identify here the transducer
         (fn [confirmed deaths] (- confirmed deaths))
         (map (comp
               (fn [case-kw-stats] (into (drop-last days case-kw-stats) (repeat days 0)))
               (fn [case-kw] (map case-kw all-stats)))
              [:c :d])))

(defn estimate-recov-for-country
  "TODO decrease estimation by the number of deaths - that can be derived from
  the (country-specific) death percentage.

  Seems like different countries have different recovery reporting policies:
  * Germany  - 14 days/reports
  * Slovakia - 23 days/reports
  "
  [[ccode stats-country-unsorted]]
  (let [stats-country (sort-by :t stats-country-unsorted)]
    (mapv (fn [est-rec stats-hm]
            (conj stats-hm {:e est-rec}))
          (estimate-recov
           14
           #_(+ 3 (* 2 7)) stats-country)
          stats-country)))

(defn reset-cache!
  ([] (reset-cache! "reset-cache!"))
  ([msg-id]
   (swap! data/cache (fn [_] {}))
   (let [tbeg (System/currentTimeMillis)]
     ;; enforce evaluation; can't be done by (force (all-rankings))
     (doall
      (data/all-rankings))
     (let [stats (->> (v1/pic-data)
                      (transduce (comp
                                  ;; group together provinces of the given country
                                  (x/by-key :cc (x/reduce conj)) ; (group-by :cc)
                                  (map estimate-recov-for-country))
                                 ;; the xform for the `into []`
                                 into [])
                      (sort-by :cc))
           day (count (data/dates))
           ]
       ;; (def stats stats)
       (let [form '(< (count (corona.api.expdev07/raw-dates)) 10)]
         ;; TODO do not call calc-functions when the
         (if (eval form)
           (warnf "Some stuff may not be calculated: %s" form))
         (do
           (doall
              (map (fn [ccode] (plot/plot-country ccode stats day))
                   (cset/difference
                    (set ccc/all-country-codes)
                    ;; TODO have a look at the web service; there's no json-data
                    (set
                     [
                      ccc/im ccc/mp ccc/ck ccc/gf ccc/sx ccc/tk ccc/tf ccc/kp
                      ccc/nu ccc/nf ccc/ax ccc/cx ccc/mf ccc/sj ccc/tm ccc/gu
                      ccc/vu ccc/pf ccc/bm ccc/vg ccc/pn ccc/pr ccc/qq ccc/um
                      ccc/gg ccc/bq ccc/mo ccc/ky ccc/nr ccc/aw ccc/fm ccc/cc
                      ccc/ws ccc/to ccc/sh ccc/wf ccc/tv ccc/bl ccc/ms ccc/gp

                      ccc/bv ccc/as ccc/fk ccc/gs ccc/mq ccc/fo ccc/aq ccc/mh
                      ccc/vi ccc/gi ccc/nc ccc/yt ccc/tc ccc/re ccc/gl ccc/ki
                      ccc/hk ccc/io ccc/cw ccc/je ccc/hm ccc/pm ccc/ai ccc/pw]))))
           (doall
            (map (fn [ccode] (msg/detailed-info ccode))
                 ccc/all-country-codes))
           (doall
              (map (fn [plot-fn]
                     (run! (fn [case-kw]
                             #_(debugf "Calculating %s %s" plot-fn case-kw)
                             (plot-fn case-kw stats day))
                           com/absolute-cases))
                   [plot/plot-sum-by-case plot/plot-absolute-by-case])))))
     (debugf "[%s] %s chars cached in %s ms"
             msg-id
             (count (str @data/cache)) (- (System/currentTimeMillis) tbeg)))))

(defn -main
  "Fetch api service data and only then register the telegram commands."
  ([] (-main "component--main"))
  ([msg-id] (-main msg-id nil))
  ([msg-id & [env-type]]
   (infof "[%s] Starting version %s in environment %s..."
          msg-id com/commit (or env-type com/undef))
   (reset-cache!)
   ;; TODO I guess the telegram-server, i.e. morse.handler
   ;; should be set to the component atom
   (swap! component (fn [_] true))
   (let [funs [
               (fn p-endlessly [] (endlessly reset-cache! com/ttl))
               (fn p-telegram [] (telegram com/telegram-token))]]
     (debugf "[-main] Execute in parallel: %s..." funs)
     (pmap (fn [fun] (fun)) funs))
   (infof "[%s] Starting [..] ... done" msg-id)))

(defn stop
  ([] (stop "component-stop"))
  ([msg-id]
   (infof "[%s] Stopping..." msg-id)
   (run! (fn [obj-q]
           (let [obj (eval obj-q)]
             (if (= obj-q 'corona.telegram/telegram-port)
               (if-let [old-tgram-port (deref obj)]
                 (do
                   (debugf "[%s] Closing old-tgram-port %s..."
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
         ['corona.telegram/component
          'corona.api.expdev07/cache
          'corona.telegram/continue
          'corona.telegram/telegram-port])
   (infof "[%s] Stopping... done" msg-id)))

(defn restart
  ([] (restart "component-restart"))
  ([msg-id]
   (infof "[%s] Restarting..." msg-id)
   (when @component
     (stop)
     (Thread/sleep 400))
   (-main com/env-type)
   (infof "[%s] Restarting... done" msg-id)))
