(printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.common)

(ns corona.common
  (:require
   [clj-http.client :as client]
   [clj-time.coerce :as ctc]
   [clj-time.core :as ctime]
   [clj-time.format :as ctf]
   [clojure.data.json :as json]
   [clojure.string :as cstr]
   [environ.core :as env]
   [corona.envdef :as envdef]
   [utils.num :as utn]
   [utils.core :as utc :exclude [id]]
   #_[corona.country-codes :as ccc]
   [taoensso.timbre :as timbre :refer [debugf infof
                                       #_errorf]]
   [corona.pom-version-get :as pom]
   [clojure.java.io :as jio]
   ))

;; (set! *warn-on-reflection* true)

(def ^:const project-name envdef/project-name)
(def ^:const undef "<UNDEF>")

(def ^:const ^Long webapp-port (if-let [env-port (env/env :port)]
                                 (read-string env-port)
                                 ;; keep port-nr in sync with README.md
                                 5050))

(def environment envdef/environment)

(def env-type
  "When deving check:
      echo $CORONA_ENV_TYPE
  When testing locally via `heroku local --env=.heroku-local.env` check
  the file .heroku-local.env

  TODO env-type priority could / should be:
  1. command line parameter
  2. some config/env file - however not the .heroku-local.env
  3. environment variable
  "
  (env/env :corona-env-type))

;; TODO use clojure.spec to validate env-type
(let [env-types (set (keys environment))]
  (if (utc/in? env-types env-type)
    (debugf "%s %s is valid" 'env-type env-type)
    (throw (Exception.
            (format
             "Invalid %s: %s. It must be an element of %s"
             'env-type env-type env-types)))))

(def ^:const ^String bot-name        (get-in environment
                                             [env-type :bot-name]))
(def ^:const ^String webapp-server   (get-in environment
                                             [env-type :web-server]))
(def ^:const ^String json-api-server (get-in environment
                                             [env-type :json-server]))

;; forward declarations
(declare env-prod? env-hokuspokus? env-local? env-devel?)

(defn- define-env-predicates
  "Defines vars:
  `env-prod?`, `env-hokuspokus?`, `env-local?`, `env-devel?`"
  []
  (run! (fn [v]
          (let [symb-v (symbol (format "env-%s?" (cstr/lower-case v)))]
            (reset-meta! (intern *ns* symb-v (= env-type v))
                         {:const true :tag `Boolean})))
        (keys environment)))

(define-env-predicates)

(def ^:const ^Boolean on-heroku? (or env-prod? env-hokuspokus?))

(def ^:const ^String telegram-token (env/env :telegram-token))

(defn system-exit [exit-status]
  (debugf "Exiting with status %s ..." exit-status)
  (System/exit exit-status))

(def ^:const chat-id
  "Telegram chat-id."
  "112885364")

(defn calculate-active [{:keys [c r d]}]
  (- c (+ r d)))

(defn calc-rate-active
  [{:keys [a c]}]
  (utn/percentage a c))

(defn calc-rate-recovered
  [{:keys [r c]}]
  (utn/percentage r c))

(defn calc-rate-deaths
  [{:keys [d c]}]
  (utn/percentage d c))

(defn calc-rate-closed
  [{:keys [d r c]}]
  (utn/percentage (+ d r) c))

(defn per-1e5
  "See https://groups.google.com/forum/#!topic/clojure/nH-E5uD8CY4"
  ([place total-count] (per-1e5 :normal place total-count))
  ([mode place total-count]
   (utn/round mode (/ (* place 1e5) total-count))))

(defn calculate-cases-per-100k [case-kw]
  (fn [{:keys [p c r d] :as prm}]
    (if (zero? p)
      0
      (per-1e5 (case case-kw
                 :a (calculate-active prm)
                 :r r
                 :d d
                 :c c)
               p))))

(def pom-version
  (->
   #_(format "META-INF/maven/%s/%s/pom.xml" project-name project-name)
   #_(jio/resource)
   "pom.xml"
   (slurp)
   (pom/parse-xml-str)))

(def botver
  (if-let [commit (env/env :commit)]
    (when (and pom-version commit)
      (format "%s-%s" pom-version commit))
    ;; if-let ... else
    undef))

(defn show-env []
  (mapv (fn [env-var-q]
          (format "%s: %s"
                  env-var-q
                  (let [env-var (eval env-var-q)]
                    (if (or env-var (false? env-var))
                      (if (utc/in? ['corona.common/telegram-token] env-var-q)
                        "<PRESENT>" env-var)
                      ;; if-let ... else
                      undef))))
        ['corona.common/env-type
         'corona.common/on-heroku?
         'corona.common/telegram-token
         'corona.common/webapp-port
         'corona.common/bot-name
         'corona.common/pom-version
         'corona.common/botver]))

#_(debugf "\n  %s" (clojure.string/join "\n  " (show-env)))

;; TODO (System/exit <val>) if some var is undefined

(defn fix-octal-val
  "(read-string \"08\") produces a NumberFormatException - octal numbers
  https://clojuredocs.org/clojure.core/read-string#example-5ccee021e4b0ca44402ef71a"
  [s]
  (cstr/replace s #"^0+" ""))

(defn read-number [v]
  (if (or (empty? v) (= "0" v))
    0
    (-> v fix-octal-val read-string)))

(defn left-pad
  ([s padding-len] (left-pad s "0" padding-len))
  ([s with padding-len]
   (cstr/replace (format (str "%" padding-len "s") s) " " with)))

#_
(defn left-pad [s padding-len]
  (str (cstr/join (repeat (- padding-len (count s)) " "))
       s))

(defn right-pad
  ([s padding-len] (right-pad s " " padding-len))
  ([s with padding-len]
   (str s
        (cstr/join (repeat (- padding-len (count s)) with)))))

(defn get-json [url]
  (infof "Requesting json-data from %s ..." url)
  (let [result (json/read-str
                (:body (client/get url {:accept :json})))]
    (infof "Requesting json-data from %s ... done. %s chars received" (count result))
    result))

(defn encode-cmd [s] (str (if (empty? s) "" "/") s))

(defn encode-pseudo-cmd
  "For displaying e.g. /<command-name>"
  [lexical-token parse_mode]
  {:pre (utc/in? ["HTML" "Markdown"] parse_mode)}
  (let [fun (if (= parse_mode "HTML")
              (comp #(cstr/replace % "<" "&lt;")
                    #(cstr/replace % ">" "&gt;"))
              identity)]
    (fun lexical-token)))

(def ^:const case-params
  ":idx - defines an order in appearance
  :p ~ population
  :c ~ closed cased
  :r ~ recovered cased
  :d ~ deaths
  :a ~ active cases i.e. ill"
  [
   {:idx  0 :kw :p                :threshold {:inc (int 1e6) :val (int 1e7)}}
   {:idx  1 :kw :c                :threshold {:inc 10000     :val (int 1110e3)}}
   {:idx  2 :kw :r :listing-idx 1 :threshold {:inc 5000      :val (int 547e3)}}
   {:idx  3 :kw :d :listing-idx 2 :threshold {:inc 500       :val (int 35e3)}}
   {:idx  4 :kw :a :listing-idx 0 :threshold {:inc 5000      :val (int 429e3)}}
   {:idx  5 :kw :a100k}
   {:idx  6 :kw :r100k}
   {:idx  7 :kw :d100k}
   {:idx  8 :kw :c100k}

   {:idx  9 :kw :a-rate}
   {:idx 10 :kw :r-rate}
   {:idx 11 :kw :d-rate}
   {:idx 12 :kw :c-rate} ;; closed-rate
   ])

(def ^:const absolute-cases (->> case-params
                                 (filter (fn [m] (utc/in? [1 2 3 4] (:idx m))))
                                 (mapv :kw)))

(def ^:const basic-cases (->> case-params
                              (filter (fn [m] (utc/in? [1 2 3 4 5 6 7 8] (:idx m))))
                              (mapv :kw)))
(def ^:const all-cases (->> case-params
                            (mapv :kw)))

(def ^:const ranking-cases [:p :c100k :r100k :d100k :a100k])

(def ^:const listing-cases-per-100k
  "No listing of :c100k - Closed cases per 100k"
  (->> case-params
       (filter (fn [m] (utc/in? [5 6 7] (:idx m))))
       (mapv :kw)))

(def ^:const listing-cases-absolute
  (->> case-params
       (filter (fn [m] (utc/in? [0 1 2] (:listing-idx m))))
       (sort-by :listing-idx)
       (mapv :kw)))

(defn fmt-date-fun [fmts]
  (fn [date]
    (ctf/unparse (ctf/with-zone (ctf/formatter fmts) (ctime/default-time-zone))
                 (ctc/from-date date))))

(def fmt-date
  "(fmt-date (.parse (new java.text.SimpleDateFormat \"MM/dd/yy\")
            \"4/26/20\"))"
  (fmt-date-fun "dd MMM yyyy"))

(def fmt-date-dbg
  "(fmt-date-dbg (.parse (new java.text.SimpleDateFormat \"MM/dd/yy\")
                \"4/26/20\"))"
  (fmt-date-fun "dd.MM."))

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

(def ttl
  "Time to live in (* <hours> <minutes> <seconds> <miliseconds>)."
  (* 3 60 60 1000))

(printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.common)
