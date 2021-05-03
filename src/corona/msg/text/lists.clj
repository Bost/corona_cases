;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.msg.text.lists)

(ns corona.msg.text.lists
  (:require [clojure.string :as cstr]
            [corona.api.cache :as cache]
            [corona.api.expdev07 :as data]
            [corona.common :as com]
            [corona.countries :as ccr]
            [corona.country-codes :as ccc]
            [corona.lang :as lang]
            [corona.macro :refer [defn-fun-id debugf]]
            [corona.msg.text.common :as msgc]
            [taoensso.timbre :as timbre]))

(def ^:const cnt-messages-in-listing
  "nr-countries / nr-patitions : 126 / 6, 110 / 5, 149 / 7"
  7)

(defn list-kw [fun case-kw]
  [:list ((comp keyword :name meta find-var) fun) case-kw])

(defn calc-listings! "" [stats prm case-kws fun]
  ;; fun is on of: per-100k, absolute-vals - TODO spec it!
  ((comp
    doall
    (partial
     map
     (fn [case-kw]
       (let [coll (sort-by
                   ((comp
                     (fn [kw] (get {:r :er
                                   :a :ea
                                   :r100k :er100k
                                   :a100k :ea100k
                                   :s :es}
                                  kw kw)))
                    case-kw)
                   < stats)
             ;; Split the long list of all countries into smaller sub-parts
             sub-msgs (partition-all (/ (count coll)
                                        cnt-messages-in-listing) coll)
             sub-msgs-prm (assoc prm :cnt-msgs (count sub-msgs))]
         ((comp
           doall
           (partial map-indexed
                    (fn [idx sub-msg]
                      ((comp
                        (partial cache/cache!
                                 (fn [] ((eval fun) case-kw
                                        (assoc sub-msgs-prm
                                               :msg-idx (inc idx)
                                               :data sub-msg))))
                        (partial conj (list-kw fun case-kw))
                        keyword
                        str)
                       idx))))
          sub-msgs)))))
   case-kws))

(defn column-label
  "I.e. a header for a column table - see corona.msg.text.details/label-val"
  [lense-fun text sorted-case-kw case-kw]
  ;; TODO number of deaths is not estimated
  (str (lense-fun :s lang/hm-estimated) text
       (if (= sorted-case-kw case-kw)
         "▴" #_" " #_"▲"
         " ")))

(defn-fun-id absolute-vals
  "Listing commands in the message footer correspond to the columns in the
  listing. See also `footer`, `bot-father-edit`."
  [case-kw {:keys [msg-idx cnt-msgs data cnt-reports lense-fun header footer]}]
  #_(debugf "case-kw %s" case-kw)
  (let [spacer " "
        omag-active 7 ;; omag - order of magnitude i.e. number of digits
        omag-recove (inc omag-active)
        omag-deaths (dec omag-active)
        msg
        (format
         (msgc/format-linewise
          [["%s\n"    [header]]
           ["%s\n"    [(format "%s %s;  %s/%s" lang/report cnt-reports msg-idx cnt-msgs)]]
           ["    %s " [(column-label lense-fun lang/active :a case-kw)]]
           ["%s"      [spacer]]
           ["%s "     [(column-label lense-fun lang/recovered :r case-kw)]]
           ["%s"      [spacer]]
           ["%s\n"    [(column-label lense-fun lang/deaths :d case-kw)]]
           ["%s"      [(str
                        "%s"     ; listing table
                        "%s"     ; sorted-by description; has its own new-line
                        "\n\n%s" ; footer
                        )]]])
         ((comp
           (partial cstr/join "\n")
           (partial map (fn [{:keys [a r d ccode]}]
                          (let [cname (ccr/country-name-aliased ccode)]
                            (format "<code>%s%s%s%s%s %s</code>  %s"
                                    (com/left-pad a " " omag-active)
                                    spacer
                                    (com/left-pad r " " omag-recove)
                                    spacer
                                    (com/left-pad d " " omag-deaths)
                                    (com/right-pad cname 17)
                                    (cstr/lower-case (com/encode-cmd ccode)))))))
          data)
         ""
         footer)]
    (debugf "case-kw %s msg-idx %s msg-size %s"
            case-kw msg-idx (com/measure msg))
    msg))

(defn-fun-id per-100k
  "Listing commands in the message footer correspond to the columns in the
  listing. See also `footer`, `bot-father-edit`."
  [case-kw {:keys [msg-idx cnt-msgs data cnt-reports lense-fun header footer]}]
  #_(debugf "case-kw %s" case-kw)
  (let [spacer " "
        omag-active 4 ;; omag - order of magnitude i.e. number of digits
        omag-recove omag-active
        omag-deaths (dec omag-active)
        msg
        (format
         (msgc/format-linewise
          [["%s\n" [header]]
           ["%s\n" [(format "%s %s;  %s/%s" lang/report cnt-reports msg-idx cnt-msgs)]]
           ["%s "  [(column-label lense-fun lang/active-per-1e5 :a100k case-kw)]]
           ["%s"   [spacer]]
           ["%s "  [(column-label lense-fun lang/recove-per-1e5 :r100k case-kw)]]
           ["%s"   [spacer]]
           ["%s"   [(column-label lense-fun lang/deaths-per-1e5 :d100k case-kw)]]
           ["\n%s" [(str
                     "%s"     ; listing table
                     "%s"     ; sorted-by description; has its own new-line
                     "\n\n%s" ; footer
                     )]]])
         ((comp
           (partial cstr/join "\n")
           (partial map (fn [{:keys [d100k ccode] :as hm}]
                          (let [a100k (lense-fun :a100k hm)
                                r100k (lense-fun :r100k hm)
                                cname (ccr/country-name-aliased ccode)]
                            (format "<code>   %s%s   %s%s    %s %s</code>  %s"
                                    (com/left-pad a100k " " omag-active)
                                    spacer
                                    (com/left-pad r100k " " omag-recove)
                                    spacer
                                    (com/left-pad d100k " " omag-deaths)
                                    (com/right-pad cname 17)
                                    (cstr/lower-case (com/encode-cmd ccode)))))))
          data)
         ""
         footer)]
    (debugf "case-kw %s msg-idx %s msg-size %s"
            case-kw msg-idx (com/measure msg))
    msg))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.msg.text.lists)
