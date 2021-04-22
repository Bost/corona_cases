;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.msg.text.message)

(ns corona.msg.text.details
  (:require [clojure.string :as cstr]
            [corona.api.cache :as cache]
            [corona.api.expdev07 :as data]
            [corona.common :as com]
            [corona.countries :as ccr]
            [corona.country-codes :as ccc]
            [corona.lang :as lang]
            [corona.macro :refer [defn-fun-id debugf]]
            [taoensso.timbre :as timbre]
            [corona.msg.text.common :as msgc]
            [incanter.stats :as istats]
            [utils.core :as utc]
            [clj-time.bost :as ctb]
            [utils.num :as utn]))

;; (set! *warn-on-reflection* true)

(defn fmt
  [{:keys
    [header cname-aliased country-cmds cnt-reports population vaccinated
    confirmed footer notes details]}]
  (msgc/format-linewise
   ;; extended header
   [["%s\n" [(msgc/format-linewise [["%s  " [header]]
                                    ["%s "  [cname-aliased]]
                                    ["%s"   [country-cmds]]])]]
    ["%s\n" [cnt-reports]]
    ["%s\n" [((comp
               msgc/format-linewise
               (partial remove nil?)
               (partial apply conj [population vaccinated notes confirmed]))
              details)]]
    ["%s\n" [footer]]]))

(defn round-nr [value] (int (utn/round-precision value 0)))

(def f msgc/fmt-to-cols)

(defn last-7-block [{:keys [emoji title vals]}]
  ["\n%s\n"
   [(format
     (str
      "<code>" "%s" "</code> %s\n"
      "%s")
     (str (if emoji emoji (str msgc/blank msgc/blank)) msgc/blank)
     (str msgc/blank title)
     (utc/sjoin vals))]])

(defn rank-for-case [stats-countries rank-kw]
  (map-indexed
   (fn [idx hm]
     (update-in (select-keys hm [:ccode]) [:rank rank-kw]
                ;; inc - ranking starts from 1, not from 0
                (fn [_] (inc idx))))
   (sort-by rank-kw > stats-countries)))

(defn all-rankings
  "TODO verify ranking for one and zero countries"
  [stats-countries]
  (map (fn [ccode]
         (apply utc/deep-merge
                (reduce into []
                        (map (fn [ranking]
                               (filter (fn [hm] (= (:ccode hm) ccode)) ranking))
                             ((comp
                               utc/transpose
                               (partial map (partial rank-for-case stats-countries)))
                              com/ranking-cases)))))
       com/relevant-country-codes))

(defn- last-7 [k last-8] ((comp rest k) last-8))

(defn-fun-id confirmed-info "TODO reintroduce max-active-date"
  [ccode last-report last-8 rankings delta maxes cnt-countries]
  (debugf "ccode %s" ccode)
  #_(def last-8 last-8)
  (let [{max-active :active max-deaths :deaths} maxes
        has-confirmed? ((comp pos? :c) last-report)
        popula-last-7 (last-7 :p last-8)
        vaccin-last-7 (last-7 :v last-8)
        active-last-7 (last-7 :a last-8)
        confir-last-7 (last-7 :c last-8)]
    ;; TODO add effective reproduction number (R)
    ((comp
      (partial remove nil?)
      (partial apply conj))
     [(when has-confirmed?
        (mapv
         f
         [{:s lang/active         :n (:a     last-report) :diff (:a     delta) :emoji "🤒"}
          {:s lang/activ-estim    :n (:ea    last-report) :diff (:ea    delta) :emoji "🤒"}
          {:s lang/active-per-1e5 :n (:a100k last-report) :diff (:a100k delta)}
          {:s lang/active-last-7-avg
           :n ((comp round-nr istats/mean) active-last-7)}
          {:s lang/active-change-last-7-avg
           :n
           ;; ActC(t0)    = active(t0)    - active(t0-1d)
           ;; ActC(t0-1d) = active(t0-1d) - active(t0-2d)
           ;; ActC(t0-2d) = active(t0-2d) - active(t0-3d)
           ;; ActC(t0-3d) = active(t0-2d) - active(t0-4d)
           ;; ActC(t0-4d) = active(t0-2d) - active(t0-5d)
           ;; ActC(t0-5d) = active(t0-2d) - active(t0-6d)
           ;; ActC(t0-6d) = active(t0-6d) - active(t0-7d)

           ;; ActCL7CAvg =
           ;; = (ActC(t0)+ActC(t0-1d)+ActC+(t0-2d)+...+ActC(t0-6d)) / 7
           ;; = (active(t0) - active(t0-7d)) / 7
           (-> (/ (- (:a last-report) ((comp first :a) last-8)) 7.0)
               round-nr #_plus-minus)
           :show-plus-minus true}
          {:s lang/recovered      :n (:r     last-report)                  :diff (:r     delta) :emoji "🎉"}
          {:s lang/recov-estim    :n (:er    last-report)                  :diff (:er    delta) :emoji "🎉"}
          {:s lang/recove-per-1e5 :n (:r100k last-report)                  :diff (:r100k delta)}
          {:s lang/deaths         :n (:d     last-report)                  :diff (:d     delta) :emoji "⚰️"}
          {:s lang/deaths-per-1e5 :n (:d100k last-report)                  :diff (:d100k delta)}
          {:s lang/closed         :n (reduce + ((juxt :d :r) last-report)) :diff (reduce + ((juxt :d :r) delta)) :emoji "🏁"}
          {:s lang/closed-per-1e5 :n (:c100k last-report)                  :diff (:c100k delta)
           ;; TODO create command lang/cmd-closed-per-1e5
           #_#_:desc (com/encode-cmd lang/cmd-closed-per-1e5)}]))
      ;; no country ranking can be displayed for worldwide statistics
      ["\n%s\n" [(format (str
                          "%s")
                         (let [date (max-active :date)]
                           (format "%s: %s (%s)"
                                   lang/active-max (max-active :val)
                                   (com/fmt-date date)
                                   ;; TODO ctb/ago-diff: show only two segments:
                                   ;; 1 month 4 weeks ago; must be rounded
                                   #_
                                   (format "%s - %s"
                                           (com/fmt-date date)
                                           (ctb/ago-diff date
                                                         {:verbose true})))))
                 ;; max-deaths makes no sense - it's always the last report
                 #_(format (str
                            "%s\n"
                            "%s")
                           (format "%s: %s (%s)" lang/active-max (max-active :val)
                                   (com/fmt-date (max-active :date)))
                           (format "%s: %s (%s)" lang/deaths-max (max-deaths :val)
                                   (com/fmt-date (max-deaths :date))))]]
      (when-not (msgc/worldwide? ccode)
        ["\n%s\n"
         [(msgc/format-linewise
           [["%s" [lang/people         :p]]
            ["%s" [lang/active-per-1e5 :a100k]]
            ["%s" [lang/recove-per-1e5 :r100k]]
            ["%s" [lang/deaths-per-1e5 :d100k]]
            ["%s" [lang/closed-per-1e5 :c100k]]]
           :line-fmt "%s:<b>%s</b>   "
           :fn-fmts
           (fn [fmts] (format lang/ranking-desc
                             cnt-countries (cstr/join "" fmts)))
           :fn-args
           (fn [args] (update args (dec (count args))
                             (fn [_]
                               (get
                                ((comp
                                  first
                                  (partial map :rank)
                                  (partial filter (fn [hm] (= (:ccode hm) ccode))))
                                 rankings)
                                (last args))))))]])
      (when (some pos? vaccin-last-7)
        (last-7-block
         {:emoji "💉🗓"
          :title (format "%s - %s" lang/vaccin-last-7 lang/rate-of-people)
          :vals (map (fn [v p] (format "%s=%s%s"
                                    v
                                    ((com/calc-rate-precision-1 :v)
                                     {:v v :p p})
                                    msgc/percent))
                     vaccin-last-7 popula-last-7)}))
      (when has-confirmed?
        (last-7-block
         {:emoji "🤒🗓"
          :title (format "%s - %s" lang/active-last-7 lang/rate-of-confirmed)
          :vals (map (fn [a c p] (format "%s=%s%s"
                                      a
                                      ((com/calc-rate-precision-1 :a)
                                       {:a a :c c :p p})
                                      msgc/percent))
                     active-last-7 confir-last-7 popula-last-7)}))])))

(defn- max-vals [data dates]
  (let [max-val (apply max data)]
    {:val max-val
     :date (nth dates (utc/last-index-of data max-val))}))

;; By default Vars are static, but Vars can be marked as dynamic to
;; allow per-thread bindings via the macro binding. Within each thread
;; they obey a stack discipline:
#_(def ^:dynamic points [[0 0] [1 3] [2 0] [5 2] [6 1] [8 2] [11 1]])
(defn-fun-id message
  "Shows the table with the absolute and %-wise nr of cases, cases per-100k etc.
  TODO 3. show Case / Infection Fatality Rate (CFR / IFR)
  TODO Bayes' Theorem applied to PCR test: https://youtu.be/M8xlOm2wPAA
  (need 1. PCR-test accuracy, 2. Covid 19 disease prevalence)
  TODO create an API web service(s) for every field displayed in the messages
  "
  [ccode {:keys [cnt-reports dates estim rankings] :as pred-json-hm}]
  (debugf "ccode %s" ccode)
  #_(def estim estim)
  ((comp
    (fn [info]
      (debugf "ccode %s size %s" ccode (com/measure info))
      info)
    fmt)
   (let [ccode-estim (filter (fn [ehm] (= ccode (:ccode ehm))) estim)
         last-2-reports (take-last 2 ccode-estim)
         last-report (last last-2-reports)
         {vaccinated :v confirmed :c} last-report
         delta ((comp
                 (partial reduce into {})
                 (partial apply (fn [prv lst]
                                  ((comp
                                    (partial map (fn [k]
                                                   ;; TODO see also clojure.core/find
                                                   #_(debugf "k %s" k)
                                                   {k (- (k lst) (k prv))})))
                                   com/all-cases))))
                last-2-reports)]
     (conj
      (select-keys pred-json-hm [:header :footer])
      {:cname-aliased (ccr/country-name-aliased ccode)
       :country-cmds
       ((comp (partial apply #(format "     %s    %s" %1 %2))
              (partial map (comp com/encode-cmd cstr/lower-case)))
        [ccode (ccc/country-code-3-letter ccode)])
       :cnt-reports (str lang/report " " cnt-reports)
       :population
       (f (conj {:s lang/people :n (:p last-report) :emoji "👥"}))

       :vaccinated
       (f {:s lang/vaccinated
           :n    (if (zero? vaccinated) com/unknown vaccinated)
           :diff (if (zero? vaccinated) com/unknown (:v delta))
           :emoji "💉"})

       :confirmed
       (f {:emoji "🦠" :s lang/confirmed :n confirmed :diff (:c delta)})}

      (when (zero? vaccinated)
        {:notes (when (zero? vaccinated)
                  ["%s\n" [lang/vaccin-data-not-published]])})

      (let [last-8 (let [kws ((comp keys first) ccode-estim)]
                     ((comp
                       (partial zipmap kws)
                       (partial map vals)
                       utc/transpose
                       (partial map (fn [hm] (select-keys hm kws)))
                       (partial take-last 8))
                      ccode-estim))]
        (when (or (pos? confirmed)
                  ((comp (partial some pos?) rest :v) last-8))
          {:details (confirmed-info
                     ccode
                     last-report
                     last-8
                     rankings
                     delta
                     {:deaths (max-vals ((comp (partial map :d)) ccode-estim) dates)
                      :active (max-vals ((comp (partial map :a)) ccode-estim) dates)}
                     (count com/relevant-country-codes))}))))))

(defn message-kw [ccode] [:msg (keyword ccode)])

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.msg.text.message)
