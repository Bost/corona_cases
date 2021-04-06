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

(defn last-7-block [{:keys [condition emoji title vals]}]
  (when condition
    ["\n%s\n"
     [(format
       (str
        "<code>" "%s" "</code> %s\n"
        "%s")
       (str (if emoji emoji (str msgc/blank msgc/blank)) msgc/blank)
       (str msgc/blank title)
       (utc/sjoin vals))]]))

(defn-fun-id confirmed-info "TODO reintroduce max-active-date"
  [ccode last-report {:keys [json] :as pred-json-hm} delta maxes cnt-countries]
  (let [
        {max-active :active max-deaths :deaths} maxes
        {population      :p
         deaths          :d
         recove          :r
         recove-estim    :r
         active          :a
         active-estim    :a
         vaccin          :v
         confirmed       :c
         active-per-100k :a100k
         recove-per-100k :r100k
         deaths-per-100k :d100k
         closed-per-100k :c100k
         vaccin-per-100k :v100k
         a-rate          :a-rate
         r-rate          :r-rate
         d-rate          :d-rate
         c-rate          :c-rate ;; closed-rate
         v-rate          :v-rate} last-report

        last-8 (data/last-8-reports pred-json-hm)
        {vaccin-last-8 :v active-last-8 :a confir-last-8 :c} last-8

        [_               & vaccin-last-7] vaccin-last-8
        [active-last-8th & active-last-7] active-last-8
        [_               & confir-last-7] confir-last-8

        closed (+ deaths recove)
        {delta-deaths :d
         delta-recove :r
         delta-recove-estim :r
         delta-active :a
         delta-active-estim :a
         delta-vaccin :v
         delta-d100k  :d100k
         delta-r100k  :r100k
         delta-a100k  :a100k
         delta-v100k  :v100k}
        delta
        delta-closed (+ delta-deaths delta-recove)
        active-last-7-avg (-> active-last-7 istats/mean round-nr)

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
        active-change-last-7-avg (-> (/ (- active active-last-8th) 7.0)
                                     round-nr #_plus-minus)]

    ;; TODO add effective reproduction number (R)
    ((comp
      (partial remove nil?)
      (partial apply conj))
     [(when (pos? confirmed)
        ((comp
          (partial mapv f))
         [
          #_{:s lang/vaccinated     :n vaccin          :diff delta-vaccin :emoji "💉"}
          #_{:s lang/vaccin-per-1e5 :n vaccin-per-100k :diff delta-v100k}
          {:s lang/active         :n active          :diff delta-active       :emoji "🤒"}
          {:s lang/activ-estim    :n active-estim    :diff delta-active-estim :emoji "🤒"}
          {:s lang/active-per-1e5 :n active-per-100k :diff delta-a100k}
          #_{:s lang/active-last-7-med :n (->> active-last-7 (izoo/roll-median 7) (first) (int))}
          {:s lang/active-last-7-avg :n active-last-7-avg}
          {:s lang/active-change-last-7-avg :n active-change-last-7-avg :show-plus-minus true}
          {:s lang/recovered         :n recove          :diff delta-recove       :emoji "🎉"}
          {:s lang/recov-estim       :n recove-estim    :diff delta-recove-estim :emoji "🎉"}
          {:s lang/recove-per-1e5    :n recove-per-100k :diff delta-r100k}
          {:s lang/deaths            :n deaths          :diff delta-deaths :emoji "⚰️"}
          {:s lang/deaths-per-1e5    :n deaths-per-100k :diff delta-d100k}
          {:s lang/closed            :n closed          :diff delta-closed :emoji "🏁"}
          {:s lang/closed-per-1e5    :n closed-per-100k :diff delta-d100k
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
                                  (partial filter (fn [hm] (= (:ccode hm) ccode)))
                                  data/all-rankings)
                                 json)
                                (last args))))))]])
      (last-7-block
       {:condition (some pos? vaccin-last-7)
        :emoji "💉🗓"
        :title (format "%s - %s" lang/vaccin-last-7 lang/rate-of-people)
        :vals (map (fn [v] (format "%s=%s%s"
                                  v
                                  ((com/calc-rate-precision-1 :v)
                                   {:v v :p population})
                                  msgc/percent))
                   vaccin-last-7)})
      (last-7-block
       {:condition (pos? confirmed)
        :emoji "🤒🗓"
        :title (format "%s - %s" lang/active-last-7 lang/rate-of-confirmed)
        :vals (map (fn [a c] (format "%s=%s%s"
                                    a
                                    ((com/calc-rate-precision-1 :a)
                                     {:a a :p population :c c})
                                    msgc/percent))
                          active-last-7 confir-last-7)})])))

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
  [ccode {:keys [json] :as pred-json-hm}]
  ((comp
    (fn [info]
      (debugf "ccode %s size %s" ccode (com/measure info))
      info)
    fmt)
   (let [dates (data/dates json)
         last-report (data/last-report pred-json-hm)
         {v-rate :v-rate vaccinated :v population :p confirmed :c} last-report
         delta (data/delta pred-json-hm)
         {delta-confir :c
          delta-vaccin :v} delta
         {vaccin-last-8 :v} (data/last-8-reports pred-json-hm)
         [_ & vaccin-last-7] vaccin-last-8]
     (conj
      (select-keys pred-json-hm [:header :footer])
      {:cname-aliased (ccr/country-name-aliased ccode)
       :country-cmds
       ((comp (partial apply #(format "     %s    %s" %1 %2))
              (partial map (comp com/encode-cmd cstr/lower-case)))
        [ccode (ccc/country-code-3-letter ccode)])
       :cnt-reports (str lang/report " " (count dates))
       :population
       (f (conj {:s lang/people :n population :emoji "👥"}))

       :vaccinated
       (f {:s lang/vaccinated
           :n    (if (zero? vaccinated) com/unknown vaccinated)
           :diff (if (zero? vaccinated) com/unknown delta-vaccin)
           :emoji "💉"})

       :confirmed
       (f {:emoji "🦠" :s lang/confirmed :n confirmed :diff delta-confir})}

      (when (zero? vaccinated)
        {:notes (when (zero? vaccinated)
                  ["%s\n" [lang/vaccin-data-not-published]])})

      (when (or (pos? confirmed)
                (some pos? vaccin-last-7))
        (let [case-counts-rbr (data/case-counts-report-by-report pred-json-hm)
              maxes
              {:deaths (max-vals (:d case-counts-rbr) dates)
               :active (max-vals (:a case-counts-rbr) dates)}]
          {:details (confirmed-info
                     ccode
                     last-report
                     pred-json-hm #_(dissoc pred-json-hm :json) ;; TODO the dissoc is not needed
                     delta
                     maxes
                     (count ccc/all-country-codes))}))))))

(defn message!
  [ccode & [pred-json-hm]]
  (let [ks [:msg (keyword ccode)]]
    (if pred-json-hm
      (cache/cache! (fn [] (message ccode pred-json-hm))
                    ks)
      (get-in @cache/cache ks))))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.msg.text.message)
