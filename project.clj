(defproject corona_cases
  ;; see:
  ;; https://github.com/roomkey/lein-v
  ;; "Example of Clojure source code version output"
  ;; and:
  ;; https://github.com/arrdem/lein-git-version
  "1.9.2"

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

   ;; managing environment variables
   [environ                 "1.1.0"]

   ;; Clojure interface for Telegram Bot API
   [morse                   "0.4.3"]

   [org.clojure/data.json   "1.0.0"]
   [clojure.java-time       "0.3.2"]
   [net.cgrand/xforms       "0.19.2"]
   [org.clojars.bost/clj-time-ext "0.0.0-37-0x545c"]
   [org.clojars.bost/utils "0.0.0-32-0x8782"]

   ;; https://github.com/generateme/cljplot
   [org.clojars.bost/cljplot "0.0.2"]

   ;; See also https://github.com/jsa-aerial/hanami
   #_[aerial.hanami "0.12.1"]

   ;; internationalization, ISO 3166-1 country codes etc.
   [com.neovisionaries/nv-i18n "1.27"]

   ;; parse HTML into Clojure data structures - scrapping data from HTML tables
   [hickory "0.7.1"]]
  :min-lein-version "2.0.0"
  :uberjar-name "corona_cases-standalone.jar"
  :profiles {:production {:env {:production true}}})
