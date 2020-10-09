(ns corona.common
  (:require
   [clj-http.client :as client]
   [clj-time-ext.core :as te]
   [clj-time.coerce :as tc]
   [clj-time.core :as t]
   [clj-time.format :as tf]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.core.memoize :as memo]
   [clojure.string :as s]
   [environ.core :as en]
   [utils.num :as un]
   [utils.core :refer [in?] :exclude [id]]
   [corona.country-codes :refer :all]
   [taoensso.timbre :as timbre :refer :all]
   [clojure.core.cache :as cache]
   [clojure.xml :as xml]
   [clojure.zip :as zip]
   ))

(defn ttt
  "For debugging.
  TODO move ttt to utils and make it monadic"
  [fun & args]
  (debug fun #_args)
  (let [tbeg (System/currentTimeMillis)]
    (let [r (doall (apply fun args))]
      (debug (format "%s took %s ms"
                     fun
                     (- (System/currentTimeMillis) tbeg)))
      r)))

(defn zip-str
  "Convenience function, first seen at nakkaya.com later in clj.zip src"
  [s]
  (zip/xml-zip
   (xml/parse (java.io.ByteArrayInputStream. (.getBytes s)))))

(def ^:const project-name
  "From pom.xml tag: <name>"
  (->> "pom.xml" (slurp) (zip-str) (first) :content
       (filter (fn [elem]
                 (= (:tag elem)
                    (keyword "xmlns.http%3A%2F%2Fmaven.apache.org%2FPOM%2F4.0.0/name"))))
       (map (fn [elem] (->> elem :content first)))
       (first)))

(def environment
  "Mapping env-type -> bot-name"
  {"PROD"  {:bot-name project-name}
   "TEST"  {:bot-name "hokuspokus"}
   "DEVEL" {:bot-name "hokuspokus"}})

(def env-type (en/env :corona-env-type))

(let [env-types (set (keys environment))]
  (if (in? env-types env-type)
    (debug (format "env-type %s is valid" env-type))
    (throw (Exception.
            (format
             "Invalid env-type: %s. It must be an element of %s"
             env-type env-types)))))

(def telegram-token (en/env :telegram-token))

(def port "Needed only in the corona.web" (en/env :port))

(def bot-name (get-in environment [env-type :bot-name]))

(map (fn [env-var-q]
       (debug (format "%s: %s"
                      env-var-q
                      (if-let [v (let [v (eval env-var-q)]
                                   (if (in? ['telegram-token] env-var-q)
                                     (s/replace v #"[a-zA-Z0-9]" "*")
                                     v))]
                        v "<UNDEFINED>"))))
     ['env-type 'telegram-token 'port 'bot-name])

(defn- define-env-predicates
  "Defines vars: env-prod? env-test? env-devel?"
  []
  (let []
    (run! (fn [v]
            (let [symb-v (symbol (format "env-%s?" (s/lower-case v)))]
              (reset-meta! (intern *ns* symb-v (= env-type v))
                           {:const true :tag `Boolean})))
          (keys environment))))

(define-env-predicates)

(def ^:const chat-id "112885364")

(defn calculate-active [{:keys [c r d]}]
  (- c (+ r d)))

(defn per-1e5
  "See https://groups.google.com/forum/#!topic/clojure/nH-E5uD8CY4"
  ([place total-count] (per-1e5 :normal place total-count))
  ([mode place total-count]
   (un/round mode (/ (* place 1e5) total-count))))

(defn calculate-cases-per-100k [case-kw]
  (fn [{:keys [p c r d] :as prm}]
    (if (zero? p)
      0
      (per-1e5 (case case-kw
                 :i (calculate-active prm)
                 :r r
                 :d d
                 :c c)
               p))))

(defn telegram-token-suffix
  "TODO hard coded `recognized-token-suffixes` make the code not portable"
  []
  (let [suffix (.substring telegram-token (- (count telegram-token) 3))
        recognized-token-suffixes (set ["Fq8" "MR8"])]
    (if (in? recognized-token-suffixes suffix)
      suffix
      (throw (Exception.
              (format "TELEGRAM_TOKEN suffix %s must be an element of %s"
                      suffix
                      recognized-token-suffixes))))))

(def project-version-number
  "From META-INF/maven/%s/%s/pom.properties in uberjar; see the depstar plugin"
  (let [url (format "META-INF/maven/%s/%s/pom.properties"
                    project-name project-name)]
    (if-let [props (try (with-open
                          [reader (some-> url (io/resource) (io/reader))]
                          (doto (java.util.Properties.)
                            (.load reader)))
                        (catch NullPointerException ex
                          (if-not env-devel?
                            (error "Can't read resource" url))))]
      (get props "version"))))

(def bot-ver
  (format "%s-%s" project-version-number (en/env :bot-ver)))

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
  (info (format "Requesting json-data from %s ..." url))
  (json/read-json
   (:body (client/get url {:accept :json}))))

(defn encode-cmd [s] (str (if (empty? s) "" "/") s))

(defn encode-pseudo-cmd
  "For displaying e.g. /<command-name>"
  [s parse_mode]
  {:pre (in? ["HTML" "Markdown"] parse_mode)}
  (if (= parse_mode "HTML")
    (let [s (s/replace s "<" "&lt;")
          s (s/replace s ">" "&gt;")]
      s)
    s))

(def ^:const case-params
  ":idx - defines an order in appearance
  :p ~ population
  :c ~ closed cased
  :r ~ recovered cased
  :d ~ deaths
  :i ~ ill, i.e. active cases"
  [
   {:idx 0 :kw :p                :threshold {:inc (int 1e6) :val (int 1e7)}}
   {:idx 1 :kw :c                :threshold {:inc 5000      :val (int 690e3)}}
   {:idx 2 :kw :r :listing-idx 1 :threshold {:inc 2500      :val (int 448e3)}}
   {:idx 3 :kw :d :listing-idx 2 :threshold {:inc 500       :val (int 28e3)}}
   {:idx 4 :kw :i :listing-idx 0 :threshold {:inc 1000      :val (int 169e3)}}
   {:idx 5 :kw :i100k}
   {:idx 6 :kw :r100k}
   {:idx 7 :kw :d100k}
   {:idx 8 :kw :c100k}])

(def ^:const absolute-cases (->> case-params
                                 (filter (fn [m] (in? [1 2 3 4] (:idx m))))
                                 (mapv :kw)))

(def ^:const basic-cases (->> case-params
                              (filter (fn [m] (in? [1 2 3 4 5 6 7 8] (:idx m))))
                              (mapv :kw)))
(def ^:const all-cases (->> case-params
                            (mapv :kw)))

(def ^:const listing-cases-per-100k
  "No listing of :c100k - Closed cases per 100k"
  (->> case-params
       (filter (fn [m] (in? [5 6 7] (:idx m))))
       (mapv :kw)))

(def ^:const listing-cases-absolute
  (->> case-params
       (filter (fn [m] (in? [0 1 2] (:listing-idx m))))
       (sort-by :listing-idx)
       (mapv :kw)))

(defn fmt-date [date]
  (tf/unparse (tf/with-zone (tf/formatter "dd MMM yyyy")
                (t/default-time-zone))
              (tc/from-date date)))

(defn- threshold [case-kw]
  (->> case-params
       (filter (fn [m] (= (:kw m) case-kw)))
       (map :threshold)
       (first)))

(defn min-threshold
  "Countries with the number of cases less than the threshold are grouped into
  \"Rest\"."
  [case-kw]
  (->> case-kw threshold :val))

(defn threshold-increase
  "Case-dependent threshold recalculation increase."
  [case-kw]
  (->> case-kw threshold :inc))

(def ^:const desc-ws
  "A placeholder"
  "")

;; TODO evaluate web services
;; https://sheets.googleapis.com/v4/spreadsheets/1jxkZpw2XjQzG04VTwChsqRnWn4-FsHH6a7UHVxvO95c/values/Dati?majorDimension=ROWS&key=AIzaSyAy6NFBLKa42yB9KMkFNucI4NLyXxlJ6jQ

;; https://github.com/iceweasel1/COVID-19-Germany

(def ^:const api-data-source
  "jhu"

  ;; csbs throws:
  ;; Execution error (ArityException) at cljplot.impl.line/eval34748$fn (line.clj:155).
  ;; Wrong number of args (0) passed to: cljplot.common/fast-max
  #_"csbs")

(def ^:const heroku-host-api-server
  "covid-tracker-us.herokuapp.com"
  #_"coronavirus-tracker-api.herokuapp.com")

(def ^:const api-server (cond (or env-prod? env-test?) heroku-host-api-server
                              :else                    "localhost:8000"))

(def ttl
  "Time to live in (* <minutes> <seconds> <miliseconds>)."
  (* 60 60 1000))

;; TODO reload only the latest N reports. e.g. try one week
(defn memo-ttl
  "E.g.:
  (def foo-fun-memo (co/memo-ttl foo-fun))"
  [fun]
  #_fun
  (memo/ttl fun {} :ttl/threshold ttl))
