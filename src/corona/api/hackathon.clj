(ns corona.api.hackathon
  (:require
   [corona.common :as co]
   [corona.country-codes :as cc]
   ))

(defn data
  "Iterate over all countries and
  {
  :confirmed
  {
  :last_updated	\"2020-03-24T20:40:57.586773Z\"
  :latest	378547
  :locations \"[...]\"
  }
  :deaths    nil
  :latest    nil
  :recovered nil
  }
  "
  []
  (->>
   #_(cc/all-country-codes)
   #_(take 3)
   [
    "DE"
    #_"SK"]
   (map (fn [country-code]
           ;; convert to 3 letter code
           (as-> (cc/country-code-3-letter country-code) $
             (format "https://corona.ndo.dev/api/timespan?country=%s&time=year" $)
             (co/get-json $)
             (:timeseries $)
             (map :data $)
             (map (fn [m]
                    (as-> m $$
                      (filter (fn [m]
                                (=
                                 #_"https://raw.githubusercontent.com/jgehrcke/covid-19-germany-gae/master/data.csv"
                                 "https://github.com/CSSEGISandData/COVID-19"
                                 (:url m)))
                              $$)
                      (map (fn [m] (select-keys m [:cases :recovered :deaths :active])) $$)))
                  $)
             (reduce into [] $)
             #_(first $)
             #_(apply merge-with + $))))))
