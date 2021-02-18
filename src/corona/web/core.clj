;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.web.core)

(ns corona.web.core
  (:require [clj-time-ext.core :as cte]
            [clj-time.core :as ctc]
            [clojure.data.json :as json]
            [clojure.string :as cstr]
            [com.stuartsierra.component :as component]
            [compojure.core :as cjc]
            compojure.handler
            [corona.common :as com]
            [corona.country-codes :as ccc]
            [corona.plot :as plot]
            [corona.telegram :as tgram]
            [corona.web.response :as webresp]
            drawbridge.core
            [morse.api :as moa]
            [morse.handlers :as moh]
            ring.adapter.jetty
            [ring.middleware.basic-authentication :as basic]
            ring.middleware.json
            ring.middleware.keyword-params
            ring.middleware.nested-params
            ring.middleware.params
            [ring.middleware.session :as session]
            ring.util.http-response
            [corona.macro :refer [defn-fun-id debugf infof]]
            [taoensso.timbre :as timbre])
  (:import java.time.ZoneId
           java.util.TimeZone))

;; (set! *warn-on-reflection* true)

(def ^:const google-hook "google")

(defn home-page []
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (cstr/join "\n" ["home page"])})

(def ^:const ws-path (format "ws/%s" webresp/pom-version))

(defn webhook-url [telegram-token]
  (format "%s/%s" com/webapp-server telegram-token))

(declare tgram-handlers)

(when com/use-webhook?
  (timbre/debugf "Defining tgram-handlers at compile time ...")
  (moh/apply-macro moh/defhandler tgram-handlers (tgram/create-handlers))
  (timbre/debugf "Defining tgram-handlers at compile time ... done"))

(defn-fun-id setup-webhook "" []
  (if com/use-webhook?
    (when (empty? (->> com/telegram-token moa/get-info-webhook
                       :body :result :url))
      (let [res (moa/set-webhook com/telegram-token
                                 (webhook-url com/telegram-token))]
        (debugf "(set-webhook ...) %s" (:body res))))
    (when-not (empty? (->> com/telegram-token moa/get-info-webhook
                           :body :result :url))
      (let [res (moa/del-webhook com/telegram-token)]
        (debugf "(del-webhook ...) %s" (:body res))))))

(defn- authenticated? [user pass]
  ;; TODO: heroku config:add REPL_USER=[...] REPL_PASSWORD=[...]
  (= [user pass] [com/repl-user com/repl-password]))

(def ^:private drawbridge
  (-> (drawbridge.core/ring-handler)
      (session/wrap-session)
      (basic/wrap-basic-authentication authenticated?)))

(cjc/defroutes app-routes
  (cjc/ANY "/repl" {:as req}
    (do
      (drawbridge req)
      (Thread/sleep 400)))

  (cjc/POST
    (format "/%s" com/telegram-token)
    args
    (let [body (get-in args [:body])]
      (timbre/debugf "webhook request body:\n%s" args)
      (tgram-handlers body)
      (ring.util.http-response/ok)))

  (cjc/POST
    (format "/%s/%s" google-hook com/telegram-token)
    req ;; {{input :input} :params}
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body
     (json/write-str {:chat_id (->> req :params :message :chat :id)
                      :text (format "Hello from %s webhook" google-hook)})})
  (cjc/GET "/" [] (home-page))
  (cjc/GET "/links" [] (webresp/links))
  (cjc/GET (format "/%s/:id/:aggregation/:case" com/graphs-path)
           [id aggregation case]
           ;; TODO make sure strings and keywords are not getting confused
           {:status 200 :headers {"Content-Type" "image/png"}
            :body (plot/aggregation!
                   id
                   (keyword aggregation)
                   (keyword case))})
  (cjc/GET (format "/%s/beds" ws-path) []
    (webresp/web-service {:type :beds}))
  (cjc/GET (format "/%s/names" ws-path) []
    (webresp/web-service {:type :names}))
  (cjc/GET (format "/%s/codes" ws-path) []
    (webresp/web-service {:type :codes}))
  (cjc/ANY "*" []
    (ring.util.http-response/not-found)
    #_(compojure.route/not-found
       "Not found"
       #_(slurp (jio/resource "404.html")))))

(def ^:private drawbridge-handler
  (-> (drawbridge.core/ring-handler)
      (ring.middleware.keyword-params/wrap-keyword-params)
      (ring.middleware.nested-params/wrap-nested-params)
      (ring.middleware.params/wrap-params)
      (ring.middleware.session/wrap-session)
      (basic/wrap-basic-authentication authenticated?)))

(defn wrap-drawbridge [handler]
  (fn [req]
    (let [handler (if (= "/repl" (:uri req))
                    (basic/wrap-basic-authentication
                     drawbridge-handler authenticated?)
                    handler)]
      (handler req))))

;; For interactive development:
(defonce server (atom nil))

(defn-fun-id webapp-start "" [port]
  (if com/use-webhook?
    (infof "Starting ...")
    (infof "Starting ...\n  %s" (cstr/join "\n  " (com/show-env))))

  (let [web-server
        (ring.adapter.jetty/run-jetty
         (-> #'app-routes
             (wrap-drawbridge)
             (compojure.handler/site)
               ;; wrap-json-body is needed for the destructing the
               ;; (POST "..." {body :body} ...)
             (ring.middleware.json/wrap-json-body {:keywords? true}))
         {:port port :join? false})]
    (debugf "web-server %s" web-server)
    (swap! server (fn [_] web-server))
    (infof "Starting ... done")
    web-server))

(defn-fun-id -main "TODO test this by bin/build; heroku local" [& [port]]
  (let [port (or port com/webapp-port)]
    (debugf "Starting ...")
    (infof "\n  %s" (cstr/join "\n  " (com/show-env)))
    (if (= (str (ctc/default-time-zone))
           (str (ZoneId/systemDefault))
           (.getID (TimeZone/getDefault)))
      (debugf "TimeZone: %s; current time: %s (%s in %s)"
              (str (ctc/default-time-zone))
              (cte/tnow)
              (cte/tnow ccc/zone-id)
              ccc/zone-id)
      (debugf (str "ctc/default-time-zone %s; "
                   "ZoneId/systemDefault: %s; "
                   "TimeZone/getDefault: %s\n")
              (ctc/default-time-zone)
              (ZoneId/systemDefault)
              (.getID (TimeZone/getDefault))))
    ;; The webapp must be always started, this error occurs otherwise:
    ;; Error R10 (Boot timeout) -> Web process failed to bind to
    ;; $PORT within 60 seconds of launch
    ;; https://devcenter.heroku.com/articles/run-non-web-java-processes-on-heroku
    (webapp-start port)

    ;; setup-webhook should be done in the end after everything is initialized
    (setup-webhook)

    (tgram/start)
    (debugf "Staring ... done")))

(defn-fun-id webapp-stop "" []
  (debugf "Stopping ...")
  (.stop ^org.eclipse.jetty.server.Server @server)
  (let [objs ['corona.web/server]]
    (run! (fn [obj-q]
            (let [obj (eval obj-q)]
              (swap! obj (fn [_] nil))
              (debugf "%s new value: %s"
                      obj-q (if-let [v (deref obj)] v "nil"))))
          objs))
  (debugf "Stopping ... done"))

(defn webapp-restart []
  (when @server
    (webapp-stop)
    (Thread/sleep 400))
  (webapp-start com/env-type com/webapp-port))

(defrecord WebServer [http-server app-component]
  component/Lifecycle
  (start [this] (assoc this :http-server
                       (webapp-start com/env-type com/webapp-port)))
  (stop [this] (webapp-stop) this))

(defn web-server
  "Returns a new instance of the web server component which
  creates its handler dynamically."
  []
  (component/using (map->WebServer {})
                   [:app-component]))

(def system (web-server))

(comment
  (alter-var-root #'corona.web.core/system com.stuartsierra.component/start)
  (alter-var-root #'corona.web.core/system com.stuartsierra.component/stop))

(printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.web.core)
