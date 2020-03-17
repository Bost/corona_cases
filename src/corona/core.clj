(ns corona.core
  (:require [environ.core :refer [env]]
            [clojure.string :as s]
            [clojure.data.json :as json]
            [clj-time-ext.core :as te]
            [clj-http.client :as client]
            [clojure.java.io :as io]))

(defmacro dbg [body]
  `(let [x# ~body]
     (println "dbg:" '~body "=" x#)
     x#))

(defn in?
  "true if seq contains elm"
  [seq elm]
  (boolean (some (fn [e] (= elm e)) seq)))

(def project-name "corona_cases") ;; see project.clj: defproject
(def token (env :telegram-token))
(def chat-id "112885364")
(def bot-name (str "@" project-name "_bot"))

(defn calculate-ill [c r d] (- c (+ r d)))

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
