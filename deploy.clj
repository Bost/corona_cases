#!/usr/bin/env bb

(load-file "envdef.clj")
(load-file "pom_version_get.clj")
(ns deploy
  (:require
   [clojure.string :as cstr]
   [clojure.java.io :as jio]
   [envdef :as env]
   [pom-version-get :as pom]))

;; need to define LEIN_SNAPSHOTS_IN_RELEASE=true because of
;; cljplot "0.0.2-SNAPSHOT"
;; heroku config:set LEIN_SNAPSHOTS_IN_RELEASE=true --app $APP
;; heroku config:unset LEIN_SNAPSHOTS_IN_RELEASE --app $APP
;; heroku config:set CORONA_ENV_TYPE="PROD"  --app $APP
;; heroku config:set CORONA_ENV_TYPE="TEST"  --app $APP
;; heroku config:set CORONA_ENV_TYPE="DEVEL" --app $APP
;; heroku config:set PORT="..."              --app $APP ;; optional
;; heroku config:set HOME_PAGE="..."         --app $APP

;; activate when using data from the local COVID-19_repo
#_(let [repo "../COVID-19"]
    (env/sh "git" "clone" "https://github.com/CSSEGISandData/COVID-19.git" (str repo "/.git"))
    ;; TODO following command composition should be done by a monadic bind
    (let [status0
          (env/sh "git" (str "--git-dir=" repo) "pull"
                  "--rebase" "origin" "master")

          status1
          (env/sh
           "cp" "-r"
           (str repo "/csse_covid_19_data/csse_covid_19_daily_reports/*.csv")
           "resources/csv")

          status2
          (env/sh "git" "add" "resources/csv/*.csv")]
      (if (zero? (:exit status))
        (env/sh "git commit -m \"Add new csv file(s)\""))))

(def commit (env/sh "git" "rev-parse" "--short" "master"))

(def pom-version (pom/pom-version))

(def clojure-cli-version
  (let [props (java.util.Properties.)
        key "CLOJURE_CLI_VERSION"]
    (.load props (jio/reader ".heroku-local.env"))
    (str key "=" (get props key))))

(printf "%s: %s\n" 'pom-version pom-version)
(printf "%s\n" clojure-cli-version)

;; `heroku logs --tail --app $APP` blocks the execution
(env/sh "heroku" "addons:open" "papertrail" "--app" env/app)
(env/sh "heroku" "ps:scale" "web=0" "--app" env/app)
(env/sh "heroku" "config:set" (str "COMMIT=" commit) "--app" env/app)
(env/sh "heroku" "config:set" clojure-cli-version "--app" env/app)
(env/sh "git" "push" (cstr/join " " env/rest-args) env/remote "master")
(env/sh "heroku" "ps:scale" "web=1" "--app" env/app)

;; ;; publish source code only when deploying to production
(when (= env/env-type env/prod)
  ;; seems like `git push --tags` pushes only tags w/o the code
  (env/sh "git" "tag" "--annotate" "--message" "''"
          (str pom-version "-" commit))

  (doseq [remote ["origin" "gitlab"]]
    ;; See also `git push --tags $pushFlags $remote`
    (env/sh "git" "push" "--follow-tags" "--verbose" remote)))

;; ;; heroku ps:scale web=0 --app $APP; and \
;; ;; heroku ps:scale web=1 --app $APP
;; ;; heroku open --app $APP
;; ;; heroku addons:open papertrail --app $APP
;; ;; heroku logs --tail --app $APP
;; ;; heroku maintenance:on  --app $APP
;; ;; heroku config:unset type private_key_id private_key client_id client_email \
;;     ;; --app $APP
;; ;; heroku maintenance:off --app $APP

;; ;; Fix: Push rejected, submodule install failed
;; ;; Thanks to https://stackoverflow.com/a/31545903
;; ;; heroku plugins:install https://github.com/heroku/heroku-repo.git
;; ;; heroku repo:reset --app $APP

;; ;; heroku run bash --app $APP

;; ;; run locally:
;; ;; lein uberjar; and \
;; ;; set --export COMMIT= $botVerSHA; and \
;; ;; java $JVM_OPTS -cp target/corona_cases-standalone.jar:$cljjar:$cljsjar \
;;     ;; clojure.main -m corona.web

;; ;; resize screnshot from CLI
;; ;; set screenshot path/to/screenshot.jpg
;; ;; convert -resize 40% $screenshot resources/pics/screenshot_40-percents.jpg
