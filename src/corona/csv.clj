(ns corona.csv
  (:require [clojure.data.csv :as dcsv]
            #_[clojure-csv.core :as ccsv]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [corona.countries :as co]
            [corona.core :as c])
  (:import java.text.SimpleDateFormat))

;; get all the file names in one directory
(def directory (io/file "resources/csv"))
(def files (filter #(.isFile %) (file-seq directory)))
(def filestr (map str files))
;; find all the xls files in the directory
(def csv-files (->> filestr
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
(defn geti [[_ _ u c d r]]
  (let [[nc nd nr] (map c/read-number [c d r])]
    (c/calculate-ill nc nr nd)))

(defn sum-up-file [{:keys [sum-up-fn pred-csv pred file] :as prm}]
  (->> file take-csv rest
       #_(take 1)
       (filter (fn [[_ loc _ c _ _]]
                 (if-not pred-csv
                   (println "sum-up-fn" sum-up-fn))
                 (if-not loc
                   (println "loc" loc))
                 #_(println c loc (pred-csv loc))
                 (pred-csv loc)))
       (transduce
        (comp
         (map sum-up-fn)
         (map c/fix-octal-val)
         (remove empty?)
         (map read-string))
        + 0)))

(defn sum-up [prm]
  (->> csv-files
       #_(take 4)
       #_(take-last 1)
       (map (fn [file] (sum-up-file (assoc prm :file file))))))

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
       (sum-up (assoc prm :sum-up-fn geti))
       ))

;; http://blog.cognitect.com/blog/2017/6/5/repl-debugging-no-stacktrace-required
(defn confirmed [prm] (map :c (get-counts prm)))
(defn deaths    [prm] (map :d (get-counts prm)))
(defn recovered [prm] (map :r (get-counts prm)))
(defn ill       [prm] (map :i (get-counts prm)))
(defn dates     []    (map :f (get-counts {:pred (fn [_] true) :pred-csv (fn [_] true)})))

(defn last-day  [prm] (last (get-counts prm)))

(def url "https://github.com/CSSEGISandData/COVID-19")

(defn all-affected-country-codes
  []
  (->> csv-files
       #_(take 2)
       (map take-csv)
       (map rest)
       (reduce into [])
       (map second)
       (into #{})
       (map co/country_code)
       sort
       vec
       (into co/default-affected-country-codes)))
