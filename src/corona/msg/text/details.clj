;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.msg.text.details)

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
            [utils.core :as utc]
            [utils.num :as utn]))

;; (set! *warn-on-reflection* true)

(defn fmt
  [{:keys
    [header cname-aliased country-cmds cnt-reports population vaccinated
    new-confirmed footer notes details]}]
  (msgc/format-linewise
   ;; extended header
   [["%s\n" [(msgc/format-linewise [["%s  " [header]]
                                    ["%s "  [cname-aliased]]
                                    ["%s"   [country-cmds]]])]]
    ["%s\n" [cnt-reports]]
    ["%s\n" [((comp
               msgc/format-linewise
               (partial remove nil?)
               (partial apply conj [population vaccinated notes new-confirmed]))
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

(defn rank-for-case
  "stats is stats-countries"
  [stats rank-kw]
  (let [unlensed-case-ks (com/unlense rank-kw)]
  ((comp
    (partial map-indexed
             (fn [idx hm]
               (update-in (select-keys hm [:ccode])
                          [:rank unlensed-case-ks]
                          ;; inc - ranking starts from 1, not from 0
                          (fn [_] (inc idx)))))
    (partial sort-by unlensed-case-ks >))
   stats)
  ))

(defn all-rankings
  "Works also for one and zero countries"
  [lense-fun stats-countries]
  (let [stats-all-ranking-cases
        ((comp
          utc/transpose
          (partial map (partial rank-for-case stats-countries))
          (partial map lense-fun))
         com/ranking-cases)]
    {:lense-fun lense-fun
     :vals
     (map (fn [ccode]
            ((comp
              (partial apply utc/deep-merge)
              (partial reduce into [])
              (partial map (partial filter (fn [hm] (= (:ccode hm) ccode)))))
             stats-all-ranking-cases))
          ccc/relevant-country-codes)}))

(defn- last-7 [kws last-8] ((comp rest
                                  (partial get-in last-8))
                            kws))

(defn mean
  [numbers]
  (if (empty? numbers)
    0
    (/ (reduce + numbers) (count numbers))))

(defn label-val
  "Encoding of (label, value) pair. See corona.msg.text.lists/column-label"
  [lense-fun hm-text case-kw hm]
  (let [fun-case-kw (lense-fun case-kw)]
    [(get-in hm-text fun-case-kw) (get-in hm fun-case-kw)]))

(defn-fun-id confirmed-info "TODO reintroduce max-active-date"
  [ccode some-recove? lense-fun has-n-confi? last-report
   last-8 rankings delta maxes cnt-countries]
  #_(debugf "ccode %s" ccode)
  (let [{max-active :a max-deaths :d} maxes
        fun-s (fn [hm] (lense-fun :s))
        fun-n (lense-fun :n)
        fun-a (lense-fun :a)
        fun-v (lense-fun :v)
        fun-r (lense-fun :r)
        fun-d (lense-fun :d)

        fun-c
        (lense-fun :c)
        #_(comp
         (partial apply com/calculate-closed)
         (juxt fun-d
               fun-r))
        fun-c1e5
        (lense-fun :c1e5)
        ;; alternatively implement (lense-fun :c) - i.e. :ec
        #_(comp
         (partial apply com/calculate-closed)
         (juxt fun-d1e5
               fun-r1e5))

        fun-a1e5 (lense-fun :a1e5)
        fun-r1e5 (lense-fun :r1e5)
        fun-d1e5 (lense-fun :d1e5)
        popula-last-7 (last-7 (lense-fun :p) last-8)
        vaccin-last-7 (last-7 fun-v last-8)
        active-last-7 (last-7 fun-a last-8)]
    #_(def lense-fun lense-fun)
    #_(def last-8 last-8)
    #_(def delta delta)
    #_(def fun-c fun-c)
    ;; TODO some countries report too low recov. numbers
    ;; TODO add effective reproduction number (R)
    ((comp
      (partial remove nil?)
      (partial apply conj))
     [(when has-n-confi?
        (mapv
         f
         [
          {:s    (get-in lang/hm-active fun-a)
           :n    (get-in last-report fun-a)
           :diff (get-in delta fun-a)
           :emoji "🤒"}
          {:s    (get-in lang/hm-active-per-1e5 fun-a1e5)
           :n    (get-in last-report fun-a1e5)
           :diff (get-in delta fun-a1e5)}
          {:s (get-in lang/hm-active-last-7-avg fun-a)
           :n ((comp round-nr mean) active-last-7)}
          {:s (get-in lang/hm-active-change-last-7-avg fun-a)
           :n
           ;; ActC(t0)    = active(t0)    - active(t0-1d)
           ;; ActC(t0-1d) = active(t0-1d) - active(t0-2d)
           ;; ActC(t0-2d) = active(t0-2d) - active(t0-3d)
           ;; ActC(t0-3d) = active(t0-3d) - active(t0-4d)
           ;; ActC(t0-4d) = active(t0-4d) - active(t0-5d)
           ;; ActC(t0-5d) = active(t0-5d) - active(t0-6d)
           ;; ActC(t0-6d) = active(t0-6d) - active(t0-7d)

           ;; ActCL7CAvg =
           ;; = (ActC(t0)+ActC(t0-1d)+ActC(t0-2d)+...+ActC(t0-6d)) / 7
           ;; = (active(t0) - active(t0-7d)) / 7
           (-> (/ (- (get-in last-report fun-a)
                     ((comp
                       first)
                      (get-in
                       ;; 8 values are needed to calculate 7 differences among them
                       last-8 fun-a)))
                  7.0)
               round-nr #_plus-minus)
           :show-plus-minus true}
          {:s    (get-in lang/hm-recovered fun-r)
           :n    (get-in last-report fun-r)
           :diff (get-in delta fun-r)
           :emoji "🎉"}
          {:s    (get-in lang/hm-recove-per-1e5 fun-r1e5)
           :n    (get-in last-report fun-r1e5)
           :diff (get-in delta fun-r1e5)}
          {:s lang/deaths
           :n    (get-in last-report fun-d)
           :diff (get-in delta fun-d)
           :emoji "⚰️"}
          {:s lang/deaths-per-1e5
           :n    (get-in last-report fun-d1e5)
           :diff (get-in delta fun-d1e5)}
          {:s    (get-in lang/hm-closed fun-c)
           :n    (get-in last-report fun-c)
           :diff (get-in delta fun-c)
           :emoji "🏁"}
          {:s    (get-in lang/hm-closed-per-1e5 fun-c1e5)
           :n    (get-in last-report fun-c1e5)
           :diff (get-in delta fun-c1e5)
           ;; TODO create command lang/cmd-closed-per-1e5
           #_#_:desc (com/encode-cmd lang/cmd-closed-per-1e5)}]))
      ;; 1. no country ranking can be displayed for worldwide statistics
      ;; 2. max-deaths makes no sense - it's always the last report
      ["\n%s\n"
       [(format (str
                 "%s")
                (let [date (max-active :date)]
                  (format "%s: %s (%s)"
                          (get-in lang/hm-active-max fun-a)
                          (max-active :val)
                          (com/fmt-date date)
                          ;; TODO clj-time.bost/ago-diff:
                          ;; show only two segments:
                          ;; 1 month 4 weeks ago; must be rounded
                          #_
                          (format "%s - %s"
                                  (com/fmt-date date)
                                  (clj-time.bost/ago-diff date
                                                          {:verbose true})))))]]
      (when-not (msgc/worldwide? ccode)
        ["\n%s\n"
         [(msgc/format-linewise
           (let [rankings-lense-fun (:lense-fun rankings)
                 hm-ranking ((comp
                      first
                      (partial map :rank)
                      (partial filter (fn [hm] (= (:ccode hm) ccode))))
                     (:vals rankings))]
             ;; TODO test estimated-indication for a subset of countries which
             ;; do report recovery cases
             [["%s" [lang/people (:p hm-ranking)]]
              ["%s" (label-val rankings-lense-fun lang/hm-active-per-1e5 :a1e5 hm-ranking)]
              ["%s" (label-val rankings-lense-fun lang/hm-recove-per-1e5 :r1e5 hm-ranking)]
              ["%s" [lang/deaths-per-1e5 (:d1e5 hm-ranking)]]
              ["%s" (label-val rankings-lense-fun lang/hm-closed-per-1e5 :c1e5 hm-ranking)]])
           :line-fmt "%s:<b>%s</b>   "
           :fn-fmts
           (fn [fmts] (format lang/ranking-desc
                             cnt-countries (cstr/join "" fmts))))]])
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
      (when has-n-confi?
        (last-7-block
         {:emoji "🤒🗓"
          :title (format "%s - %s"
                         (get-in lang/hm-active-last-7 fun-a)
                         lang/rate-of-confirmed)
          :vals (map (fn [a n p] (format "%s=%s%s"
                                        a
                                        ((com/calc-rate-precision-1 :a)
                                         {:a a :n n :p p})
                                        msgc/percent))
                     active-last-7
                     (last-7 fun-n last-8)
                     popula-last-7)}))])))

(defn- max-vals [data dates]
  (let [max-val (apply max data)]
    {:val max-val
     :date (nth dates (utc/last-index-of data max-val))}))

;; By default Vars are static, but Vars can be marked as dynamic to
;; allow per-thread bindings via the macro binding. Within each thread
;; they obey a stack discipline:
#_(def ^:dynamic points [[0 0] [1 3] [2 0] [5 2] [6 1] [8 2] [11 1]])
(defn-fun-id message
  "Shows the table with the absolute and %-wise nr of cases, cases per-1e5 etc.
  TODO 3. show Case / Infection Fatality Rate (CFR / IFR)
  TODO Bayes' Theorem applied to PCR test: https://youtu.be/M8xlOm2wPAA
  (need 1. PCR-test accuracy, 2. Covid 19 disease prevalence)
  TODO create an API web service(s) for every field displayed in the messages
  "
  [ccode {:keys [cnt-reports dates estim rankings] :as prm}]
  #_(debugf "ccode %s" ccode)
  ((comp
    (fn [info]
      (debugf "ccode %s size %s" ccode (com/measure info))
      info)
    fmt)
   (let [ccode-estim (filter (fn [ehm] (= ccode (:ccode ehm))) estim)
         last-8 (let [kws ((comp keys first) ccode-estim)]
                  ((comp
                    (partial zipmap kws)
                    (partial map vals)
                    utc/transpose
                    (partial map (fn [hm] (select-keys hm kws)))
                    (partial take-last 8))
                   ccode-estim))]
     #_(def last-8 last-8)
     (let [some-recove?
           ((comp (partial some pos?))
            (last-7
             ;; the 'original' value does or does not contain recovered cases
             (com/ident-fun :r)
             last-8))

           lense-fun (if (and some-recove? (not (msgc/worldwide? ccode)))
                       com/ident-fun com/estim-fun)
           fun-v (lense-fun :v)
           fun-n (lense-fun :n)
           fun-d (lense-fun :d)
           fun-a (lense-fun :a)]
       #_(when some-recove?
         (debugf "ccode %s some-recove? %s - %s"
                 ccode some-recove? (last-7 (com/ident-fun :r) last-8)))
       #_(def lense-fun lense-fun)
       (let [last-2-reports (take-last 2 ccode-estim)
             last-report    (last last-2-reports)
             vaccinated     (or (get-in last-report fun-v) 0)
             new-confirmed  (or (get-in last-report fun-n) 0)
             delta ((comp
                     (partial reduce into {})
                     (partial apply (fn [prev-report last-report]
                                      ((comp
                                        (partial map (fn [k]
                                                       {k (- (k last-report)
                                                             (k prev-report))})))
                                       com/all-cases))))
                    last-2-reports)]
         #_(def last-2-reports last-2-reports)
         #_(def last-report last-report)
         #_(def delta delta)
         (conj
          (select-keys prm [:header :footer])
          {:cname-aliased (ccr/country-name-aliased ccode)
           :country-cmds
           ((comp (partial apply #(format "     %s    %s" %1 %2))
                  (partial map (comp com/encode-cmd cstr/lower-case)))
            [ccode (ccc/country-code-3-letter ccode)])
           :cnt-reports (str lang/report " " cnt-reports)
           :population
           (f (conj {:s lang/people :n (get-in last-report (lense-fun :p)) :emoji "👥"}))

           :vaccinated
           (f {:s lang/vaccinated
               :n    (if (zero? vaccinated) com/unknown vaccinated)
               :diff (if (zero? vaccinated) com/unknown (get-in delta fun-v))
               :emoji "💉"})

           :new-confirmed
           (f {:emoji "🦠"
               :s lang/confirmed :n new-confirmed
               :diff (if-let [dn (get-in delta fun-n)] dn 0)})}

          (when (zero? vaccinated)
            {:notes (when (zero? vaccinated)
                      ["%s\n" [lang/vaccin-data-not-published]])})

          (let [has-n-confi? ((comp pos? (fn [hm] (get-in hm fun-n))) last-report)
                some-vaccinated? ((comp (partial some pos?))
                                  (last-7 fun-v last-8))]
            (when (or has-n-confi? some-vaccinated?)
              {:details (confirmed-info
                         ccode
                         some-recove?
                         lense-fun
                         has-n-confi?
                         last-report
                         last-8
                         rankings
                         delta
                         (let [kws [:d :a]]
                           ((comp
                             (partial zipmap kws)
                             (partial map (fn [data] (max-vals data dates)))
                             (partial map
                                      (fn [ks]
                                        (map (fn [hm] (get-in hm ks)) ccode-estim)))
                             (partial map lense-fun))
                            kws))
                         (count ccc/relevant-country-codes))}))))))))

(defn message-kw [ccode] [:msg (keyword ccode)])

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.msg.text.details)
