(defproject corona_cases
  ;; see:
  ;; https://github.com/roomkey/lein-v
  ;; "Example of Clojure source code version output"
  ;; and:
  ;; https://github.com/arrdem/lein-git-version
  "2.0.2"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies
  [
   [org.clojure/clojure "1.10.1"]
   [ring-server "0.5.0"]
   [reagent "0.10.0"]
   [reagent-utils "0.3.3"]
   [ring "1.8.1"]
   [ring/ring-defaults "0.3.2"]
   [hiccup "1.0.5"]
   [yogthos/config "1.1.7"]
   [metosin/reitit "0.5.2"]
   [metosin/jsonista "0.2.6"]
   [pez/clerk "1.0.0"]
   [venantius/accountant "0.2.5"
    :exclusions [org.clojure/tools.reader]]


   #_[org.clojure/clojurescript "1.10.773" :scope "provided"]
   [org.clojars.bost/clojurescript "1.10.785" :scope "provided"]
   #_[viebel/klipse "7.9.6"]
   [org.clojars.bost/klipse "7.9.13"]

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


   ]

  :plugins
  [
   [lein-environ "1.2.0"]
   #_[lein-cljsbuild "1.1.8"]
   [org.clojars.bost/lein-cljsbuild "1.1.9"]
   [lein-asset-minifier "0.4.6"
    :exclusions [org.clojure/clojure]]
   ]

  :ring {:handler corona.handler/app
         :uberwar-name "corona.war"}

  :min-lein-version "2.5.0"
  :uberjar-name "corona.jar"
  :main corona.server
  :clean-targets ^{:protect false}
  [:target-path
   [:cljsbuild :builds :app :compiler :output-dir]
   [:cljsbuild :builds :app :compiler :output-to]]

  :source-paths ["src/clj" "src/cljc" "src/cljs"]
  :resource-paths ["resources" "target/cljsbuild"]

  :minify-assets
  [[:css {:source "resources/public/css/site.css"
          :target "resources/public/css/site.min.css"}]]

  :cljsbuild
  {
   :builds
   {
    :min
    {
     :source-paths ["src/cljs" "src/cljc" "env/prod/cljs"]
     :compiler {
                :output-to        "target/cljsbuild/public/js/app.js"
                :output-dir       "target/cljsbuild/public/js"
                :source-map       "target/cljsbuild/public/js/app.js.map"
                :optimizations :advanced
                :infer-externs true
                :pretty-print  false
                }
     }
    :app
    {
     :source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
     :figwheel {:on-jsload "corona.core/mount-root"}
     :compiler {
                :main "corona.dev"
                :asset-path "/js/out"
                :output-to "target/cljsbuild/public/js/app.js"
                :output-dir "target/cljsbuild/public/js/out"
                :source-map true
                :optimizations :none
                :pretty-print  true
                }
     }
    }
   }

  :figwheel
  {
   :http-server-root "public"
   :server-port 3449
   :nrepl-port 7002
   :nrepl-middleware [
                      cider.piggieback/wrap-cljs-repl
                      cider.nrepl/cider-middleware
                      refactor-nrepl.middleware/wrap-refactor
                      ]
   :css-dirs ["resources/public/css"]
   :ring-handler corona.handler/app
   }

  :profiles
  {
   :dev
   {
    :repl-options {:init-ns corona.repl}
    :dependencies [
                   [cider/piggieback "0.5.0"]
                   [binaryage/devtools "1.0.2"]
                   [ring/ring-mock "0.4.0"]
                   [ring/ring-devel "1.8.1"]
                   [prone "2020-01-17"]
                   [figwheel-sidecar "0.5.20"]
                   [nrepl "0.7.0"]
                   [pjstadig/humane-test-output "0.10.0"]
                   ]

    :source-paths ["env/dev/clj"]
    :plugins [
              [lein-figwheel "0.5.20"]
              [cider/cider-nrepl "0.25.2"]
              [org.clojure/tools.namespace "1.0.0"
               :exclusions [org.clojure/tools.reader]]
              [refactor-nrepl "2.5.0"
               :exclusions [org.clojure/clojure]]
              ]

    :injections [(require 'pjstadig.humane-test-output)
                 (pjstadig.humane-test-output/activate!)]

    :env {:dev true}
    }

   :uberjar {
             :hooks [minify-assets.plugin/hooks]
             :source-paths ["env/prod/clj"]
             :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
             :env {:production true}
             :aot :all
             :omit-source true
             }
   }
  )
