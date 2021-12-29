#!/usr/bin/env bb

(load-file "src/corona/envdef.clj")
(load-file "src/corona/pom_version_get.clj")

(ns heroku
  "See https://github.com/clojure/tools.cli"
  (:require
   [clojure.string :as cstr]
   [corona.envdef :as env]
   [clojure.tools.cli :refer [parse-opts]]
   [clojure.java.io :as jio]
   [corona.pom-version-get :as pom]
   )
  (:import
   java.lang.ProcessBuilder$Redirect
   ))

(def git "git")
(def push "push")
(def master "master")

(def heroku-envs
  "levels 0 and 1 - the top two levels"
  ((comp
    set
    (partial map (comp name first))
    (partial filter (fn [[k v]] (<= (get-in v [:level]) 1))))
   env/environment))
#_(println "heroku-envs" heroku-envs)

(defn heroku-env-name [level]
  ((comp
    name first first
    (partial filter (fn [[k v]] (= (get-in v [:level]) level))))
   env/environment))

(def heroku-envs
  "levels 0 and 1 - the top two levels"
  (set (map heroku-env-name [0 1])))

(def heroku-prod
  "levels 0 and 1 - the top two levels"
  (heroku-env-name 0))
#_(println "heroku-prod" heroku-prod)

(defn heroku-app-name [heroku-env] (str heroku-env "-bot"))

(def pipelined-heroku-apps
  "Git branch-names must have the format 'heroku-<map-value>'."
  (zipmap [:src-app :dst-app]
          ((comp
            vec
            (partial map (comp heroku-app-name heroku-env-name)))
           [1 0])))
#_(println "pipelined-heroku-apps" pipelined-heroku-apps)

(defn heroku-app-name [heroku-env] (str heroku-env "-bot"))

(def pipelined-heroku-apps
  "git branch-names must be equal to map-values"
  (zipmap [:src-app :dst-app]
          ((comp
            vec
            (partial map (comp heroku-app-name heroku-env-name)))
           [1 0])))

(def heroku-apps
  ((comp
    set
    (partial map heroku-app-name))
   heroku-envs))
#_(println "heroku-apps" heroku-apps)

#_(System/exit 1)

(defn in?
  "true if `sequence` contains `elem`. See (contains? (set sequence) elem)"
  [sequence elem]
  (boolean (some (fn [e] (= elem e)) sequence)))

(def start         "start")
(def stop          "stop")
(def restart       "restart")
(def deploy        "deploy")
(def deleteWebhook "deleteWebhook")
(def setWebhook    "setWebhook")
(def showUsers     "showUsers")
(def promote       "promote")
(def getMockData   "getMockData")
(def getLogs       "getLogs")
(def updateClojureCliVersion "updateClojureCliVersion")

(def cli-actions
  "TODO a new action must be manually inserted into this list. Use macros for
  that"
  [start stop restart deploy deleteWebhook setWebhook showUsers promote
   getMockData getLogs
   updateClojureCliVersion])

(def cli-options
  ;; An option with a required argument
  [["-e" "--heroku-env HENV"
    (str "Heroku environment to run the action against.\n"
         (let [align "                         "]
           (str align "One of the:\n"
                (cstr/join \newline
                           (map (fn [a] (str align "  " a)) heroku-envs)))))
    :validate [(fn [henv] (in? heroku-envs henv))
               (str "Must be an element of " heroku-envs)]]
   ["-f" "--force" "Force deployment"]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Usage: program-name action [options]"
        ""
        "Action is one of the:"
        (cstr/join \newline (map (fn [a] (str "  " a)) cli-actions))
        ""
        "Options:"
        options-summary]
       (cstr/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (cstr/join \newline errors)))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with a error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary]}
        (parse-opts args cli-options)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}

      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}

      ;; custom validation on arguments
      (= 1 (count arguments))
      (cond
        ((set cli-actions)
         (first arguments))
        {:action (first arguments) :options options}

        :else
        {:exit-message (format "Unknown action: %s\n\n%s"
                               (first arguments) (usage summary))})

      :else ; failed custom validation => exit with usage summary
      {:exit-message (usage summary)})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn trim-last-newline [s]
  (if (and (not-empty s) (.endsWith s "\n"))
    (.substring s 0 (dec (count s)))
    s))

(defn shell-command
  "Executes shell command. Exits script when the shell-command has a non-zero
  exit code, propagating it. Accepts the following options:
  `:input`: instead of reading from stdin, read from this string.
  `:to-string?`: instead of writing to stdoud, write to a string and
  return it."
  ([args] (shell-command args nil))
  ([args {:keys [:input :to-string?]}]
   (let [args (mapv str args)
         pb (cond-> (-> (ProcessBuilder. ^java.util.List args)
                        (.redirectError ProcessBuilder$Redirect/INHERIT))
              (not to-string?)
              (.redirectOutput ProcessBuilder$Redirect/INHERIT)
              #_(.redirectOutput ProcessBuilder$Redirect/PIPE)
              #_(.redirectOutput (ProcessBuilder$Redirect/appendTo temp))

              (not input) (.redirectInput ProcessBuilder$Redirect/INHERIT))
         proc (.start pb)]
     (when input
       (with-open [w (jio/writer (.getOutputStream proc))]
         (binding [*out* w]
           (print input)
           (flush))))
     (let [string-out
           (when to-string? ;; i.e when output not redirected
             #_(println "Not redirected; reading from proc to-string ...")
             (let [sw (java.io.StringWriter.)]
               (with-open [w (jio/reader
                              (.getInputStream proc)
                              #_(jio/input-stream (File. (.toString temp)))
                              )]
                 (jio/copy w sw))
               (str sw)))
           exit-code (.waitFor proc)]
       (when-not (zero? exit-code)
         (System/exit exit-code))
       (printf "%s\n" string-out)
       string-out))))

(defn sh [& raw-args]
    (let [args (remove empty? raw-args)]
      (println "#" (cstr/join " " args))
      (->> (shell-command args
                          (conj
                           #_{:to-string? false}
                           {:to-string? true}))
           (trim-last-newline))))

(defn sh-mkdir-p [dir-name]
  (sh "mkdir" "-p" dir-name))

(defn sh-heroku
  [app & cmds]
  {:pre [(in? heroku-apps app)]}
  (apply sh (into ["heroku"] (conj (vec cmds) "--app" app))))

(defn get-commit!
  ([] (get-commit! master))
  ([branch] (sh git "rev-parse" "--short" branch)))

(defn set-config! [app commit clojure-cli-version]
    {:pre [(in? heroku-apps app)]}
  (sh-heroku app "config:set" (str "COMMIT=" commit) clojure-cli-version))

(def ps:scale "ps:scale")

(defn stop! [app]
  {:pre [(in? heroku-apps app)]}
  (sh-heroku app ps:scale "web=0"))

(defn start! [app]
  {:pre [(in? heroku-apps app)]}
  (sh-heroku app ps:scale "web=1"))

(defn restart!
  "There's a special heroku command for it"
  [app]
  {:pre [(in? heroku-apps app)]}
  (sh-heroku app "ps:restart"))

(defn open-papertrail [app]
  {:pre [(in? heroku-apps app)]}
  (sh-heroku app "addons:open" "papertrail"))

(defn publish-source!
  "Publish the source code only when deploying to production"
  [commit rest-args]
  ;; seems like `git push --tags` pushes only tags w/o the code
  (sh git "tag" "--annotate" "--message" "''"
      (str pom/pom-version "-" commit) commit)
  (doseq [remote ["origin" "gitlab"]]
    ;; See also `git push --tags $pushFlags $remote`
    (sh git push rest-args "--follow-tags" "--verbose" remote)))

;; (defmacro dbg-let
;;   "Display the let-values; does not evaluate the body"
;;   [vs & body]
;;   `(let ~vs
;;      ((comp
;;        doall
;;        (partial map (fn [~'v]
;;                       (printf "%s: %s\n" (first ~'v) (eval (second ~'v)))))
;;        (partial partition-all 2))
;;       (quote ~vs))))

(defn deploy! [prm-app {:keys [heroku-env] :as options}]
  {:pre [(in? heroku-apps prm-app)]}
  (let [commit (get-commit!)
        clojure-cli-version (let [props (java.util.Properties.)
                                  key "CLOJURE_CLI_VERSION"]
                              (.load props (jio/reader ".heroku-local.env"))
                              (str key "=" (get props key)))
        app (heroku-app-name heroku-env)
        remote (str "heroku-" app)
        rest-args (if (:force options) "--force" "")]
    (open-papertrail app)
    (stop! app)
    (set-config! app commit clojure-cli-version)
    (sh git push rest-args remote master)
    (start! app)
    (when (= heroku-env heroku-prod)
      (publish-source! commit rest-args))))

(defn promote! [{:keys [src-app dst-app]} options]
  {:pre [(and (not= src-app dst-app)
              (in? heroku-apps src-app)
              (in? heroku-apps dst-app))]}
  (let [commit (get-commit! (format "remotes/%s/%s" src-app master))
        clojure-cli-version (let [props (java.util.Properties.)
                                  key "CLOJURE_CLI_VERSION"]
                              (.load props (jio/reader ".heroku-local.env"))
                              (str key "=" (get props key)))
        rest-args (if (:force options) "--force" "")]
    (open-papertrail dst-app)
    (stop! dst-app)
    (set-config! dst-app commit clojure-cli-version)
    (sh-heroku src-app "pipelines:promote")
    (start! dst-app)
    (publish-source! commit rest-args)))

(defn sh-curl [& cmds] (apply (partial sh "curl") cmds))
(defn sh-wget [& cmds] (apply (partial sh "wget") cmds))

(def --output-document "--output-document") #_(def -O "-O")
(def --form "--form")

(defn webhook-action-prms [webhook-action telegram-token]
  [--form "drop_pending_updates=true" "--request" "POST"
   (format "https://api.telegram.org/bot%s/%s"
           telegram-token webhook-action)])

;; Examples:
;; ./heroku.clj deploy --heroku-env hokuspokus
;; ./heroku.clj deploy -e           hokuspokus
(let [{:keys [action options exit-message ok?]}
      (validate-args *command-line-args*)]
  (if exit-message
    (exit (if ok? 0 1) exit-message)
    (let [heroku-env (:heroku-env options)
          heroku-app (heroku-app-name heroku-env)
          telegram-token (get-in env/environment
                                 [(keyword heroku-env) :telegram-token])]
      (condp = action
        start   (start! heroku-app)
        stop    (stop! heroku-app)
        restart (restart! heroku-app)
        deploy  (deploy! heroku-app options)
        promote (promote! pipelined-heroku-apps options)

        #_(str "
         # Search in logs:
         rg --search-zip SEARCH_TERM ./log/
         gzip -cd ./log/* | grep SEARCH_TERM
         gzip --to-stdout --decompress ./log/* | grep SEARCH_TERM

         heroku logs --num 1500 --app $APP # last 1500 lines
         heroku logs --tail --app $APP
         ")
        ;; TODO plot log entries on a timescale:
        ;; parse the logfile, compute timestamp diffs
        getLogs
        (let [log-dir (str "log/" heroku-env)
              pt-token (sh-heroku heroku-app
                                  "config:get" "PAPERTRAIL_API_TOKEN")
              pt-header (format "X-Papertrail-Token: %s" pt-token)]

          (sh-mkdir-p log-dir)

          (doseq [hour-ago '(0 1)]
;;; It takes approximately 6-7 hours for logs to be available in the archive.
;;; https://help.papertrailapp.com/kb/how-it-works/permanent-log-archives
            (let [hour-ago-delayed (+ hour-ago 7)
                  date (str hour-ago-delayed " hours ago")
                  date-ago (sh "date" "-u" (str "--date=" date) "+%Y-%m-%d-%H")
                  out-file (format "%s/%s-UTC.tsv.gz" log-dir date-ago)]
              (sh-curl "--silent" "--no-include" "--output" out-file
                       "--location" "--header" pt-header
                       (format
                        "https://papertrailapp.com/api/v1/archives/%s/download"
                        date-ago)))))

        getMockData
        (let [dst-dir "resources/mockup"]
          (sh-mkdir-p dst-dir) ;; when running for the 1st time
          (sh-wget
           #_"https://coronavirus-tracker-api.herokuapp.com/all"
           #_"https://covid-tracker-us.herokuapp.com/all"
           ((comp
             (partial format "https://%s/all")
             #_first
             second)
            env/api-servers)
           --output-document (format "%s/all.json" dst-dir))
          (sh-wget env/owid-prod
                   --output-document
                   (format "%s/owid-covid-data.json" dst-dir)))

        deleteWebhook
        (apply sh-curl
               (webhook-action-prms deleteWebhook telegram-token))

        setWebhook
        #_(printf "%s\n" (into
                        [--form (format "'url=https://%s.herokuapp.com/%s'"
                                        heroku-app telegram-token)]
                        (webhook-action-prms telegram-token setWebhook)))
        (apply sh-curl
               (into
                ;; WTF? wrapping single quotes around url=... causes webhook
                ;; deletion
                [--form (format "url=https://%s.herokuapp.com/%s"
                                heroku-app telegram-token)]
                (webhook-action-prms setWebhook telegram-token)))

        ;; obtain the log using the heroku-papertrail plugin
        showUsers
        ;; TODO prohibit sh-heroku from writing to stdout
        ((comp
          (fn [s] (cstr/split s #"\n")))
         (sh-heroku heroku-app
                    ;; pt stands for the papertrail plugin
                    "pt" (format ":type -ssl-client-cert -%s"
                                 (System/getenv "MY_TELEGRAM_ID"))))

        updateClojureCliVersion
        ((comp
          (fn [script]
            (sh "sed" "--in-place" "-e" script ".heroku-local.env" ".env"))
          (partial format "s|CLOJURE_CLI_VERSION=.*|CLOJURE_CLI_VERSION=%s|")
          first
          re-find
          (partial re-matcher #"((\d+)\.)+(\d+)"))
         (sh "clj" "--version"))
        ))))
