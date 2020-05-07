(ns corona.telegram
  (:gen-class)
  (:require [clj-time-ext.core :as te]
            [clojure.core.async :as async :refer [<!!]]
            [clojure.string :as s]
            [clojure.tools.logging :as log]
            [corona.commands :as cmds]
            [corona.core :as c :refer [bot-ver token env-type]]
            [environ.core :refer [env]]
            [morse.handlers :as h]
            [morse.polling :as p]
            [morse.polling-patch :as p-patch]
            [corona.messages :as msg]))

(def chats (atom #{}))

;; long polling
(def handler
  (->> (cmds/cmds)
       ;; TODO use monad for logging
       (mapv (fn [{:keys [name f] :as prm}]
               (h/command-fn
                name
                (fn [{{chat-id :id :as chat} :chat}]
                  #_(when (= cmd "start")
                      (swap! chats clojure.set/union #{chat})
                      (->> @chats
                           prn-str
                           (spit "chats.edn")))
                  (let [tbeg (te/tnow)
                        log-fmt "[%s%s%s %s /%s] %s\n"]
                    (printf log-fmt tbeg " " "          " bot-ver name chat)
                    (f chat-id)
                    (printf log-fmt tbeg ":" (te/tnow)    bot-ver name chat))))))
       (into [(h/callback-fn msg/callback-handler-fn)])
       (apply h/handlers)))

(defn start-polling
  "Starts long-polling process.
  Handler is supposed to process immediately, as it will
  be called in a blocking manner."
  ([token handler] (start-polling token handler {}))
  ([token handler opts]
   (let [running (async/chan)
         updates (p-patch/create-producer-with-handle
                  running token opts (fn []
                                       (when c/env-prod? (System/exit 2))))]
     (println "Polling on handler" handler "...")
     (p/create-consumer updates handler)
     running)))

(defn -main [& args]
  (log/info (str "[" (te/tnow) " " bot-ver "]")
            (str "Starting " env-type " Telegram Chatbot..."))
  (let [blank-prms (filter #(-> % env s/blank?) [:telegram-token])]
    (when (not-empty blank-prms)
      (log/fatal (str "Undef environment var(s): " blank-prms))
      (System/exit 1)))
  (<!! (start-polling token handler)))

;; For interactive development:
(def test-obj (atom nil))
(defn start   [] (swap! test-obj (fn [_] (start-polling token handler))))
(defn stop    [] (swap! test-obj (fn [_] (p/stop @test-obj))))
(defn restart []
  (when @test-obj
    (stop)
    (Thread/sleep 400))
  (start))
