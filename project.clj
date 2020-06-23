(defproject corona_cases
  ;; see:
  ;; https://github.com/roomkey/lein-v
  ;; "Example of Clojure source code version output"
  ;; and:
  ;; https://github.com/arrdem/lein-git-version
  "2.0.0"

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
   [compojure               "1.6.1"]

   ;; Ring Jetty adapter
   [ring/ring-jetty-adapter "1.8.1"
    :exclusions
    [
     ring/ring-core
     ring/ring-codec
     commons-fileupload
     ]
    ]

   [cider/cider-nrepl "0.25.2"
    ;; :exclusions [nrepl]
    ]

   ;; managing environment variables
   [environ                 "1.2.0"]

   ;; JSON and JSON SMILE encoding
   [cheshire "5.10.0"]  ;; it looks like the clj-http requires this dependency.
                        ;; otherwise I get `namespace 'cheshire.factory' not
                        ;; found`
   ;; for the get-json function
   [clj-http                "3.10.1"]

   ;; Clojure interface for Telegram Bot API
   [org.clojars.bost/morse  "0.0.0-157-0x8c5c"
    :exclusions
    [
     org.clojure/spec.alpha
     org.clojure/core.cache
     com.fasterxml.jackson.core/jackson-core
     org.clojure/tools.analyzer
     org.clojure/tools.analyzer.jvm
     org.clojure/core.memoize
     ]
    ]

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

   ;; for klipse
   [org.clojure/clojurescript "1.10.773"
    :exclusions
    [
     com.google.errorprone/error_prone_annotations
     com.google.jsinterop/jsinterop-annotations
     org.clojure/google-closure-library-third-party
     com.google.javascript/closure-compiler-externs
     com.google.protobuf/protobuf-java
     ]
    ]

   ;; web repl
   [viebel/klipse "7.9.6"]
   ]
  :min-lein-version "2.0.0"
  :uberjar-name "corona_cases-standalone.jar"
  :profiles {:production {:env {:production true}}})
