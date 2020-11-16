#!/usr/bin/env bb

(load-file "cli_env.clj")
(ns deploy
  (:require
   [cli-env :as cli]))

;; See https://devcenter.heroku.com/articles/dynos#automatic-dyno-restarts
(cli/sh "heroku" "ps:restart" "--app" cli/app)

;; (sh "heroku" "ps:scale" "web=0" "--app" app)
;; (sh "heroku" "ps:scale" "web=1" "--app" app)
