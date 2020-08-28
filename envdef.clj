#!/usr/bin/env bb

(ns envdef
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.java.shell :as shell]
   #_[babashka.process :refer [process]])
  (:import
   java.lang.ProcessBuilder$Redirect
   java.io.File
   ))

(def tst "--test")
(def prd "--prod")

(def env {tst "hokuspokus"
          prd "corona-cases"})

(def env-type (first *command-line-args*))
(def rest-args (rest *command-line-args*))

(defn trim-last-newline [s]
  (if (and (not-empty s) (.endsWith s "\n"))
    (.substring s 0 (dec (count s)))
    s))

#_(defn sh
    "See https://github.com/babashka/babashka.process"
    [& raw-args]
  #_(-> (process raw-args) :out)
  #_(-> (process raw-args {:out :stream}) :out slurp)
  #_(with-out-str (process raw-args {:out *out*}))
  #_(do (process raw-args {:out :inherit}) :out slurp)
  #_(-> (process raw-args {:out :inherit}) :out )
  #_(do (process raw-args {:out :inherit}) nil) ;; the best
  )

#_(defn sh
    "Uses clojure.java.shell"
    [& raw-args]
  (let [args (remove empty? raw-args)
        cmd (str/join " " args)]
    (println cmd)
    (let [r (apply shell/sh args)
            exit (:exit r)]
        #_(println "r:" r)
        (if (zero? exit)
          (let [rr (if-let [out (not-empty (:out r))] out (:err r))]
            (print rr)
            (trim-last-newline rr))
          (do
            (binding [*out* *err*]
              (print (:err r)))
            (System/exit exit))))))

#_(defn sh
  "From https://book.babashka.org/#_java_lang_processbuilder"
  [& raw-args]
  (let [sh-process
        (fn [& raw-args]
          (let [cmd (remove empty? raw-args)]
            #_(println (str/join " " cmd))
            (let [pb (doto (ProcessBuilder. cmd)
                       (.redirectOutput ProcessBuilder$Redirect/INHERIT))
                  proc (.start pb)]
              (-> (Runtime/getRuntime)
                  (.addShutdownHook (Thread. #(.destroy proc))))
              proc)))]
    (.waitFor (apply sh-process raw-args))))

(defn shell-command
  "Executes shell command. Exits script when the shell-command has a non-zero exit code, propagating it.
  Accepts the following options:
  `:input`: instead of reading from stdin, read from this string.
  `:to-string?`: instead of writing to stdoud, write to a string and
  return it."
  ([args] (shell-command args nil))
  ([args {:keys [:input :to-string?] :as prm}]
   #_(println 'prm prm)
   (let [
         ;; temp (File/createTempFile "file" ".tmp")
         ]
     #_(println "Temp file:" (.toString temp) "(type temp)" (type temp))
     #_(printf "to-string? %s; (not to-string?): %s => " to-string? (not to-string?))
     #_(if (not to-string?)
       (printf "Output redirected\n")
       (printf "Output not redirected\n"))
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
         (with-open [w (io/writer (.getOutputStream proc))]
           (binding [*out* w]
             (print input)
             (flush))))
       (let [string-out
             (when to-string? ;; i.e when output not redirected
               #_(println "Not redirected; reading from proc to-string...")
               (let [sw (java.io.StringWriter.)]
                 (with-open [w (io/reader
                                (.getInputStream proc)
                                #_(io/input-stream (File. (.toString temp)))
                                )]
                   (io/copy w sw))
                 (str sw)))
             exit-code (.waitFor proc)]
         (when-not (zero? exit-code)
           (System/exit exit-code))
         (printf "%s\n" string-out)
         string-out)))))

(defn sh [& raw-args]
    (let [args (remove empty? raw-args)]
      (println "#" (str/join " " args))
      (->> (shell-command args
                          (conj
                           #_{:to-string? false}
                           {:to-string? true}))
           (trim-last-newline))))

(def env-name
  (or (get env env-type)
      (let [file (->> (sh "basename" *file*) str/trim-newline (str "./"))]
        (if (empty? env-type)
          (println "ERR: Undefined parameter" 'env-type)
          (println "ERR: Unknown value of parameter" 'env-type env-type))
        ;; w/o the '--' every list element gets printed on a separate line
        ;; as if invoked in a for-loop. WTF?
        (let [env-names (keys env)]
          (println (format "Usage: %s {%s}\n\nExamples:" file
                           (str/join " | " env-names)))
          (doseq [en env-names]
            (print (format "%s %s\n" file en))))
        ;; TODO proper return
        (System/exit 1))))

(def app (str env-name "-bot"))
(def remote (str "heroku-" app))

(println (format "DBG: app: %s\nDBG: remote: %s\nDBG: rest-args: %s\n"
                 app remote (str/join " " rest-args)))
