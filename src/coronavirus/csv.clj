(ns coronavirus.csv
  (:require [clojure.data.csv :as dcsv]
            #_[clojure-csv.core :as ccsv]
            [clojure.java.io :as io]
            [clojure.string :as s]))

(defn fix-octal-val
  "(read-string s-day \"08\") produces a NumberFormatException
  https://clojuredocs.org/clojure.core/read-string#example-5ccee021e4b0ca44402ef71a"
  [s]
  (clojure.string/replace s #"^0+" ""))

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

(defn getc [[_ _ _ c _ _]] c)
(defn getd [[_ _ _ _ d _]] d)
(defn getr [[_ _ _ _ _ r]] r)

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
  (->> csv-files
       (map (fn [file] (sum-up-file sum-up-fn file)))))

(defn get-counts []
  (map (fn [f c d r] {:f
                     (subs f (inc (s/last-index-of f "/")))
                     :c c :d d :r r})
       csv-files
       (sum-up getc)
       (sum-up getd)
       (sum-up getr)))

