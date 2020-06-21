(ns corona.web
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [compojure.core :refer [ANY defroutes GET POST]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [corona.common :as com]
            [corona.api.beds :as beds]
            [corona.core :as c :refer [chat-id token]]
            [corona.telegram :as telegram]
            [environ.core :refer [env]]
            [ring.adapter.jetty :as jetty]))

(def telegram-hook "telegram")
(def google-hook "google")

(defn home-page []
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body
   (s/join "\n"
           #_[:div "TODO klipse"]
           ["home page"])})

(def project-ver "See also `c/project-ver`" "dev" #_"1.7.2")
(def ws-path (format "ws/%s" project-ver))

(defn web-service [{:keys [type] :as prm}]
  (println "web-service" prm)
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body
   (json/write-str
    (->>
     (condp = type
         :beds (conj #_{"desc" ""}
                     {(name type) (beds/h)})
         :names (conj {"desc" com/sorry-ws})
         :codes (conj {"desc" com/sorry-ws})
         (format "Error. Wrong type %s" type))
     (conj (when (= "dev" project-ver)
             {"warn" "Under construction. Don't use it in PROD env"}))
     (conj {"source" "https://github.com/Bost/corona_cases"})
     ;; swapped order x y -> y x
     (into (sorted-map-by (fn [x y] (compare y x))))))})

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
  (let [hook telegram-hook]
    (POST
     (str "/" hook "/" token) req ;; {{input :input} :params}
     {:status 200
      :headers {"Content-Type" "text/plain"}
      :body
      (json/write-str (conj {:chat_id (->> req :params :message :chat :id)}
                            {:text (str "Hello from " hook " webhook")}))}))

  (let [hook google-hook]
    (POST
     (str "/" hook "/" token) req ;; {{input :input} :params}
     {:status 200
      :headers {"Content-Type" "text/plain"}
      :body
      (json/write-str (conj {:chat_id (->> req :params :message :chat :id)}
                            {:text (str "Hello from " hook " webhook")}))}))
  (GET "/camel" {{input :input} :params}
       {:status 200
        :headers {"Content-Type" "text/plain"}
        :body (str "input was " input)})
  (GET "/" []
       (home-page))
  (GET "/links" []
       (links))
  (GET (format "/%s/beds" ws-path) []
       (web-service {:type :beds}))
  (GET (format "/%s/names" ws-path) []
       (web-service {:type :names}))
  (GET (format "/%s/codes" ws-path) []
       (web-service {:type :codes}))
  (ANY "*" []
       (route/not-found (slurp (io/resource "404.html")))))

(defn webapp [& [port]]
  (println (str "Starting " c/env-type " webapp..."))
  (let [port (Integer. (or port (env :port)
                           (cond c/env-prod? 5000
                                 ;; keep port-nr in sync with README.md
                                 :else 5050)))]
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
