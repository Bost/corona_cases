;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.msg.text.details)

(ns corona.msg.text.details
  (:require [clojure.string :as cstr]
            [corona.api.cache :as cache]
            [corona.api.expdev07 :as data]
            [corona.common :as com :refer
             [kact kdea krec kclo kest kmax krep k1e5 kls7 kabs kavg kchg krnk kcco
              lense]]
            [corona.cases :as cases]
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
     new-confirmed
     new-confirmed-incidence
     footer notes details]}]
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
  "
  (rank-for-case stats-countries lensed-rank-kw)
  "
  [stats-countries lensed-rank-kw]
  #_(def stats-countries stats-countries)
  #_(def lensed-rank-kw lensed-rank-kw)
  ((comp
    (partial map-indexed
             (fn [idx hm]
               (update-in (select-keys hm [kcco])
                          lensed-rank-kw
                          ;; inc - ranking starts from 1, not from 0
                          (fn [_] (inc idx)))))
    (partial sort-by
             (fn [m] (get-in m (butlast lensed-rank-kw)))
             >))
   stats-countries))

(defn all-rankings
  "
  (all-rankings lense-fun stats-countries)
  Works also for one and zero countries.
  TODO all-rankings-new should be calculated in the corona.telegram"
  [lense-fun stats-countries]
  #_(def lense-fun lense-fun )
  #_(def stats-countries stats-countries)
  (let [stats-all-ranking-cases
        ((comp
          utc/transpose
          (partial map (partial rank-for-case stats-countries))
          (partial map lense-fun))
         #_[:p :a :r :d :c]
         cases/ranking-cases)]
    #_(def stats-all-ranking-cases stats-all-ranking-cases)
    {:lense-fun lense-fun
     :vals
     (map (fn [ccode]
            ((comp
              (partial apply utc/deep-merge)
              ;; TODO have a look at lazy-cat
              (partial reduce concat)
              (partial map (partial filter (fn [hm] (= (kcco hm) ccode)))))
             stats-all-ranking-cases))
          ccc/relevant-country-codes)}))

(defn- last-7-xxx [kws last-8] ((comp rest
                                  (partial get-in last-8))
                            kws))

(defn- last-7-yyy [kws last-8]
  (let [case-kw (first kws)]
    ((comp
      (partial map (partial hash-map case-kw))
      rest
      (partial get-in last-8))
     [case-kw])))

(defn mean
  [numbers]
  (if (empty? numbers)
    0
    (/ (reduce + numbers) (count numbers))))

(defn label-val
  "Encoding of (label, value) pair. See corona.msg.text.lists/column-label"
  [lense-fun hm-text case-kw hm]
  (let [fun-case-kw (lense-fun case-kw)]
    [(get-in hm-text (butlast fun-case-kw)) (get-in hm fun-case-kw)]))

(defn delta-for-case-kw
  "(delta-case-kw lensed-case-kw case-kw pair)"
  [lensed-case-kw case-kw pair]
  ((comp
    ;; (partial reduce merge)
    (partial apply (fn [fst snd]
                     (- (get-in snd lensed-case-kw)
                        (get-in fst lensed-case-kw)))))
   pair))

(defn pairs [ls-init]
  (loop [ls ls-init
         acc []]
    (if (>= (count ls) 2)
      (let [[fst snd & tail] ls
            vfst (vector fst)
            vsnd (vector snd)]
        (recur (concat vsnd tail) (conj acc (concat vfst vsnd))))
      acc)))

(defn incidence
  "e.g. last two weeks:
  (incidence lense-fun-p fun-n :n 7 country-stats)"
  [lense-fun-p lensed-case-kw case-kw nr-of-reports country-stats]
  ((comp
    round-nr
    (fn [diffs]
      (let [population
            ((comp
              (fn [hm] (get-in hm lense-fun-p))
              ;; `last` implies using the most actual population count
              last)
             country-stats)]
        (* (/ (reduce + diffs)
              population)
           1e5)))
    (partial map (partial delta-for-case-kw lensed-case-kw case-kw))
    pairs
    (partial take-last nr-of-reports))
   country-stats))

(defn-fun-id confirmed-info
  "
  (confirmed-info ccode predicates lense-funs reports vaccin-last-7 rankings
 delta-last-2 maxes cnt-countries)
  "
  [ccode
   {:keys [country-reports-recovered? has-n-confi? some-vaccinated?]
    :as predicates}
   {:keys [lense-fun fun-v fun-n fun-d fun-a fun-p] :as lense-funs}
   {:keys [ccode-stats last-report ccode-stats-last-8 last-8] :as reports}
   vaccin-last-7
   rankings
   delta-last-2
   maxes
   cnt-countries
   ]
  #_(debugf "ccode %s" ccode)
  (let [
        {max-active :a max-deaths :d} maxes
        fun-s (fn [hm] (lense-fun :s))
        fun-r (lense-fun :r)
        fun-c (lense-fun :c)
        fun-c1e5 (lense-fun :c1e5)
        fun-a1e5 (lense-fun :a1e5)
        fun-r1e5 (lense-fun :r1e5)
        fun-d1e5 (lense-fun :d1e5)
        popula-last-7 (last-7-xxx fun-p last-8)
        active-last-7 (map (fn [m] (get-in m fun-a))
                           (last-7-yyy fun-a last-8))]
    ((comp
      (partial remove nil?)
      (partial apply conj))
     [(when has-n-confi?
        (mapv
         f
         [
          (let [nr-of-reports 7]
            {:s (lang/incidence nr-of-reports)
             ;; TODO implement incidence delta
             :n (incidence fun-p fun-n :n nr-of-reports ccode-stats)})
          {:s    (get-in lang/hm-active fun-a)
           :n    (get-in last-report fun-a)
           :diff (get-in delta-last-2 fun-a)
           :emoji "🤒"}
          {:s    (get-in lang/hm-active (lense kact kest k1e5))
           :n    (get-in last-report fun-a1e5)
           :diff (get-in delta-last-2 fun-a1e5)}
          {:s (get-in lang/hm-active (lense kact kest kls7 kabs kavg))
           :n ((comp round-nr mean) active-last-7)}
          {:s (get-in lang/hm-active (lense kact kest kls7 kchg kavg))
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
           (let [fun fun-a]
             (-> (/ (- (get-in last-report fun)
                       (get-in
                        ;; 8 values are needed to calculate 7 differences among them
                        (first ccode-stats-last-8) fun))
                    7.0)
                 round-nr #_plus-minus))
           :show-plus-minus true}
          {:s    (get-in lang/hm-recovered (lense krec kest kabs))
           :n    (get-in last-report fun-r)
           :diff (get-in delta-last-2 fun-r)
           :emoji "🎉"}
          {:s    (get-in lang/hm-recovered (lense krec kest k1e5))
           :n    (get-in last-report fun-r1e5)
           :diff (get-in delta-last-2 fun-r1e5)}
          {:s    (get-in lang/hm-deaths (lense kdea krep kabs))
           :n    (get-in last-report fun-d)
           :diff (get-in delta-last-2 fun-d)
           :emoji "⚰️"}
          {:s    (get-in lang/hm-deaths (lense kdea krep k1e5))
           :n    (get-in last-report fun-d1e5)
           :diff (get-in delta-last-2 fun-d1e5)}
          {:s    (get-in lang/hm-closed (lense kclo kest kabs))
           :n    (get-in last-report fun-c)
           :diff (get-in delta-last-2 fun-c)
           :emoji "🏁"}
          {:s    (get-in lang/hm-closed (lense kclo kest k1e5))
           :n    (get-in last-report fun-c1e5)
           :diff (get-in delta-last-2 fun-c1e5)
           ;; TODO create command lang/cmd-closed-per-1e5
           #_#_:desc (com/encode-cmd lang/cmd-closed-per-1e5)}]))
      ;; 1. no country ranking can be displayed for worldwide statistics
      ;; 2. max-deaths makes no sense - it's always the last report
      ["\n%s\n"
       [(format (str
                 "%s")
                (let [date (max-active :date)]
                  (format "%s: %s (%s)"
                          (get-in lang/hm-active (lense kact kest kmax))
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
                 hm-ranking
                 ((comp
                   first
                   (fn [v] (def flt v) v)
                   (partial filter (fn [hm] (= (kcco hm) ccode))))
                  (:vals rankings))]
             #_(when (= ccode "DE")
                 (def hm-ranking hm-ranking)
                 (def ccode ccode)
                 (def rankings-lense-fun rankings-lense-fun)
                 (def rankings rankings)
                 (def hm-ranking hm-ranking))
             [["%s" [lang/people (get-in hm-ranking (rankings-lense-fun :p))]]
              ["%s" (label-val rankings-lense-fun lang/hm-active :a1e5 hm-ranking)]
              ["%s" (label-val rankings-lense-fun lang/hm-recovered :r1e5 hm-ranking)]
              ["%s" [(get-in lang/hm-deaths (lense kdea krep k1e5)) (get-in hm-ranking (rankings-lense-fun :d1e5))]]
              ["%s" (label-val rankings-lense-fun lang/hm-closed :c1e5 hm-ranking)]])
           :line-fmt "%s:<b>%s</b>   "
           :fn-fmts
           (fn [fmts] (format lang/ranking-desc
                              cnt-countries (cstr/join "" fmts))))]])
      (when some-vaccinated?
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
        (let [new-last-7 (map (fn [m] (get-in m fun-n))
                              (last-7-yyy fun-n last-8))]
          (last-7-block
           {:emoji "🤒🗓"
            :title (format "%s - %s"
                           lang/estim-active-last-7
                           lang/rate-of-confirmed)
            :vals (map (fn [a n p] (format "%s=%s%s"
                                           a
                                           ((com/calc-rate-precision-1 :a)
                                            {:a a :n n :p p})
                                           msgc/percent))
                       active-last-7 new-last-7 popula-last-7)})))])))

(defn- max-vals [data dates]
  (let [max-val (apply max data)]
    {:val max-val
     :date (nth dates (utc/last-index-of data max-val))}))

;; By default Vars are static, but Vars can be marked as dynamic to
;; allow per-thread bindings via the macro binding. Within each thread
;; they obey a stack discipline:
#_(def ^:dynamic points [[0 0] [1 3] [2 0] [5 2] [6 1] [8 2] [11 1]])
(defn-fun-id mexxage
  "Shows the table with the absolute and %-wise nr of cases, cases per-1e5 etc.
  TODO Case / Infection Fatality Rate (CFR / IFR)
  TODO Bayes' Theorem applied to PCR test: https://youtu.be/M8xlOm2wPAA
  (need 1. PCR-test accuracy, 2. Covid 19 disease prevalence)
  TODO create an API web service(s) for every field displayed in the messages
  TODO add effective reproduction number (R)
  "
  [ccode estim dates rankings cnt-reports
   ;; TODO take apart prm. It contains :header and :footer
   prm-base]
  ((comp
    fmt)
   (let [
         ccode-stats (filter (fn [ehm] (= ccode (kcco ehm))) estim)
         ccode-stats-last-8 ((comp
                              (partial take-last 8))
                             ccode-stats)
         last-8 (let [kws ((comp keys first) ccode-stats)]
                  ((comp
                    (partial zipmap kws)
                    (partial map vals)
                    utc/transpose
                    (partial map (fn [hm] (select-keys hm kws))))
                   ccode-stats-last-8))]
     #_(when (= ccode "DE")
         (def ccode ccode)
         (def estim estim)
         (def dates dates)
         (def rankings rankings)
         (def cnt-reports cnt-reports)
         (def last-8 last-8))
     (let [
           country-reports-recovered?
           ;; the difference between reported and estimated values must be within
           ;; the range <0.8, 1.2>
           ((comp
             #_(fn [v] (if v (println "country-reports-recovered?" ccode v)) v)
             (partial some (fn [v] (<= 0.8 v 1.2)))
             #_(fn [v] (def r v) v)
             (partial apply map (comp float
                                      (fn [reported-hm estimated-hm]
                                        (let [reported (get-in reported-hm [:r :rep :abs])
                                              estimated (get-in estimated-hm [:r :est :abs])]
                                          #_(println "r" reported "e" estimated)
                                          (if (pos? estimated)
                                            (/ reported estimated)
                                            0)))))
             #_(fn [v] (def re v) v)
             (partial map (fn [fun]
                            (last-7-yyy (fun :r) last-8)
                            #_(last-7-xxx (fun :r) last-8))))
            [com/ident-fun
             com/lense-fun])

           lense-fun (if (and country-reports-recovered?
                              (not (msgc/worldwide? ccode)))
                       com/ident-fun
                       com/lense-fun)
           fun-v (lense-fun :v)
           fun-n (lense-fun :n)
           fun-d (lense-fun :d)
           fun-a (lense-fun :a)
           fun-p (lense-fun :p)

           last-2-reports (take-last 2 ccode-stats)
           last-report    (last last-2-reports)

           vaccinated     (or (get-in last-report fun-v) 0)
           new-confirmed  (or (get-in last-report fun-n) 0)

           case-kws (into
                     ;; this function
                     [:v :n :d :a :p]
                     [:r :c :c1e5 :a1e5 :r1e5 :d1e5]
                     )
           ]
       #_(when (= ccode "DE")
           (def ccode ccode)
           (def case-kws case-kws)
           (def fun-a fun-a)
           (def last-report last-report)
           (def last-2-reports last-2-reports)
           (def lense-fun lense-fun)
           )
       (let [
             delta-last-2
             ((comp
               (partial reduce utc/deep-merge)
               (partial mapv (fn [case-kw]
                               (let [lensed-case-kw (lense-fun case-kw)]
                                 #_(when (and (= ccode "DE") (= case-kw :a))
                                     (def case-kw case-kw)
                                     (def lensed-case-kw lensed-case-kw))
                                 (update-in {}
                                            lensed-case-kw
                                            (fn [_]
                                              (delta-for-case-kw
                                               lensed-case-kw
                                               case-kw
                                               last-2-reports)))))))
              case-kws)
             ]
         #_(when (= ccode "DE")
             (def last-report last-report)
             (def delta-last-2 delta-last-2)
             )
         (conj
          (select-keys prm-base [:header :footer])
          {:cname-aliased (ccr/country-name-aliased ccode)
           :country-cmds
           ((comp (partial apply #(format "     %s    %s" %1 %2))
                  (partial map (comp com/encode-cmd cstr/lower-case)))
            [ccode (ccc/country-code-3-letter ccode)])
           :cnt-reports (str lang/report " " cnt-reports)
           :population
           (f (conj {:s lang/people :n (get-in last-report fun-p) :emoji "👥"}))

           :vaccinated
           (f {:s lang/vaccinated
               :n    (if (zero? vaccinated) com/unknown vaccinated)
               :diff (if (zero? vaccinated) com/unknown (get-in delta-last-2 fun-v))
               :emoji "💉"})

           :new-confirmed
           (f {:emoji "🦠"
               :s lang/confirmed :n new-confirmed
               :diff (if-let [dn (get-in delta-last-2 fun-n)] dn 0)})}

          (when (zero? vaccinated)
            {:notes (when (zero? vaccinated)
                      ["%s\n" [lang/vaccin-data-not-published]])})

          (let [vaccin-last-7 (map (fn [m] (get-in m fun-v))
                                   (last-7-yyy fun-v last-8))
                has-n-confi? ((comp pos? (fn [hm] (get-in hm fun-n))) last-report)
                some-vaccinated? ((comp (partial some pos?)) vaccin-last-7)]
            (when (or has-n-confi? some-vaccinated?)
              (let [reports {:ccode-stats ccode-stats
                             :last-report last-report
                             :ccode-stats-last-8 ccode-stats-last-8
                             :last-8 last-8}

                    lense-funs {:lense-fun lense-fun
                                :fun-v fun-v
                                :fun-n fun-n
                                :fun-d fun-d
                                :fun-a fun-a
                                :fun-p fun-p}
                    predicates {:country-reports-recovered? country-reports-recovered?
                                :has-n-confi?               has-n-confi?
                                :some-vaccinated?           some-vaccinated?}
                    maxes
                    (let [kws [:d :a]]
                      ((comp
                        (partial zipmap kws)
                        (partial map (fn [data] (max-vals data dates)))
                        (partial map
                                 (fn [ks]
                                   (map (fn [hm] (get-in hm ks)) ccode-stats)))
                        (partial map lense-fun))
                       kws))]
                {:details (confirmed-info
                           ccode
                           predicates
                           lense-funs
                           reports
                           vaccin-last-7
                           rankings
                           delta-last-2
                           maxes
                           (count ccc/relevant-country-codes))})))))))))

(defn message-kw [ccode] [:msg (keyword ccode)])

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.msg.text.details)
