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
   [corona.api.beds :as beds]
   [corona.common :as co]
   [corona.telegram :as telegram]
   [ring.adapter.jetty :as jetty]
   )
  (:import
   java.time.ZoneId
   java.util.TimeZone))

(def telegram-hook "telegram")
(def google-hook "google")

(defn home-page []
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (s/join "\n" ["home page"])})

(def project-ver "See also `co/project-ver`" "dev" #_"1.7.2")
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
         :names (conj {"desc" co/sorry-ws})
         :codes (conj {"desc" co/sorry-ws})
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
  (let [msg (str "Starting " co/env-type " webapp...")]
    (let [tbeg (te/tnow)
          log-fmt "[%s%s%s %s] %s\n"]
      (printf log-fmt tbeg " " "          " co/bot-ver msg)
      (let [port (Integer. (or port co/port
                               (cond co/env-prod? 5000
                                     ;; keep port-nr in sync with README.md
                                     :else 5050)))]
        (jetty/run-jetty (site #'app) {:port port :join? false}))
      (printf log-fmt tbeg ":" (te/tnow)    co/bot-ver (str msg " done")))))

(defn -main [& [port]]
  (let [msg (str "Starting " co/env-type " -main...")]
    (let [tbeg (te/tnow)
          log-fmt "[%s%s%s %s] %s\n"]
      (printf log-fmt tbeg " " "          " co/bot-ver msg)
      (do
        (if (= (str (t/default-time-zone))
               (str (ZoneId/systemDefault))
               (.getID (TimeZone/getDefault)))
          (println (str "[" (te/tnow) " " co/bot-ver "]")
                   "TimeZone:" (str (t/default-time-zone)))
          (println (str "[" (te/tnow) " " co/bot-ver "]")
                   (format (str "t/default-time-zone %s; "
                                "ZoneId/systemDefault: %s; "
                                "TimeZone/getDefault: %s\n")
                           (t/default-time-zone)
                           (ZoneId/systemDefault )
                           (.getID (TimeZone/getDefault)))))
        (pmap (fn [fn-name] (fn-name))
              [telegram/-main
               ;; Seems like the webapp must be always started, otherwise I get:
               ;; Error R10 (Boot timeout) -> Web process failed to bind to
               ;; $PORT within 60 seconds of launch
               webapp]))
      (printf log-fmt tbeg ":" (te/tnow)    co/bot-ver (str msg " done")))))

;; For interactive development:
(def test-obj (atom nil))
(defn start []
  (println "@test-obj" @test-obj)
  (swap! test-obj (fn [_] (webapp))))
(defn stop []
  (println "Stopping" @test-obj)
  (.stop @test-obj))
(defn restart []
  (when @test-obj (stop))
  (start))
