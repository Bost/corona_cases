(ns coronavirus.telegram
  (:require [clojure.core.async :refer [<!!]]
            [clojure.string :as str]
            [environ.core :refer [env]]
            [morse.handlers :as h]
            [morse.polling :as p]
            [morse.polling-patch :as p-patch]
            [morse.api :as a]
            [clojure.java.io :as io]
            [clj-time-ext.core :as te]
            [clj-time.core :as t]
            [com.hypirion.clj-xchart :as c]
            [clojure.tools.logging :as log]
            [clojure.core.async :as async]
            [clojure.core.memoize :as memo]
            [coronavirus
             [csv :as csv]
             [interpolate :as i]]
            )
  (:import java.text.SimpleDateFormat)
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
       (throw
        (Exception. (str "ERROR: "
                         "get-percentage [:high|:low|:normal] "
                         "<PLACE> <TOTAL_COUNT>")))))))

(defn info-msg []
  (let [{day :f confirmed :c deaths :d recovered :r} (last (csv/get-counts))]
    (str
     "\n"
     day "\n"
     "Confirmed: " confirmed "\n"
     "Deaths: " deaths
     "  ~  " (get-percentage deaths confirmed) "%\n"
     "Recovered: " recovered
     "  ~  " (get-percentage recovered confirmed) "%\n"
     msg-footer)))

(defn link [name url] (str "[" name "]""(" url ")"))

;; By default Vars are static, but Vars can be marked as dynamic to
;; allow per-thread bindings via the macro binding. Within each thread
;; they obey a stack discipline:
#_(def ^:dynamic points [[0 0] [1 3] [2 0] [5 2] [6 1] [8 2] [11 1]])

(defn absolute-numbers-pic []
  (let [dates (map (fn [hm] (.parse (new SimpleDateFormat "MM-dd-yyyy")
                                   (subs (:f hm) 0 10)))
                   (csv/get-counts))
        [confirmed deaths recovered] [(map :c (csv/get-counts))
                                      (map :d (csv/get-counts))
                                      (map :r (csv/get-counts))]]
    (-> (c/xy-chart
         (conj {}
               {"Confirmed"
                (conj {}
                      {:x dates
                       :y confirmed
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
                       :y deaths}
                      {:style
                       (conj {}
                             {:marker-type :none}
                             {:render-style :line}
                             #_{:line-color :red})
                       })}
               {"Recovered"
                (conj {}
                      {:x dates
                       :y recovered}
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

(defn aproximation-pic []
  (let [points
        #_[[0 0] [1 3] [2 0] [5 2] [6 1] [8 2] [11 1]]
        (mapv (fn [x y] [x y]) (range) (map :c (csv/get-counts)))]
    #_(println "points" points)
    (i/go points)))

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
  (a/send-photo token id (absolute-numbers-pic)))

(defn about-cmd-fn [id]
  #_(a/send-photo token id
                  (io/input-stream -stream "/path/to/photo.png"))
  (a/send-text
   token id
   {:parse_mode "Markdown"
    :disable_web_page_preview true}
   (str
    "Bot version: " bot-ver "\n"
    "Percentage calculation: <cases> / confirmed\n"
    "See also " (link "visual dashboard" "https://arcg.is/0fHmTX") " and "
    (link "worldometer"
          "https://www.worldometers.info/coronavirus/coronavirus-cases/")
    "\n"
    "\n"
    "- The spike observed on Feb12 is the result, for the most part, of a"
    " change in diagnosis classification adopted by the province of Hubei.\n"
    "- Due to the frequent changes of the "
    (link "data source" "https://github.com/CSSEGISandData/COVID-19")
    " format and structure the updates are done manually once per day."
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
  (let [blank-prms (->> [:telegram-token]
                        (filter (fn [prm] (str/blank? (env prm)))))]
    (when (not-empty blank-prms)
      (log/fatal (str "Undef environment var(s): " blank-prms))
      (System/exit 1)))

  (<!! (start-polling token handler)))

;; For interactive development:
(def test-obj (atom nil))
(defn start   []
  (swap! test-obj (fn [_] (start-polling token handler))))

(defn stop    [] (p/stop @test-obj))
(defn restart [] (if @test-obj (stop)) (start))
