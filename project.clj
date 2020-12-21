;; See https://elements.heroku.com/buildpacks/heroku/heroku-buildpack-clojure

(defproject corona_cases
  ;; see:
  ;; https://github.com/roomkey/lein-v
  ;; "Example of Clojure source code version output"
  ;; and:
  ;; https://github.com/arrdem/lein-git-version
  "2.2.8"

  :description "Telegram Chatbot for tracking coronavirus information"
  :url "http://corona-cases-bot.herokuapp.com"
  :license {:name "Eclipse Public License v1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies
  [
   [org.clojure/clojure     "1.10.1"]

   ;; CSV reader/writer to/from Clojure data structures.
   [org.clojure/data.csv    "1.0.0"]

   ;; Ring routing lib; dispatching of GET, PUT, etc.
   [compojure               "1.6.2"]

   ;; Ring Jetty adapter
   [ring/ring-jetty-adapter "1.8.2"]

   ;; managing environment variables
   [environ                 "1.2.0"]

   ;; JSON and JSON SMILE encoding - see also clj-http
   [cheshire "5.10.0"]

   ;; for the get-json function. Not having it cheshire as a dependency results
   ;; in: `namespace 'cheshire.factory' not found`
   [clj-http                "3.11.0"]

   ;; Clojure interface for Telegram Bot API
   [org.clojars.bost/morse  "0.0.0-160-0x1afd"]

   [org.clojure/data.json   "1.0.0"]
   [clojure.java-time       "0.3.2"]
   [net.cgrand/xforms       "0.19.2"]
   [org.clojars.bost/clj-time-ext "0.0.0-39-0x3d91"]
   [org.clojars.bost/utils "0.0.0-45-0x5a42"]

   ;; https://github.com/generateme/cljplot
   [org.clojars.bost/cljplot "0.0.2"]

   ;; plotting - see also https://github.com/jsa-aerial/hanami
   #_[aerial.hanami "0.12.1"]

   ;; internationalization, ISO 3166-1 country codes etc.
   [com.neovisionaries/nv-i18n "1.27"]

   ;; parse HTML into Clojure data structures - scrapping data from HTML tables
   [hickory "0.7.1"]

   ;; TODO debugging - changes prompt according to sexp result
   ;; https://github.com/AppsFlyer/mate-clj

   [incanter/incanter-zoo "1.9.3"]   ;; roll-mean
   [incanter/incanter-core "1.9.3"]  ;; mean

   ;; logging
   [com.taoensso/timbre "5.1.0"]
   ]

  :min-lein-version "2.0.0"
  :uberjar-name "corona_cases-standalone.jar"
  :profiles
  {
   ;; TODO test :uberjar {:aot :all}
   :production {:env {:production true}}
   })

;; TODO analyze results of lein nvd check
;; $ lein nvd check
;; ...
;; Checking dependencies for corona_cases 2.0.8 ...
;; using nvd-clojure:  and dependency-check: 5.3.2
;; +--------------------------------------------------|----------------+
;; | dependency                                       | status         |
;; +--------------------------------------------------|----------------+
;; | commons-compress-1.8.jar                         | CVE-2018-11771 |
;; | google-closure-library-0.0-20160609-f42b4a24.jar | CVE-2020-8910  |
;; | guava-19.0.jar                                   | CVE-2018-10237 |
;; | jetty-server-9.4.28.v20200408.jar                | CVE-2019-17638 |
;; | protobuf-java-2.5.0.jar                          | CVE-2015-5237  |
;; +--------------------------------------------------|----------------+

;; 5 vulnerabilities detected. Severity: HIGH
;; Detailed reports saved in: target/nvd
