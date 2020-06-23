(defproject corona_cases
  ;; see:
  ;; https://github.com/roomkey/lein-v
  ;; "Example of Clojure source code version output"
  ;; and:
  ;; https://github.com/arrdem/lein-git-version
  "2.0.1"

  :description "Telegram Chatbot for tracking coronavirus information"
  :url "http://corona-cases-bot.herokuapp.com"
  :license {:name "Eclipse Public License v1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies
  [
   [org.clojure/clojure "1.10.1"]

   ;; CSV reader/writer to/from Clojure data structures.
   [org.clojure/data.csv    "1.0.0"]

   ;; Ring routing lib; dispatching of GET, PUT, etc.
   [compojure               "1.6.1"]

   ;; Ring Jetty adapter
   [ring/ring-jetty-adapter "1.8.1"]

   ;; managing environment variables
   [environ                 "1.2.0"]

   ;; JSON and JSON SMILE encoding - see also clj-http
   [cheshire "5.10.0"]

   ;; for the get-json function. Not having it cheshire as a dependency results
   ;; in: `namespace 'cheshire.factory' not found`
   [clj-http                "3.10.1"]

   ;; Clojure interface for Telegram Bot API
   [org.clojars.bost/morse  "0.0.0-157-0x8c5c"]

   [org.clojure/data.json   "1.0.0"]
   [clojure.java-time       "0.3.2"]
   [net.cgrand/xforms       "0.19.2"]
   [org.clojars.bost/clj-time-ext "0.0.0-37-0x545c"]
   [org.clojars.bost/utils  "0.0.0-37-0xc96a"]

   ;; https://github.com/generateme/cljplot
   [org.clojars.bost/cljplot "0.0.2"]

   ;; plotting - see also https://github.com/jsa-aerial/hanami
   #_[aerial.hanami "0.12.1"]

   ;; internationalization, ISO 3166-1 country codes etc.
   [com.neovisionaries/nv-i18n "1.27"]

   ;; parse HTML into Clojure data structures - scrapping data from HTML tables
   [hickory "0.7.1"]

   [org.clojars.bost/clojurescript "1.10.785"]
   [org.clojure/core.async  "1.2.603"]
   [reagent "0.10.0"]
   [org.clojars.bost/klipse "7.9.10"]
   ]

  :plugins
  [
   [lein-figwheel "0.5.20"]
   [lein-cljsbuild "1.1.8" :exclusions [[org.clojure/clojure]]]
   ]

  :source-paths ["src/clj" "src/cljs"]
  ;; :resource-paths ["scripts" "src" "resources" "target"]

  :cljsbuild
  {
   :builds
   [{:id "dev"
     :source-paths ["src/cljs"]

     ;; The presence of a :figwheel configuration here will cause figwheel to
     ;; inject the figwheel client into your build
     :figwheel
     {
      :on-jsload "corona.core/on-js-reload"
      ;; :open-urls will pop open your application in the default browser once
      ;; Figwheel has started and compiled your application.
      ;; Comment this out once it no longer serves you.
      :open-urls ["http://localhost:3449/index.html"]}

     :compiler
     {
      :main corona.core
      :asset-path "js/compiled/out"
      :output-to "resources/public/js/compiled/corona.js"
      :output-dir "resources/public/js/compiled/out"
      :source-map-timestamp true
      ;; To console.log CLJS data-structures, enable devtools in the Chrome
      ;; https://github.com/binaryage/cljs-devtools
      :preloads [devtools.preload]
      }
     }
    ;; This next build is a compressed minified build for production. You can
    ;; build this with `lein cljsbuild once min`
    {:id "min"
     :source-paths ["src/cljs"]
     :compiler {:output-to "resources/public/js/compiled/corona.js"
                :main corona.core
                :optimizations :advanced
                :pretty-print false}}]}

  :figwheel
  {
   ;; :http-server-root "public" ;; default and assumes "resources"
   ;; :server-port 3449 ;; default
   ;; :server-ip "127.0.0.1"

   :css-dirs ["resources/public/css"] ;; watch and update CSS

   ;; Start an nREPL server into the running figwheel process
   :nrepl-port 7888

   :nrepl-middleware [cider.piggieback/wrap-cljs-repl
                      cider.nrepl/cider-middleware
                      refactor-nrepl.middleware/wrap-refactor
                      ]

   ;; Server Ring Handler (optional)
   ;; if you want to embed a ring handler into the figwheel http-kit server,
   ;; this is for simple ring servers, if this doesn't work for you just run
   ;; your own server :) (see lein-ring)

   ;; :ring-handler hello_world.server/handler

   ;; To be able to open files in your editor from the heads up display you will
   ;; need to put a script on your path.
   ;; that script will have to take a file path and a line number ie. in
   ;; ~/bin/myfile-opener
   ;; #! /bin/sh
   ;; emacsclient -n +$2 $1
   ;;
   ;; :open-file-command "myfile-opener"

   ;; if you are using emacsclient you can just use
   ;; :open-file-command "emacsclient"

   ;; if you want to disable the REPL
   ;; :repl false

   ;; to configure a different figwheel logfile path
   ;; :server-logfile "tmp/logs/figwheel-logfile.log"

   ;; to pipe all the output to the repl
   ;; :server-logfile false
   }
  :min-lein-version "2.9.1"
  :uberjar-name "corona_cases-standalone.jar"
  :profiles
  {
   ;; recommended by
   ;; https://devcenter.heroku.com/articles/deploying-clojure#the-project-clj-file
   :uberjar {:aot :all}

   ;; comes from `lein new heroku ...`
   :production {:env {:production true}}

   :dev
   {
    :dependencies
    [
     [cider/piggieback "0.5.0"]
     [binaryage/devtools "1.0.2"]
     [figwheel-sidecar "0.5.20"]
     [nrepl "0.7.0"]
     ]
    :source-paths ["src/clj" "src/cljs" "dev"]
    :plugins
    [
     [lein-figwheel "0.5.20"]
     [cider/cider-nrepl "0.25.2"]
     [org.clojure/tools.namespace "1.0.0"
      :exclusions [org.clojure/tools.reader]]
     [refactor-nrepl "2.5.0"
      :exclusions [org.clojure/clojure]]
     ]
    :env {:dev true}
    ;; need to add the compiled assets to the :clean-targets
    :clean-targets ^{:protect false} ["resources/public/js/compiled"
                                      :target-path]}
   }
  )
