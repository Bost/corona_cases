(ns coronavirus.telegram
  (:require [clojure.core.async :refer [<!!]]
            [clojure.string :as str]
            [environ.core :refer [env]]
            [morse.handlers :as h]
            [morse.polling :as p]
            [morse.polling-patch :as p-patch]
            [clj-time-ext.core :as te]
            [clojure.tools.logging :as log]
            [clojure.core.async :as async]
            [clojure.core.memoize :as memo]
            [coronavirus
             [messages :as m]]
            )
  (:import java.text.SimpleDateFormat)
  (:gen-class))

#_(log/info "Telegram Chatbot:" bot)

(def chats (atom #{}))

(defn register-cmd [cmd cmd-fn]
  (h/command-fn
   cmd
   (fn [{{chat-id :id :as chat} :chat}]
     (when (= cmd "start")
       (swap! chats clojure.set/union #{chat})
       (->> @chats
            prn-str
            (spit "chats.edn")
            ))
     (let [tbeg (te/tnow)]
       (println (str "[" tbeg "           " " " m/bot-ver " /" cmd "]") chat)
       (cmd-fn chat-id)
       (println (str "[" tbeg ":" (te/tnow) " " m/bot-ver " /" cmd "]") chat)))))

;; long polling
(h/defhandler handler
  (register-cmd "start"       (fn [chat-id] (m/refresh-cmd-fn     chat-id)))
  (register-cmd "refresh"     (fn [chat-id] (m/refresh-cmd-fn     chat-id)))
  (register-cmd "interpolate" (fn [chat-id] (m/interpolate-cmd-fn chat-id)))
  (register-cmd "about"       (fn [chat-id] (m/about-cmd-fn       chat-id))))

(defn start-polling
  "Starts long-polling process.
  Handler is supposed to process immediately, as it will
  be called in a blocking manner."
  ([token handler] (start-polling token handler {}))
  ([token handler opts]
   (let [running (async/chan)
         updates (p-patch/create-producer-with-handle
                  running token opts (fn []
                                       (when (= m/bot-type "PROD")
                                         (System/exit 2))))]
     (p/create-consumer updates handler)
     running)))

(defn -main
  [& args]
  (log/info (str "[" (te/tnow) " " m/bot-ver "]") "Starting Telegram Chatbot...")
  (let [blank-prms (filter #(-> % env str/blank?) [:telegram-token])]
    (when (not-empty blank-prms)
      (log/fatal (str "Undef environment var(s): " blank-prms))
      (System/exit 1)))
  (<!! (start-polling m/token handler)))

;; For interactive development:
(def test-obj (atom nil))
(defn start   [] (swap! test-obj (fn [_] (start-polling m/token handler))))
(defn stop    [] (p/stop @test-obj))
(defn restart [] (if @test-obj (stop)) (start))
