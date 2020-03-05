(ns corona.core
  (:require [environ.core :refer [env]]
            [clojure.java.io :as io]))

(def project-name "corona_cases") ;; see project.clj: defproject
(def token (env :telegram-token))
(def chat-id "112885364")
(def bot-name (str "@" project-name "_bot"))

(defn telegram-token-suffix []
  (let [suffix (.substring token (- (count token) 3))]
    (if (or (= suffix "Fq8") (= suffix "MR8"))
      suffix
      (throw (Exception.
              (format "Unrecognized TELEGRAM_TOKEN suffix: %s" suffix))))))

(def bot-type
  (let [suffix (telegram-token-suffix)]
    (case suffix
      "Fq8" "PROD"
      "MR8" "TEST")))

(def bot-ver
  (str (let [pom-props
             (with-open
               [pom-props-reader
                (->> (format "META-INF/maven/%s/%s/pom.properties"
                             project-name project-name)
                     io/resource
                     io/reader)]
               (doto (java.util.Properties.)
                 (.load pom-props-reader)))]
         (get pom-props "version"))
       "-" (env :bot-ver)))

(def bot (str bot-ver ":" bot-type))

(defn fix-octal-val
  "(read-string s-day \"08\") produces a NumberFormatException
  https://clojuredocs.org/clojure.core/read-string#example-5ccee021e4b0ca44402ef71a"
  [s]
  (clojure.string/replace s #"^0+" ""))

(defn read-number [v]
  (if (or (empty? v) (= "0" v))
    0
    (-> v fix-octal-val read-string)))
