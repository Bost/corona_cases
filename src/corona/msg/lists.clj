;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.msg.lists)

(ns corona.msg.lists
  (:require [clojure.string :as cstr]
            [corona.api.cache :as cache]
            [corona.api.expdev07 :as data]
            [corona.common :as com]
            [corona.countries :as ccr]
            [corona.country-codes :as ccc]
            [corona.lang :as lang]
            [corona.macro :refer [defn-fun-id]]
            [corona.msg.common :as msgc]
            [taoensso.timbre :as timbre :refer [debugf]]))

(def ^:const cnt-messages-in-listing
  "nr-countries / nr-patitions : 126 / 6, 110 / 5, 149 / 7"
  7)

(defn calc-listings
  [json fun case-kws]
  (run! (fn [case-kw]
         (let [coll (sort-by case-kw < (data/stats-countries json))
                ;; Split the long list of all countries into smaller sub-parts
               sub-msgs (partition-all (/ (count coll)
                                          cnt-messages-in-listing) coll)
               prm {:parse_mode com/html
                    :cnt-msgs (count sub-msgs)
                    :pred-hm ((comp
                               data/create-pred-hm
                               ccr/get-country-code)
                              ccc/worldwide)}]
           (doall
            (map-indexed (fn [idx sub-msg]
                           (fun case-kw json (inc idx) (conj prm {:data sub-msg})))
                         sub-msgs))))
       case-kws))

(defn-fun-id calc-list-countries
  "Listing commands in the message footer correspond to the columns in the listing.
  See also `footer`, `bot-father-edit-cmds`."
  [case-kw json msg-idx {:keys [cnt-msgs data parse_mode pred-hm]}]
  (let [cnt-reports (count (data/dates json))
        header-txt (msgc/header parse_mode pred-hm json)
        spacer " "
        sort-indicator "▴" ;; " " "▲"
        omag-active 7 ;; order of magnitude i.e. number of digits
        omag-recov  (inc omag-active)
        omag-deaths (dec omag-active)

        msg
        (format
         (msgc/format-linewise
          [["%s\n"   [header-txt]]
           ["%s\n"   [(format "%s %s;  %s/%s" lang/report cnt-reports msg-idx cnt-msgs)]]
           ["    %s " [(str lang/active    (if (= :a case-kw) sort-indicator " "))]]
           ["%s"     [spacer]]
           ["%s "    [(str lang/recovered (if (= :r case-kw) sort-indicator " "))]]
           ["%s"     [spacer]]
           ["%s\n"   [(str lang/deaths    (if (= :d case-kw) sort-indicator " "))]]
           ["%s"     [(str
                       "%s"   ; listing table
                       "%s"   ; sorted-by description; has its own new-line
                       "\n\n"
                       "%s"   ; footer
                       )]]])
         (cstr/join
          "\n"
          (map (fn [{:keys [a r d ccode]}]
                 (let [cname (ccr/country-name-aliased ccode)]
                   (format "<code>%s%s%s%s%s %s</code>  %s"
                           (com/left-pad a " " omag-active)
                           spacer
                           (com/left-pad r " " omag-recov)
                           spacer
                           (com/left-pad d " " omag-deaths)
                           (com/right-pad cname 17)
                           (cstr/lower-case (com/encode-cmd ccode)))))
               data))
         ""
         (msgc/footer parse_mode))]
    (debugf "[%s] case-kw %s msg-idx %s msg-size %s"
            fun-id case-kw msg-idx (com/measure msg))
    msg))

(defn get-from-cache! [case-kw json msg-idx prm ks fun]
  (if (and json msg-idx prm)
    (cache/cache! (fn [] (fun case-kw json msg-idx prm))
                  (conj ks (keyword (str msg-idx))))
    (vals (get-in @cache/cache ks))))

(defn list-countries
  [case-kw & [json msg-idx prm]]
  (get-from-cache! case-kw json msg-idx prm
                   [:list :countries case-kw] calc-list-countries))

(defn-fun-id calc-list-per-100k
  "Listing commands in the message footer correspond to the columns in the
  listing. See also `footer`, `bot-father-edit-cmds`."
  [case-kw json msg-idx {:keys [cnt-msgs data parse_mode pred-hm]}]
  (let [cnt-reports (count (data/dates json))
        header-txt (msgc/header parse_mode pred-hm json)
        spacer " "
        sort-indicator "▴" ;; " " "▲"
         ;; omag - order of magnitude i.e. number of digits
        omag-active-per-100k 4
        omag-recove-per-100k omag-active-per-100k
        omag-deaths-per-100k (dec omag-active-per-100k)
        msg
        (format
         (msgc/format-linewise
          [["%s\n" [header-txt]]
           ["%s\n" [(format "%s %s;  %s/%s"
                            lang/report cnt-reports msg-idx cnt-msgs)]]
           ["%s "  [(str lang/active-per-1e5
                         (if (= :a100k case-kw) sort-indicator " "))]]
           ["%s"   [spacer]]
           ["%s "  [(str lang/recove-per-1e5
                         (if (= :r100k case-kw) sort-indicator " "))]]
           ["%s"   [spacer]]
           ["%s"   [(str lang/deaths-per-1e5
                         (if (= :d100k case-kw) sort-indicator " "))]]
           ["\n%s" [(str
                     "%s"     ; listing table
                     "%s"     ; sorted-by description; has its own new-line
                     "\n\n%s" ; footer
                     )]]])
         (cstr/join
          "\n"
          (map (fn [{:keys [a100k r100k d100k ccode]}]
                 (let [cname (ccr/country-name-aliased ccode)]
                   (format "<code>   %s%s   %s%s    %s %s</code>  %s"
                           (com/left-pad a100k " " omag-active-per-100k)
                           spacer
                           (com/left-pad r100k " " omag-recove-per-100k)
                           spacer
                           (com/left-pad d100k " " omag-deaths-per-100k)
                           (com/right-pad cname 17)
                           (cstr/lower-case (com/encode-cmd ccode)))))
               data))
         ""
         (msgc/footer parse_mode))]
    (debugf "[%s] case-kw %s msg-idx %s msg-size %s"
            fun-id case-kw msg-idx (com/measure msg))
    msg))

(defn list-per-100k
  [case-kw & [json msg-idx prm]]
  (get-from-cache! case-kw json msg-idx prm
                   [:list :100k case-kw] calc-list-per-100k))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.msg.lists)

