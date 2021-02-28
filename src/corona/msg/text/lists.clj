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

(defn get-from-cache! [case-kw json msg-idx prm quoted-ns-qualif-fun]
  (let [full-kws [:list (keyword (:name (meta (find-var quoted-ns-qualif-fun))))
                  case-kw]]
    (if (and json msg-idx prm)
      (cache/cache! (fn []
                      ((eval quoted-ns-qualif-fun) case-kw json msg-idx prm))
                    (conj full-kws (keyword (str msg-idx))))
      (vals (get-in @cache/cache full-kws)))))

(defn calc-listings
  [case-kws json fun]
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
             (map-indexed
              (fn [idx sub-msg]
                (get-from-cache!
                 case-kw json (inc idx) (conj prm {:data sub-msg}) fun))
              sub-msgs))))
        case-kws))

(defn-fun-id absolute-vals
  "Listing commands in the message footer correspond to the columns in the
  listing. See also `footer`, `bot-father-edit`."
  [case-kw json msg-idx {:keys [cnt-msgs data parse_mode pred-hm]}]
  (let [pred-json-hm (assoc pred-hm :json json)
        cnt-reports (count (data/dates json))
        header-txt (msgc/header parse_mode pred-json-hm)
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
    (debugf "case-kw %s msg-idx %s msg-size %s"
            case-kw msg-idx (com/measure msg))
    msg))

(defn-fun-id per-100k
  "Listing commands in the message footer correspond to the columns in the
  listing. See also `footer`, `bot-father-edit`."
  [case-kw json msg-idx {:keys [cnt-msgs data parse_mode pred-hm]}]
  (let [pred-json-hm (assoc pred-hm :json json)
        cnt-reports (count (data/dates json))
        header-txt (msgc/header parse_mode pred-json-hm)
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
    (debugf "case-kw %s msg-idx %s msg-size %s"
            case-kw msg-idx (com/measure msg))
    msg))

(defmulti  list-cases (fn [listing-cases-per-100k?] listing-cases-per-100k?))

(defmethod list-cases true  [_]
  (fn [case-kw & [json msg-idx prm]]
    (get-from-cache! case-kw json msg-idx prm 'corona.msg.text.lists/per-100k)))

(defmethod list-cases false [_]
  (fn [case-kw & [json msg-idx prm]]
    (get-from-cache! case-kw json msg-idx prm 'corona.msg.text.lists/absolute-vals)))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.msg.text.lists)
