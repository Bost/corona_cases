(ns corona.web
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.tools.logging :as log]
            [compojure.core :refer [ANY defroutes GET POST]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [corona.common :as com]
            [corona.api.beds :as beds]
            [corona.core :as c :refer [bot-type chat-id token]]
            [corona.telegram :as telegram]
            [environ.core :refer [env]]
            [ring.adapter.jetty :as jetty]))

(def telegram-hook "telegram")
(def google-hook "google")

(defn home-page []
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body "TODO home page"})

(def project-ver "See also `c/project-ver`" "dev" #_"1.7.2")
(def ws-path (format "ws/%s" project-ver))

(defn json-ok-response [body]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (->> body
              (conj (when (= "dev" project-ver)
                      {"warn" "Under construction. Don't use it in PROD env"})) 
              (conj {"source" "https://github.com/Bost/corona_cases"})
              (into (sorted-map-by (fn [x y] (compare y x))))
              json/write-str)})

(defn links []
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body
   (s/join
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
  (POST (str "/telegram/" token) req
   (json-ok-response (conj {:chat_id (->> req :params :message :chat :id)}
                           {:text "Hello from telegram webhook"})))
  (POST (str "/google/" token) req
        (json-ok-response (conj {:chat_id (->> req :params :message :chat :id)}
                                {:text "Hello from google webhook"})))
  (GET "/camel" {{input :input} :params}
       {:status 200
        :headers {"Content-Type" "text/plain"}
        :body (str "input was " input)})
  (GET "/" [] (home-page))
  (GET "/links" [] (links))
  (GET (format "/%s/beds" ws-path) [] (json-ok-response {:beds (beds/h)}))
  (GET (format "/%s/names" ws-path) [] (json-ok-response {:type :names}))
  (GET (format "/%s/codes" ws-path) [] (json-ok-response {:type :codes}))
  (ANY "*" [] (route/not-found (slurp (io/resource "404.html")))))

(defn webapp [& [port]]
  (log/info (str "Starting " bot-type " webapp..."))
  (let [port (Integer. (or port (env :port)
                           (if (= c/bot-type "PROD") 5000 5050)))]
    (jetty/run-jetty (site #'app) {:port port :join? false})))

(defn -main [& [port]]
  (pmap (fn [fn-name] (fn-name)) [telegram/-main webapp]))

;; For interactive development:
(def test-obj (atom nil))
(defn start [] (swap! test-obj (fn [_] (webapp))))
(defn stop []  (.stop @test-obj))
(defn restart []
  (when @test-obj (stop))
  (start))
