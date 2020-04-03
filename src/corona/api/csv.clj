(ns corona.csv
  (:require [clojure.data.csv :as dcsv]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [corona.common :as com]
            [corona.core :as c]
            [corona.countries :as cr])
  (:import java.text.SimpleDateFormat))

;; find all the CSV files in the directory
(def csv-files
  (->> (file-seq (io/file "resources/csv"))
       (filter #(.isFile %))
       (map str)
       (filter #(re-find #".csv" %))
       sort))

;; TODO read the resources/COVID-19/master.zip + delete the csv-files
(defn take-csv
  "Takes file name and reads data."
  [fname]
  (with-open [file (io/reader fname)]
    #_(-> file (slurp) (ccsv/parse-csv))
    (-> file (dcsv/read-csv) (doall))))

(defn getc [[_ _ _ c _ _]] c)
(defn getd [[_ _ _ _ d _]] d)
(defn getr [[_ _ _ _ _ r]] r)
(defn geti [[_ _ _ c d r]]
  (let [[nc nd nr] (map c/read-number [c d r])]
    (c/calculate-ill nc nr nd)))

(defn sum-up-file-de [{:keys [sum-up-fn pred file] :as prm}]
  (->> file take-csv rest
       #_(take-last 1)
       (filter (fn [[_ country-name _ c _ _]]
                 (->> country-name
                      com/country-code
                      pred)))))

(defn sum-up-file [{:keys [sum-up-fn pred file] :as prm}]
  #_(println "prm" prm)
  (->> (sum-up-file-de prm)
       (transduce
        (comp
         (map sum-up-fn)
         (map c/fix-octal-val)
         (remove empty?)
         (map read-string))
        + 0)))

(defn sum-up [prm]
  (map (fn [file] (sum-up-file (assoc prm :file file)))
       csv-files))

(defn get-counts [{:keys [pred] :as prm}]
  (map (fn [f c d r i] {:f
                       (let [date (subs f (inc (s/last-index-of f "/")))
                             sdf (new SimpleDateFormat "MM-dd-yyyy")]
                         (.parse sdf date))
                       :c c :d d :r r :i i})
       csv-files
       (sum-up (assoc prm :sum-up-fn getc))
       (sum-up (assoc prm :sum-up-fn getd))
       (sum-up (assoc prm :sum-up-fn getr))
       (sum-up (assoc prm :sum-up-fn geti))))

;; http://blog.cognitect.com/blog/2017/6/5/repl-debugging-no-stacktrace-required
(defn confirmed [prm] (map :c (get-counts prm)))
(defn deaths    [prm] (map :d (get-counts prm)))
(defn recovered [prm] (map :r (get-counts prm)))
(defn ill       [prm] (map :i (get-counts prm)))
(defn dates     []    (map :f (get-counts {:pred (fn [_] true)})))

(defn last-day  [prm]
  #_(last (get-counts prm))
  (->> (get-counts prm)
       (take-last 1)
       first))

(defn prev-day  [prm]
  (->> (get-counts prm)
       (take-last 2)
       first))

(defn delta [prm]
  (let [last-d (last-day prm)
        prev-d (prev-day prm)]
    (map (fn [l p])) last-d prev-d))

(defn all-affected-country-codes
  []
  (->> csv-files
       #_(take 2)
       (map take-csv)
       (map rest)
       (reduce into [])
       (map second)
       (into #{})
       (map com/country-code)
       sort
       vec
       (into cr/default-affected-country-codes)))
