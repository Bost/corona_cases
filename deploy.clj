#!/usr/bin/env bb

(load-file "envdef.clj")
(ns deploy
  (:require
   [clojure.string :as str]
   [envdef :refer :all]
   [clojure.data.xml :as xml]
   [clojure.zip :as zip]))

(defn zip-str
  "Convenience function, first seen at nakkaya.com later in clj.zip src"
  [s]
  (zip/xml-zip
   (xml/parse (java.io.ByteArrayInputStream. (.getBytes s)))))

(defn bot-version-number []
  (->> "pom.xml" (slurp) (zip-str) (first) :content
       (filter (fn [elem] (= (:tag elem)
                            (keyword "xmlns.http%3A%2F%2Fmaven.apache.org%2FPOM%2F4.0.0/version"))))
       (map (fn [elem] (->> elem :content first)))
       (first)))

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
    (sh "git" "clone" "https://github.com/CSSEGISandData/COVID-19.git" (str repo "/.git"))
    ;; TODO following command composition should be done by a monadic bind
    (let [status0
          (sh "git" (str "--git-dir=" repo) "pull"
              "--rebase" "origin" "master")

          status1
          (sh
           "cp" "-r"
           (str repo "/csse_covid_19_data/csse_covid_19_daily_reports/*.csv")
           "resources/csv")

          status2
          (sh "git" "add" "resources/csv/*.csv")]
      (if (zero? (:exit status))
        (sh "git commit -m \"Add new csv file(s)\""))))

(def botVerSHA (sh "git" "rev-parse" "--short" "master"))
(def botVerNr (bot-version-number))

(println (format "bot-version-number: %s" botVerNr))

;; `heroku logs --tail --app $APP` blocks the execution
(sh "heroku" "addons:open" "papertrail" "--app" app)
(sh "heroku" "ps:scale" "web=0" "--app" app)
(sh "git" "push" (str/join " " rest-args) remote "master")
(sh "heroku" "config:set" (str "BOT_VER=" botVerSHA) "--app" app)
(sh "heroku" "ps:scale" "web=1" "--app" app)

;; ;; publish source code only when deploying to production
(if (= env-type prd)
  (do
    ;; seems like `git push --tags` pushes only tags w/o the code
    (sh "git" "tag" "--annotate" "--message" "''"
        (str botVerNr "-" botVerSHA))

    (doseq [remote ["origin" "gitlab"]]
      (sh "git" "push" "--follow-tags" "--verbose" remote)

      ;; if test $status != 0
      ;;     break
      ;; end

      ;; TODO this should not be needed
      ;; set cmd git push --tags $pushFlags $remote
      ;; echo $cmd
      ;; eval $cmd
      ;; if test $status != 0
      ;;     break
      ;; end
      )))

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
;; ;; set --export BOT_VER $botVerSHA; and \
;; ;; java $JVM_OPTS -cp target/corona_cases-standalone.jar:$cljjar:$cljsjar \
;;     ;; clojure.main -m corona.web

;; ;; resize screnshot from CLI
;; ;; set screenshot path/to/screenshot.jpg
;; ;; convert -resize 40% $screenshot resources/pics/screenshot_40-percents.jpg
