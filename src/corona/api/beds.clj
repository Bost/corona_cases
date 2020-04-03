(ns corona.api.beds
  (:require [hickory.core :refer [parse as-hickory]]
            [hickory.select :as s]
            [clj-http.client :as client]
            [clojure.string :as string]))

(def url
  "https://en.wikipedia.org/wiki/Template:Hospital_beds_by_country")

(def site (-> (client/get url) :body parse as-hickory))

(defn h ;; FIXME descriptive name
  ;; TODO docstring / document return value -- what does this seq of numbers represent?
  []
  (as-> (s/select (s/child
                   (s/tag :tr))
                  site) $
    (drop 3 $)
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
                    $))) $)
    (reduce into [] $)))
