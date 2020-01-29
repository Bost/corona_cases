(ns coronavirus.data
  (:require [google-apps-clj.google-sheets-v4 :as v4]
            [google-apps-clj.credentials :as c]
            [environ.core :refer [env]]
            [clojure.data.json :as json]
            ))

(defn json-env-prms []
  (as-> [
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
         ] $
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

(defn sum-up [sheet range]
  (Thread/sleep 1000)
  (->> [(str sheet "!" range)]
       (v4/get-cell-values service spreadsheet-id)
       (flatten)
       (remove nil?)
       (apply +)
       (int)))

(defn confirmed-sum [sheet range] (sum-up sheet (str "D2:D" range)))
(defn deaths-sum    [sheet range] (sum-up sheet (str "E2:E" range)))
(defn recovered-sum [sheet range] (sum-up sheet (str "F2:F" range)))

(defn sheet-titles []
  (->> (v4/get-sheet-titles service spreadsheet-id)
       (map first)
       (sort)))

(defn normal-month [month]
  (case month
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
    (throw (Exception. (format "Unrecognized month: %s" month)))))

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
     (.substring time-am-pm 0 (- length 2))])
)
(defn normal-time
  "Remember: Always code as if the person who ends up maintaining your code is a
  violent psychopath who knows where you live"
  [messy-formatted-date-time]
  (let [
        date-time    (clojure.string/lower-case messy-formatted-date-time)
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

(defn last-sheet []
  (->> (sheet-titles)
       (map (fn [name] {:name name}))
       (map (fn [hm] (conj hm {:normal-time (normal-time (:name hm))})))
       (sort-by :normal-time)
       (last)
       :name))

(defonce sheet-id (v4/find-sheet-id service
                                    spreadsheet-id
                                    (last-sheet)))
(defn row-count []
  (->> (v4/get-sheet-info service spreadsheet-id sheet-id)
       (.getGridProperties)
       (.getRowCount)))

(defn calc-per-sheet [sheet]
  (let [range (row-count)]
    {:d (deaths-sum    sheet range)
     :c (confirmed-sum sheet range)
     :r (recovered-sum sheet range)}))

(defn day [sheet-title]
  (.substring sheet-title 0 5))

(defn sheet-names-of-day [di]
  (filterv (fn [st] (= (day st) di)) (sheet-titles)))

(defn days [] (distinct (map day (sheet-titles))))

(defn document []
  (mapv (fn [day]
          (println "day" day)
          {:day day
           :sheets (mapv (fn [sheet-name] {:name sheet-name})
                         (sheet-names-of-day day))})
        (days)))

(defn graph []
  (mapv (fn [day-sheets-hm]
          (let [{day :day sheets :sheets} day-sheets-hm]
            {:day day
             :sheets (mapv (fn [name-hm]
                             (let [{sheet-name :name} name-hm]
                               (conj name-hm
                                     {:count (calc-per-sheet sheet-name)})))
                           sheets)}))
        (document)))

(def data
 [{:day "Feb01",
   :sheets
   [{:name "Feb01_10am", :count {:d 259, :c 12024, :r 287}}
    {:name "Feb01_6pm", :count {:d 259, :c 12037, :r 284}}]}
  {:day "Jan22",
   :sheets
   [{:name "Jan22_12am", :count {:d 169, :c 332, :r 0}}
    {:name "Jan22_12pm", :count {:d 137, :c 555, :r 0}}]}
  {:day "Jan23",
   :sheets [{:name "Jan23_12pm", :count {:d 144, :c 653, :r 30}}]}
  {:day "Jan24",
   :sheets
   [{:name "Jan24_12am", :count {:d 115, :c 881, :r 34}}
    {:name "Jan24_12pm", :count {:d 159, :c 941, :r 36}}]}
  {:day "Jan25",
   :sheets
   [{:name "Jan25_10pm", :count {:d 406, :c 2019, :r 49}}
    {:name "Jan25_12am", :count {:d 73, :c 1354, :r 38}}
    {:name "Jan25_12pm", :count {:d 404, :c 1438, :r 39}}]}
  {:day "Jan26",
   :sheets
   [{:name "Jan26_11am", :count {:d 56, :c 2116, :r 52}}
    {:name "Jan26_11pm", :count {:d 80, :c 2794, :r 54}}]}
  {:day "Jan27",
   :sheets
   [{:name "Jan27_7pm", :count {:d 82, :c 2927, :r 61}}
    {:name "Jan27_830pm", :count {:d 107, :c 4473, :r 63}}
    {:name "Jan27_9am", :count {:d 81, :c 2886, :r 59}}]}
  {:day "Jan28",
   :sheets
   [{:name "Jan28_11pm", :count {:d 132, :c 6057, :r 110}}
    {:name "Jan28_1pm", :count {:d 106, :c 4690, :r 79}}
    {:name "Jan28_6pm", :count {:d 131, :c 5578, :r 107}}]}
  {:day "Jan29",
   :sheets
   [{:name "Jan29_130pm", :count {:d 132, :c 6164, :r 112}}
    {:name "Jan29_230pm", :count {:d 133, :c 6165, :r 126}}
    {:name "Jan29_9pm", :count {:d 170, :c 7783, :r 133}}]}
  {:day "Jan30",
   :sheets
   [{:name "Jan30_11am", :count {:d 171, :c 8235, :r 143}}
    {:name "Jan30_930pm", :count {:d 213, :c 9776, :r 187}}]}
  {:day "Jan31",
   :sheets
   [{:name "Jan31_2pm", :count {:d 213, :c 9926, :r 222}}
    {:name "Jan31_7pm", :count {:d 259, :c 11374, :r 252}}]}])

(defn sum-up-sheets-key [key sheets]
  {key (->> sheets
            (map (fn [sheet] (->> sheet :count key)))
            (apply +))})

(defn sum-up-sheets [sheets]
  (->> [:d :c :r]
       (map (fn [k] (sum-up-sheets-key k sheets)))
       (apply conj)))

(defn all-sheet-names []
  (->> data
       (map :sheets)
       flatten
       (map :name)
       (map normal-time)))

(defn last-sheet-of [sheets]
  #_"Jan26_11pm"
  (->> sheets
       (map (fn [hm] (conj hm {:normal-time (normal-time (:name hm))})))
       (sort-by :normal-time)
       (last)
       :name
       ))

(defn graph-sums
  "Calculate the data"
  []
  (mapv (fn [day-sheets-hm]
          (conj day-sheets-hm
                {:count
                 (let [{day :day sheets :sheets} day-sheets-hm]
                   (let [last-sheet-of (last-sheet-of sheets)]
                     (->> sheets
                          (filter (fn [name-count-hm]
                                    (= last-sheet-of (:name name-count-hm))))
                          (map :count)
                          (into {}))))}))
        data
        #_(graph)))
