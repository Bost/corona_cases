(ns corona.common
  (:require
   [clj-http.client :as client]
   [clj-time-ext.core :as te]
   [clj-time.coerce :as tc]
   [clj-time.core :as t]
   [clj-time.format :as tf]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.string :as s]
   [environ.core :as en]
   [utils.num :as un]
   [utils.core :refer [in?] :exclude [id]]
   [corona.country-codes :refer :all]
   ))

(def project-name "corona_cases") ;; see project.clj: defproject

(def token (en/env :telegram-token))
(def port "Needed only in the corona.web" (en/env :port))

(def env-type (en/env :corona-env-type))
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

(defn calculate-active
  ([{:keys [cc f p c r d] :as prm}] (calculate-active p c r d))
  ([_ c r d] (- c (+ r d))))

(defn per-1e5
  "See https://groups.google.com/forum/#!topic/clojure/nH-E5uD8CY4"
  ([place total-count] (per-1e5 :normal place total-count))
  ([mode place total-count]
   (un/round mode (/ (* place 1e5) total-count))))

(defn calculate-active-per-100k
  ([{:keys [cc f p c r d] :as prm}] (calculate-active-per-100k p c r d))
  ([p c r d]
   (if (zero? p)
     0
     (per-1e5 (calculate-active p c r d) p))))

(defn fn-calculate-cases-per-100k [case-kw]
  #_(println fn-calculate-cases-per-100k case-kw)
  (fn calculate-cases-per-100k
    ([{:keys [cc f p c r d] :as prm}] (calculate-cases-per-100k p c r d))
    ([p c r d]
     (if (zero? p)
       0
       (per-1e5
        (case case-kw
          :r r
          :d d
          :c c)
        p)))))

(defn calculate-recovered-per-100k
  ([{:keys [cc f p c r d] :as prm}] (calculate-recovered-per-100k p c r d))
  ([p c r d]
   (if (zero? p)
     0
     (per-1e5 r p))))

(defn calculate-deaths-per-100k
  ([{:keys [cc f p c r d] :as prm}] (calculate-deaths-per-100k p c r d))
  ([p c r d]
   (if (zero? p)
     0
     (per-1e5 d p))))

(defn calculate-closed-per-100k
  ([{:keys [cc f p c r d] :as prm}] (calculate-closed-per-100k p c r d))
  ([p c r d]
   (if (zero? p)
     0
     (per-1e5 c p))))

(defn telegram-token-suffix []
  (let [suffix (.substring token (- (count token) 3))]
    (if (or (= suffix "Fq8") (= suffix "MR8"))
      suffix
      (throw (Exception.
              (format "Unrecognized TELEGRAM_TOKEN suffix: %s" suffix))))))

(def project-ver
  "From target/classes/META-INF/maven/%s/%s/pom.properties

  TODO there's a sha-sum in the pom.properties. Use it, don't calculate it
  "
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
  (format "%s-%s" project-ver (en/env :bot-ver)))

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
  ;; TODO use monad (Kleisli Category) for logging
  (let [tbeg (te/tnow)]
    (println (str "[" tbeg "           " " " bot-ver " /" "get-json " url "]"))
    (let [r (as-> url $
              (client/get $ {:accept :json})
              (:body $)
              (json/read-json $))]
      (println (str "[" tbeg ":" (te/tnow) " " bot-ver " /" "get-json " url "]"))
      r)))
(defn encode-cmd [s] (str "/" s))

(defn encode-pseudo-cmd
  "For displaying e.g. /<command-name>"
  [s parse_mode]
  {:pre (in? ["HTML" "Markdown"] parse_mode)}
  (if (= parse_mode "HTML")
    (let [s (s/replace s "<" "&lt;")
          s (s/replace s ">" "&gt;")]
      s)
    s))

(def absolute-cases [:c :r :d :i])
(def basic-cases (into absolute-cases [:i100k :r100k :d100k :c100k]))
(def all-cases (into [:p] basic-cases))

(def listing-cases-per-100k
  "No listing of :c100k - Closed cases per 100k"
  [:i100k :r100k :d100k])

(def listing-cases-absolute
  (into [:i :r :d]
        #_listing-cases-per-100k))

(defn fmt-date [date]
  (tf/unparse (tf/with-zone (tf/formatter "dd MMM yyyy")
                (t/default-time-zone))
              (tc/from-date date)))

(defn min-threshold
  "Countries with the number of cases less than the threshold are grouped into
  \"Rest\". See also `threshold-increase`."
  [case-kw]
  (case-kw (zipmap all-cases
                   [(int 1e7) (int 335e3) (int 263e3) (int 19e3) (int 129e3)])))

(defn threshold-increase
  "Case-dependent threshold recalculation increase. See also `min-threshold`."
  [case-kw]
  (case-kw (zipmap all-cases [(int 1e6) 5000 2500 500 1000])))

(def desc-ws
  "A placeholder"
  "")

;; TODO evaluate web services
;; https://sheets.googleapis.com/v4/spreadsheets/1jxkZpw2XjQzG04VTwChsqRnWn4-FsHH6a7UHVxvO95c/values/Dati?majorDimension=ROWS&key=AIzaSyAy6NFBLKa42yB9KMkFNucI4NLyXxlJ6jQ

;; https://github.com/iceweasel1/COVID-19-Germany

(def api-data-source
  "jhu"

  ;; csbs throws:
  ;; Execution error (ArityException) at cljplot.impl.line/eval34748$fn (line.clj:155).
  ;; Wrong number of args (0) passed to: cljplot.common/fast-max
  #_"csbs")

(def heroku-host-api-server
  "covid-tracker-us.herokuapp.com"
  #_"coronavirus-tracker-api.herokuapp.com")

(def api-server  (cond (or env-prod? env-test?) heroku-host-api-server
                       :else                    "localhost:8000"))

(def time-to-live "In minutes" 15)
