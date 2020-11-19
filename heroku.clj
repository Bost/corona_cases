#!/usr/bin/env bb

(load-file "src/corona/envdef.clj")

(ns heroku
  "See https://github.com/clojure/tools.cli"
  (:require
   [clojure.string :as cstr]
   [corona.envdef :as env]
   [clojure.tools.cli :refer [parse-opts]]
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

(let [{:keys [action options exit-message ok?]} (validate-args *command-line-args*)]
  (if exit-message
    (exit (if ok? 0 1) exit-message)
    (condp = action
      restart (println (format "%s %s" action options))
      deploy (println (format "%s %s" action options)))))
