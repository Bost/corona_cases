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
   ))

(def ^:const project-name "corona_cases") ;; see project.clj: defproject

(def token (en/env :telegram-token))
(def port "Needed only in the corona.web" (en/env :port))

(def env-type (en/env :corona-env-type))
(def env-prod?  (= env-type "PROD"))
(def env-test?  (= env-type "TEST"))
(def env-devel? (= env-type "DEVEL"))

(def ^:const chat-id "112885364")
(def bot-name (str "@"
                   (cond
                     env-prod? project-name
                     env-test? "hokuspokus"
                     env-devel? "hokuspokus"
                     :else "undefined")
                   "_bot"))

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
  ;; TODO for logging use monad (Kleisli Category) or taoensso.timber
  (let [tbeg (te/tnow)]
    (info (str "[" tbeg "           " " " bot-ver " /" "get-json " url "]"))
    (let [r (as-> url $
              (client/get $ {:accept :json})
              (:body $)
              (json/read-json $))]
      (info (str "[" tbeg ":" (te/tnow) " " bot-ver " /" "get-json " url "]"))
      r)))

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
   {:idx 1 :kw :c                :threshold {:inc 5000      :val (int 670e3)}}
   {:idx 2 :kw :r :listing-idx 1 :threshold {:inc 2500      :val (int 431e3)}}
   {:idx 3 :kw :d :listing-idx 2 :threshold {:inc 500       :val (int 25e3)}}
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

(defn memo-ttl
  "In (* <minutes> 60 1000)
  TODO reload only the latest N reports
  TODO auto-reload expired cache, don't wait for the next request"
  [fun]
  (memo/ttl fun {} :ttl/threshold (* 60 60 1000)))
