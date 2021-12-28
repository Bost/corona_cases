;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.common)

(ns corona.common
  (:require clj-http.client
            [clj-time.coerce :as ctc]
            [clj-time.core :as ctime]
            [clj-time.format :as ctf]
            [clojure.data.json :as json]
            [clojure.spec.alpha :as spec]
            [clojure.string :as cstr]
            [corona.envdef :as envdef]
            [corona.pom-version-get :as pom]
            [environ.core :as env]
            [corona.macro :refer [defn-fun-id debugf infof warnf errorf]]
            [taoensso.timbre :as timbre]
            [utils.core :as utc]
            [clj-memory-meter.core :as meter]
            [utils.num :as utn]
            [clojure.algo.monads
             :refer
             [
              monad
              domonad with-monad
              identity-m ;; sequence-m maybe-m writer-m m-lift
              state-m
              ]])
  (:import java.security.MessageDigest
           java.net.URI))

;; (set! *warn-on-reflection* true)

(defn nr-of-days [cnt-reports]
  "Number of days in the plots and the number of reports the calculations are
restricted to. TODO separate Report\\Day to N-Days and M-Reports. AFAIR the flu
reports are done once a week."
  cnt-reports
  #_365)

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
  ((comp keyword cstr/lower-case :corona-env-type) env/env))

(when-not (spec/valid? ::env-type env-type)
  (throw (Exception.
          (spec/explain-str ::env-type env-type))))

(spec/def ::fun clojure.core/fn?)

(def ^:const ^String bot-name
  (get-in environment [env-type :bot-name]))

(def ^:const ^String webapp-server
  (get-in environment [env-type :web-server]))

(def db-uri (java.net.URI.
             (let [env-var "DATABASE_URL"]
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

(defn system-time [] (System/currentTimeMillis))

(defn-fun-id measure "" [object & prm]
  (try (apply (partial meter/measure object) prm)
       (catch java.lang.reflect.InaccessibleObjectException e
         (warnf (str "Caught %s. Returning count of chars.") e)
         ((comp count str) object))))

(defn format-bytes
  "Nicely format `num-bytes` as kilobytes/megabytes/etc.
    (format-bytes 1024) ; -> 2.0 KB
  See
  https://github.com/metabase/metabase
  metabase.util/format-bytes "
  [num-bytes]
  (loop [n num-bytes [suffix & more] ["B" "kB" "MB" "GB"]]
    (if (and (seq more)
             (>= n 1024))
      (recur (/ n 1024) more)
      (format "%.1f %s" (float n) suffix))))

(defn add-calc-time
  "Returns a state-monad function that assumes the state to be a map"
  [fun-id plain-val]
  (fn [state]
    (let [accumulator (get state :acc)
          time-begin (get state :tbeg)
          calc-time (- (system-time) (+ (apply + accumulator) time-begin))]
      (timbre/debugf
       "[%s] %s obtained in %s ms. Available heap %s"
       fun-id (if (nil? plain-val) "nil-value" (measure plain-val))
       calc-time
       ((comp
         format-bytes
         (fn [{:keys [size max free]}] (+ (- max size) free)))
        (let [runtime (Runtime/getRuntime)]
          {:size (.totalMemory runtime) ;; current size of heap in bytes

           ;; max size of heap in bytes. The heap cannot grow beyond this size. Any
           ;; attempt will result in an OutOfMemoryException.
           :max (.maxMemory runtime)

           ;; amount of free memory within the heap in bytes. This size will increase
           ;; after garbage collection and decrease as new objects are created.
           :free (.freeMemory runtime)})))
      ((domonad state-m [mvv (m-result plain-val)] mvv)
       (update-in state [:acc]
                  ;; the acc-value is ignored so `comp` can't be used
                  (fn [_] (vec (concat accumulator (vector calc-time)))))))))

(defn system-exit
  "!!! It looks line it can't be defined by defn-fun-id !!!"
  [exit-status]
  (timbre/debugf "[system-exit] Exiting with status %s ..." exit-status)
  (System/exit exit-status))

(def ^:const chat-id
  "Telegram chat-id."
  "112885364")

(defn calc-closed [recovered deaths]
  (+ recovered deaths))

(defn calc-active [new-confirmed recove deaths]
  (- new-confirmed
     (calc-closed recove deaths)))

(defn calc-recov [new-confirmed deaths]
  (- new-confirmed deaths))

(defn calc-closed [recovered deaths]
  (+ recovered deaths))

(defn calc-closed [recovered deaths]
  (+ recovered deaths))

;; k as in keyword
(def kabs "absolute" #_:α :abs)
(def ksum "sum" #_:Σ #_:σ :sum)
(def krnk "ranking = σειρά κατάταξης [seirá katátaxis]"
  #_:ξ :rnk)
(def kest "estimate/assessment = εκτίμηση [ektímisi]"
  #_:ε :est)
(def krep "reported"
  #_:ρ :rep)
(def k1e5 "per 100 0000"
  #_:κ :1e5)
(def k%%% "percent = τοις εκατό [tois ekató]"
  #_:τ :%%%)
(def kls7 "last 7"
  #_:7 :ls7)
(def kavg "average = arithmetic mean"
  #_:ø :avg)
(def kchg "change"
  #_:Δ :chg)
(def kmax "maximum = μέγιστο [mégisto]"
  #_:μ :max)

(def kcco "country code"
  :cco)
(def ktst "timestamp"
  :t)
(def kpop "population"
  :p)
(def kvac "vaccinated"
  :v)
(def kact "active"
  :a)
(def krec "recovered"
  :r)
(def kdea "deaths"
  :d)
(def knew "new confirmed"
  :n)
(def kclo "closed"
  :c)

(def ka1e5 :a1e5)
(def kr1e5 :r1e5)
(def kc1e5 :c1e5)
(def kd1e5 :d1e5)
(def kv1e5 :v1e5)
(def ker_  :er)
(def kea_  :ea)

(def kcase-kw
  #_:case-kw
  :ckw)

(def klense-fun
  :lense-fun)

(defn calc-rate-precision-1 [case-kw]
  (fn [prm]
    (let [p (get prm kpop)
          n (get prm knew)]
      (utn/round-div-precision (* (case-kw prm) 100.0)
                               (condp = case-kw
                                 kvac p
                                 n)
                               1))))

(defn calc-rate [case-kw]
  (fn [prm]
    (let [p (get prm kpop)
          n (get prm knew)]
      (utn/percentage (case-kw prm) (condp = case-kw
                                      kvac p
                                      n)))))

(defn per-1e5
  "See https://groups.google.com/forum/#!topic/clojure/nH-E5uD8CY4"
  ([place total-count] (per-1e5 :normal place total-count))
  ([mode place total-count]
   (utn/round mode (/ (* place 1e5) total-count))))

(defn calc-per-1e5 [case-kw]
  (fn [prm]
    (let [p (get prm kpop)]
      (if (zero? p)
        0
        (per-1e5 (case-kw prm)
                 p)))))

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
  "TODO should the spec checking be done here?"
  []
  ((comp
    (partial into
             [(let [prop "java.runtime.version"]
                (format "%s: %s" prop (System/getProperty prop)))])
    (partial mapv pr-env))
   ['corona.common/env-type
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
;; TODO (System/exit <val>) if some var is undefined

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
  ([s padding-len] (left-pad s "0" padding-len))
  ([s with padding-len]
   (cstr/replace (format (str "%" padding-len "s") s) " " with)))

(defn right-pad
  ([s padding-len] (right-pad s " " padding-len))
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
           [data (m-result (clj-http.client/get url prms))
            _ (add-calc-time "data" data)]
           data))
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

(defn fmt-date-fun [fmts]
  (fn [date]
    (ctf/unparse (ctf/with-zone (ctf/formatter fmts) (ctime/default-time-zone))
                 (ctc/from-date date))))

(def fmt-vaccination-date
  "(fmt-date (.parse (new java.text.SimpleDateFormat \"MM/dd/yy\")
            \"4/26/20\"))
  E.g.:
  2021-01-15 -> 1/15/20"
  (fmt-date-fun "M/d/yy"))

(def fmt-date
  "(fmt-date (.parse (new java.text.SimpleDateFormat \"MM/dd/yy\")
            \"4/26/20\"))"
  (fmt-date-fun "dd MMM yy"))

(def fmt-date-dbg
  "(fmt-date-dbg (.parse (new java.text.SimpleDateFormat \"MM/dd/yy\")
                \"4/26/20\"))"
  (fmt-date-fun "dd.MM."))

(def ^:const desc-ws
  "A placeholder"
  "")

(def ^:const api-data-source
  "jhu"

  ;; csbs throws:
  ;; Execution error (ArityException) at cljplot.impl.line/eval34748$fn (line.clj:155).
  ;; Wrong number of args (0) passed to: cljplot.common/fast-max
  #_"csbs")

(def ttl
  "Time to live in (* <hours> <minutes> <seconds> <milliseconds>)."
  (* 3 60 60 1000))

(def ^:const ^String bot-name-in-markdown
  (cstr/replace bot-name #"_" "\\\\_"))

(defn fmap
  "See clojure.algo.generic.functor/fmap"
  [f m]
  (into (empty m) (for [[k v] m] [k (f v)])))

(defn-fun-id heap-info
  "See https://github.com/metrics-clojure/metrics-clojure"
  []
  ((comp
    (fn [v] (debugf "%s" v)) ;; debugf is a macro
    (partial fmap format-bytes))
   (let [runtime (Runtime/getRuntime)]
     {:size (.totalMemory runtime) ;; current size of heap in bytes

      ;; max size of heap in bytes. The heap cannot grow beyond this size. Any
      ;; attempt will result in an OutOfMemoryException.
      :max (.maxMemory runtime)

      ;; amount of free memory within the heap in bytes. This size will increase
      ;; after garbage collection and decrease as new objects are created.
      :free (.freeMemory runtime)})))

(defn log-obj [obj]
  (let [so (str obj)
        separator (cond
                    (.contains so "ManyToManyChannel") "ManyToManyChannel"
                    (.contains so "p_endlessly") "p_endlessly"
                    (.contains so "p_long_polling") "p_long_polling"
                    (.contains so "reset_cache_BANG_") "reset_cache_BANG_"
                    ;; (.contains so "handlers$") "handlers$"
                    ;; (.contains so "@") "@"
                    :else so)]
    (subs so (.indexOf so separator))))

(defn sum [kws hms]
  ((comp
    (partial reduce +)
    (partial map (fn [hm] (get-in hm kws))))
   hms))

(defn makelense [& case-kws] (apply vector case-kws))

#_{
 [kact kabs] (makelense kact kest kabs)
 [kact k1e5] (makelense kact kest k1e5)

 [krec kabs] (makelense krec kest kabs)
 [krec k1e5] (makelense krec kest k1e5)

 [kclo kabs] (makelense kclo kest kabs)
 [kclo k1e5] (makelense kclo kest k1e5)

 [kdea kabs] (makelense kdea krep kabs)
 [kdea k1e5] (makelense kdea krep k1e5)

 [kvas kabs] (makelense kvac krep kabs)
 [kvas k1e5] (makelense kvac krep k1e5)

 [kpop kabs] (makelense kpop krep kabs)
 }

(def lense-map
  {kact  (makelense kact kest kabs)  ;; can be only estimated
   ka1e5 (makelense kact kest k1e5)
   krec  (makelense krec kest kabs)    ;; can be only estimated
   kr1e5 (makelense krec kest k1e5)
   kclo  (makelense kclo kest kabs)    ;; can be only estimated
   kc1e5 (makelense kclo kest k1e5)
   kdea  (makelense kdea krep kabs)    ;; reported
   kd1e5 (makelense kdea krep k1e5) ;; reported

   knew  (makelense knew krep kabs)

   kv1e5 (makelense kvac krep kabs) ;; reported
   kpop  (makelense kpop) ;; TODO population can be also estimated and reported i.e. absolute
   })

(def lense-map-with-strings
  (into lense-map
        {:s (makelense :es) ;; :s is a string - could be used for translations
         }))

(defn getlense-map
  [m kw]
  ((comp
    (partial apply get m)
    ;; second element is for `not-found` parameter of `get`
    (fn [kw] [kw (makelense kw krep kabs)]))
   kw))

(defn basic-lense [kw]
  (getlense-map lense-map-with-strings kw))

(def stats-for-country-case--lense-map
  (conj
   lense-map
   {
    ker_ (basic-lense krec)
    kea_ (basic-lense kact)
    }))

(defn ranking-lense
  [kw]
  (conj (getlense-map lense-map kw) krnk))

(defn identity-lense "" [kw]
  ((comp
    vector)
   kw))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.common)
