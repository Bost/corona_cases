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

(defn calc-listings! "" [stats {:keys [lense-fun] :as prm} case-kws fun]
  ;; fun is on of: per-1e5, absolute-vals - TODO spec it!
  ((comp
    doall
    (partial
     map
     (fn [case-kw]
       (let [lensed-case-kw (lense-fun case-kw)]
         (let [coll ((comp
                      (partial sort-by (com/unlense lensed-case-kw) <))
                     stats)
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
            sub-msgs))))))
   case-kws))

(defn sort-sign [sorted-case-kw case-kw]
  (if (= sorted-case-kw case-kw)
    "▴" #_" " #_"▲"
    " "))

(defn column-label
  "I.e. a header for a column table - see corona.msg.text.details/label-val"
  [lense-fun hm-text sorted-case-kw case-kw]
  (str (get-in hm-text (lense-fun sorted-case-kw))
       (sort-sign sorted-case-kw case-kw)))

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
           ["    %s " [(column-label lense-fun lang/hm-active :a case-kw)]]
           ["%s"      [spacer]]
           ["%s "     [(column-label lense-fun lang/hm-recovered :r case-kw)]]
           ["%s"      [spacer]]
           ["%s\n"    [(str lang/deaths (sort-sign :d case-kw))]]
           ["%s"      [(str
                        "%s"     ; listing table
                        "%s"     ; sorted-by description; has its own new-line
                        "\n\n%s" ; footer
                        )]]])
         ((comp
           (partial cstr/join "\n")
           (partial map (fn [{:keys [d ccode] :as hm}]
                          (let [a (get-in hm (lense-fun :a))
                                r (get-in hm (lense-fun :r))
                                cname (ccr/country-name-aliased ccode)]
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

(defn-fun-id per-1e5
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
           ["%s "  [(column-label lense-fun lang/hm-active-per-1e5 :a1e5 case-kw)]]
           ["%s"   [spacer]]
           ["%s "  [(column-label lense-fun lang/hm-recove-per-1e5 :r1e5 case-kw)]]
           ["%s"   [spacer]]
           ["%s"   [(str lang/deaths-per-1e5 (sort-sign :d1e5 case-kw))]]
           ["\n%s" [(str
                     "%s"     ; listing table
                     "%s"     ; sorted-by description; has its own new-line
                     "\n\n%s" ; footer
                     )]]])
         ((comp
           (partial cstr/join "\n")
           (partial map (fn [{:keys [d1e5 ccode] :as hm}]
                          (let [a1e5 (get-in hm (lense-fun :a1e5))
                                r1e5 (get-in hm (lense-fun :r1e5))
                                cname (ccr/country-name-aliased ccode)]
                            (format "<code>   %s%s   %s%s    %s %s</code>  %s"
                                    (com/left-pad a1e5 " " omag-active)
                                    spacer
                                    (com/left-pad r1e5 " " omag-recove)
                                    spacer
                                    (com/left-pad d1e5 " " omag-deaths)
                                    (com/right-pad cname 17)
                                    (cstr/lower-case (com/encode-cmd ccode)))))))
          data)
         ""
         footer)]
    (debugf "case-kw %s msg-idx %s msg-size %s"
            case-kw msg-idx (com/measure msg))
    msg))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.msg.text.lists)
