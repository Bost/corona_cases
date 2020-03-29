(defproject corona_cases
  "1.7.7"
  :description "Telegram Chatbot for tracking coronavirus information"
  :url "http://corona-cases-bot.herokuapp.com"
  :license {:name "Eclipse Public License v1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies
  [
   [org.clojure/clojure     "1.10.1"]
   [org.clojure/data.csv    "1.0.0"]
   [compojure               "1.6.1"]
   [ring/ring-jetty-adapter "1.8.0"]
   [environ                 "1.1.0"]
   [morse                   "0.4.3"]
   [org.clojure/data.json   "0.2.7"]
   [org.clojars.bost/clj-time-ext "0.0.0-37-0x545c"]

   ;; TODO use  instead of com.hypirion/clj-xchart
   ;; https://github.com/generateme/cljplot
   #_[cljplot "0.0.2-SNAPSHOT"]
   ;; See also https://github.com/jsa-aerial/hanami
   #_[aerial.hanami "0.12.1"]
   [com.hypirion/clj-xchart "0.2.0"]

   ;; internationalization, ISO 3166-1 country codes etc.
   [com.neovisionaries/nv-i18n "1.27"]

   [hickory "0.7.1"]]
  :min-lein-version "2.0.0"
  :plugins
  [
   [environ/environ.lein "0.3.1"]
   ]
  :hooks [environ.leiningen.hooks]
  :uberjar-name "corona_cases-standalone.jar"
  :profiles {:production {:env {:production true}}})
