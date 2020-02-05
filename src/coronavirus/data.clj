(ns coronavirus.data
  (:require [google-apps-clj.google-sheets-v4 :as v4]
            [google-apps-clj.credentials :as c]
            [environ.core :refer [env]]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [camel-snake-kebab.core :as csk]
            [coronavirus.raw-data :as r]
            )
  (:import java.util.GregorianCalendar
           java.util.TimeZone))

(def env-prms [
               :type
               #_:project-id
               :private-key-id
               :private-key
               :client-email
               :client-id
               #_:auth-uri
               #_:token-uri
               #_:auth-provider-x509-cert-url
               #_:client-x509-cert-url
               ])

(defn json-env-prms []
  (as-> env-prms $
       (mapv (fn [prm] {(.replaceAll (name prm) "-" "_")
                       (env prm)})
             $)
       (into {} $)
       (json/write-str $ :escape-slash false)
       (.replaceAll $ "\\\\n" "\\n")))

(defonce service (->> (c/credential-with-scopes
                       (c/credential-from-json (json-env-prms))
                       (set [com.google.api.services.drive.DriveScopes/DRIVE]))
                      (v4/build-service)))

(defonce spreadsheet-id
  #_"1yZv9w9zRKwrGTaR-YzmAqMefw4wMlaXocejdxZaTs6w" ;; old
  "1wQVypefm946ch4XDp37uZ-wartW4V7ILdg-qYiDXUHM")

;; :d stands for Demised or Deaths
(def sheet-structure
  [
   {:sheet "Feb04_10PM"   :range "D2:F" :kws [:c :d :r] :at-once true}
   {:sheet "Feb04_1150AM" :range "D2:F" :kws [:c :d :r] :at-once true}
   {:sheet "Feb04_8AM"    :range "D2:F" :kws [:c :d :r] :at-once true}
   {:sheet "Feb03_940pm"  :range "D2:F" :kws [:c :d :r] :at-once true}
   {:sheet "Feb03_1230pm" :range "D2:F" :kws [:c :d :r] :at-once true}
   {:sheet "Feb02_9PM"    :range "D2:F" :kws [:c :d :r] :at-once true}
   {:sheet "Feb02_745pm"  :range "D2:F" :kws [:c :d :r] :at-once true}
   {:sheet "Feb02_5am"    :range "D2:F" :kws [:c :d :r] :at-once true}
   {:sheet "Feb01_11pm"   :range "D2:F" :kws [:c :d :r] :at-once true}
   {:sheet "Feb01_6pm"    :range "D2:F" :kws [:c :d :r] :at-once true} ;; Starting from this tab, map is updating (almost) in real time (China data - at least once per hour; non China data - several times per day). This table is planning to be updated twice a day. The discrepancy between the map and this sheet is expected. Sorry for any confusion and inconvenience.
   {:sheet "Feb01_10am"   :range "D2:F" :kws [:c :d :r]}
   {:sheet "Jan31_7pm"    :range "D2:F" :kws [:c :d :r]}
   {:sheet "Jan31_2pm"    :range "D2:F" :kws [:c :d :r]}
   {:sheet "Jan30_930pm"  :range "D2:F" :kws [:c :d :r]}
   {:sheet "Jan30_11am"   :range "D2:F" :kws [:c :d :r]}
   {:sheet "Jan29_9pm"    :range "D2:F" :kws [:c :d :r]}
   {:sheet "Jan29_230pm"  :range "D2:F" :kws [:c :d :r]}
   {:sheet "Jan29_130pm"  :range "D2:F" :kws [:c :d :r]}
   {:sheet "Jan28_11pm"   :range "D2:F" :kws [:c :d :r]}
   {:sheet "Jan28_6pm"    :range "D2:F" :kws [:c :d :r]}
   {:sheet "Jan28_1pm"    :range "D2:F" :kws [:c :d :r]}
   {:sheet "Jan27_830pm"  :range "D2:F" :kws [:c :d :r]}
   {:sheet "Jan27_7pm"    :range "D2:F" :kws [:c :d :r]}
   {:sheet "Jan27_9am"    :range "D2:F" :kws [:c :d :r]}
   {:sheet "Jan26_11pm"   :range "D2:F" :kws [:c :d :r]}
   {:sheet "Jan26_11am"   :range "D2:G" :kws [:c :d :r :s]}
   {:sheet "Jan25_10pm"   :range "D2:G" :kws [:c :s :r :d]}
   {:sheet "Jan25_12pm"   :range "D2:G" :kws [:c :s :r :d]}
   {:sheet "Jan25_12am"   :range "D2:G" :kws [:c :s :r :d]}
   {:sheet "Jan24_12pm"   :range "D2:G" :kws [:c :s :r :d]}
   {:sheet "Jan24_12am"   :range "D2:G" :kws [:c :s :r :d]}
   {:sheet "Jan23_12pm"   :range "D2:G" :kws [:c :s :r :d]}
   {:sheet "Jan22_12pm"   :range "D2:E" :kws [:c :s]}
   {:sheet "Jan22_12am"   :range "D2:E" :kws [:c :s] :at-once true}
   ])

(defn get-from-sheet-structure [sheet kw]
  (->> sheet-structure
       (filter (fn [range] (= sheet (:sheet range))))
       (first)
       kw))

(defn transpose [coll] (apply mapv vector coll))

#_Jan22_12pm
#_Jan22_12am
;; (set! *print-level* 3) (set! *print-length* 3)
(set! *print-level* nil) (set! *print-length* nil)

(defn row-count [sheet]
  1000
  #_(->> sheet
       (sheet-id)
       (v4/get-sheet-info service spreadsheet-id)
       (.getGridProperties)
       (.getRowCount)))

(defn sum-up-at-once
  ([sheet]
   (sum-up-at-once sheet 800))
  ([sheet delay]
   (let [range (get-from-sheet-structure sheet :range)
         kws (get-from-sheet-structure sheet :kws)]
     (Thread/sleep delay)
     (let [full-range (str range (row-count sheet))
           sheet-range (str sheet "!" full-range)]
       (let [sheet-vals (->> [sheet-range]
                             (v4/get-cell-values service spreadsheet-id))]
         (println "sum-up-at-once" "sheet-range" sheet-range "kws" kws
                  ;; "sheet-vals" sheet-vals
                  )
         #_(def sheet-vals sheet-vals)
         (->>
          sheet-vals
          (first)
          #_(take 30)
          (filter (fn [row] (some some? row)))  ;; keep only rows containing at least one value
          (vec)
          (mapv (fn [col] (vec (map (fn [v] (if (nil? v) 0
                                              (int v)))
                                   col))))
          (transpose)
          (mapv (fn [v] (apply + v)))
          (zipmap kws)))))))

(defn get-ranges [range]
  (case range
    "D2:E" ["D2:D" "E2:E"]
    "D2:F" ["D2:D" "E2:E" "F2:F"]
    "D2:G" ["D2:D" "E2:E" "F2:F" "G2:G"]
    (throw (Exception. (format "Unrecognized range: %s" range)))))

(defn sum-up-col
  [sheet range row-count kw]
  (let [full-range (str range row-count)
        sheet-range (str sheet "!" full-range)]
    (let [sheet-vals
          (->> [sheet-range]
               (v4/get-cell-values service spreadsheet-id))]
      (println "sum-up-col" "sheet-range" sheet-range "kw" kw
               ;; "sheet-vals" sheet-vals
               )
      #_sheet-vals
      (->>
       sheet-vals
       (first)
       #_(take 30)
       (filter (fn [row] (some some? row)))  ;; keep only rows containing at least one value
       (vec)
       (mapv (fn [col] (vec (map (fn [v] (if (nil? v) 0
                                           (int v)))
                                col))))
       (transpose)
       (mapv (fn [v] (apply + v)))
       (zipmap [kw])))))

(defn sum-up-one-by-one
  ([sheet]
   (sum-up-one-by-one sheet 800))
  ([sheet delay]
   (let [range (get-from-sheet-structure sheet :range)
         ranges (get-ranges range)
         kws (get-from-sheet-structure sheet :kws)
         row-count (row-count sheet)
         ]
     #_ranges
     (->> ranges
          (map-indexed (fn [idx r]
                         (sum-up-col sheet r row-count (nth kws idx))))
          (into {})))))

(defn sum-up
  ([sheet]
   (sum-up sheet 1000))
  ([sheet delay]
   (if-let [at-once (get-from-sheet-structure sheet :at-once)]
     (sum-up-at-once    sheet delay)
     (sum-up-one-by-one sheet delay))))

(defn sheet-titles []
  (->> (v4/get-sheet-titles service spreadsheet-id)
       (map first)
       (sort)))

(def month-nr-hm
  {
  "jan" "01"
  "feb" "02"
  "mar" "03"
  "apr" "04"
  "may" "05"
  "jun" "06"
  "jul" "07"
  "aug" "08"
  "sep" "09"
  "oct" "10"
  "nov" "11"
  "dec" "12"
   })

(defn normal-month [messy-month]
  (if-let [normal-m (get month-nr-hm messy-month)]
    normal-m
    (throw (Exception. (format "Unrecognized month: %s" messy-month)))))

(defn messy-month [normal-month]
  (if-let [messy-m (get (clojure.set/map-invert month-nr-hm) normal-month)]
    messy-m
    (throw (Exception. (format "Unrecognized month: %s" normal-month)))))

(defn time-am-pm [date-time]
  (let [time-am-pm (.substring date-time 6)
        length (count time-am-pm)
        am-pm (.substring time-am-pm (- length 2) length)]
    [
     ;; am-pm
     (if (or (= am-pm "am") (= am-pm "pm"))
       am-pm
       (-> "Unrecognized 12-hour (am/pm) format: %s"
           (format date-time)
           Exception.
           throw))
     ;; time
     (.substring time-am-pm 0 (- length 2))]))

(defn normal-time
  "Remember: Always code as if the person who ends up maintaining your code is a
  violent psychopath who knows where you live

  e.g. (normal-time \"Jan28_11pm\")"
  [messy-date-time]
  (let [
        date-time    (clojure.string/lower-case messy-date-time)
        month        (.substring date-time 0 3)
        month-normal (normal-month month)
        [am-pm time] (time-am-pm date-time)
        time-normal  (case (count time)
                       1 (str "0" time "00") ;; e.g. 1pm
                       2 (str time "00")     ;; e.g. 10pm
                       3 (str "0" time)      ;; e.g. 930pm
                       4 time)               ;; e.g. 1130pm
        s-hour       (.substring time-normal 0 2)
        s-min        (.substring time-normal 2)
        n-hour       (read-string (if (.startsWith s-hour "0")
                                    (.substring s-hour 1)
                                    s-hour))
        hour-normal  (+ n-hour (case am-pm
                                 ;; 12am ~ 00:00 / Midnight / day-start
                                 "am" (if (= 12 n-hour) -12 0)
                                 ;; 12pm ~ 12:00
                                 "pm" (if (= 12 n-hour) 0 12)))
        s-hour-normal (str "0" hour-normal)
        normal        (str (.substring s-hour-normal
                                       (- (count s-hour-normal) 2))
                           s-min)
        ]
    (str
     month-normal
     (.substring date-time 3 6)
     normal)))

(defn last-sheet-of [sheets]
  #_"Jan26_11pm"
  (->> sheets
       (map (fn [hm] (conj hm {:normal (normal-time (:name hm))})))
       (sort-by :normal)
       (last)
       :name))

(defn last-sheet []
  (->> (sheet-titles)
       (map (fn [name] {:name name}))
       (last-sheet-of)))

(defn sheet-id [sheet]
  (v4/find-sheet-id service spreadsheet-id sheet))

(defn get-messy-day [sheet-title]
  (.substring sheet-title 0 5))

(defn sort-messy-days [days]
  (->> days
       (map (fn [d] (str d "_12am"))) ;; start of the day
       (map normal-time)
       (sort)
       (map (fn [dt]
              (str (->> (.substring dt 0 2)
                        (messy-month)
                        (csk/->PascalCase))
                   (.substring dt 2 4))))))

(defn sum-up-sheets-key [key sheets]
  {key (->> sheets
            (map (fn [sheet] (->> sheet :count key)))
            (apply +))})

(defn sum-up-sheets [sheets]
  (->> [:d :c :r]
       (map (fn [k] (sum-up-sheets-key k sheets)))
       (apply conj)))

(defn create-hms-day-sheets
  "Returns e.g
  [{:day \"Feb04\" :normal \"0204_0000\"
  :sheets [{:name \"Feb04_8AM\" :normal \"0204_0800\"}
           {:name \"Feb04_10PM\" :normal \"0204_2200\"}
           {:name \"Feb04_1150AM\" :normal \"0204_1150\"}]}]"
  [sheets]
  (->> sheets
       (map get-messy-day)
       (distinct)
       (sort-messy-days)
       (mapv (defn messy-day-sheets [messy-day]
               #_(println "messy-day" messy-day)
               {:day messy-day
                :normal (normal-time (str messy-day "_12am"))
                :sheets
                (->> sheets
                     (filterv (fn [sheet] (= (get-messy-day sheet)
                                                  messy-day)))
                     (mapv (fn [sheet] {:name sheet
                                       :normal (normal-time sheet)})))}))))

(defn create-date [messy-day]
  (let [n-day (read-string (.substring messy-day 3))
        month (->> (.substring messy-day 0 3)
                   (clojure.string/lower-case)
                   (normal-month)
                   (read-string)
                   )
        year 2020
        time-zone
        #_(TimeZone/getTimeZone "Europe/Berlin")
        (TimeZone/getTimeZone "Europe/London")
        c (GregorianCalendar.)
        ]
    (.clear c)
    (.setTimeZone c time-zone)
    (.set c year (dec month) n-day 0 0 0)
    (.getTime c)))

(defn missing-sheets
  "Returns a set of all missing sheets
  e.g. #{\"Feb04_8AM\" \"Feb04_10PM\" \"Feb04_1150AM\"}"
  []
  (let [old-sheet-titles (->> @r/hms-day-sheets
                              (map :sheets)
                              (flatten)
                              (map :name))]
    (clojure.set/difference (set (sheet-titles))
                            (set old-sheet-titles))))

(defn count-date--hm-day-sheets [{:keys [day sheets] :as hm-day-sheets}]
  (conj hm-day-sheets
        {:count
         (let [last-sheet-of (last-sheet-of sheets)]
           (->> sheets
                (filter (fn [name-count-hm]
                          (= last-sheet-of (:name name-count-hm))))
                (map :count)
                (into {})))}
        {:date (create-date day)}))

(defn add-count-sheet [{:keys [name delay] :as sheet}]
  (conj sheet {:count (sum-up name delay)}))

(defn last-date-count []
  (let [last-day (->> @r/hms-day-sheets
                      #_(map (fn [hm-day-sheets] (select-keys hm-day-sheets [:day :normal :count])))
                      (sort-by :normal)
                      (last)
                      :day
                      )]
    (select-keys (->> @r/hms-day-sheets
                      (filter (fn [hm] (= (:day hm) last-day)))
                      (first))
                 [:date :count])))

(defn calc-count-per-messy-day
  "Calculate the counts per messy-day"
  []
  #_(if-let [sheets-to-calculate (not-empty
                                #_(set ["Feb01_10am"])
                                #_(set ["Jan22_12am"])
                                #_(set ["Jan24_12pm"])
                                ;; calculate only missing sheets
                                (missing-sheets)
                                ;; (re-)calculate all sheets
                                #_(set (sheet-titles))
                                ;; (re-)calculate only the last sheet
                                #_(set (take-last 2 (sheet-titles))))]
    #_[{:day "Feb04",
        :sheets
        [{:name "Feb04_8AM", :count {:c 20680, :d 427, :r 723}}
         {:name "Feb04_10PM", :count {:c 24503, :d 492, :r 899}}
         {:name "Feb04_1150AM", :count {:c 20704, :d 427, :r 727}}],
        :count {:c 24503, :d 492, :r 899},
        :date #inst "2020-02-04T00:00:00.000-00:00"}]
    (->>
     ;; e.g
     ;; #{"Feb04_8AM" "Feb04_10PM" "Feb04_1150AM"}
     sheets-to-calculate
     ;; e.g.
     ;; [{:day "Feb04" :normal "0204_0000"
     ;;   :sheets [{:name "Feb04_8AM"}
     ;;            {:name "Feb04_10PM"}
     ;;            {:name "Feb04_1150AM"}]}]
     (create-hms-day-sheets)

     ;; e.g.
     ;; [{:day "Feb04" :normal "0204_0000"
     ;;   :sheets [{:name "Feb04_8AM"    :count {:c 20680 :d 427 :r 723}}
     ;;            {:name "Feb04_10PM"   :count {:c 24503 :d 492 :r 899}}
     ;;            {:name "Feb04_1150AM" :count {:c 20704 :d 427 :r 727}}]}]
     (mapv (fn [{:keys [sheets] :as hm-day-sheets}]
             (conj hm-day-sheets
                   {:sheets (mapv add-count-sheet (conj sheets {:delay 0}))})))
     (mapv count-date--hm-day-sheets))
    ;; TODO update the @r/hms-day-sheets
    )

  ;; get the counts from
  (last-date-count))
