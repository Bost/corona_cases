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

(def heroku-apps
  (->> (map (fn [kw]
              (get-in env/environment [kw :cli]))
            (keys env/environment))
       (into {})
       (vals)
       (set)))

#_(println "heroku-apps" heroku-apps)
(def cli-options
  ;; An option with a required argument
  [["-a" "--app APP" "Required Heroku app to run command against"
    :validate [(fn [elem]
                 ;; contains? can be used to test set membership.
                 ;; See https://clojuredocs.org/clojure.core/contains_q#example-542692cdc026201cdc326d2f
                 (contains? heroku-apps elem))
               (str "Must be an element of " heroku-apps)]]
   ["-f" "--force" "Force deployment"]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Usage: program-name action [options]"
        ""
        "Options:"
        options-summary]
       (cstr/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (cstr/join \newline errors)))

(def restart "restart")
(def deploy "deploy")

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with a error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}

      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}

      ;; custom validation on arguments
      (= 1 (count arguments))
      (cond
        (#{restart deploy} (first arguments))
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
;; Examples:
;; ./heroku.clj deploy --app hokuspokus-bot
(let [{:keys [action options exit-message ok?]} (validate-args *command-line-args*)]
  (if exit-message
    (exit (if ok? 0 1) exit-message)
    (condp = action
      restart (println (format "%s %s" action options))
      deploy
      (do
        (println (format "%s %s" action options))
        (let [commit (sh "git" "rev-parse" "--short" "master")
              clojure-cli-version (let [props (java.util.Properties.)
                                        key "CLOJURE_CLI_VERSION"]
                                    (.load props (jio/reader ".heroku-local.env"))
                                    (str key "=" (get props key)))

              app (str (:app options) "-bot")
              remote (str "heroku-" app)
              rest-args (if (:force options)
                          "--force"
                          "")
              ]
          (printf "%s: %s\n" 'pom/pom-version pom/pom-version)
          (printf "app %s \n" app)
          (printf "remote %s \n" remote)
          (printf "rest-args %s \n" rest-args)
          (printf "commit %s \n" commit)
          (printf "clojure-cli-version %s \n" clojure-cli-version)

          (sh "heroku" "addons:open" "papertrail" "--app" app)
          (sh "heroku" "ps:scale" "web=0" "--app" app)
          (sh "heroku" "config:set"
              (str "COMMIT=" commit)
              clojure-cli-version
              "--app" app)
          (sh "git" "push" rest-args remote "master")
          (sh "heroku" "ps:scale" "web=1" "--app" app)

          ;; ;; publish source code only when deploying to production
          (when false ; (= env-type prod)
            ;; seems like `git push --tags` pushes only tags w/o the code
            (sh "git" "tag" "--annotate" "--message" "''"
                (str pom/pom-version "-" commit))

            (doseq [remote ["origin" "gitlab"]]
              ;; See also `git push --tags $pushFlags $remote`
              (sh "git" "push" rest-args "--follow-tags" "--verbose" remote)))
          )))))
