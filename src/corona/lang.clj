;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.lang)

(ns corona.lang
  (:require
   [corona.common :as com]
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

;; :s as in 'String'
(def ^:const hm-estimated        {:s "" :es "*"})

(def ^:const hm-rec              {:r "Rec"       :er "*Rec"})
(def ^:const hm-recov            {:r "Recov"     :er "*Recov"})
(def ^:const hm-recove           {:r "Recove"    :er "*Recove"})
(def ^:const hm-recovered        {:r "Recovered" :er "*Recovered"})
(def ^:const hm-act              {:a "Act"       :ea "*Act"})
(def ^:const hm-active           {:a "Active"    :ea "*Active"})
(def ^:const hm-clo              {:c "Clo"       :ec "*Clo"})
(def ^:const hm-closed           {:c "Closed"    :ec "*Closed"})

(def ^:const vac              "Vac")
(def ^:const vacc             "Vacc")
(def ^:const vaccinated       "Vaccinated")
(def ^:const conf             "Conf")
(def ^:const confir           "Confir")
(def ^:const confirmed        "Confirmed")
(def ^:const deaths           "Deaths")
(def ^:const rec              ((com/ident-fun :r) hm-rec))
(def ^:const recov            ((com/ident-fun :r) hm-recov))
(def ^:const recove           ((com/ident-fun :r) hm-recove))
(def ^:const recovered        ((com/ident-fun :r) hm-recovered))
(def ^:const act              ((com/ident-fun :a) hm-act))
(def ^:const active           ((com/ident-fun :a) hm-active))
(def ^:const clo              ((com/ident-fun :c) hm-clo))
(def ^:const closed           ((com/ident-fun :c) hm-closed))


(def ^:const active-cases     (format "%s %s" active cases))
(def ^:const recovered-cases  (format "%s %s" recovered cases))
(def ^:const closed-cases     (format "%s %s" closed cases))
(def ^:const confirmed-cases  (format "%s %s" confirmed cases))
(def ^:const vaccinated-cases "Vaccinations")
(def ^:const death-cases      (format "%s %s" "Death" cases))

;; TODO recov-estim & friends are used in the graphs
(def ^:const recov-estim      "Recov. estimated")
(def ^:const activ-estim      "Active estimated")
(def ^:const close-estim      "Closed estimated")

(def ^:const people "The word 'Population' is too long. See `fmt-to-cols`"  "People")

(def ^:const hundred-k        "100k")
(def ^:const cmd-active-per-1e5 (str "a" hundred-k))
(def ^:const cmd-recove-per-1e5 (str "r" hundred-k))
(def ^:const cmd-deaths-per-1e5 (str "d" hundred-k))
(def ^:const cmd-closed-per-1e5 (str "c" hundred-k))
(def ^:const cmd-vaccin-per-1e5 (str "v" hundred-k))
(def ^:const cases-per-1e5 "Cases per 100 000 people")

(def ^:const vaccin-per-1e5 "Vaccinations per 100 000"    (str vac hundred-k))
(def ^:const active-per-1e5 "Active cases per 100 000"    (str act hundred-k))
(def ^:const recove-per-1e5 "Recovered cases per 100 000" (str rec hundred-k))
(def ^:const deaths-per-1e5 "Deaths per 100 000"          (str "Dea" hundred-k))
(def ^:const closed-per-1e5 "Closed per 100 000"          (str clo hundred-k))

(def ^:const active-max "Peak of active cases from all reports" (format "Max %s" active))
(def ^:const deaths-max "Peak of deaths from all reports"       (format "Max %s" deaths))

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

(def ^:const L7 "L7")
(def ^:const active-last-7 "Active cases in last 7 reports" (str act L7))
(def ^:const vaccin-last-7 "Vaccinations in last 7 reports" (str vac L7))

(def ^:const missing-vaccin-data
  (format "No data for today. See <code>%s</code>:" vaccin-last-7))

(def ^:const active-last-7-med
  "Active cases in last 7 reports - simple moving Median rounded"
  (str active-last-7 "Med"))

(def ^:const avg "Avg")
(def ^:const active-last-7-avg
  "Active cases in last 7 reports - simple moving Average rounded" (str active-last-7 avg))

(def ^:const active-last-8th "Active cases of the last 8th report"
  (str act "L8th"))
(def ^:const active-change-last-7-avg "Active cases Change - simple moving Average of last 7 values"
  (str act "C" L7 avg))

(def ^:const write-a-message-to
  "Just write a message to @RostislavSvoboda thanks.")

(def ^:const data-source-text
  (str
   "Data provided by "
   "[Our World in Data]"
   "(https://github.com/owid/covid-19-data/tree/master/public/data)"
   " and "
   "[JHU CSSE](https://github.com/CSSEGISandData/COVID-19)"
   ", via "
   "[ExpDev07/coronavirus-tracker-api]"
   "(https://github.com/ExpDev07/coronavirus-tracker-api) service"))

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
    (partial com/text-for-case case-kw))
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
  (zipmap com/absolute-cases ["Co" "Re" "De" "Ac"]))

(def ^:const aggregations "Aggregations for worldwide graphs"
  (zipmap com/aggregation-cases ["Σ" "A"]))

(defn button-text [case-kw aggregation-kw]
  (str (get short-case-name case-kw)
       (get aggregations aggregation-kw)))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.lang)
