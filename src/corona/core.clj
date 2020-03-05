(ns corona.core
  (:require [environ.core :refer [env]]
            [clojure.java.io :as io]))

(def token (env :telegram-token))
(def bot-name
  "@corona_cases_bot"
  #_"@corona\\_cases\\_bot")

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
                (->> "META-INF/maven/corona/corona/pom.properties"
                     io/resource
                     io/reader)]
               (doto (java.util.Properties.)
                 (.load pom-props-reader)))]
         (get pom-props "version"))
       "-" (env :bot-ver)))

(def bot (str bot-ver ":" bot-type))

