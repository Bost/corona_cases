;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.common)

(ns corona.common
  (:require
   [clj-http.client]
   [clj-time.coerce :as ctc]
   [clj-time.core :as ctime]
   [clj-time.format :as ctf]
   [clojure.algo.monads :refer [domonad state-m]]
   [clojure.data.json :as json]
   [clojure.spec.alpha :as spec]
   [clojure.string :as cstr]
   [corona.envdef :as envdef]
   [corona.keywords :refer :all]
   [corona.pom-version-get :as pom]
   [corona.telemetry :refer [env-type add-calc-time debugf defn-fun-id errorf
                             infof system-time warnf]]
   [corona.utils.core :as cutc]
   [environ.core :as env]
   [taoensso.timbre :as timbre]
   [utils.core :as utc]
   [utils.num :as utn]
   )
  (:import
   (java.net URI)
   (java.security MessageDigest)
   (java.text SimpleDateFormat)
   (java.util TimeZone)
   ))

;; (set! *warn-on-reflection* true)

(defn nr-of-days [cnt-reports]
  "Number of days in the plots and the number of reports the calculations are
restricted to. TODO: separate Report\\Day to N-Days and M-Reports. AFAIR the flu
reports are done once a week."
  #_cnt-reports
  #_20
  365)

(def ^:const ^String unknown "?")

(def ^:const undef '<UNDEF>)
(def ^:const present '<PRESENT>)

(spec/def ::port number?)

(def ^:const ^Long webapp-port envdef/webapp-port)
(def ^:const ^Long mockup-port envdef/mockup-port)

(when-not (spec/valid? ::port webapp-port)
  (throw (Exception.
          (spec/explain-str ::port webapp-port))))

(def environment envdef/environment)

(spec/def ::env-type (set (keys environment)))

(when-not (spec/valid? ::env-type env-type)
  (throw (Exception.
          (spec/explain-str ::env-type env-type))))

(spec/def ::fun clojure.core/fn?)

(def ^:const ^String bot-name
  (get-in environment [env-type :bot-name]))

(def ^:const ^String webapp-server
  (get-in environment [env-type :web-server]))

;; TODO: put CORONA_DATABASE_URL to .envrc (or something better)
;; and set it with heroku config:set
(def db-uri (java.net.URI.
             (let [env-var "CORONA_DATABASE_URL"]
               (if-let [uri (System/getenv env-var)]
                 uri
                 (throw (Exception.
                         (format "Undefined environment variable: %s"
                                 env-var)))))))

(def user-and-password
  (if (nil? (.getUserInfo db-uri))
    nil (clojure.string/split (.getUserInfo db-uri) #":")))

(def ^:const ^String dbase
  {:dbtype "postgresql"
   :user (get user-and-password 0)
   :password (get user-and-password 1)
   :host (.getHost db-uri)
   :port (.getPort db-uri)
   :dbname (subs (.getPath db-uri) 1)})

(def ^:const ^String json-servers-v1
  (get-in environment [env-type :json-server :v1]))

(def ^:const ^String json-apis-v1
  ((comp
    #_doall
    (partial mapv (fn [s] (format "http://%s/all" s))))
   json-servers-v1))

(def ^:const ^String json-api-owid
  (get-in environment [env-type :json-server :owid]))

(def ^:const ^String graphs-path "graphs")

;; forward declarations
(declare env-corona-cases? env-hokuspokus? env-local? env-devel?)

(defn- define-env-predicates
  "Defines vars:
  `env-env-corona-cases?`, `env-hokuspokus?`, `env-local?`, `env-devel?`"
  []
  (run! (fn [v]
          (let [symb-v ((comp symbol
                              (partial format "env-%s?")
                              cstr/lower-case
                              name)
                        v)]
            (reset-meta! (intern *ns* symb-v (= env-type v))
                         {:const true :tag `Boolean})))
        (keys environment)))

(define-env-predicates)

(def ^:const ^Boolean use-webhook? (or env-corona-cases? env-hokuspokus?))
(def ^:const ^String telegram-token (env/env :telegram-token))
(def ^:const ^String repl-user      (env/env :repl-user))
(def ^:const ^String repl-password  (env/env :repl-password))

(defn system-exit
  "!!! It looks line it can't be defined by defn-fun-id !!!"
  [exit-status]
  (timbre/debugf "[system-exit] Exiting with status %s ..." exit-status)
  (System/exit exit-status))

(def ^:const chat-id
  "Telegram chat-id."
  "112885364")

(defn calc-closed
  "
  (calc-closed      90 10) => 100
  (calc-closed 1000 90 10) => 1100
  "
  ([         recove deaths] (calc-closed 0 recove deaths))
  ([previous recove deaths] (+ previous
                                 (+ recove deaths))))

(defn calc-active
  "'previous' means 'previous active'
  TODO: shouldn't be (calc-closed previous-deaths recove deaths)

  (calc-active      100 90 10) => 0
  (calc-active 1000 100 90 10) => 1000
  (calc-active    0 100 90 10) => 0
  "
  ([         new-confir recove deaths] (calc-active 0 new-confir recove deaths))
  ([previous new-confir recove deaths] (+ previous
                                            (- new-confir
                                               (calc-closed recove deaths)))))

(defn calc-recov
  "
  (calc-recov previous new-confir deaths)
  E.g.:
  (calc-recov      90 10) => 80
  (calc-recov 1000 90 10) => 1080

  "
  ([         new-confir deaths] (calc-recov 0 new-confir deaths))
  ([previous new-confir deaths] (+ previous
                                     (- new-confir deaths))))

(defn calc-rate-precision-1
  "Returns a helper functions that acts on a hash-map.
  E.g.:
  ((calc-rate-precision-1     kact) {kact 1e3 kpop 1e6 knew 101}) => 990.1
  ((calc-rate-precision-1 200 kact) {kact 1e3 kpop 1e6 knew 101}) => 332.2
  "
  ([         case-kw] (calc-rate-precision-1 0 case-kw))
  ([previous case-kw]
   (fn [prm]
     (utn/round-div-precision (* (case-kw prm) 100.0)
                              (+ previous
                                 (condp = case-kw
                                   kvac (get prm kpop)
                                   (get prm knew)))
                              1))))

(defn calc-rate
  "Returns a helper function that acts on a hash-map.
  E.g.:
  ((calc-rate     kact) {kact 1e3 kpop 1e6 knew 101}) => 990
  ((calc-rate 200 kact) {kact 1e3 kpop 1e6 knew 101}) => 332
  "
  ([         case-kw] (calc-rate 0 case-kw))
  ([previous case-kw]
   (fn [prm]
     (utn/percentage (case-kw prm) (+ previous
                                      (condp = case-kw
                                        kvac (get prm kpop)
                                        (get prm knew)))))))

(defn per-1e5
  "See https://groups.google.com/forum/#!topic/clojure/nH-E5uD8CY4
  dividend = numerator, divisor = denominator
  Ratio 'numerator over denominator' expressed in terms of \"per 100k\".
  E.g.:
  (per-1e5 1 2)   ;; => 50000 ; \"one half\" expressed as \"50k out of 100k\"
  (per-1e5 1 1e5) ;; => 1     ; 0.00001 expressed as \"1 out of 100k\""
  ([     numerator denominator] (per-1e5 :normal numerator denominator))
  ([mode numerator denominator]
   ;; (type 1e5) => java.lang.Double
   (utn/round mode (/ (* numerator 1e5) denominator))))

(defn calc-per-1e5
  ([         case-kw] (calc-per-1e5 0 case-kw))
  ([previous case-kw]
   (fn [prm]
     (let [p (get prm kpop)]
       (if (zero? p)
         (+ previous
            0)
         (per-1e5 (case-kw prm)
                  (+ previous
                     p)))))))

(def botver
  (if-let [commit (env/env :commit)]
    (when (and pom/pom-version commit)
      (format "%s-%s" pom/pom-version commit))
    ;; if-let ... else
    undef))

(defn pr-env [env-var-q]
  ((comp
    (partial format "%s: %s" env-var-q)
    (fn [env-var]
      (cond
        (or env-var (false? env-var))
        (cond
          (utc/in? ['corona.common/telegram-token
                    'corona.common/repl-password] env-var-q)
          present

          (utc/in? ['corona.common/dbase] env-var-q)
          (pr-str (assoc env-var :password present))

          (= env-var undef)
          undef

          :else
          (pr-str env-var))

        :else
        undef))
    eval)
   env-var-q))

(defn show-env
  "TODO: should the spec checking be done here?"
  []
  ((comp
    (partial into
             [(let [prop "java.runtime.version"]
                (format "%s: %s" prop (System/getProperty prop)))])
    (partial mapv pr-env))
   ['corona.telemetry/env-type
    'corona.common/use-webhook?
    'corona.common/telegram-token
    'corona.common/repl-user
    'corona.common/repl-password
    'corona.common/webapp-server
    'corona.common/webapp-port
    'corona.common/bot-name
    'corona.common/botver
    'corona.common/dbase
    'corona.common/json-apis-v1
    'corona.common/json-api-owid]))
;; TODO: (System/exit <val>) if some var is undefined

(defn fix-octal-val
  "(read-string \"08\") produces a NumberFormatException - octal numbers
  https://clojuredocs.org/clojure.core/read-string#example-5ccee021e4b0ca44402ef71a"
  [s]
  (cstr/replace s #"^0+" ""))

(defn read-number [v]
  (if (or (empty? v) (= "0" v))
    0
    ((comp read-string fix-octal-val) v)))

(defn left-pad
  ([s      padding-len] (left-pad s "0" padding-len))
  ([s with padding-len]
   (cstr/replace (format (str "%" padding-len "s") s) " " with)))

(defn right-pad
  ([s      padding-len] (right-pad s " " padding-len))
  ([s with padding-len]
   (str s
        (cstr/join (repeat (- padding-len (count s)) with)))))

(defn hash-fn [data]
  (let [hash-size 6
        algorith "md5" ;; "sha1" "sha-256" "sha-512"
        raw (.digest (MessageDigest/getInstance algorith)
                     (.getBytes (str data)))]
    (-> (format "%x" (BigInteger. 1 raw))
        (subs 0 hash-size))))

(defn my-find-var
  "find-var throws exception "
  [sym]
  (try (find-var sym)
       (catch Exception e
         nil)))

(defn-fun-id retry
  "args and params of fun must correspondent with each other.
   fun must be quoted and namespace-qualified"
  [{:keys [max-attempts attempt fun args]
    :or {attempt 1} :as prm}]
  (let [res
        (do
          (infof "(%s %s) attempt %s of %s"
                 (some-> fun my-find-var meta :name)
                 (pr-str args)
                 attempt
                 max-attempts)
          (try {:value (apply (eval fun) [args])}
               (catch Exception e
                 (errorf "Caught %s" e)
                 (if (= max-attempts attempt)
                   (throw e)
                   {:exception e}))))]
    (if (:exception res)
      (let [sleep-time (+ (* 20 1000) (rand-int (* 5 1000)))]
        (debugf "Sleeping for %s ms ..." sleep-time)
        (Thread/sleep sleep-time)
        (recur (assoc-in prm [:attempt] (inc attempt))))
      (:value res))))

(defn-fun-id get-json-single ""
  [url]
  ((comp
    (fn [s] (json/read-str s :key-fn clojure.core/keyword))
    :body
    (fn [url]
      (let [;; 1.5 minutes
            timeout (int (* 3/2 60 1000))
            prms
            {;; See
             ;; https://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/client/config/RequestConfig.html
             ;; SO_TIMEOUT - timeout for waiting for data or, put
             ;; differently, a maximum period inactivity between two
             ;; consecutive data packets
             :socket-timeout timeout

             ;; timeout used when requesting a connection from the
             ;; connection manager
             :connection-timeout timeout

             ;; timeout until a connection is established
             ;; :connect-timeout timeout
             ;; :debug true
             ;; :debug-body true
             :accept :json}
            ;; tbeg must be captured before the monadic function composition
            init-state {:tbeg (system-time) :acc []}]
        ((comp
          first
          (domonad
           #_identity-m
           state-m
           [recv-data (m-result (clj-http.client/get url prms))
            _ (add-calc-time "recv-data" recv-data)]
           recv-data))
         init-state)))
    #_(fn [url] (infof "from %s ..." url) url))
   url))

(defn-fun-id get-json "Retries `get-json-single` 3 times" [url]
  (try
    (retry {:max-attempts 3 :fun 'corona.common/get-json-single :args url})
    (catch Exception e
      (errorf "Caught %s" e))))

(defn encode-cmd [s] (str (if (empty? s) "" "/") s))

(def ^:const ^String html "HTML")
(def ^:const ^String markdown "Markdown")

(defn encode-pseudo-cmd
  "For displaying e.g. /<command-name>"
  [lexical-token parse_mode]
  {:pre (utc/in? [html markdown] parse_mode)}
  (let [fun (if (= parse_mode html)
              (comp #(cstr/replace % "<" "&lt;")
                    #(cstr/replace % ">" "&gt;"))
              (comp #(cstr/replace % "_" "\\_")
                    #(cstr/replace % "*" "\\*")
                    #(cstr/replace % "`" "\\`")
                    #(cstr/replace % "[" "\\[")))]
    (fun lexical-token)))

;; avoid creating new class each time the `fmt` function is called
(def ^SimpleDateFormat date-format
  "yyyy-MM-dd"
  (let [sdf (new SimpleDateFormat "yyyy-MM-dd")]
    (.setTimeZone sdf (TimeZone/getDefault)) ;; returns nil
    sdf))

(defn parse-date-str
  "(parse-date-str \"2020-12-31\") => #inst \"2020-12-31T00:00:00.000-00:00\""
  [date-str] (.parse date-format date-str))

;; TODO: investigate if update-in saves memory
;; TODO: use conversion functions from corona.common
(def ^SimpleDateFormat raw-date-format
  "MM/dd/yy"
  (let [sdf (new SimpleDateFormat "MM/dd/yy")]
    (.setTimeZone sdf (TimeZone/getDefault)) ;; returns nil
    sdf))

(defn parse-raw-date-str
  "
  (parse-raw-date-str \"4/26/20\") => #inst \"2020-04-26T00:00:00.000-00:00\""
  [date-str] (.parse raw-date-format date-str))

(def ^SimpleDateFormat updated_at-date-format
  "yyyy-MM-dd HH:mm:ss"
  (let [sdf (new SimpleDateFormat "yyyy-MM-dd HH:mm:ss")]
    (.setTimeZone sdf (TimeZone/getDefault)) ;; returns nil
    sdf))

(defn fmt-date-fun
  "Returns a conversion-function"
  [fmts]
  (fn [date]
    (ctf/unparse (ctf/with-zone (ctf/formatter fmts) (ctime/default-time-zone))
                 (ctc/from-date date))))

(def fmt-vaccination-date
  "For conversion e.g. 2021-01-15 -> 1/15/20
  TODO: better name

  (fmt-vaccination-date (parse-raw-date-str \"15/1/20\"))"
  (fmt-date-fun "M/d/yy"))

(def fmt-human-date
  "Human readable
  (fmt-human-date (parse-raw-date-str \"4/26/20\")) => \"26 Apr 20\""
  (fmt-date-fun "dd MMM yy"))

(def fmt-dbg-date
  "For conversion e.g. 4/26/20 -> 26.04.
  (fmt-dbg-date (parse-raw-date-str \"4/26/20\"))"
  (fmt-date-fun "dd.MM."))

(def fmt-sortable-date
  "For conversion e.g. 4/26/20 -> 2020-04-26
  (fmt-sortable-date (parse-raw-date-str \"4/26/20\"))"
  (fmt-date-fun "yyyy-MM-dd"))

(def ^:const desc-ws
  "A placeholder"
  "")

(def ^:const api-data-source
  "jhu"

  ;; csbs throws:
  ;; Execution error (ArityException) at cljplot.impl.line/eval34748$fn (line.clj:155).
  ;; Wrong number of args (0) passed to: cljplot.common/fast-max
  #_"csbs")

(def ^:const ^String bot-name-in-markdown
  (cstr/replace bot-name #"_" "\\\\_"))

(defn log-obj [obj]
  (let [str-obj (str obj)
        separator (cond
                    (.contains str-obj "ManyToManyChannel") "ManyToManyChannel"
                    (.contains str-obj "p_endlessly") "p_endlessly"
                    (.contains str-obj "p_long_polling") "p_long_polling"
                    (.contains str-obj "reset_cache_BANG_") "reset_cache_BANG_"
                    ;; (.contains so "handlers$") "handlers$"
                    ;; (.contains so "@") "@"
                    :else str-obj)]
    (subs str-obj (.indexOf str-obj separator))))

(defn sum
  "E.g.:
  (sum kws hms)
  "
  [kws hms]
  ((comp
    (partial reduce +)
    (partial map (partial cutc/get-in :ks kws)))
   hms))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.common)
