(ns coronavirus.telegram
  (:require [clojure.core.async :refer [<!!]]
            [clojure.string :as str]
            [environ.core :refer [env]]
            [morse.handlers :as h]
            [morse.polling :as p]
            [morse.polling-patch :as p-patch]
            [morse.api :as a]
            [google-apps-clj.credentials]
            [google-apps-clj.google-sheets-v4 :as v4]
            [clojure.java.io :as io]
            [clj-time-ext.core :as te]
            [clj-time.core :as t]
            [com.hypirion.clj-xchart :as c]
            [clojure.tools.logging :as log]
            [clojure.core.async :as async]
            [coronavirus.data :as d]
            [coronavirus.raw-data :as r]
            [clojure.core.memoize :as memo]
            )
  (:gen-class))

(def token (env :telegram-token))

(def bot-ver
  (str
   (let [pom-props
         (with-open
           [pom-props-reader
            (->> "META-INF/maven/coronavirus/coronavirus/pom.properties"
                 io/resource
                 io/reader)]
           (doto (java.util.Properties.)
             (.load pom-props-reader)))]
     (get pom-props "version"))
   "-"
   (env :bot-ver)))

(defn telegram-token-suffix []
  (let [suffix (.substring token (- (count token) 3))]
    (if (or (= suffix "Fq8") (= suffix "MR8"))
      suffix
      (throw (Exception.
              (format "Unrecognized TELEGRAM_TOKEN suffix: %s" suffix))))))

(def bot-type
  (let [suffix (telegram-token-suffix)]
    (case suffix
      "Fq8" "PROD"
      "MR8" "TEST")))

(def bot
  (let [suffix (telegram-token-suffix)]
    (format "%s:%s"
            bot-ver
            bot-type)))

#_(log/info "Telegram Chatbot:" bot)

;; TODO I hope the headers don't change!
(def confirmed-header
  (memo/memo
   (defn confirmed-header-query [sheet]
     (println "confirmed-header-query")
     (let [column "D"]
       (->>
        [(str sheet "!" column "1:" column "1")]
        (v4/get-cell-values d/service d/spreadsheet-id)
        (flatten)
        (apply str))))))

(def deaths-header
  (memo/memo
   (defn deaths-header-query[sheet]
     (println "deaths-header-query")
     (let [column "E"]
       (->>
        [(str sheet "!" column "1:" column "1")]
        (v4/get-cell-values d/service d/spreadsheet-id)
        (flatten)
        (apply str))))))

(def recovered-header
  (memo/memo
   (defn recovered-header-query [sheet]
     (println "recovered-header-query")
     (let [column "F"]
       (->> [(str sheet "!" column "1:" column "1")]
            (v4/get-cell-values d/service d/spreadsheet-id)
            (flatten)
            (apply str))))))

(def msg-footer (str
                 "\n"
                 "Available commands:  /refresh   /about"
                 #_(str "Response generated in " " seconds")))

(defn get-percentage
  ([place total-count] (get-percentage :normal place total-count))
  ([mode place total-count]
   (let [percentage (/ (* place 100.0) total-count)]
     (condp = mode
       :high     (int (Math/ceil  percentage))
       :low      (int (Math/floor percentage))
       :normal   (int (Math/round percentage))
       (throw (Exception. "ERROR: get-percentage [:high|:low|:normal] <PLACE> <TOTAL_COUNT>"))))))

(defn info-msg []
  (let [sheet (d/last-sheet)]
    #_(println "sheet" sheet)
    (let [{confirmed :c deaths :d recovered :r} (:count (d/calc-count-per-messy-day))]
      (str
       "\n"
       ;; TODO fix this
       (str "Feb15_820PM" " EST (Eastern Time Zone):") "\n"
       (confirmed-header sheet) ": " confirmed "\n"
       (deaths-header    sheet) ": " deaths
       "  ~  " (get-percentage deaths confirmed) "%\n"
       (recovered-header sheet) ": " recovered
       "  ~  " (get-percentage recovered confirmed) "%\n"
       msg-footer))))

(defn link [name url] (str "[" name "]""(" url ")"))

(defn pic-data []
  (->> @r/hms-day-sheets
       (map (fn [hm]
              (select-keys hm [:date :count])))
       (sort-by :date)))

(defn get-vals [pic-data k]
  (->> pic-data
       (map (fn [hm] (->> hm :count k)))
       (map (fn [v] (if (nil? v) 0 v)))))

(defn pic []
  (let [pic-data (pic-data)
        dates (map :date pic-data)]
    (-> (c/xy-chart
         (conj {}
               {"Confirmed"
                (conj {}
                      {:x dates
                       :y (get-vals pic-data :c)
                       :style
                       (conj {}
                             {:marker-type :none}
                             #_{:render-style :line}
                             #_{:line-color :orange}
                             #_{:fill-color :orange}
                             )})}
               {"Deaths"
                (conj {}
                      {:x dates
                       :y (get-vals pic-data :d)}
                      {:style
                       (conj {}
                             {:marker-type :none}
                             {:render-style :line}
                             #_{:line-color :red})
                       })}
               {"Recovered"
                (conj {}
                      {:x dates
                       :y (get-vals pic-data :r)}
                      {:style
                       (conj {}
                             {:marker-type :none}
                             {:render-style :line}
                             #_{:line-color :green})})})
         (conj {}
               {:title "Coronavirus (2019-nCoV) - see the /about command"
                :render-style :area
                :legend {:position :inside-nw}
                :x-axis {:title "Day"}
                :y-axis {:title "Cases"}}
               #_{:theme :matlab}
               #_{:width 640 :height 500}
               {:width 800 :height 600}
               {:date-pattern "MMMd"}))
        #_(c/view)
        (c/to-bytes :png))))

(defn register-cmd [cmd cmd-fn]
  (h/command-fn
   cmd
   (fn [{{id :id :as chat} :chat}]
     (let [tbeg (te/tnow)]
       (println (str "[" tbeg "           " " " bot-ver " /" cmd "]") chat)
       (cmd-fn id)
       (println (str "[" tbeg ":" (te/tnow) " " bot-ver " /" cmd "]") chat)))))

(defn refresh-cmd-fn [id]
  (a/send-text token id (info-msg))
  (a/send-photo token id (pic)))

(defn about-cmd-fn [id]
  #_(a/send-photo token id
                  (io/input-stream -stream "/path/to/photo.png"))
  (a/send-text
   token id
   {:parse_mode "Markdown"}
   (str
    "Bot version: " bot-ver "\n"
    "Percentage calculation: <cases> / confirmed\n"
    "See "
    (link "data source" "https://github.com/CSSEGISandData/COVID-19"
          #_(str "https://docs.google.com/spreadsheets/d/"
               d/spreadsheet-id "/edit?usp=sharing"))
    " and "
    (link "dashboard & geo map"
          (str "https://gisanddata.maps.arcgis.com/apps/"
               "opsdashboard/index.html#/"
               "bda7594740fd40299423467b48e9ecf6"))
    "\n"
    "\n"
    "- The data collected for Feb05, Feb07 is apparently not complete.\n"
    "- A full data source query takes ~10 seconds and it is cached for "
    d/time-to-live-minutes " minutes."
    "\n"
    msg-footer)))

;; long polling
(h/defhandler handler
  (register-cmd "start"   (fn [id] (refresh-cmd-fn id)))
  (register-cmd "refresh" (fn [id] (refresh-cmd-fn id)))
  (register-cmd "about"   (fn [id] (about-cmd-fn id)))

  #_(h/message-fn
     (fn [{{id :id} :chat :as message}]
       (a/send-text token id (str "Echoing message: " message)))))

(defn start-polling
  "Starts long-polling process.
  Handler is supposed to process immediately, as it will
  be called in a blocking manner."
  ([token handler] (start-polling token handler {}))
  ([token handler opts]
   (let [running (async/chan)
         updates (p-patch/create-producer-with-handle
                  running token opts (fn []
                                       (when (= bot-type "PROD")
                                         (System/exit 2))))]
     (p/create-consumer updates handler)
     running)))

(defn -main
  [& args]
  (log/info (str "[" (te/tnow) " " bot-ver "]") "Starting Telegram Chatbot...")
  (let [blank-prms (->> (conj d/env-prms :telegram-token)
                        (filter (fn [prm] (str/blank? (env prm)))))]
    (when (not-empty blank-prms)
      (log/fatal (str "Undef environment var(s): " blank-prms))
      (System/exit 1)))

  (<!! (start-polling token handler)))

;; For interactive development:
(def test-obj (atom nil))
(defn start   []
  (->> [d/row-count d/sum-up-at-once d/sum-up-col d/sheet-titles d/sheet-id
        confirmed-header deaths-header recovered-header]
       (memo/memo-clear!))
  (swap! test-obj (fn [_] (start-polling token handler))))

(defn stop    [] (p/stop @test-obj))
(defn restart [] (if @test-obj (stop)) (start))
