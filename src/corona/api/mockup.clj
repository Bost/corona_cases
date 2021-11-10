;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.api.mockup)

(ns corona.api.mockup
  "TODO download the json files when starting instead of serving them statically
  TODO run in a separate REPL under different JVM"
  (:require [compojure.core :refer [defroutes GET]]
            [ring.adapter.jetty :refer [run-jetty]]
            [corona.envdef :refer [mockup-port]]
            [ring.middleware.json :refer [wrap-json-body]]))

(def path "resources/mockup")

(defroutes app-routes
  ;; TODO code repetition - create a macro
  (GET
    "/all" []
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body ((comp
             slurp
             (partial str path "/"))
            "all.json")})
  (GET
    "/owid-covid-data.json" []
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body ((comp
             slurp
             (partial str path "/"))
            "owid-covid-data.json")}))

(defonce server (atom nil))

(defn run-server
  "See: ss -tulpn | rg 5051 # see `mockup-port`
  (clj-memory-meter.core/measure server) doesn't work"
  [_]
  (swap! server (fn [_]
                  (run-jetty
                   (wrap-json-body #'app-routes {:keywords? true})
                   {:port mockup-port :join? false})))
  (println @server))

(defn -main [] (run-server nil))

(comment
  (load "corona/api/mockup")
  (corona.api.mockup/run-server)
  #_(.start @corona.api.mockup/server)
  #_(.stop @corona.api.mockup/server)
  ,)

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.api.mockup)
