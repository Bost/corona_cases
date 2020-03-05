(ns corona.csv
  (:require [clojure.data.csv :as dcsv]
            #_[clojure-csv.core :as ccsv]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [corona.core :refer [read-number]])
  (:import java.text.SimpleDateFormat))

;; get all the file names in one directory
(def directory (io/file "resources/csv"))
(def files (filter #(.isFile %) (file-seq directory)))
(def filestr (map str files))
;; find all the xls files in the directory
(def csv-files (->> filestr
                    (filter #(re-find #".csv" %))
                    sort))

(defn take-csv
  "Takes file name and reads data."
  [fname]
  (with-open [file (io/reader fname)]
    #_(-> file (slurp) (ccsv/parse-csv))
    (-> file (dcsv/read-csv) (doall))))

(defn calculate-ill [c r d] (- c (+ r d)))

(defn getc [[_ _ _ c _ _]] c)
(defn getd [[_ _ _ _ d _]] d)
(defn getr [[_ _ _ _ _ r]] r)
(defn geti [[_ _ u c d r]]
  (let [[nc nd nr] (map read-number [c d r])]
    (calculate-ill nc nr nd)))

(defn sum-up-file [sum-up-fn file]
  (transduce
   (comp
    (map sum-up-fn)
    (map fix-octal-val)
    (remove empty?)
    (map read-string))
   + 0
   (->> file take-csv rest)))

(defn sum-up [sum-up-fn]
  (map (fn [file] (sum-up-file sum-up-fn file))
       csv-files))

(defn get-counts []
  (map (fn [f c d r i] {:f
                       (let [date (subs f (inc (s/last-index-of f "/")))
                             sdf (new SimpleDateFormat "MM-dd-yyyy")]
                         (.parse sdf date))
                       :c c :d d :r r :i i})
       csv-files
       (sum-up getc)
       (sum-up getd)
       (sum-up getr)
       (sum-up geti)))

(defn confirmed [] (map :c (get-counts)))
(defn deaths    [] (map :d (get-counts)))
(defn recovered [] (map :r (get-counts)))
(defn ill       [] (map :i (get-counts)))
(defn dates     [] (map :f (get-counts)))
(defn last-day  [] (last (get-counts)))

(def url "https://github.com/CSSEGISandData/COVID-19")
