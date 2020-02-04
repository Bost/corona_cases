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
            [clj-time-ext.core :as t]
            [com.hypirion.clj-xchart :as c]
            [coronavirus.data :as d]
            [clojure.tools.logging :as log]
            [clojure.core.async :as async]
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

(log/info "Telegram Chatbot:" bot)

(defn confirmed-header [sheet]
  (let [column "D"]
    (->>
     [(str sheet "!" column "1:" column "1")]
     (v4/get-cell-values d/service d/spreadsheet-id)
     (flatten)
     (apply str))))

(defn deaths-header [sheet]
  (let [column "E"]
    (->>
     [(str sheet "!" column "1:" column "1")]
     (v4/get-cell-values d/service d/spreadsheet-id)
     (flatten)
     (apply str))))

(defn recovered-header [sheet]
  (let [column "F"]
    (->> [(str sheet "!" column "1:" column "1")]
         (v4/get-cell-values d/service d/spreadsheet-id)
         (flatten)
         (apply str))))

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

(defn info-msg
  "TODO memoize it and reread on notification"
  []
  (let [range (d/row-count)
        sheet (d/last-sheet)
        confirmed (d/confirmed-sum sheet range)
        deaths    (d/deaths-sum    sheet range)
        recovered (d/recovered-sum sheet range)
        ]
    (str
     "\n"
     (str sheet " EST (Eastern Time Zone):") "\n"
     (confirmed-header sheet) ": " confirmed "\n"
     (deaths-header    sheet) ": " deaths
     "  ~  " (get-percentage deaths confirmed) "%\n"
     (recovered-header sheet) ": " recovered
     "  ~  " (get-percentage recovered confirmed) "%\n"
     msg-footer)))

(defn link [name url] (str "[" name "]""(" url ")"))


(defn pic2 []
  (c/view
   (c/xy-chart
    {"Error bars" {:x (range 0 100 10)
                   :y [20 30 45 40 60 65 80 75 95 90]
                   ;; :error-bars [5 8 2 9 3 3 8 3 9 3]
                   }}
    #_{:error-bars-color :match-series})))

(defn pic []
  (def r (java.util.Random. 42))
  (-> {"Maxime" {:x (range 10)
                 :y (mapv #(+ % (* 3 (.nextDouble r)))
                          (range 10))}
       "Tyrone" {:x (range 10)
                 :y (mapv #(+ 2 % (* 4 (.nextDouble r)))
                          (range 0 5 0.5))}}
      (c/xy-chart
       {:title "Longest running distance"
        :x-axis {:title "Months (since start)"}
        :y-axis {:title "Distance"
                 :decimal-pattern "##.## km"}
        :theme :matlab})
      #_(c/to-bytes :png)
      (c/view)))

(defn register-cmd [cmd cmd-fn]
  (h/command-fn
   cmd
   (fn [{{id :id :as chat} :chat}]
     (let [tbeg (t/tnow)]
       (println (str "[" tbeg "           "" " bot-ver " /" cmd "]") chat)
       (cmd-fn id)
       (println (str "[" tbeg ":" (t/tnow) " " bot-ver " /" cmd "]") chat)))))

;; long polling
(h/defhandler handler
  (register-cmd "start"   (fn [id] (a/send-text token id (info-msg))))
  (register-cmd "refresh" (fn [id] (a/send-text token id (info-msg))))
  (register-cmd
   "about" (fn [id]
             #_(a/send-photo token id (pic))
             #_(a/send-photo token id
                             (io/input-stream -stream "/path/to/photo.png"))
             (a/send-text
              token id
              {:parse_mode "Markdown"}
              (str
               "Bot version: " bot-ver "\n"
               "Percentage calculation: <cases> / confirmed\n"
               "See "
               (link "data source"
                     (str "https://docs.google.com/spreadsheets/d/"
                          d/spreadsheet-id "/edit?usp=sharing"))
               " and "
               (link "dashboard & geo map"
                     (str "https://gisanddata.maps.arcgis.com/apps/"
                          "opsdashboard/index.html#/"
                          "bda7594740fd40299423467b48e9ecf6"))
               "\n"
               msg-footer))
             (a/send-text token id (info-msg))))

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
  (log/info (str "[" (t/tnow) " " bot-ver "]") "Starting Telegram Chatbot...")
  (let [blank-prms (->> (conj d/env-prms :telegram-token)
                        (filter (fn [prm] (str/blank? (env prm)))))]
    (when (not-empty blank-prms)
      (log/fatal (str "Undef environment var(s): " blank-prms))
      (System/exit 1)))

  (<!! (start-polling token handler)))

;; For interactive development:
(def test-obj (atom nil))
(defn start   [] (swap! test-obj (fn [_] (start-polling token handler))))
(defn stop    [] (p/stop @test-obj))
(defn restart [] (if @test-obj (stop)) (start))
