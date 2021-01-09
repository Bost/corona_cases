;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.web.core)

(ns corona.web.core
  (:require
   [clj-time-ext.core :as cte]
   [clj-time.core :as ctc]
   [clojure.data.json :as json]
   [clojure.string :as cstr]
   [com.stuartsierra.component :as component]
   [compojure.core :as cjc]
   compojure.handler
   [corona.common :as com]
   [corona.country-codes :as ccc]
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
   [taoensso.timbre :as timbre :refer [debugf info infof]])
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
  (debugf "Defining tgram-handlers at compile time ...")
  (moh/apply-macro moh/defhandler tgram-handlers (tgram/create-handlers))
  (debugf "Defining tgram-handlers at compile time ... done"))

(defn setup-webhook
  ([] (setup-webhook "setup-webhook"))
  ([fun-id]
   (if com/use-webhook?
     (do
       (when (empty? (->> com/telegram-token moa/get-info-webhook
                          :body :result :url))
         (let [res (moa/set-webhook com/telegram-token
                                    (webhook-url com/telegram-token))]
           ;; (debugf "[%s] (set-webhook %s %s)" fun-id com/telegram-token webhook-url)
           (debugf "[%s] (set-webhook ...) %s" fun-id (:body res)))))
     (when-not (empty? (->> com/telegram-token moa/get-info-webhook
                            :body :result :url))
       (let [res (moa/del-webhook com/telegram-token)]
         ;; (debugf "[%s] (del-webhook %s)" fun-id com/telegram-token)
         (debugf "[%s] (del-webhook ...) %s" fun-id (:body res)))))))

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
      (debugf "webhook request body:\n%s" args)
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
  (cjc/GET "/" []
    (home-page))
  (cjc/GET "/links" []
           (webresp/links))
  (cjc/GET "/graphs" args
           (debugf "args %s" args))
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

(defn webapp-start [env-type port]
  (let [fun-id "webapp"
        msg (format "[%s] starting" fun-id)
        version (if com/env-devel? com/undef com/botver)]
    (infof "%s version %s in environment %s on port %s ..."
           msg version env-type port)
    (let [web-server
          (ring.adapter.jetty/run-jetty
           #_(compojure.handler/api #'app-routes)
           (-> #'app-routes
               (wrap-drawbridge)
               (compojure.handler/site)
               ;; wrap-json-body is needed for the destructing the
               ;; (POST "..." {body :body} ...)
               (ring.middleware.json/wrap-json-body {:keywords? true}))
           {:port port :join? false})]
      (debugf "[%s] web-server %s" fun-id web-server)
      (swap! server (fn [_] web-server))
      (infof "%s ... done" msg)
      web-server)))

(defn -main [& [env-type port]]
  (let [fun-id "-main"
        env-type (or env-type com/env-type)
        port (or port com/webapp-port)
        msg (format "[%s] starting" fun-id)]
    #_(infof "%s version %s in environment %s on port %s ..."
             msg (if com/env-devel? com/undef com/botver) env-type port)
    (debugf "%s ..." msg)
    (infof "\n  %s" (clojure.string/join "\n  " (com/show-env)))
    (if (= (str (ctc/default-time-zone))
           (str (ZoneId/systemDefault))
           (.getID (TimeZone/getDefault)))
      (debugf "[%s] TimeZone: %s; current time: %s (%s in %s)"
              fun-id
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
    (webapp-start env-type port)

    ;; setup-webhook should be done in the end after everything is initialized
    (setup-webhook)

    (tgram/start env-type)
    (debugf "%s ... done" msg)))

(defn webapp-stop
  ([] (webapp-stop "webapp"))
  ([fun-id]
   (let [msg (format "[%s] stopping" fun-id)]
     (debugf "%s ..." msg)
     (.stop ^org.eclipse.jetty.server.Server @server)
     (let [objs ['corona.web/server]]
       (run! (fn [obj-q]
               (let [obj (eval obj-q)]
                 (swap! obj (fn [_] nil))
                 (debugf "[%s] %s new value: %s"
                         fun-id
                         obj-q (if-let [v (deref obj)] v "nil"))))
             objs))
     (debugf "%s ... done" msg))))

(defn webapp-restart []
  (when @server
    (webapp-stop)
    (Thread/sleep 400))
  (webapp-start com/env-type com/webapp-port))

#_(let [doc
        "Attention!
Value is reset to nil when reloading current buffer,
e.g. via `s-u` my=cider-save-and-load-current-buffer."]
    (->> ['corona.web/server 'data/cache 'tgram/continue 'tgram/my-component]
         (run! (fn [v] (alter-meta! (get (ns-interns *ns*) v) assoc :doc doc)))))

(defrecord WebServer [http-server app-component]
  component/Lifecycle
  (start [this]
    (assoc this :http-server
           (webapp-start com/env-type com/webapp-port)
           #_(web-framework/start-http-server (app-routes app-component))))
  (stop [this]
    (webapp-stop)
    #_(stop-http-server http-server)
    this))

(defn web-server
  "Returns a new instance of the web server component which
  creates its handler dynamically."
  []
  (component/using (map->WebServer {})
                   [:app-component]))

(def system (web-server))

(comment
  (alter-var-root #'system component/start)
  (alter-var-root #'system component/stop))

(printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.web.core)
