(ns build
  "corona_cases's build script.

  clojure -T:build uberjar

  For more information, run:

  clojure -A:deps -T:build help/doc"
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]))

(def lib 'org.clojars.bost/corona_cases)

(defn- the-version [patch] (format "1.2.%s" patch))
(def version (the-version (b/git-count-revs nil)))
(def snapshot (the-version "999-SNAPSHOT"))

(defn uberjar "Run the CI pipeline of tests (and build the uberjar)." [opts]
  (-> opts
      (assoc :lib lib
             ;; :main main
             )
      #_(bb/run-tests)
      (bb/clean)
      (bb/uber)))
