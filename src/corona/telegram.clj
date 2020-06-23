(ns corona.telegram
  (:gen-class)
  (:require [clj-time-ext.core :as te]
            [clojure.core.async :as async :refer [<!!]]
            [clojure.string :as s]
            [corona.commands :as cmds]
            [corona.core :as c :refer [bot-ver env-type token]]
            [corona.messages :as msg]
            [environ.core :refer [env]]
            [morse.handlers :as h]
            [morse.polling :as p]
            [clj-time.core :as t]
            [morse.polling-patch :as p-patch])
  (:import java.time.ZoneId
           java.util.TimeZone))

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
                (printf log-fmt tbeg " " "          " bot-ver name chat)))
       :post (fn [& args]
               (let [[fn-result {:keys [chat]}] args]
                 (printf log-fmt tbeg ":" (te/tnow)    bot-ver name chat)
                 fn-result))}))))

(def handler
  "Receiving incoming updates using long polling (getUpdates method)
  https://en.wikipedia.org/wiki/Push_technology#Long_polling
  An Array of Update-objects is returned."
  (let [cmds (cmds/cmds)]
    (println "Registering" (count cmds) "chatbot commands")
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
         updates (p-patch/create-producer-with-handle
                  running token opts (fn []
                                       (when c/env-prod? (System/exit 2))))]
     (println "Polling on handler" handler "...")
     (p/create-consumer updates handler)
     running)))

(defn -main [& args]
  (println (str "[" (te/tnow) " " bot-ver "]"))
  (if (= (str (t/default-time-zone))
         (str (ZoneId/systemDefault))
         (.getID (TimeZone/getDefault)))
    (println "TimeZone:" (str (t/default-time-zone)))
    (printf (str "t/default-time-zone %s; "
                 "ZoneId/systemDefault: %s; "
                 "TimeZone/getDefault: %s\n")
            (t/default-time-zone)
            (ZoneId/systemDefault )
            (.getID (TimeZone/getDefault))))
  (println (str "[" (te/tnow) " " bot-ver "]")
            (str "Starting " env-type " Telegram Chatbot..."))
  (let [blank-prms (filter #(-> % env s/blank?) [:telegram-token])]
    (when (not-empty blank-prms)
      (println "ERROR" "Undefined environment var(s):" blank-prms)
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
