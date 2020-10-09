(ns corona.web
  (:require
   [clj-time-ext.core :as te]
   [clj-time.core :as t]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.string :as s]
   [compojure.core :refer [ANY defroutes GET POST]]
   [compojure.handler :refer [site]]
   [compojure.route :as route]
   [corona.common :as co]
   [corona.telegram :as tgram]
   [ring.adapter.jetty :as jetty]
   [taoensso.timbre :as timbre :refer :all]
   )
  (:import
   java.time.ZoneId
   java.util.TimeZone))

(def ^:const telegram-hook "telegram")
(def ^:const google-hook "google")

(defn home-page []
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (s/join "\n" ["home page"])})

(def ^:const project-version-number "See `co/project-version-number`" nil)
(def ^:const ws-path (format "ws/%s" project-version-number))

(defn web-service [{:keys [type] :as prm}]
  (info "web-service" prm)
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body
   (json/write-str
    (->>
     (condp = type
         :names (conj {"desc" co/desc-ws})
         :codes (conj {"desc" co/desc-ws})
         (format "Error. Wrong type %s" type))
     (conj (when-not project-version-number
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
          "-d '{\"chat_id\":" co/chat-id ",\"text\":\"curl test msg\",\"disable_notification\":true}' "
          "\"https://api.telegram.org/bot$TELEGRAM_TOKEN/sendMessage\"")
     ""
     (str "curl --request POST --form chat_id=" co/chat-id " "
          "--form photo=@/tmp/pic.png "
          "\"https://api.telegram.org/bot$TELEGRAM_TOKEN/sendPhoto\"")
     ])})

(defroutes app
  (let [hook telegram-hook]
    (POST
     (str "/" hook "/" co/token) req ;; {{input :input} :params}
     {:status 200
      :headers {"Content-Type" "text/plain"}
      :body
      (json/write-str (conj {:chat_id (->> req :params :message :chat :id)}
                            {:text (str "Hello from " hook " webhook")}))}))

  (let [hook google-hook]
    (POST
     (str "/" hook "/" co/token) req ;; {{input :input} :params}
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
  (let [msg (format "[webapp] starting...")]
    (info msg)
    (let [port (Integer. (or port co/port
                             (cond co/env-prod? 5000
                                   ;; keep port-nr in sync with README.md
                                   :else 5050)))]
      (jetty/run-jetty (site #'app) {:port port :join? false}))
    (info (format "%s done" msg))))

(defn -main [& [port]]
  (let [msg (format "[-main] starting...")]
    (info msg)
    (if (= (str (t/default-time-zone))
           (str (ZoneId/systemDefault))
           (.getID (TimeZone/getDefault)))
      (debug (let [zone-id "Europe/Berlin"]
               (format "TimeZone: %s; current time: %s (%s in %s)"
                       (str (t/default-time-zone))
                       (te/tnow)
                       (te/tnow zone-id)
                       zone-id)))
      (debug (format (str "t/default-time-zone %s; "
                          "ZoneId/systemDefault: %s; "
                          "TimeZone/getDefault: %s\n")
                     (t/default-time-zone)
                     (ZoneId/systemDefault)
                     (.getID (TimeZone/getDefault)))))
    ;; Seems like the webapp must be always started, otherwise I get:
    ;; Error R10 (Boot timeout) -> Web process failed to bind to
    ;; $PORT within 60 seconds of launch
    ;; TODO try to change it in the Procfile. See in the console:
    ;;     remote: -----> Discovering process types
    ;;     remote:        Procfile declares types -> web
    ;; during the deployment process
    (webapp port)
    (tgram/-main)
    (info (format "%s done" msg))))

;; For interactive development:
(def test-obj (atom nil))

(defn start []
  (info "@test-obj" @test-obj)
  (swap! test-obj (fn [_] (webapp))))

(defn stop []
  (info "Stopping" @test-obj)
  (.stop @test-obj))

(defn restart []
  (when @test-obj
    (stop)
    (Thread/sleep 400))
  (start))
