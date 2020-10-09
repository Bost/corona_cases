#!/usr/bin/env bb

(load-file "envdef.clj")
(ns download-logs)
;; TODO rest-args should contain "... hours ago"

(def log-dir (str "log/" env-mame))
(sh "mkdir" "-p" log-dir)

(def pt-token (sh "heroku" "config:get" "PAPERTRAIL_API_TOKEN" "--app" app))
(def pt-header (format "'X-Papertrail-Token: %s'" pt-token))

(doseq [hour-ago (seq 0 0)]
  ;; It takes approximately 6-7 hours for logs to be available in the archive.
  ;; https://help.papertrailapp.com/kb/how-it-works/permanent-log-archives

  (let [hour-ago-delayed (+ hour-ago 7)
        date (str hour-ago-delayed " hours ago")
        date-ago (sh "date" "-u" (str "--date=" date) "+%Y-%m-%d-%H")
        out-file (format "%s/%s-UTC.tsv.gz" log-dir date-ago)]
    (println 'out-file out-file)
    (sh
     "curl" "--silent" "--no-include" "--output" out-file
     "--location" "--header" pt-header
     (str "https://papertrailapp.com/api/v1/archives/" date-ago "/download"))))

;; Search in logs:
;; rg --search-zip SEARCH_TERM ./log/
;; gzip -cd ./log/* | grep SEARCH_TERM
;; gzip --to-stdout --decompress ./log/* | grep SEARCH_TERM

;; heroku logs --num 1500 --app $APP # last 1500 lines
;; heroku logs --tail --app $APP
