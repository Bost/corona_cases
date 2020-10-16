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
   [taoensso.timbre :as timbre :refer [debugf info infof warn errorf fatalf]]
   [corona.api.expdev07 :as data]
   [corona.api.v1 :as v1]
   [corona.country-codes :as ccc :refer :all]
   ))

(set! *warn-on-reflection* true)

(defn wrap-fn-pre-post-hooks
  "Add :pre and :post hooks / advices around `function`
  Thanks to https://stackoverflow.com/a/10778647/5151982
  TODO doesn't work for multiarity functions. E.g.
  (defn f ([] (f 1)) ([x] x))"
  [{:keys [f pre post]}]
  (fn [& args]
    (apply pre args)
    (let [result (apply f args)]
      (apply post (cons result args)))))

;; logging alternatives - see also method advising (using multimethod):
;; https://github.com/camsaul/methodical
;; https://github.com/technomancy/robert-hooke

(defn cmd-handler
  "Use :pre and :post hooks to see in the log if and how request are made and
  responded"
  [{:keys [name f]}]
  (h/command-fn
   name
   (wrap-fn-pre-post-hooks
    (let [msg (format "[cmd-handler] cmd /%s; hook %%s; chat %%s" name)]
      {:f (fn [prm] (f (-> prm :chat :id)))
       :pre (fn [& args]
              (let [chat (:chat (first args))]
                (infof msg :pre chat)))
       :post (fn [& args]
               (let [[fn-result {:keys [chat]}] args]
                 (infof msg :post chat)
                 fn-result))}))))

(defn create-handler
  "Receiving incoming updates using long polling (getUpdates method)
  https://en.wikipedia.org/wiki/Push_technology#Long_polling
  An Array of Update-objects is returned."
  []
  (infof "Registering %s chatbot commands..." (count cmd/cmds))
  (apply h/handlers
         (into [(h/callback-fn msg/callback-handler-fn)]
               (mapv cmd-handler cmd/cmds))))

(defn start-polling
  "Starts long-polling process.
  Handler is supposed to process immediately, as it will be called in a blocking
  manner."
  ([token handler] (start-polling token handler {}))
  ([token handler opts]
   (let [channel (async/chan)]
     (debugf "(async/chan) returned %s" channel)
     (let [producer (p/create-producer
                     channel token opts (fn []
                                          (when com/env-prod?
                                            (com/system-exit 2))))]
       (debugf "Created producer %s" producer)
       (infof "Polling on handler %s ..." handler)
       (let [consumer (p/create-consumer producer handler)]
         (debugf "Created consumer %s" consumer)
         channel)))))

(defn telegram [telegram-token]
  (let [msg "[telegram] starting..."]
    (info msg)
    (if-let [telegram-handler (create-handler)]
      (do
        (debugf "Created telegram-handler %s" telegram-handler)
        (let [port (start-polling telegram-token telegram-handler)]
          (let [retval-async<!! (async/<!! port)]
            (debugf "%s done. retval-async<!! %s"
                    msg (if-let [v retval-async<!!] v "nil"))
            (fatalf "Further telegram requests may NOT be answered!!!" msg)
            (when com/env-prod?
              (com/system-exit 2)))))
      (errorf "telegram-handler not created"))))

(defonce continue (atom true))

(defn endlessly
  "Invoke fun and put the thread to sleep for millis in an endless loop.
  TODO have a look at `repeatedly`"
  [fun ttl]
  (let [msg "[endlessly] starting..."]
    (info msg)
    (while @continue
      (Thread/sleep ttl)
      (fun))
    (debugf "%s done" msg)
    (warn "Displayed data will NOT be updated!")))

;; TODO use com.stuartsierra.compoment for start / stop
;; For interactive development:
(defonce component (atom nil))

(defn reset-cache! []
  (swap! data/cache (fn [_] {}))
  (let [tbeg (System/currentTimeMillis)]
    ;; enforce evaluation; can't be done by (force (all-rankings))
    (doall
     (data/all-rankings))
    (let [stats (v1/pic-data)
          day (count (data/dates))]
      (let [ccodes
            (cset/difference
             (set ccc/all-country-codes)
             (set [im mp ck gf sx tk tf kp nu nf ax cx mf sj tm gu vu pf bm vg
                   pn pr qq um gg bq mo ky nr aw fm cc ws to sh wf tv bl ms gp
                   bv as fk gs mq fo aq mh vi gi nc yt tc re gl ki hk io cw je
                   hm pm ai pw]))]
        (doall
         (map (fn [ccode] (plot/plot-country ccode stats day)) ccodes))
        (doall
         (map (fn [ccode] (msg/detailed-info ccode
                                            ;; parse_mode
                                            "HTML"
                                            ;; :pred
                                            (msg/create-pred-hm ccode)
                                            ))
              ccodes)))
      (doall
       (run! (fn [plot-fn]
              (run! (fn [case-kw]
                      #_(debugf "Calculating %s %s" plot-fn case-kw)
                      (plot-fn case-kw stats day))
                    com/absolute-cases))
            [plot/plot-sum-by-case plot/plot-absolute-by-case])))
    (debugf "%s chars cached in %s ms"
            (count (str @data/cache)) (- (System/currentTimeMillis) tbeg))))

(defn -main
  "Fetch api service data and only then register the telegram commands."
  [& [env-type]]
  (let [starting "[-main] starting"
        msg (format "%s version %s in environment %s..."
                    starting
                    com/commit
                    env-type)]
    (info msg)
    (reset-cache!)
    (let [funs [(fn p-endlessly [] (endlessly reset-cache! com/ttl))
                (fn p-telegram []
                  (let [telegram-server
                        (telegram com/telegram-token)]
                    ;; TODO I guess the telegram-server, i.e. morse.handler
                    ;; should be set to the component atom
                    (swap! component (fn [_] []))
                    telegram-server))]]
      (debugf "[-main] execute in parallel: %s..." funs)
      (pmap (fn [fun] (fun)) funs))
    (infof "%s... done" starting)))

(defn stop []
  (info "[telegram] stopping...")
  (let [objs ['component 'data/cache 'continue]]
    (run! (fn [obj-q]
            (let [obj (eval obj-q)]
              (swap! obj (fn [_] nil))
              (debugf "%s new value: %s"
                      obj-q (if-let [v (deref obj)] v "nil"))))
          objs)))

(defn restart []
  (info "Restarting...")
  (when @component
    (stop)
    (Thread/sleep 400))
  (-main com/env-type))
