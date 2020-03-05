(ns corona.messages
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.java.io :as io]
            [com.hypirion.clj-xchart :as c]
            [corona.api
             :refer
             [confirmed dates deaths host ill last-day recovered time-to-live
             url]]
            [corona.core :refer [bot-name bot-ver token]]
            [corona.interpolate :as i]
            [morse.api :as a]))

#_[corona.csv
   :refer
   [confirmed dates deaths ill last-day recovered url]]
#_[corona.api
   :refer
   [confirmed dates deaths ill last-day recovered url
    host time-to-live]]

(def home-page
  ;; TODO (env :home-page)
  "https://corona-cases-bot.herokuapp.com/")

(defn msg-footer [cmds]
  (let [spacer "    "]
    (str "\n"
         "Try:" spacer (clojure.string/join spacer (map #(str "/" %) cmds)))))

(def cmd-s-about "about")

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
(def s-deaths    "Deaths")
(def s-recovered "Recovered")
(def s-sick      "Sick")

(def custom-time-formatter (tf/with-zone (tf/formatter "dd MMM yyyy")
                             (t/default-time-zone)))

(defn info-msg [cmds]
  (let [{day :f confirmed :c deaths :d recovered :r ill :i} (last-day)]
    (str
     "*" (tf/unparse custom-time-formatter (tc/from-date day)) "*\n"
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
                {:x dates :y confirmed
                 :style (conj {}
                              {:marker-type :none}
                              {:render-style :line}
                              #_{:line-color :orange}
                              #_{:fill-color :orange})}}
               {s-deaths
                {:x dates :y deaths
                 :style (conj {}
                              {:marker-type :none}
                              {:render-style :line}
                              #_{:line-color :red})}}
               {s-recovered
                {:x dates :y recovered
                 :style (conj {}
                              {:marker-type :none}
                              {:render-style :line}
                              #_{:line-color :green})}}
               {s-sick
                {:x dates :y ill
                 :style (conj {}
                              {:marker-type :none}
                              #_{:render-style :line}
                              #_{:line-color :green})}})
         (conj {}
               {:title
                (str bot-name ": total numbers; see /" cmd-s-about)
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
  (a/send-text token chat-id
               {:parse_mode "Markdown"
                :disable_web_page_preview true}
               (info-msg cmd-names))
  (a/send-photo token chat-id (absolute-numbers-pic)))

(def interpolated-vals (ill))
(def interpolated-name s-sick)

(defn interpolate-cmd-fn [cmd-names chat-id]
  (as-> (str bot-name ": interpolation - " interpolated-name "; see /"
             cmd-s-about) $
    (i/create-pic $ interpolated-vals)
    (a/send-photo token chat-id $)))

(defn about-msg [cmd-names]
  (str
   ;; escape underscores for the markdown parsing
   (clojure.string/replace bot-name #"_" "\\\\_")
   " version: " bot-ver " :  "
   (str
    (link "GitHub" "https://github.com/Bost/corona_cases") ", "
    (link "GitLab" "https://gitlab.com/rostislav.svoboda/corona_cases"))
   "\n"
    "- Percentage calculation: <cases> / confirmed.\n"
    "- Interpolation method: "
    (link "b-spline" "https://en.wikipedia.org/wiki/B-spline")
    "; degree of \"smoothness\" " (i/degree interpolated-vals) ".\n"
    #_(link "Country codes"
            "https://en.wikipedia.org/wiki/List_of_ISO_3166_country_codes")
    "- Feb12-spike caused mainly by a change in the diagnosis classification"
    " of the Hubei province.\n"
    "- Data retrieved *CONTINUOUSLY* every " time-to-live " minutes from the"
    " " (link host url) ".\n"
    (str
     "- See also " (link "visual dashboard" "https://arcg.is/0fHmTX") ", "
     (link "worldometer"
           "https://www.worldometers.info/coronavirus/coronavirus-cases/")
     ".\n")
    ;; TODO home page; average recovery time
    #_(str
     "\n"
     " - " (link "Home page" home-page))
    (msg-footer cmd-names)))

(defn about-cmd-fn [cmd-names chat-id]
  (a/send-text token chat-id
                 {:parse_mode "Markdown" :disable_web_page_preview true}
                 (about-msg cmd-names))
  (a/send-text token chat-id
               {:disable_web_page_preview false}
               #_"https://www.who.int/gpsc/clean_hands_protection/en/"
               "https://www.who.int/gpsc/media/how_to_handwash_lge.gif")
  #_(a/send-photo token chat-id (io/input-stream
                               "resources/pics/how_to_handwash_lge.gif")))

(defn keepcalm-cmd-fn [cmd-names chat-id]
  (a/send-photo token chat-id (io/input-stream
                               "resources/pics/keepcalm.jpg"))
  #_(a/send-text
   token chat-id
   {:disable_web_page_preview false}
   "https://i.dailymail.co.uk/1s/2020/03/03/23/25501886-8071359-image-a-20_1583277118353.jpg"))


(def cmd-names ["refresh" "interpolate" cmd-s-about "whattodo"])

(def cmds
  [
   {:name "refresh"     :f (fn [chat-id] (refresh-cmd-fn     cmd-names chat-id))
    :desc "Start here"}
   {:name "interpolate" :f (fn [chat-id] (interpolate-cmd-fn cmd-names chat-id))
    :desc "Smooth the data / leave out the noise"}
   {:name cmd-s-about   :f (fn [chat-id] (about-cmd-fn       cmd-names chat-id))
    :desc "Bot version & some additional info"}
   {:name "whattodo"    :f (fn [chat-id] (keepcalm-cmd-fn    cmd-names chat-id))
    :desc "Some personalized instructions"}
   ])
