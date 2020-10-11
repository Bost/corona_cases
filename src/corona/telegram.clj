(printf "Current-ns [%s] loading %s\n" *ns* 'corona.telegram)

(ns corona.telegram
  (:gen-class)
  (:require
   [clj-time-ext.core :as cte]
   [clojure.core.async :as async]
   [clojure.string :as s]
   [corona.common :as com]
   [corona.commands :as cmd]
   [corona.messages :as msg]
   [morse.handlers :as h]
   [morse.polling :as p]
   [taoensso.timbre :as timbre :refer :all]
   [corona.api.expdev07 :as data]
   ))

;; (debugf "Loading namespace %s" *ns*)

(defn wrap-fn-pre-post-hooks
  "Add :pre and :post hooks / advices around `function`
  Thanks to https://stackoverflow.com/a/10778647/5151982
  TODO doesn't work for multiarity functions. E.g. (defn f ([] (f 1)) ([x] x))
  "
  [{:keys [f pre post]}]
  (fn [& args]
    (apply pre args)
    (let [result (apply f args)]
      (apply post (cons result args)))))

;; logging alternatives - see also method advising (using multimethod):
;; https://github.com/camsaul/methodical
;; https://github.com/technomancy/robert-hooke

(defn cmd-handler [{:keys [name f] :as prm}]
  (h/command-fn
   name
   (wrap-fn-pre-post-hooks
    (let [tbeg (cte/tnow)
          msg (format "[cmd-handler] hook %%s; cmd /%s; chat %%s" name)]
      {:f (fn [prm] (f (-> prm :chat :id)))
       :pre (fn [& args]
              (let [chat (:chat (first args))]
                ;; show who's doing what
                (infof msg :pre chat)))
       :post (fn [& args]
               (let [[fn-result {:keys [chat]}] args]
                 #_(debugf msg :post chat)
                 fn-result))}))))

(defn create-handler
  "Receiving incoming updates using long polling (getUpdates method)
  https://en.wikipedia.org/wiki/Push_technology#Long_polling
  An Array of Update-objects is returned."
  []
  (let [cmds (cmd/cmds)]
    (infof "Registering %s chatbot commands..." (count cmds))
    (apply h/handlers
           (into [(h/callback-fn msg/callback-handler-fn)]
                 (mapv cmd-handler cmds)))))

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
                                          (when com/env-prod? (System/exit 2))))]
       (when com/env-devel?
         (def producer producer))
       (debugf "Created producer %s" producer)
       (infof "Polling on handler %s ..." handler)
       (let [consumer (p/create-consumer producer handler)]
         (when com/env-devel?
           (def consumer consumer))
         (debugf "Created consumer %s" producer)
         channel)))))

(defn telegram [telegram-token]
  (let [msg "[telegram] starting..."]
    (info msg)
    (let [port (start-polling com/telegram-token (create-handler))]
      (let [retval-async<!! (async/<!! port)]
        (debugf "%s done. retval-async<!! %s"
                msg (if-let [v retval-async<!!] v "nil"))
        (fatalf "Further telegram requests may NOT be answered!!!" msg)))))

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

(defn -main
  "Fetch api service data and only then register the telegram commands."
  [& [env-type]]
  (let [starting "[-main] starting"
        msg (format "%s version %s in environment %s..."
                    starting
                    com/commit
                    env-type)]
    (info msg)
    (data/request!)
    (let [funs [(fn p-endlessly [] (endlessly data/request! com/ttl))
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
