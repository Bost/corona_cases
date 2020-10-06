#!/usr/bin/env bb

(load-file "envdef.clj")
(ns deploy
  (:require
   [envdef :refer :all]))

;; See https://devcenter.heroku.com/articles/dynos#automatic-dyno-restarts
(sh "heroku" "ps:restart" "--app" app)

;; (sh "heroku" "ps:scale" "web=0" "--app" app)
;; (sh "heroku" "ps:scale" "web=1" "--app" app)
