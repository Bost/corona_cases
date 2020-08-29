(ns corona.api.beds
  (:require [hickory.core :refer [parse as-hickory]]
            [hickory.select :as s]
            [clj-http.client :as client]
            [clojure.string :as string]))

(def ^:const url
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

;; (def wiki-page
;;   "We want this data, but it's only published as HTML."
;;   (slurp "https://en.wikipedia.org/wiki/List_of_countries_by_hospital_beds"))

;; (defn deepest-text
;;   "Drill down to the deepest text node(s) and return them as a string."
;;   [node]
;;   (cond (vector? node) (apply str (mapcat deepest-text node))
;;         (map? node) (deepest-text (:content node))
;;         :else node))

;; (defn extract-tables [html]
;;   (mapv (fn [table]
;;           (mapv #(mapv deepest-text
;;                        (s/select (s/or (s/tag :th) (s/tag :td)) %))
;;                 (s/select (s/tag :tr) table)))
;;         (->> html hick/parse hick/as-hickory (s/select (s/tag :table)))))

;; (drop 3 (first (extract-tables wiki-page)))
