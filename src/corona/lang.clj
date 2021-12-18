;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.lang)

(ns corona.lang
  (:require
   [corona.common :as com :refer [makelense
                                  basic-lense
                                  kact krec kclo kdea kest kmax krep k1e5
                                  kchg kls7 kabs kavg]]
   [corona.cases :as cases]
   [clojure.string :as cstr]
   ))

;; (set! *warn-on-reflection* true)

(def ^:const tcmd           "tcmd")

;; A文 doesn't get displayed blue as a telegram command. Hmm
;; :language     "lang"
(def ^:const percentage-calc  "Percentage calculation")
(def ^:const cases            "cases")
(def ^:const ranking-desc     "Ranking among %s countries:\n%s")
(def ^:const world            "world")
(def ^:const world-desc       "Start here")
(def ^:const start            "start")
(def ^:const explain          "explain")
(def ^:const contributors     "contributors")
(def ^:const feedback         "feedback")

;; recov-estim & friends are used in the graphs
(def ^:const recov-estim      "Recov. estimated")
(def ^:const activ-estim      "Active estimated")
(def ^:const close-estim      "Closed estimated")

(def ^:const estimated-values "* Estimated values")
;; :s as in 'String'

(def ^:const people
  "The word 'Population' is too long. See `fmt-to-cols`"
  "People")

(def ^:const hundred-k        "100k")
(def ^:const cmd-active-per-1e5 (str "a" hundred-k))
(def ^:const cmd-recove-per-1e5 (str "r" hundred-k))
(def ^:const cmd-deaths-per-1e5 (str "d" hundred-k))
(def ^:const cmd-closed-per-1e5 (str "c" hundred-k))
(def ^:const cmd-vaccin-per-1e5 (str "v" hundred-k))
(def ^:const cases-per-1e5 "Cases per 100 000 people")

(def ^:const L7 "L7")
(def ^:const L14 "L14")
(def ^:const avg "Avg")

(def ^:const hm-estimated {:s "" :es "*"})

(def ^:const hm-rec       {krec {krep {kabs "Rec"}
                                 kest {kabs "*Rec"}}})
(def ^:const hm-recov     {krec {krep {kabs "Recov"}
                                 kest {kabs "*Recov"}}})
(def ^:const hm-recove    {krec {krep {kabs "Recove"}
                                 kest {kabs "*Recove"}}})
(def ^:const hm-recovered {krec {krep {kabs "Recovered"}
                                 kest {kabs "*Recovered"
                                       k1e5 "*Rec100k"}}})
(def ^:const hm-act       {kact {krep {kabs "Act"}
                                 kest {kabs "*Act"}}})

(def ^:const hm-active    {kact {krep {kabs "Active"
                                       k1e5 "Act100k"
                                       kls7 {kabs {kabs "ActL7"
                                                   kavg "ActL7Avg"}
                                             kchg {kavg "ActCL7Avg"}}}
                                 kest {kabs "*Active"
                                       k1e5 "*Act100k"
                                       kmax "*Max Active"
                                       kls7 {kabs {kabs "*ActL7"
                                                   kavg "*ActL7Avg"}
                                             kchg {kavg "*ActCL7Avg"}}}}})

(def ^:const hm-clo       {kclo {krep {kabs "Clo"}
                                 kest {kabs "*Clo"}}})
(def ^:const hm-closed    {kclo {krep {kabs "Closed"}
                                 kest {kabs "*Closed"
                                       k1e5 "*Clo100k"}}})
(def ^:const hm-deaths    {kdea {krep {kabs "Deaths"
                                       k1e5 "Dea100k"}}})

(def ^:const estim-active-last-7-avg
  "Estimated active cases in last 7 reports - simple moving Average rounded"
  (get-in hm-active
          (makelense kact kest kls7 kabs kavg)))

(def ^:const active-last-7-avg
  "Active cases in last 7 reports - simple moving Average rounded"
  ;; remove leading asterisk '*'
  (subs estim-active-last-7-avg 1))

(def ^:const vac              "Vac")
(def ^:const vacc             "Vacc")
(def ^:const vaccinated       "Vaccinated")
(def ^:const conf             "Conf")
(def ^:const confir           "Confir")
(def ^:const confirmed        "Confirmed")
(def ^:const dea              "Dea")
(def ^:const deaths           "Deaths")
(def ^:const rec              (get-in hm-rec       (basic-lense krec)))
(def ^:const recov            (get-in hm-recov     (basic-lense krec)))
(def ^:const recove           (get-in hm-recove    (basic-lense krec)))
(def ^:const recovered        (get-in hm-recovered (basic-lense krec)))
(def ^:const act              (get-in hm-act       (basic-lense kact)))
(def ^:const active           (get-in hm-active    (basic-lense kact)))
(def ^:const clo              (get-in hm-clo       (basic-lense kclo)))
(def ^:const closed           (get-in hm-closed    (basic-lense kclo)))

(def ^:const active-cases     (format "%s %s" active cases))
(def ^:const recovered-cases  (format "%s %s" recovered cases))
(def ^:const closed-cases     (format "%s %s" closed cases))
(def ^:const confirmed-cases  (format "%s %s" confirmed cases))
(def ^:const vaccinated-cases "Vaccinations")
(def ^:const death-cases      (format "%s %s" "Death" cases))

(def ^:const vaccin-per-1e5 "Vaccinations per 100 000"    (str vac hundred-k))
(def ^:const active-per-1e5 "Active cases per 100 000"    (str act hundred-k))
(def ^:const recove-per-1e5 "Recovered cases per 100 000" (str rec hundred-k))
(def ^:const deaths-per-1e5 "Deaths per 100 000"          (str dea hundred-k))
(def ^:const closed-per-1e5 "Closed per 100 000"          (str clo hundred-k))

(def ^:const estim-active-last-7 "Estimated active cases in last 7 reports"
  (get-in hm-active
          (makelense kact kest kls7 kabs kabs)))

;; TODO active-last-7 is only for the backward compatibility, under /explain
(def ^:const active-last-7 "Active cases in last 7 reports"
  (subs estim-active-last-7 1))

(def ^:const report
  "Coincidentally there is 1 report per day"
  "Day\\Report")
(def ^:const absolute        "absolute")
(def ^:const active-absolute (format "%s %s" active absolute))
(def ^:const vaccin-data-not-published "Vaccination data not published yet")
(def ^:const stats           "Stats")
(def ^:const list-desc       "List of countries")
(def ^:const listings        "lists")

;; https://en.wikipedia.org/wiki/Moving_average#Moving_median
;; simple moving median SMM

;; https://en.wikipedia.org/wiki/Moving_average#Simple_moving_average
;; simple moving average SMA

(def ^:const rate-of-people    (str "% of " people))
(def ^:const rate-of-confirmed (str "% of " confirmed))

(defn incidence [nr-of-reports]
  (str "Incidence"
       (case nr-of-reports
         7 L7
         14 L14
         (throw (Exception.
                 (format "Wrong nr-of-reports %s - must be 7 or 14"
                         nr-of-reports))))))

(def ^:const vaccin-last-7 "Vaccinations in last 7 reports" (str vac L7))

(def ^:const missing-vaccin-data
  (format "No data for today. See <code>%s</code>:" vaccin-last-7))

(def ^:const active-last-7-med
  "Active cases in last 7 reports - simple moving Median rounded"
  (str active-last-7 "Med"))

(def ^:const active-last-8th
  "Active cases of the last 8th report"
  (str act "L8th"))

(def ^:const active-change-last-7-avg
  "Active cases Change - simple moving Average of last 7 values"
  (str act "C" L7 avg))

(def ^:const write-a-message-to
  "Just write a message to @RostislavSvoboda thanks.")

(def ^:const data-source-text
  (str
   "Data provided by "
   "[Our World in Data]"
   "(https://github.owid/covid-19-data/tree/master/public/data)"
   " and "
   "[JHU CSSE](https://github.CSSEGISandData/COVID-19)"
   ", via "
   "[ExpDev07/coronavirus-tracker-api]"
   "(https://github.ExpDev07/coronavirus-tracker-api) service"))

(def ^:const contributors-text
  (str
   "The rest of the contributors prefer anonymity or haven't "
   "approved their inclusion to this list yet. 🙏 Thanks folks."))

(def ^:const estim-motivation
  (str
   "For many countries the recovery data is missing or incomplete therefore we "
   "provide" ))

(def ^:const recov-estim-explained
  (str
   "Estimated number of recovered cases, based on the average number "
   "of days between symptoms outbreak and full recovery being"))

(def ^:const activ-estim-explained
  (str
   "Estimated number of active cases, based on the average number of days "
   "between symptoms outbreak and death being"))

(def ^:const millions-rounded "Mill")

(defn- lower-case-texts [case-kw texts]
  ((comp
    cstr/lower-case
    (partial cases/text-for-case case-kw))
   texts))

(defn list-sorted-by [case-kw]
  (lower-case-texts
   case-kw
   ;; TODO the order matters
   [
    conf recov deaths active
    cmd-active-per-1e5 cmd-recove-per-1e5 cmd-deaths-per-1e5
    vacc
    ]))

(defn list-sorted-by-desc [case-kw]
  ((comp
    (partial format "Countries sorted by nr. of %s" #_"... in ascending order")
    (partial lower-case-texts case-kw))
   [vaccinated-cases confirmed-cases recovered-cases deaths active-cases
    cmd-vaccin-per-1e5 active-per-1e5 recove-per-1e5 deaths-per-1e5]))

(def ^:const short-case-name "Shortened case names"
  (zipmap cases/absolute-cases ["Co" "Re" "De" "Ac"]))

(def ^:const aggregations "Aggregations for worldwide graphs"
  (zipmap cases/aggregation-cases ["Σ" "A"]))

(defn button-text [case-kw aggregation-kw]
  (str (get short-case-name case-kw)
       (get aggregations aggregation-kw)))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.lang)
