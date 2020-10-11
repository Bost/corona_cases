(printf "Current-ns [%s] loading %s\n" *ns* 'corona.web)

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
   [corona.country-codes :as cc])
  (:import
   java.time.ZoneId
   java.util.TimeZone))

;; (debugf "Loading namespace %s" *ns*)

(def ^:const telegram-hook "telegram")
(def ^:const google-hook "google")
(def ^:const undef "<UNDEF>")

(defn home-page []
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (s/join "\n" ["home page"])})

(def ^:const prj-vernum "See `co/prj-vernum`" nil)
(def ^:const ws-path (format "ws/%s" prj-vernum))

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
     (conj (when-not prj-vernum
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
     (str "/" hook "/" co/telegram-token) req ;; {{input :input} :params}
     {:status 200
      :headers {"Content-Type" "text/plain"}
      :body
      (json/write-str (conj {:chat_id (->> req :params :message :chat :id)}
                            {:text (str "Hello from " hook " webhook")}))}))

  (let [hook google-hook]
    (POST
     (str "/" hook "/" co/telegram-token) req ;; {{input :input} :params}
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

;; For interactive development:
(defonce component
  (atom nil))

(defn webapp-start [& [env-type port]]
  (let [port (Integer. (or port co/webapp-port
                           (cond co/env-prod? 5000
                                 ;; keep port-nr in sync with README.md
                                 :else 5050)))

        starting "[webapp] starting"
        msg (format "%s version %s in environment %s on port %s..."
                    starting
                    (if co/env-devel? undef co/commit)
                    env-type
                    port)]
    (info msg)
    (let [web-server (jetty/run-jetty (site #'app) {:port port :join? false})]
      (swap! component (fn [_] web-server))
      (infof "%s... done" starting)
      web-server)))

(defn -main [& [env-type port]]
  (let [env-type (or env-type co/env-type)
        port (or port co/webapp-port)
        starting "[-main] starting"
        msg (format "%s version %s in environment %s on port %s..."
                    starting
                    (if co/env-devel? undef co/commit)
                    env-type
                    port)]
    (info msg)
    (if (= (str (t/default-time-zone))
           (str (ZoneId/systemDefault))
           (.getID (TimeZone/getDefault)))
      (debugf "TimeZone: %s; current time: %s (%s in %s)"
              (str (t/default-time-zone))
              (te/tnow)
              (te/tnow cc/zone-id)
              cc/zone-id)
      (debugf (str "t/default-time-zone %s; "
                   "ZoneId/systemDefault: %s; "
                   "TimeZone/getDefault: %s\n")
              (t/default-time-zone)
              (ZoneId/systemDefault)
              (.getID (TimeZone/getDefault))))
    ;; Seems like the webapp must be always started, otherwise I get:
    ;; Error R10 (Boot timeout) -> Web process failed to bind to
    ;; $PORT within 60 seconds of launch
    ;; TODO try to change it in the Procfile. See in the console:
    ;;     remote: -----> Discovering process types
    ;;     remote:        Procfile declares types -> web
    ;; during the deployment process
    (webapp-start env-type port)
    (tgram/-main env-type)
    (infof "%s... done" starting)))

(defn webapp-stop []
  (info "[webapp] stopping...")
  (.stop @component)
  (let [objs ['component]]
    (run! (fn [obj-q]
            (let [obj (eval obj-q)]
              (swap! obj (fn [_] nil))
              (debug "%s new value: %s"
                     obj-q (if-let [v (deref obj)] v "nil"))))
          objs)))

(defn webapp-restart []
  (when @component
    (webapp-stop)
    (Thread/sleep 400))
  (webapp-start co/env-type co/webapp-port))

;; TODO defonce - add metadata
#_(let [doc
      "Attention!
Value is reset to nil when reloading current buffer,
e.g. via `s-u` my=cider-save-and-load-current-buffer."]
  (->> ['component 'data/cache 'tgram/continue 'tgram/component]
       (run! (fn [v] (alter-meta! (get (ns-interns *ns*) v) assoc :doc doc)))))




