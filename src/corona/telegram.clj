;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.telegram)

(ns corona.telegram
  (:gen-class)
  (:require
   [clojure.algo.monads :refer [domonad m-result state-m]]
   [clojure.core.async :as async]
   [clojure.data];; for clojure.data/diff
   [clojure.inspector :as insp :refer [inspect-table inspect-tree]]
   [clojure.string :as cstr]
   [corona.api.cache :as cache]
   [corona.api.expdev07 :as data]
   [corona.api.owid :as vac]
   [corona.api.v1 :as v1]
   [corona.cases :as cases]
   [corona.commands :as cmd]
   [corona.countries :as ccr]
   [corona.country-codes :as ccc]
   [corona.estimate :as est]
   [corona.keywords :refer :all]
   [corona.lang :as lang]
   [corona.models.dbase :as dbase]
   [corona.models.migration]
   [corona.msg.graph.plot :as plot]
   [corona.msg.text.common :as msgc]
   [corona.msg.text.details :as msgi]
   [corona.msg.text.lists :as msgl]
   [corona.msg.text.messages :as msg]
   [corona.common :as com]
   [corona.telemetry :as telemetry]
   [corona.telemetry :refer [add-calc-time debugf defn-fun-id fatalf heap-info
                             infof measure system-ok? system-time warnf]]
   [corona.utils.core :as cutc]
   [morse.api :as morse]
   [morse.handlers :as moh]
   [morse.polling :as mop]
   [taoensso.timbre :as timbre]
   [utils.core :as utc]
   [utils.debug :as utd :refer [dbg]]
   )
  (:import
   (java.time Instant LocalDateTime ZoneId)
   ))

;; (set! *warn-on-reflection* true)

(defonce continue (atom true))

(defonce initialized
  ^{:doc
    "For interactive development.
A particular state of the cache and webhook can be used to signal
successful initialization instead of setting it to:
    (swap! initialized (fn [_] true))"}
  (atom nil))

(defonce telegram-port (atom nil))

(defn-fun-id wrap-in-hooks
  "See also https://github.com/MichaelDrogalis/dire

  Add pre- and post-hooks / advices around `main-fun`.

  The first argument of the post-hook is the return-value from the `main-fun`,
  i.e. it is an one extra argument! Examples see utils.core/wrap-in-hooks"
  [{:keys [pre post]} fun]
  (fn [& args]
    (apply pre args)
    (let [result (apply fun args)]
      (apply post (cons result args)))))

;; logging alternatives - see also method advising (using multimethod):
;; https://github.com/camsaul/methodical
;; https://github.com/technomancy/robert-hooke

(defn-fun-id create-cmds "" [cmds]
  (map
   (fn [m]
     (let [{name :name fun :fun} m]
       ((comp
         (partial moh/command-fn name)
         (partial wrap-in-hooks
                  {:pre (fn [& args]
                          (let [chat-prm (:chat (first args))]
                            (infof ":pre /%s chat %s" name chat-prm)
                            (cond
                              (= name lang/start)
                              (when-not (dbase/chat-exists? chat-prm)
                                (dbase/insert-chat! chat-prm)))))
                   :post (fn [& args]
                           (let [[fn-result {:keys [chat]}] args]
                             (infof ":post /%s chat %s" name chat)
                             fn-result))})
         fun
         :id
         (fn [m]
           (debugf "1: %s" m)
           (when (nil? m)
             (throw (new Exception "No :chat defined"))) m)
         :chat
         (fn [m] (debugf "0: %s" m) m))
        m)))
   cmds))

(defn-fun-id create-callbacks
  "For the Sum and Aggregation buttons"
  [funs]
  (map
   (comp
    moh/callback-fn
    (partial
     wrap-in-hooks
     {:pre (fn [& args]
             (let [{:keys [data message]} (first args)]
               (infof ":pre data %s chat %s" data (:chat message))))
      :post (fn [& args]
              (let [[fn-result {:keys [data message]}] args]
                (infof ":post data %s chat %s" data (:chat message))
                fn-result))}))
   funs))

(defn-fun-id create-handlers
  "Receiving incoming updates using long polling (getUpdates method)
  https://en.wikipedia.org/wiki/Push_technology#Long_polling
  An Array of Update-objects is returned."
  []
  (let [callbacks (create-callbacks [msg/worldwide-plots])]
    (debugf "%s callback(s) created" (count callbacks))
    (let [commands (create-cmds cmd/all-handlers)]
      (debugf "%s command(s) created" (count commands))
      (infof "Registering %s chatbot command(s) and %s callback(s) ..."
             (count commands) (count callbacks))
      #_(infof "Registering %s chatbot command(s) and %s callback(s) ..."
             (count commands) (count callbacks))
      (into callbacks commands))))

(declare tgram-handlers)

(when com/use-webhook?
  (timbre/debugf "Defining tgram-handlers at compile time ...")
  (moh/apply-macro moh/defhandler tgram-handlers (create-handlers))
  (timbre/debugf "Defining tgram-handlers at compile time ... done"))

(defn webhook-url [telegram-token]
  (format "%s/%s" com/webapp-server telegram-token))

(defn-fun-id setup-webhook "" [telegram-token]
  ;; (debugf "telegram-token: %s" telegram-token)
  (let [webbook-info-result ((comp
                              :result
                              :body
                              morse/get-info-webhook)
                             telegram-token)
        pending-updates (:pending_update_count webbook-info-result)
        webhook-set? (if telegram-token
                       ((comp
                         seq ;; (seq x) is idiom for (not (empty? x))
                         :url)
                        webbook-info-result))]
    (if (pos? pending-updates)
      (warnf "Found %s pending-update(s). Consider dropping them using:\n   %s"
             pending-updates
             (str
              "curl --request GET "
              "--form \"drop_pending_updates=true\" "
              "\"https://api.telegram.org/bot$TELEGRAM_TOKEN/deleteWebhook\"")))
    (if com/use-webhook?
      (if webhook-set?
        (debugf "Condition 'webhook-set' satisfied. Do nothing.")
        ((comp
          (fn [v] (debugf "morse/set-webhook returned: %s" v))
          :body
          (partial morse/set-webhook telegram-token)
          webhook-url
          (fn [v]
            (if v
              v
              (fatalf
               (str
                "Can't call morse/set-webhook. Undefined telegram-token."
                "TODO: terminate when telegram-token is undefined.")))))
         telegram-token))
      (if webhook-set?
        ((comp
          (fn [v] (debugf "morse/del-webhook returned: %s" v) v)
          :body
          morse/del-webhook
          (fn [v]
            (if v
              v
              (fatalf
               (str
                "Can't call morse/del-webhook. Undefined telegram-token"
                "TODO: terminate when telegram-token is undefined.")))))
         telegram-token)
        (debugf "Condition 'webhook-not-set' satisfied. Do nothing.")))))

(defn api-error-handler [] (when com/env-corona-cases? (com/system-exit 2)))

(defn-fun-id start-polling
  "Receiving incoming updates using long polling (getUpdates method)
  https://en.wikipedia.org/wiki/Push_technology#Long_polling
  An Array of Update-objects is returned.

  Starts long-polling process.
  Handler is supposed to process immediately, as it will be called in a blocking
  manner."
  [token handler]
  (let [opts {}
        channel (async/chan)]
    (debugf "Started channel %s" (com/log-obj channel))
    (let [producer (mop/create-producer channel token opts api-error-handler)]
      (infof "Created producer %s on channel %s"
             (com/log-obj producer) (com/log-obj channel))
      (debugf "Creating consumer for produced %s with handler %s ..."
                producer handler)
      (let [consumer (mop/create-consumer producer handler)]
        (infof "Created consumer %s for producer %s with handler %s"
               (com/log-obj consumer) (com/log-obj producer)
               (com/log-obj handler))
        channel))))

(defn-fun-id long-polling
  "See also 'Bot freezes for unknown reasons' issue
 https://github.com/Otann/morse/issues/32"
  [tgram-token]
  (infof "Starting ...")
  (debugf "Calling (create-handlers)")
  (let [handlers (create-handlers)]
    (debugf "handlers %s" handlers)
    ;; activate handling
    (if-let [polling-handlers (apply moh/handlers (create-handlers))]
      (do
        (debugf "Created polling-handlers %s" (com/log-obj polling-handlers))
        (let [port (start-polling tgram-token polling-handlers)]
          (swap! telegram-port (fn [_] port))
          (let [retval-async<!! (async/<!! port)]
            (warnf "Taking vals on port %s stopped with retval-async<! %s"
                   (com/log-obj port) (if-let [v retval-async<!!] v "nil"))
            (fatalf "Further requests may NOT be answered!!!")
            (api-error-handler))))
      (fatalf "polling-handlers not created")))
  (infof "Starting ... done"))

(defn-fun-id endlessly
  "Invoke fun and put the thread to sleep for millis in an endless loop."
  [fun time-to-live]
  (infof "Starting ...")
  (while @continue
    (infof "Next eval of %s scheduled at %s"
           (com/log-obj fun)
           (LocalDateTime/ofInstant
            (Instant/ofEpochMilli (+ (System/currentTimeMillis) time-to-live))
            (ZoneId/of telemetry/zone-id)))
    (Thread/sleep time-to-live)
    (fun))
  (warnf "Starting ... done. Data will NOT be updated!"))

(defn gc "Garbage collection" []
  (do
    (timbre/debugf "(System/gc)")
    (System/gc) ;; also (.gc (Runtime/getRuntime))
    (Thread/sleep 100)))

(defn make-sortable
  "E.g.:
  (make-sortable \"10/4/22\")           => \"2021-12-04\"
  (make-sortable (keyword \"1/22/20\")) => \"2020-01-22\"
  "
  [raw-date]
  ((comp com/fmt-sortable-date
         com/parse-raw-date-str
         (fn [s] (subs s 1))
         str)
   raw-date))

;; TODO: inspect the full-json-owid keys :OWID_SAM :OWID_UMC :OWID_KOS etc.

;; TODO: use types for the filter-owid and filter-v1 functions (and others)
(defn filter-owid
  "Return reports with :date value among desired-dates

  (filter-owid desired-dates full-json-owid)"
  [desired-dates full-json-owid]
  ((comp
    (partial reduce into {})
    (partial map
             (fn [[k v]]
               (hash-map
                k
                (update-in
                 v [:data]
                 (partial filter
                          (comp (partial utc/in? desired-dates) :date)))))))
   full-json-owid))

(defn filter-v1
  "Return reports with :date value among desired-dates
  E.g.:
  (filter-v1 desired-raw-dates full-json-v1)"
  [desired-raw-dates full-json-v1]
  (def desired-raw-dates desired-raw-dates)
  (def full-json-v1 full-json-v1)

  ((comp
    (partial reduce into {})
    (partial map
             (fn [[k v]]
               (hash-map
                k
                (update-in
                 v [:locations]
                 (partial map
                          (partial cutc/update-in :ks [:history]
                                   :f (partial cutc/select-keys
                                               :ks desired-raw-dates))))))))
   full-json-v1))

(defn equal-at-index?
  "backwards means 0 is the last report, 1 is one before the last one, etc.
  Range is 0 ... 365, i.e. (max (com/nr-of-days nil) est/max-shift)
  "
  [eo en idx-backwards]
  (printf "idx-backwards %s\n" idx-backwards)
  ((comp
    #_(partial apply =)
    (fn [[neo nen]]
      ;; (def neo neo)
      ;; (def nen nen)
      (if-let [are-equal? (= neo nen)]
        are-equal?
        (do
          (let [ccode "DE"]
            (def de-neo (filter (fn [m] (= (kcco m) ccode)) neo))
            (def de-nen (filter (fn [m] (= (kcco m) ccode)) nen)))
          (clojure.data/diff de-neo de-nen)
          false)))
    (partial map (comp
                  (partial sort-by kcco)
                  (partial mapv
                           (fn [[ccode hms]]
                             (let [idx
                                   (dec
                                    (- (count hms)
                                       (min
                                        (dec
                                         (max (com/nr-of-days nil)
                                              est/max-shift))
                                        idx-backwards)))]
                               #_
                               (printf "ccode: %s, (count hms) %s, idx %s\n"
                                       ccode (count hms) idx)
                               (nth hms idx)))))))
   [eo en]))

(defn estimations-equal?
  "Test if the old and new estimations are equal. Test the range boundaries 0,
    max-coll-idx and some 5 random indexes from this range"
  [eo en]
  ((comp
    (partial every? true?)
    (partial map (fn [i]
                   (let [eq-result (equal-at-index? eo en i)]
                     (printf "(equal-at-index? eo en %s): %s\n"
                             i eq-result)
                     eq-result))))
   (let [max-coll-idx (dec (- (com/nr-of-days nil) est/max-shift))]
     (into [0 max-coll-idx]
           (repeatedly 5 (fn [] (inc (rand-int (dec max-coll-idx)))))))))

;;; Split raw-dates-v1 json-v1 json-owid to years, calculate estimates
;;; for every part and combine them, i.e. define a monoidal plus operation
;;;
;;; It seems like specifying narrowing down the raw-dates-v1 should do the job.
;;; Count the days and stop when having all requested. (Is it possible to use
;;; continuations for this?)

(defn-fun-id calc-cache!
  "
(calc-cache! aggregation-hash json-owid json-v1)

TODO: regarding garbage collection - see object finalization:
https://clojuredocs.org/clojure.core/reify#example-60252402e4b0b1e3652d744c"
  [aggregation-hash json-owid json-v1]
  (def aggregation-hash aggregation-hash)
  (def json-owid json-owid)
  (def json-v1 json-v1)

  (let [;; tbeg must be captured before the function composition
        init-state {:tbeg (system-time) :acc []}]
    ((comp
      first ;; just extract the value out of the monad
      (domonad
       state-m
       [
;;; Split raw-dates-v1 json-v1 json-owid to years, calculate estimates
;;; for every part and combine them, i.e. define a monoidal plus operation
;;;
;;; It seems like specifying narrowing down the raw-dates-v1 should do the job.
;;; Count the days and stop when having all requested. (Is it possible to use
;;; continuations for this?)


        all-raw-dates-v1     ((comp m-result data/raw-dates) json-v1)

        ;; TODO: test for negative and too large n-first-days-to-drop
        n-first-days-to-drop
        ;; reports until 2022-09-??, including.
        ;; number of reports/days to reduce to a sum
        ((comp m-result)
         0
         #_(- (count all-raw-dates-v1)
            (max (com/nr-of-days nil)
                 est/max-shift)))

        desired-raw-dates-v1 ((comp m-result
                                    (partial drop n-first-days-to-drop))
                              all-raw-dates-v1)
        desired-dates-v1     ((comp m-result
                                    (partial map make-sortable))
                              desired-raw-dates-v1)

        all-dates-owid ((comp
                         m-result
                         (partial map (fn [m] (get-in m [:date])))
                         (fn [m] (get-in m [:ITA :data])))
                        json-owid)

        desired-dates-both ((comp
                             m-result
                             (partial filter (partial utc/in? all-dates-owid)))
                            desired-dates-v1)
        desired-raw-dates-both ((comp
                                 m-result
                                 (partial
                                  map
                                  (comp
                                   keyword
                                   com/fmt-vaccination-date
                                   com/parse-date-str)))
                                desired-dates-both)

        desired-json-owid ((comp m-result
                                 (partial filter-owid desired-dates-both ))
                           json-owid)
        desired-json-v1   ((comp m-result
                                 (partial filter-v1 desired-raw-dates-both ))
                           json-v1)
        cnt-reports        ((comp m-result count) desired-dates-both)

        estim ((comp
                m-result
                #_
                (fn [m]
                  (clojure.pprint/pprint
                   ((comp
                     (partial sort-by kcco)
                     (partial mapv (fn [[_ hms]] (last hms))))
                    m)
                   (clojure.java.io/writer est/last-estim-edn-file))
                  m)
                (partial est/estimate est/zero-sum)
                (fn [p] (def pd p) p)
                (partial v1/pic-data cnt-reports)
                (fn [p] (def d p) p)
                data/data-with-pop)
               desired-raw-dates-both
               #_desired-raw-dates-v1
               desired-dates-both desired-json-v1 desired-json-owid)
        _ (add-calc-time "estim" estim)

;;; TODO: test what's gonna happen if the first-date and last-date are the same!
        last-date    ((comp m-result
                            com/parse-date-str
                            last (partial sort-by ktst)) desired-dates-both)
        ;; first-date   ((comp m-result first (partial sort-by ktst)) desired-dates-both)

        stats-countries
        ((comp
          m-result
          (partial filter (fn [hm] (= (ktst hm) last-date))))
         estim)
        _ (add-calc-time "stats-countries" stats-countries)

        ;; garbage-coll (m-result (gc))
        ;; _ (add-calc-time "garbage-coll" garbage-coll)

        _ (m-result
           ;; TODO: don't exec all-ccode-messages when (< cnt-reports 10)
           (when (< cnt-reports 10)
             (warnf "Some stuff may not be calculated. Too few %s: %s"
                    'cnt-reports cnt-reports)))

        header (m-result (msgc/header com/html last-date))
        footer (m-result (msgc/footer com/html true))

        all-calc-listings
        (let [prm
              (assoc {:cnt-reports cnt-reports}
                     kcco (ccr/get-country-code ccc/worldwide))]
          ((comp
            m-result doall
            (partial
             map
             (partial
              apply
              msgl/calc-listings!
              stats-countries header footer basic-lense prm)))
           [[cases/listing-cases-absolute 'corona.msg.text.lists/absolute-vals]
            [cases/listing-cases-per-1e5 'corona.msg.text.lists/per-1e5]]))
        _ (add-calc-time "all-calc-listings" all-calc-listings)

        garbage-coll (m-result (gc))
        _ (add-calc-time "garbage-coll" garbage-coll)

        rankings
        ((comp
          m-result)
         (msgi/all-rankings stats-countries))
        _ (add-calc-time "rankings" rankings)

        ;; garbage-coll (m-result (gc))
        ;; _ (add-calc-time "garbage-coll" garbage-coll)

        dates ((comp m-result data/dates) desired-raw-dates-both)

        all-ccode-messages
        ;; pmap 16499ms, map 35961ms
        (let [sorted-estim (sort-by ktst estim)]
          ((comp
            m-result
            (fn [v] (def acm v) v)
            doall
            (partial
             map ;; pmap is faster however it eats too much memory
             (fn [ccode]
               ((comp
                 (fn [v]
                   (debugf "%s messages created. ccode %s" (count v) ccode)
                   v)
                 (partial map (partial apply cache/cache!)))
                [[(fn []
                    (msgi/mexxage
                     ccode estim dates rankings cnt-reports header footer))
                  (msgi/message-kw ccode)]
                 [(fn []
                    (plot/mezzage
                     cnt-reports ccode sorted-estim last-date cnt-reports))
                  (plot/message-kw ccode)]]))))
           ;; here also "ZZ" worldwide messages
           ccc/all-country-codes))
        _ (add-calc-time "all-ccode-messages" all-ccode-messages)

        garbage-coll (m-result (gc))
        _ (add-calc-time "garbage-coll" garbage-coll)

        thresholds
        (let [norm-ths (dbase/get-thresholds)]
          ((comp
            m-result
            (fn [p] (def tn p) p)
            (partial concat norm-ths)
            flatten
            (partial map (fn [case-kw]
                           (filter (comp (partial = case-kw) :kw)
                                   cases/threshold-defaults)))
            (fn [ts]
              (clojure.set/difference
               (set (map :kw cases/threshold-defaults))
               (set (map :kw ts)))))
           norm-ths))

        all-aggregations
        ((comp
          m-result doall
          ;; map is faster than pmap!!!
          ;; 1. map 4100ms, pmap 8737ms
          ;; 2. map 3982ms
          ;; 3. map 3779ms
          (partial map
                   (partial apply
                            plot/aggregation!
                            thresholds estim last-date cnt-reports
                            aggregation-hash)))
         cases/cartesian-product-all-case-types)
        _ (add-calc-time "all-aggregations" all-aggregations)

        ;; discard the intermediary results, i.e. keep only those items in the
        ;; cache which contain the final results.
        calc-result
        (m-result
         (swap! cache/cache
                (fn [_]
                  (conj
                   {:v1   {:json-hash (get-in @cache/cache [:v1   :json-hash])}}
                   {:owid {:json-hash (get-in @cache/cache [:owid :json-hash])}}
                   (select-keys
                    @cache/cache [:plot :msg klist :threshold :dbg])))))

        garbage-coll (m-result (gc))
        _ (add-calc-time "garbage-coll" garbage-coll)
        ]
       #_raw-dates-v1
       calc-result))
     init-state)))

(defn-fun-id json-changed! "" [{:keys [json-fn cache-storage]}]
  ;; TODO: spec: cache-storage must be vector; json-fns must be function
  (let [json (json-fn)
        hash-kws (conj cache-storage :json-hash)
        old-hash (get-in @cache/cache hash-kws)
        new-hash (com/hash-fn json)
        hash-changed (not= old-hash new-hash)]
    (debugf "%s; old hash %s; new hash %s; hash-changed: %s"
            cache-storage old-hash new-hash hash-changed)
    (when hash-changed
      (swap! cache/cache update-in hash-kws (fn [_] new-hash))
      #_(do (gc) (heap-info)))
    {:json json :hash-changed hash-changed}))

(defn-fun-id reset-cache! "" []
  ;; full cache cleanup is not really necessary
  #_(swap! cache/cache (fn [_] {}))
  ((comp
    (partial
     apply
     (fn [hm-owid hm-v1]
       (let [any-json-changed ((comp
                                (partial some boolean)
                                (partial map :hash-changed))
                               [hm-owid hm-v1])]
         (debugf "any-json-changed %s" any-json-changed)
         (when any-json-changed
           #_(calc-cache! (cache/aggregation-hash)
                        json-v1
                        json-owid)
           (let [;; tbeg must be captured before the function composition
                 init-state {:tbeg (system-time) :acc []}]
             ((comp
               first
               (domonad state-m
                        [calc-result
                         ((comp
                           m-result
                           (partial
                            apply
                            (partial calc-cache! (cache/aggregation-hash)))
                           (partial map :json))
                          [hm-owid hm-v1])
                         _ (add-calc-time "calc-cache!" calc-result)]
                        calc-result))
              init-state))))))
    (partial pmap json-changed!))
   [{:json-fn vac/json-data  :cache-storage [:owid]}
    {:json-fn data/json-data :cache-storage [:v1]}])

  (debugf "Keys kept in the cache %s" (keys @cache/cache))
  (debugf "Responses %s" (select-keys @cache/cache [:v1 :owid]))
  (debugf "Cache size %s" (measure @cache/cache)))

(defn- p-endlessly []
  (endlessly reset-cache!
             ;; time-to-live (* <hours> <minutes> <seconds> <milliseconds>)
             (* 3 60 60 1000)))
(defn- p-long-polling [] (long-polling com/telegram-token))

(defn-fun-id start
  "Fetch api service data and only then register the telegram commands."
  []
  (infof "Starting ...")
  (if (system-ok?)
    (do
      (setup-webhook com/telegram-token)
      (reset-cache!)
      (swap! initialized (fn [_] true))
      (let [funs (into [p-endlessly]
                       (when-not com/use-webhook?
                         [p-long-polling]))
            parallel-run?
            true
            #_false]
        (debugf "%s run %s ..." (if parallel-run? "Parallel" "Sequential") funs)
        (if parallel-run?
          (pmap (fn [fun] (fun)) funs)
          #_(map  (fn [fun] (fun)) funs)
          (do
            #_(p-endlessly)
            (p-long-polling)
            )
          ))
      (infof "Starting ... done"))
    (fatalf "Start aborted")))

(defn-fun-id stop "" []
  (infof "Stopping ...")
  (run! (fn [obj-q]
          (let [obj (eval obj-q)]
            (when (= obj-q 'corona.telegram/telegram-port)
              (if-let [old-tgram-port (deref obj)]
                (do
                  (debugf "Closing old-tgram-port %s ..."
                          (com/log-obj old-tgram-port))
                  (async/close! old-tgram-port))
                (debugf "No old-tgram-port defined")))
            (swap! obj (fn [_] nil))
            (debugf "%s new value: %s"
                    obj-q (if-let [v (deref obj)] v "nil"))))
        ['corona.telegram/initialized
         'corona.api.cache/cache
         'corona.telegram/continue
         'corona.telegram/telegram-port])
  (infof "Stopping ... done"))

(defn-fun-id restart "" []
  (infof "Restarting ...")
  (when @initialized
    (stop)
    (Thread/sleep 400))
  (start)
  (infof "Restarting ... done"))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.telegram)
