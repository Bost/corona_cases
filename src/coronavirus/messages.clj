(ns coronavirus.messages
  (:require [morse.api :as a]
            [clj-time.format :as tf]
            [environ.core :refer [env]]
            [com.hypirion.clj-xchart :as c]
            [clojure.java.io :as io]
            [coronavirus
             [csv :as csv]
             [interpolate :as i]]
            ))

(def token (env :telegram-token))
(def home-page
  ;; TODO (env :home-page)
  "https://corona-cases-bot.herokuapp.com/")

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
  (let [{day :f confirmed :c deaths :d recovered :r
         ill :i} (last (csv/get-counts))]
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
     "Currently ill: " ill "  ~  " (get-percentage ill confirmed) "%\n"
     msg-footer)))

(defn link [name url] (str "[" name "]""(" url ")"))

;; By default Vars are static, but Vars can be marked as dynamic to
;; allow per-thread bindings via the macro binding. Within each thread
;; they obey a stack discipline:
#_(def ^:dynamic points [[0 0] [1 3] [2 0] [5 2] [6 1] [8 2] [11 1]])

(defn absolute-numbers-pic []
  (let [[confirmed deaths recovered ill]
        [(map :c (csv/get-counts))
         (map :d (csv/get-counts))
         (map :r (csv/get-counts))
         (map :i (csv/get-counts))]]
    (-> (c/xy-chart
         (conj {}
               {"Confirmed"
                (conj {}
                      {:x i/dates :y confirmed
                       :style (conj {}
                                    {:marker-type :none}
                                    #_{:render-style :line}
                                    #_{:line-color :orange}
                                    #_{:fill-color :orange}
                                    )})}
               {"Deaths"
                (conj {}
                      {:x i/dates :y deaths}
                      {:style (conj {}
                                    {:marker-type :none}
                                    {:render-style :line}
                                    #_{:line-color :red})
                       })}
               {"Recovered"
                (conj {}
                      {:x i/dates :y recovered}
                      {:style (conj {}
                                    {:marker-type :none}
                                    {:render-style :line}
                                    #_{:line-color :green})})}
               {"Currently ill"
                (conj {}
                      {:x i/dates :y ill}
                      {:style (conj {}
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

(defn refresh-cmd-fn [chat-id]
  (a/send-text  token chat-id (info-msg))
  (a/send-photo token chat-id (absolute-numbers-pic)))

(defn interpolate-cmd-fn [chat-id]
  (a/send-photo token chat-id (i/create-pic i/points)))

(defn about-cmd-fn [chat-id]
  (a/send-text
   token chat-id
   {:parse_mode "Markdown"
    :disable_web_page_preview true}
   (str
    "@corona\\_cases\\_bot version: " bot-ver "\n"
    "Percentage calculation: <cases> / confirmed.\n"
    "Interpolation method: "
    (link "b-spline"
          "https://en.wikipedia.org/wiki/B-spline")
    "; degree of \"smoothness\" " i/degree "."
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
    ;; TODO home page; average recovery time
    #_(str
     "\n"
     " - " (link "Home page" home-page))
    "\n"
    msg-footer))
  (a/send-text token chat-id
               {:disable_web_page_preview false}
               "https://www.who.int/gpsc/media/how_to_handwash_lge.gif"
               #_"https://www.who.int/gpsc/clean_hands_protection/en/"))

