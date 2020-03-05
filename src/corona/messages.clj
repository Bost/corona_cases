(ns corona.messages
  (:require [clj-time.coerce :as tc]
            [clj-time.format :as tf]
            [clj-time.core :as t]
            [clojure.java.io :as io]
            [com.hypirion.clj-xchart :as c]
            [corona.api
             :refer
             [confirmed dates deaths ill last-day recovered url time-to-live]]
            [corona.core :refer [bot-ver token]]
            [corona.interpolate :as i]
            [morse.api :as a]))

#_[corona.csv
 :refer
 [confirmed dates deaths ill last-day recovered url]]

(def home-page
  ;; TODO (env :home-page)
  "https://corona-cases-bot.herokuapp.com/")

(defn msg-footer [cmds]
  (str
   "\n"
   "Try:" "    " (clojure.string/join "    " (map #(str "/" %) cmds))))

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

(def s-confirmed "Confirmed")
(def s-deaths "Deaths")
(def s-recovered "Recovered")
(def s-sick "Sick")

(def custom-time-formatter (tf/with-zone (tf/formatter "dd MMM yyyy")
                             (t/default-time-zone)))

(defn info-msg [cmds]
  (let [{day :f confirmed :c deaths :d recovered :r ill :i} (last-day)]
    (str
     "*"
     #_day
     (tf/unparse custom-time-formatter
                 (tc/from-date day))
     "*"
     "\n"
     s-confirmed ": " confirmed "\n"
     s-deaths ": " deaths
     "  ~  " (get-percentage deaths confirmed) "%\n"
     s-recovered ": " recovered
     "  ~  " (get-percentage recovered confirmed) "%\n"
     s-sick ": " ill "  ~  " (get-percentage ill confirmed) "%\n"
     (msg-footer cmds))))

(defn link [name url] (str "[" name "]""(" url ")"))

;; By default Vars are static, but Vars can be marked as dynamic to
;; allow per-thread bindings via the macro binding. Within each thread
;; they obey a stack discipline:
#_(def ^:dynamic points [[0 0] [1 3] [2 0] [5 2] [6 1] [8 2] [11 1]])

(defn absolute-numbers-pic []
  (let [confirmed (confirmed)
        deaths    (deaths)
        recovered (recovered)
        ill       (ill)
        dates     (dates)]
    (-> (c/xy-chart
         (conj {}
               {s-confirmed
                (conj {}
                      {:x dates :y confirmed
                       :style (conj {}
                                    {:marker-type :none}
                                    #_{:render-style :line}
                                    #_{:line-color :orange}
                                    #_{:fill-color :orange}
                                    )})}
               {s-deaths
                (conj {}
                      {:x dates :y deaths}
                      {:style (conj {}
                                    {:marker-type :none}
                                    {:render-style :line}
                                    #_{:line-color :red})
                       })}
               {s-recovered
                (conj {}
                      {:x dates :y recovered}
                      {:style (conj {}
                                    {:marker-type :none}
                                    {:render-style :line}
                                    #_{:line-color :green})})}
               {s-sick
                (conj {}
                      {:x dates :y ill}
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

(defn refresh-cmd-fn [cmd-names chat-id]
  (a/send-text  token chat-id
                {:parse_mode "Markdown"
                 :disable_web_page_preview true}
                (info-msg cmd-names))
  (a/send-photo token chat-id (absolute-numbers-pic)))

(defn interpolate-cmd-fn [cmd-names chat-id]
  (a/send-photo token chat-id (i/create-pic i/points)))

(defn about-msg [cmd-names]
  (str
    "@corona\\_cases\\_bot version: " bot-ver "\n"
    "- Percentage calculation: <cases> / confirmed.\n"
    "- Interpolation method: "
    (link "b-spline"
          "https://en.wikipedia.org/wiki/B-spline")
    "; degree of \"smoothness\" " i/degree "."
    "\n"
    #_(link "Country codes"
          "https://en.wikipedia.org/wiki/List_of_ISO_3166_country_codes")
    "- See also " (link "visual dashboard" "https://arcg.is/0fHmTX") " and "
    (link "worldometer"
          "https://www.worldometers.info/coronavirus/coronavirus-cases/") "."
    "\n"
    "- Feb12-spike caused mainly by a change in the diagnosis classification of"
    " the province of Hubei.\n"
    "- Data retrieved *CONTINUOUSLY* from " (link url url) " and cached for "
    time-to-live
    " minutes."
    "\n"
    "- Chatbot source code: "
    (link "GitHub" "https://github.com/Bost/corona_cases") ", "
    (link "GitLab" "https://gitlab.com/rostislav.svoboda/corona_cases")
    "."
    ;; TODO home page; average recovery time
    #_(str
     "\n"
     " - " (link "Home page" home-page))
    "\n"
    (msg-footer cmd-names)))

(defn about-cmd-fn [cmd-names chat-id]
  (a/send-text token chat-id
                 {:parse_mode "Markdown" :disable_web_page_preview true}
                 (about-msg cmd-names))
  #_(a/send-text token chat-id
               {:disable_web_page_preview false}
               "https://www.who.int/gpsc/clean_hands_protection/en/"
               #_"https://www.who.int/gpsc/media/how_to_handwash_lge.gif")
  #_(a/send-photo token chat-id (io/input-stream
                               "resources/pics/how_to_handwash_lge.gif")))

(defn keepcalm-cmd-fn [cmd-names chat-id]
  (a/send-photo token chat-id (io/input-stream
                               "resources/pics/keepcalm.jpg"))
  #_(a/send-text
   token chat-id
   {:disable_web_page_preview false}
   "https://i.dailymail.co.uk/1s/2020/03/03/23/25501886-8071359-image-a-20_1583277118353.jpg"))