;; ;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.msg.text.common)

(ns corona.msg.text.common
  (:require [clojure.string :as cstr]
            [corona.api.expdev07 :as data]
            [corona.common :as com]
            [corona.country-codes :as ccc]
            [corona.lang :as lang]
            [utils.num :as utn]
            [utils.core :as utc]))

;; (set! *warn-on-reflection* true)

(def ^:const padding-s "Stays constant" (+ 3 9))

;; The worldwide population has 3 commas:
;; (count (re-seq #"," (format "%+,2d" 7697236610))) ; => 3

(def ^:const max-diff-order-of-magnitude 8)

(def blank
  " "
  #_"\u2004" ;; U+2004 	&#8196 	Three-Per-Em Space
  #_"\u2005" ;; U+2005 	&#8197 	Four-Per-Em Space
  #_"\u2006" ;; U+2006 	&#8198 	Six-Per-Em Space
  #_" " ;; U+2009 	&#8201 	Thin Space
  )

(def vline
  " " ;; U+2009 	&#8201 	Thin Space
  #_" "
  #_blank)

(def percent "%") ;; "\uFF05" "\uFE6A"

(defn pos-neg [n]
  (cond
    (pos? n) "📈"
    (neg? n) "📉"
    :else " "))

(defn plus-minus
  "Display \"+0\" when n is zero"
  [n padding-len]
  (com/left-pad
   (if (zero? n)
     "+0"
     (str
      #_(cond (pos? n) "↑"
              (neg? n) "↓"
              :else "")
      (cond (pos? n) "+"
            :else "")
      n
      #_(pos-neg n)))
   " " padding-len))

(defn fmt-to-cols
  "Info-message numbers of aligned to columns for better readability"
  [{:keys [emoji s n diff]}]
  ["%s\n"
   [
    (let [padding-len (inc max-diff-order-of-magnitude)
          s-shift     ""
          non-s-shift "  "]
      (format
       (str
        "<code>" "%s" "</code>" #_vline
        "<code>" "%s" vline "%s" #_vline #_"%s" "</code>" vline
        "%s")
       (str (if emoji emoji (str blank blank)) vline #_blank)
       (com/left-pad (if (and (not= n com/unknown) (>= n 1e6))
                       (str (utn/round-div-precision n 1e6 1) "m")
                       (str n s-shift))
                     blank
                     #_max-diff-order-of-magnitude
                     (- padding-len (count non-s-shift)))
       #_(com/left-pad (if rate (str rate percent) blank) blank 3)
       (cond
         (= diff com/unknown)
         (com/left-pad diff blank padding-len)

         (nil? diff)
         (com/left-pad "" blank padding-len)

         :else (plus-minus diff padding-len))
       s))]])

(defn format-linewise
  "
  line-fmt e.g.: \"%s: %s\n\"
  The count of parameters for every line must be equal to the count of \"%s\"
  format specifiers in the line-fmt and the count must be at least 1.
  Note that at the moment only \"%s\" is allowed.

  lines is a matrix of the following shape:
  [[fmt0 [v00 ... v0N]]
   ...
   [fmtM [vM0 ... vMN]]]
  E.g.:

  (format-linewise
   [[\"1. %s \\n\" [\"a\" 0]]
    [\"2. %s \\n\" [\"b\" 1]]
    [\"3. %s \\n\" [\"c\" 2]]]
   :line-fmt \"%s: %s\")

  fn-fmts is a fn of one arg - a vector of strings containing \"%s\"
  fn-args is a fn of one arg
  "
  [lines & {:keys [line-fmt fn-fmts fn-args]
            :or {line-fmt "%s"
                 fn-fmts identity
                 fn-args identity}}]
  {:pre [(let [cnt-fmt-specifiers (count (re-seq #"%s" line-fmt))]
           (and (pos? cnt-fmt-specifiers)
                (apply = cnt-fmt-specifiers
                       (map (fn [line] (count (second line))) lines))))]}
  (apply format
         (->> lines (map first) (fn-fmts) (reduce str))
         (map (fn [line] (apply format line-fmt
                               (fn-args (second line))))
              lines)))

(defn header [parse_mode pred-hm json]
  (format
   (str
    "🗓 "
    (condp = parse_mode
      com/html "<b>%s</b>"
      ;; i.e. com/markdown
      "*%s*")
    " 🦠 @%s")
   (com/fmt-date (:t (data/last-report pred-hm json)))
   (condp = parse_mode
     com/html com/bot-name
     com/bot-name-in-markdown)))

(defn footer
  "Listing commands in the message footer correspond to the columns in the listing.
  See also `absolute-vals`, `bot-father-edit`."
  [parse_mode]
  (let [spacer "  "]
    (str
     ;; "Try" spacer
     (->> [lang/world lang/explain]
          (map com/encode-cmd)
          (map (fn [cmd] (com/encode-pseudo-cmd cmd parse_mode)))
          (cstr/join spacer))
     spacer
     "\n"
     ;; lang/listings ":  "
     (->> (concat com/listing-cases-per-100k
                  com/listing-cases-absolute)
          (mapv lang/list-sorted-by)
          (map com/encode-cmd)
          (cstr/join spacer)))))

(defn worldwide? [ccode]
  (utc/in? [ccc/worldwide-2-country-code ccc/worldwide-3-country-code
            ccc/worldwide] ccode))

;; ;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.msg.text.common)
