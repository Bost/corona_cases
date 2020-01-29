(ns coronavirus.web
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [ring.adapter.jetty :as jetty]
            [environ.core :refer [env]]
            [coronavirus.telegram :as corona]
            [clojure.data.json :as json]
            ))

(def chat-id "112885364")
(def token (env :telegram-token))

(def telegram-hook "telegram")
(def google-hook "google")

(defn splash []
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body
   (clojure.string/join
    "\n"
    [
     "Send out these commands from shell:"
     ""
     "curl \"http://localhost:5000/snake?input=HelloWorld\""
     ""
     (str "curl --request POST \"http://localhost:5000/" telegram-hook "/$TELEGRAM_TOKEN\"")
     (str "curl --request POST \"http://localhost:5000/" google-hook "/$TELEGRAM_TOKEN\"")
     ""
     "curl --form \"url=https://shielded-inlet-72499.herokuapp.com/$TELEGRAM_TOKEN\" \"https://api.telegram.org/bot$TELEGRAM_TOKEN/setWebhook\""
     ""
     "curl --request GET  \"https://api.telegram.org/bot$TELEGRAM_TOKEN/getMe\""
     "curl --request POST \"https://api.telegram.org/bot$TELEGRAM_TOKEN/getMe\""
     ""
     "curl --request GET  \"https://api.telegram.org/bot$TELEGRAM_TOKEN/getUpdates\""
     "curl --request POST \"https://api.telegram.org/bot$TELEGRAM_TOKEN/getUpdates\""
     "curl --request GET  \"https://api.telegram.org/bot$TELEGRAM_TOKEN/getUpdates\" | jq .message.chat.id"
     "curl --request POST \"https://api.telegram.org/bot$TELEGRAM_TOKEN/getUpdates\" | jq .message.chat.id"
     ""
     (str "curl --request POST -H 'Content-Type: application/json' "
          "-d '{\"chat_id\":" chat-id ",\"text\":\"curl test msg\",\"disable_notification\":true}' "
          "\"https://api.telegram.org/bot$TELEGRAM_TOKEN/sendMessage\"")
     ""
     (str "curl --request POST --form chat_id=" chat-id " "
          "--form photo=@/tmp/pic.png "
          "\"https://api.telegram.org/bot$TELEGRAM_TOKEN/sendPhoto\"")
     ])})

(defroutes app
  (let [hook telegram-hook]
    (POST (str "/" hook "/" token) req ;; {{input :input} :params}
          {:status 200
           :headers {"Content-Type" "text/plain"}
           :body
           (do
             #_(println hook "req:" (str req))
             (json/write-str (conj {:chat_id (->> req :params :message :chat :id)}
                                   {:text (str "Hello from " hook " webhook")})))}))

  (let [hook google-hook]
    (POST (str "/" hook "/" token) req ;; {{input :input} :params}
          {:status 200
           :headers {"Content-Type" "text/plain"}
           :body
           (do
             #_(println hook "req:" (str req))
             (json/write-str (conj {:chat_id (->> req :params :message :chat :id)}
                                   {:text (str "Hello from " hook " webhook")})))}))
  (GET "/camel" {{input :input} :params}
       {:status 200
        :headers {"Content-Type" "text/plain"}
        :body (str "input was " input)})
  (GET "/" []
       (splash))
  (ANY "*" []
       (route/not-found (slurp (io/resource "404.html")))))

(defn webapp [& [port]]
  (println "Starting webapp...")
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty (site #'app) {:port port :join? false})))

(defn -main [& [port]]
  (pmap (fn [fn-name] (fn-name)) [corona/-main webapp]))

;; For interactive development:
(def test-obj (atom nil))
(defn start [] (swap! test-obj (fn [_] (webapp))))
(defn stop []  (.stop @test-obj))
(defn restart []
  (if @test-obj (stop))
  (start))
