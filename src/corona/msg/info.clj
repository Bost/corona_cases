;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.msg.info)

(ns corona.msg.info
  (:require [clojure.string :as cstr]
            [corona.api.cache :as cache]
            [corona.api.expdev07 :as data]
            [corona.common :as com]
            [corona.countries :as ccr]
            [corona.country-codes :as ccc]
            [corona.lang :as lang]
            [corona.macro :refer [defn-fun-id debugf]]
            [corona.msg.common :as msgc]
            [incanter.stats :as istats]
            [utils.core :as utc]
            [utils.num :as utn]))

;; (set! *warn-on-reflection* true)

(defn format-detailed-info
  [{:keys
    [header-txt
     cname-aliased-txt
     country-commands-txt
     cnt-reports-txt
     population-txt
     vaccinated-txt
     vaccinated-last-7
     confirmed-txt
     footer-txt
     details-txt]}]
  (msgc/format-linewise
   ;; extended header
   [["%s\n" [(msgc/format-linewise [["%s  " [header-txt]]
                                    ["%s "  [cname-aliased-txt]]
                                    ["%s"   [country-commands-txt]]])]]
    ["%s\n" [cnt-reports-txt]]
    ["%s\n" [(msgc/format-linewise (apply conj
                                          [["%s\n" [population-txt]]
                                           ["%s\n" [vaccinated-txt]]
                                           ["%s\n\n" [vaccinated-last-7]]
                                           ["%s\n" [confirmed-txt]]]
                                          details-txt))]]
    ["%s\n" [footer-txt]]]))

(defn round-nr [value] (int (utn/round-precision value 0)))

(defn-fun-id confirmed-info ""
  [last-report pred-hm delta max-active-val max-active-date ccode cnt-countries]
  (let [json (data/json-data)
        {population      :p
         deaths          :d
         recove          :r
         active          :a
         vaccin          :v
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

        last-8 (data/last-8-reports pred-hm json)
        {vaccin-last-8 :v active-last-8 :a confir-last-8 :c} last-8
        [_               & vaccin-last-7] vaccin-last-8
        [active-last-8th & active-last-7] active-last-8
        [_               & confir-last-7] confir-last-8

        active-last-7-with-rate
        ((comp
          utc/sjoin
          (partial map (fn [a c] (format "%s=%s%s"
                                        a
                                        ((com/calc-rate-precision-1 :a)
                                         {:a a :p population :c c})
                                        msgc/percent))))
         active-last-7
         confir-last-7)

        closed (+ deaths recove)
        {delta-deaths :d
         delta-recove :r
         delta-active :a
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
    [#_["%s\n" [(msgc/fmt-to-cols
                 {:emoji "💉" :s lang/vaccinated :n vaccin
                  :diff delta-vaccin :rate v-rate})]]
     #_["%s\n" [(msgc/fmt-to-cols
                 {:s lang/vaccin-per-1e5 :n vaccin-per-100k
                  :diff delta-v100k})]]
     ["%s\n" [(msgc/fmt-to-cols
               {:emoji "🤒" :s lang/active :n active
                :diff delta-active :rate a-rate})]]
     ["%s\n" [(msgc/fmt-to-cols
               {:s lang/active-per-1e5 :n active-per-100k
                :diff delta-a100k})]]
     ["%s\n" [#_(fmt-to-cols
                 {:s lang/active-max
                  :n max-active-val})

              (format
               (str "<code>" "%s" "</code>" msgc/vline
                    "<code>" "%s" "</code>" msgc/vline
                    "(%s)")
               (com/right-pad (str (if nil nil (str msgc/blank msgc/blank))
                                   msgc/blank
                                   lang/active-max)
                              msgc/blank msgc/padding-s)
               (com/left-pad max-active-val msgc/blank msgc/padding-n)
               (com/fmt-date max-active-date))]]

     ;; TODO add effective reproduction number (R)
     #_["%s\n" [(fmt-to-cols
                 {:s lang/active-last-7-med
                  :n (->> active-last-7 (izoo/roll-median 7) (first)
                          (int))})]]
     ["%s\n" [(msgc/fmt-to-cols
               {:s lang/active-last-7-avg
                :n active-last-7-avg})]]
     ["%s\n" [(msgc/fmt-to-cols
               {:s lang/active-change-last-7-avg
                :n active-change-last-7-avg
                :show-plus-minus true})]]
     ["%s\n" [(msgc/fmt-to-cols
               {:emoji "🎉" :s lang/recovered :n recove
                :diff delta-recove :rate r-rate})]]
     ["%s\n" [(msgc/fmt-to-cols
               {:s lang/recove-per-1e5 :n recove-per-100k
                :diff delta-r100k})]]
     ["%s\n" [(msgc/fmt-to-cols
               {:emoji "⚰️" :s lang/deaths :n deaths
                :diff delta-deaths :rate d-rate})]]
     ["%s\n" [(msgc/fmt-to-cols
               {:s lang/deaths-per-1e5 :n deaths-per-100k
                :diff delta-d100k})]]
     ["%s\n" [(msgc/fmt-to-cols
               {:emoji "🏁" :s lang/closed :n closed
                :diff delta-closed :rate c-rate})]]
     ["%s\n\n" [(msgc/fmt-to-cols
                 {:s lang/closed-per-1e5 :n closed-per-100k
                  :diff delta-d100k
                  ;; TODO create command lang/cmd-closed-per-1e5
                  #_#_:desc (com/encode-cmd lang/cmd-closed-per-1e5)})]]
     ["%s\n" [(format
               "<code>%s</code>\n%s"
               (let [emoji "🤒🗓"
                     s lang/active-last-7]
                 (com/right-pad (str (if emoji emoji (str msgc/blank msgc/blank))
                                     msgc/blank s)
                                msgc/blank msgc/padding-s))
               active-last-7-with-rate)]]
     ;; no country ranking can be displayed for worldwide statistics
     (if (msgc/worldwide? ccode)
       ["" [""]]
       ["\n%s\n"
        [(msgc/format-linewise
          [["%s" [lang/people         :p]]
           ["%s" [lang/active-per-1e5 :a100k]]
           ["%s" [lang/recove-per-1e5 :r100k]]
           ["%s" [lang/deaths-per-1e5 :d100k]]
           ["%s" [lang/closed-per-1e5 :c100k]]]
          :line-fmt "%s:<b>%s</b>   "
          :fn-fmts
          (fn [fmts] (format lang/randking-desc
                             cnt-countries (cstr/join "" fmts)))
          :fn-args
          (fn [args] (update args (dec (count args))
                             (fn [_]
                               (get
                                (first
                                 (map :rank
                                      (filter (fn [hm] (= (:ccode hm) ccode))
                                              (data/all-rankings json))))
                                (last args))))))]])]))

(defn last-index-of
  "TODO move last-index-of to https://github.com/Bost/utils"
  [coll elem]
  ((comp last
         (partial keep-indexed (fn [i v] (when (= elem v) i))))
   coll))

;; By default Vars are static, but Vars can be marked as dynamic to
;; allow per-thread bindings via the macro binding. Within each thread
;; they obey a stack discipline:
#_(def ^:dynamic points [[0 0] [1 3] [2 0] [5 2] [6 1] [8 2] [11 1]])
(defn-fun-id create-detailed-info
  "Shows the table with the absolute and %-wise nr of cases, cases per-100k etc.
  TODO 3. show Case / Infection Fatality Rate (CFR / IFR)
  TODO Bayes' Theorem applied to PCR test: https://youtu.be/M8xlOm2wPAA
  (need 1. PCR-test accuracy, 2. Covid 19 disease prevalence)
  TODO create an API web service(s) for every field displayed in the messages
  "
  [json ccode parse_mode pred-hm]
  (let [dates (data/dates json)
        last-report (data/last-report pred-hm json)
        {v-rate :v-rate vaccinated :v population :p confirmed :c} last-report
        delta (data/delta pred-hm json)
        delta-confirmed (:c delta)

        {vaccin-last-8-reports :v} (data/last-8-reports pred-hm json)
        [_                      & vaccin-last-7-reports] vaccin-last-8-reports

        vaccin-last-7-reports-with-rate
        (map (fn [v] (format "%s=%s%s"
                             v
                             (com/vaccination-rate v population)
                             msgc/percent))
             vaccin-last-7-reports)
         ;; delta-vaccinated (:v delta)

        info (format-detailed-info
              (conj
               {:header-txt (msgc/header parse_mode pred-hm json)
                :cname-aliased-txt (ccr/country-name-aliased ccode)
                :country-commands-txt (apply (fn [ccode c3code]
                                               (format "     %s    %s" ccode c3code))
                                             (map (comp com/encode-cmd cstr/lower-case)
                                                  [ccode (ccc/country-code-3-letter ccode)]))
                :cnt-reports-txt (str lang/report " " (count dates))
                :population-txt (format "<code>%s %s</code> = %s %s"
                                        (com/right-pad lang/people " " (- msgc/padding-s 3))
                                        (com/left-pad population " " (+ msgc/padding-n 2))
                                        (utn/round-div-precision population 1e6 1)
                                        lang/millions-rounded)
                :vaccinated-txt
                (if (zero? vaccinated)
                  (format "<code>%s %s</code> - %s"
                          (com/right-pad (str "💉 " lang/vaccinated) " " (- msgc/padding-s 3))
                          (com/left-pad vaccinated " " (+ msgc/padding-n 2))
                          lang/missing-vaccin-data)
                  (format "<code>%s %s %s%s</code>"
                          (com/right-pad (str "💉 " lang/vaccinated) " " (- msgc/padding-s 3))
                          (com/left-pad vaccinated " " (+ msgc/padding-n 2))
                          v-rate
                          msgc/percent))
                :vaccinated-last-7
                (format
                 "<code>%s</code>\n%s"
                 (let [emoji "💉🗓"
                       s lang/vaccin-last-7]
                   (com/right-pad (str (if emoji emoji (str msgc/blank msgc/blank)) msgc/blank s)
                                  msgc/blank msgc/padding-s))
                 (utc/sjoin vaccin-last-7-reports-with-rate))

                :confirmed-txt (msgc/fmt-to-cols {:emoji "🦠" :s lang/confirmed :n confirmed
                                                  :diff delta-confirmed})
                :footer-txt (msgc/footer parse_mode)}
               (when (pos? confirmed)
                 (let [data-active (:a (data/case-counts-report-by-report pred-hm))
                       max-active-val (apply max data-active)]
                   {:details-txt (confirmed-info last-report
                                                 pred-hm
                                                 delta
                                                 max-active-val
                                                 (nth dates
                                                      (last-index-of
                                                       #_utc/last-index-of
                                                       data-active max-active-val))
                                                 ccode
                                                 (count ccc/all-country-codes))}))))]

    (debugf "ccode %s info-size %s" ccode (com/measure info))
    info))

(defn detailed-info
  [ccode & [json parse_mode pred-hm]]
  (let [ks [:msg (keyword ccode)]]
      (if (and json parse_mode pred-hm)
        (cache/cache! (fn [] (create-detailed-info json ccode parse_mode pred-hm))
                      ks)
        (get-in @cache/cache ks))))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.msg.info)
