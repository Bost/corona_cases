(defproject coronavirus
  "1.2.9"
  :description "Telegram Chatbot for tracking coronavirus information"
  :url "http://corona-cases-bot.herokuapp.com"
  :license {:name "Eclipse Public License v1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies
  [
   [org.clojure/clojure     "1.10.1"]
   [compojure               "1.6.1"]
   [ring/ring-jetty-adapter "1.8.0"]
   [environ                 "1.1.0"]
   [morse                   "0.4.3"]
   [org.clojure/data.json   "0.2.7"]
   [org.clojars.bost/clj-time-ext "0.0.0-37-0x545c"]
   [com.hypirion/clj-xchart "0.2.0"]
   #_[incanter                "1.9.3"]
   [incanter/incanter-core   "1.9.3"]
   [incanter/incanter-charts "1.9.3"]
   ]
  :min-lein-version "2.0.0"
  :plugins
  [
   [environ/environ.lein "0.3.1"]
   ]
  :hooks [environ.leiningen.hooks]
  :uberjar-name "coronavirus-standalone.jar"
  :profiles {:production {:env {:production true}}})
