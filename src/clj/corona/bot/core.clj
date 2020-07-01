(ns corona.bot.core
  (:require [environ.core :refer [env]]
            [clojure.string :as s]
            [clojure.data.json :as json]
            [clj-time-ext.core :as te]
            [utils.core :refer :all]
            [clj-http.client :as client]
            [clojure.java.io :as io]))

(def project-name "corona_cases") ;; see project.clj: defproject

(def token (env :telegram-token))

(def env-type (env :corona-env-type))
(def env-prod?  (= env-type "PROD"))
(def env-test?  (= env-type "TEST"))
(def env-devel? (= env-type "DEVEL"))

(def chat-id "112885364")
(def bot-name (str "@"
                   (cond
                     env-prod? project-name
                     env-test? "hokuspokus"
                     env-devel? "hokuspokus"
                     :else "undefined")
                   "_bot"))

(defn calculate-ill
  ([{:keys [cc f p c r d] :as prm}] (calculate-ill p c r d))
  ([_ c r d] (- c (+ r d))))

(defn telegram-token-suffix []
  (let [suffix (.substring token (- (count token) 3))]
    (if (or (= suffix "Fq8") (= suffix "MR8"))
      suffix
      (throw (Exception.
              (format "Unrecognized TELEGRAM_TOKEN suffix: %s" suffix))))))

;; TODO read pom.properties/revision=b74228d69b40b3a3fc7ef999545c05cd0d948289
(def project-ver
  "From target/classes/META-INF/maven/%s/%s/pom.properties"
  (let [pom-props
        (with-open
          [pom-props-reader
           (->> (format "META-INF/maven/%s/%s/pom.properties"
                        project-name project-name)
                io/resource
                io/reader)]
          (doto (java.util.Properties.)
            (.load pom-props-reader)))]
    (get pom-props "version")))

(def bot-ver
  (format "%s-%s" project-ver (env :bot-ver)))

(def bot (str bot-ver ":" env-type))

(defn fix-octal-val
  "(read-string s-day \"08\") produces a NumberFormatException
  https://clojuredocs.org/clojure.core/read-string#example-5ccee021e4b0ca44402ef71a"
  [s]
  (s/replace s #"^0+" ""))

(defn read-number [v]
  (if (or (empty? v) (= "0" v))
    0
    (-> v fix-octal-val read-string)))

(defn left-pad
  ([s padding-len] (left-pad s "0" padding-len))
  ([s with padding-len]
   (s/replace (format (str "%" padding-len "s") s) " " with)))

#_
(defn left-pad [s padding-len]
  (str (s/join (repeat (- padding-len (count s)) " "))
       s))

(defn right-pad
  ([s padding-len] (right-pad s " " padding-len))
  ([s with padding-len]
   (str s
        (s/join (repeat (- padding-len (count s)) with)))))

(defn get-json [url]
  ;; TODO use monad for logging
  (let [tbeg (te/tnow)]
    (println (str "[" tbeg "           " " " bot-ver " /" "get-json " url "]"))
    (let [r (as-> url $
              (client/get $ {:accept :json})
              (:body $)
              (json/read-json $))]
      (println (str "[" tbeg ":" (te/tnow) " " bot-ver " /" "get-json " url "]"))
      r)))
