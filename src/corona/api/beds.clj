(ns corona.api.beds
  (:use [hickory.core])
  (:require [hickory.select :as s]
            [clj-http.client :as client]
            [clojure.string :as string]))

(def url
  "https://en.wikipedia.org/wiki/Template:Hospital_beds_by_country")

(def site (-> (client/get url) :body parse as-hickory))

(defn h []
  (as-> (s/select (s/child
                 #_(s/class "subCalender") ; sic
                 #_(s/tag :table)
                 #_(s/tag :tbody)
                 (s/tag :tr)
                 #_(s/id :raceDates)
                 #_s/first-child
                 #_(s/tag :b))
                site) $
      ;; (nth 0) ;; first ; table header
      ;; (nth 1 );; second row
      ;; (nth 2) ;; years
      (drop 3 $)
      #_(take 1 $)
      #_(nth 3)
      (mapv (fn [row]
             (as-> (:content row) $
               (filter (fn [hm]
                         (let [content (:content hm)]
                           (and (vector? content)
                                (= 1 (count content))
                                (string? (first content))
                                ))) $)
               (into [] $)
               (nth $ 6) ; 7th column is the year 2017 - now every
               (:content $)
               (keep (fn [s]
                      (let [s-nr (subs s 0 (- (count s) 1))]
                        (if (empty? s-nr)
                          0
                          (read-string s-nr))))

                     $)
               )) $)
      (reduce into [] $)))
