(ns corona.telegram
  (:gen-class)
  (:require
   [clj-time-ext.core :as te]
   [clojure.core.async :as async]
   [clojure.string :as s]
   [corona.commands :as cm]
   [corona.common :as co]
   [corona.messages :as msg]
   [environ.core :as en]
   [morse.handlers :as h]
   [morse.polling :as p]
   ))

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
    (let [tbeg (te/tnow)
          log-fmt "[%s%s%s %s /%s] %s\n"]
      {:f (fn [prm] (f (-> prm :chat :id)))
       :pre (fn [& args]
              (let [chat (-> args first :chat)]
                (printf log-fmt tbeg " " "          " co/bot-ver name chat)))
       :post (fn [& args]
               (let [[fn-result {:keys [chat]}] args]
                 (printf log-fmt tbeg ":" (te/tnow)    co/bot-ver name chat)
                 fn-result))}))))

(defn handler
  "Receiving incoming updates using long polling (getUpdates method)
  https://en.wikipedia.org/wiki/Push_technology#Long_polling
  An Array of Update-objects is returned."
  []
  (let [cmds (cm/cmds)]
    (println (str "[" (te/tnow) " " co/bot-ver "]")
             "Registering" (count cmds) "chatbot commands...")
    (->> cmds
         (mapv cmd-handler)
         (into [(h/callback-fn msg/callback-handler-fn)])
         (apply h/handlers))))

(defn start-polling
  "Starts long-polling process.
  Handler is supposed to process immediately, as it will be called in a blocking
  manner."
  ([token handler] (start-polling token handler {}))
  ([token handler opts]
   (let [running (async/chan)
         updates (p/create-producer
                  running token opts (fn []
                                       (when co/env-prod? (System/exit 2))))]
     (println (str "[" (te/tnow) " " co/bot-ver "]")
              "Polling on handler" handler "...")
     (p/create-consumer updates handler)
     running)))

(defn -main [& args]
  (let [msg (str "Starting " co/env-type " Telegram Chatbot...")]
    (let [tbeg (te/tnow)
          log-fmt "[%s%s%s %s] %s\n"]
      (printf log-fmt tbeg " " "          " co/bot-ver msg)
      (do
        (let [blank-prms (filter (fn [v] (-> v en/env s/blank?)) [:telegram-token])]
          (when (not-empty blank-prms)
            (println (str "[" (te/tnow) " " co/bot-ver "]")
                     "ERROR" "Undefined environment var(s):" blank-prms)
            (System/exit 1)))
        (async/<!! (start-polling co/token (handler))))
      (printf log-fmt tbeg ":" (te/tnow)    co/bot-ver (str msg " done")))))

;; For interactive development:
(def test-obj (atom nil))
(defn start   [] (swap! test-obj (fn [_] (start-polling co/token (handler)))))
(defn stop    [] (swap! test-obj (fn [_] (p/stop @test-obj))))
(defn restart []
  (when @test-obj
    (stop)
    (Thread/sleep 400))
  (start))
