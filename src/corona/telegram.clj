(ns corona.telegram
  (:gen-class)
  (:require [clj-time-ext.core :as te]
            [clojure.core.async :as async :refer [<!!]]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [corona
             [core :refer [bot-ver bot-type token]]
             [messages :as m]]
            [environ.core :refer [env]]
            [morse.handlers :as h]
            [morse.polling :as p]
            [morse.polling-patch :as p-patch]))

#_(log/info "Telegram Chatbot:" bot)

(defmacro dbg [body]
  `(let [x# ~body]
     (println "dbg:" '~body "=" x#)
     x#))

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
       (println (str "[" tbeg "           " " " bot-ver " /" cmd "]") chat)
       (cmd-fn chat-id)
       (println (str "[" tbeg ":" (te/tnow) " " bot-ver " /" cmd "]") chat)))))

(def cmd-names (map :name m/cmds))

(defn create-sexp [{:keys [name f]}]
  (list 'register-cmd name (fn [chat-id] (f cmd-names chat-id))))

(declare handler)
;; long polling
;; (as-> ...) creates
;; (h/defhandler handler
;;   (register-cmd "start"   (fn [chat-id] ...))
;;   (register-cmd "refresh" (fn [chat-id] ...))
;;   ...)
(defn create-handler []
  (let [r (as-> m/cmds $
          (map create-sexp $)
          (conj $ 'handler 'h/defhandler)
          (eval $))]
    (println "create-handler" (str handler))
    r))

(defn start-polling
  "Starts long-polling process.
  Handler is supposed to process immediately, as it will
  be called in a blocking manner."
  ([token handler] (start-polling token handler {}))
  ([token handler opts]
   (let [running (async/chan)
         updates (p-patch/create-producer-with-handle
                  running token opts (fn []
                                       (when (= bot-type "PROD")
                                         (System/exit 2))))]
     (p/create-consumer updates handler)
     running)))

(defn -main
  [& args]
  (log/info (str "[" (te/tnow) " " bot-ver "]") "Starting Telegram Chatbot...")
  (let [blank-prms (filter #(-> % env str/blank?) [:telegram-token])]
    (when (not-empty blank-prms)
      (log/fatal (str "Undef environment var(s): " blank-prms))
      (System/exit 1)))
  (<!! (start-polling token (create-handler))))

;; For interactive development:
(def test-obj (atom nil))
(defn start   [] (swap! test-obj (fn [_]
                                   (start-polling token (create-handler)))))
(defn stop    [] (p/stop @test-obj))
(defn restart [] (if @test-obj (stop)) (start))

(defn bot-father-edit-cmds []
  (map (fn [{:keys [name desc]}] (println name "-" desc))
       m/cmds))
