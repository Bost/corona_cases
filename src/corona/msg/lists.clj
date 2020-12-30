;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.msg.lists)

(ns corona.msg.lists
  (:require [clojure.string :as cstr]
            [corona.api.expdev07 :as data]
            [corona.common :as com]
            [corona.countries :as ccr]
            [corona.lang :as lang]
            [corona.msg.common :as msgc]
            [taoensso.timbre :as timbre :refer [debugf]]))

(defn calc-list-countries
  "Listing commands in the message footer correspond to the columns in the listing.
  See also `footer`, `bot-father-edit-cmds`."
  ([case-kw msg-idx prm] (calc-list-countries "calc-list-countries" case-kw msg-idx prm))
  ([fun-id case-kw msg-idx {:keys [cnt-msgs cnt-reports data parse_mode pred-hm]}]
   (let [header-txt (msgc/header parse_mode pred-hm)
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
                (->> data
                     #_(take-last 11)
                     #_(partition-all 2)
                     #_(map (fn [part] (cstr/join "       " part))))))
          ""
          #_(if (= msg-idx cnt-msgs)
              (str "\n\n" (lang/list-sorted-by-desc case-kw))
              "")
          (msgc/footer parse_mode))]
     (debugf "[%s] case-kw %s msg-idx %s msg-size %s"
             fun-id case-kw msg-idx (count msg))
     msg)))

(defn get-from-cache! [case-kw ks fun msg-idx prm]
  (if prm
    (data/cache! (fn [] (fun case-kw msg-idx prm))
                 (conj ks (keyword (str msg-idx))))
    (vals (get-in @data/cache ks))))

(defn list-countries
  [case-kw & [msg-idx prm]]
  (get-from-cache! case-kw [:list :countries case-kw]
                   calc-list-countries msg-idx prm))

(defn calc-list-per-100k
  "Listing commands in the message footer correspond to the columns in the
  listing. See also `footer`, `bot-father-edit-cmds`."
  ([case-kw msg-idx prm] (calc-list-per-100k "calc-list-per-100k"
                                             case-kw msg-idx prm))
  ([fun-id case-kw msg-idx {:keys [cnt-msgs cnt-reports data parse_mode pred-hm]}]
   (let [header-txt (msgc/header parse_mode pred-hm)
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
                (->> data
                     #_(take-last 11)
                     #_(partition-all 2)
                     #_(map (fn [part] (cstr/join "       " part))))))
          ""
          #_(if (= msg-idx cnt-msgs)
              (str "\n\n" (lang/list-sorted-by-desc case-kw))
              "")
          (msgc/footer parse_mode))]
     (debugf "[%s] case-kw %s msg-idx %s msg-size %s"
             fun-id case-kw msg-idx (count msg))
     msg)))

(defn list-per-100k
  [case-kw & [msg-idx prm]]
  (get-from-cache! case-kw [:list :100k case-kw]
                   calc-list-per-100k msg-idx prm))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.msg.lists)

