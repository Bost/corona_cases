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
            [clj-time.format :as tf]
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
                 "Available commands:  /refresh   /interpolate   /about"
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
     (tf/unparse (tf/formatter "dd MMM yyyy")
                 (tf/parse (tf/formatter "MM-dd-yyyy")
                           (subs day 0 10)))
     "\n"
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
  (let [dates i/dates
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
               {:title
                "@corona_cases_bot: total numbers; see /about"
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

(defn aproximation-pic [] (i/create-pic i/points))

(def chats (atom #{}))

(defn register-cmd [cmd cmd-fn]
  (h/command-fn
   cmd
   (fn [{{chat-id :id :as chat} :chat}]
     (if (= cmd "start")
       (do
         (swap! chats clojure.set/union #{chat})
         (->> @chats
              prn-str
              (spit "chats.edn")
              )))
     (let [tbeg (te/tnow)]
       (println (str "[" tbeg "           " " " bot-ver " /" cmd "]") chat)
       (cmd-fn chat-id)
       (println (str "[" tbeg ":" (te/tnow) " " bot-ver " /" cmd "]") chat)))))

(defn refresh-cmd-fn [chat-id]
  (a/send-text token chat-id (info-msg))
  (a/send-photo token chat-id
                (absolute-numbers-pic)))

(defn interpolate-cmd-fn [chat-id]
  (a/send-photo token chat-id
                (aproximation-pic)))

(defn about-cmd-fn [chat-id]
  (a/send-text
   token chat-id
   {:parse_mode "Markdown"
    :disable_web_page_preview true}
   (str
    "@corona\\_cases\\_bot version: " bot-ver "\n"
    "Percentage calculation: <cases> / confirmed.\n"
    "The interpolation method is: linear "
    (link "least squares" "https://en.wikipedia.org/wiki/Least_squares") "."
    "\n"
    "See also " (link "visual dashboard" "https://arcg.is/0fHmTX") " and "
    (link "worldometer"
          "https://www.worldometers.info/coronavirus/coronavirus-cases/") "."
    "\n"
    "\n"
    "- The spike from Feb12 results, for the most part, from a change in"
    " the diagnosis classification adopted by the province of Hubei.\n"
    "- " (link "Data source" "https://github.com/CSSEGISandData/COVID-19")
    " (Updates deployed manually once a day.)"
    "\n"
    "- Chatbot source code available on "
    (link "GitHub" "https://github.com/Bost/corona_cases") " and "
    (link "GitLab" "https://gitlab.com/rostislav.svoboda/corona_cases")
    "."
    ;; TODO home page
    #_(str
     "\n"
     " - " (link "Home page" "https://heroku.com/corona_cases"))
    "\n"
    msg-footer)))

;; long polling
(h/defhandler handler
  (register-cmd "start"   (fn [chat-id] (refresh-cmd-fn chat-id)))
  (register-cmd "refresh" (fn [chat-id] (refresh-cmd-fn chat-id)))
  (register-cmd "interpolate"
                (fn [chat-id] (interpolate-cmd-fn chat-id)))
  (register-cmd "about"   (fn [chat-id] (about-cmd-fn chat-id))))

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
