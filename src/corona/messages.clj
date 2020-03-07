(ns corona.messages
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [com.hypirion.clj-xchart :as chart]
            [corona.api :as a]
            [corona.core :as c :refer [in?]]
            [corona.interpolate :as i]
            [morse.api :as morse]))

(def cmd-s-about "about")

(def s-confirmed "Confirmed")
(def s-deaths    "Deaths")
(def s-recovered "Recovered")
(def s-sick      "Sick")

(def home-page
  ;; TODO (env :home-page)
  "https://corona-cases-bot.herokuapp.com/")

(defn msg-footer [{:keys [cmd-names]}]
  (let [spacer "   "]
    (str "\n"
         "Try:" spacer (s/join spacer (map #(str "/" %) cmd-names)))))

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

(def custom-time-formatter (tf/with-zone (tf/formatter "dd MMM yyyy")
                             (t/default-time-zone)))

(defn info-msg [prm]
  (let [{day :f confirmed :c deaths :d recovered :r ill :i} (a/last-day prm)]
    (str
     "*" (tf/unparse custom-time-formatter (tc/from-date day)) "*\n"
     s-confirmed ": " confirmed "\n"
     s-deaths ": " deaths
     "  ~  " (get-percentage deaths confirmed) "%\n"
     s-recovered ": " recovered
     "  ~  " (get-percentage recovered confirmed) "%\n"
     s-sick ": " ill "  ~  " (get-percentage ill confirmed) "%\n"
     (msg-footer prm))))

(defn link [name url] (str "[" name "]""(" url ")"))

;; By default Vars are static, but Vars can be marked as dynamic to
;; allow per-thread bindings via the macro binding. Within each thread
;; they obey a stack discipline:
#_(def ^:dynamic points [[0 0] [1 3] [2 0] [5 2] [6 1] [8 2] [11 1]])

(defn absolute-numbers-pic [{:keys [country] :as prm}]
  (let [confirmed (a/confirmed prm)
        deaths    (a/deaths    prm)
        recovered (a/recovered prm)
        ill       (a/ill       prm)
        dates     (a/dates)]
    (-> (chart/xy-chart
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
                (str c/bot-name ": " country "; see /" cmd-s-about)
                :render-style :area
                :legend {:position :inside-nw}
                :x-axis {:title "Day"}
                :y-axis {:title "Cases"}}
               #_{:theme :matlab}
               #_{:width 640 :height 500}
               {:width 800 :height 600}
               {:date-pattern "MMMd"}))
        #_(chart/view)
        (chart/to-bytes :png))))

(defn refresh-cmd-fn [{:keys [chat-id] :as prm}]
  (morse/send-text
   c/token chat-id {:parse_mode "Markdown" :disable_web_page_preview true}
   (info-msg prm))
  (morse/send-photo c/token chat-id (absolute-numbers-pic prm)))

(defn interpolated-vals [prm] (a/ill prm))
(def interpolated-name s-sick)

(defn interpolate-cmd-fn [{:keys [chat-id country] :as prm}]
  (as-> (str c/bot-name ": " country
             " interpolation - " interpolated-name "; see /"
             cmd-s-about) $
    (i/create-pic $ (interpolated-vals prm))
    (morse/send-photo c/token chat-id $)))

(defn about-msg [prm]
  (str
   ;; escape underscores for the markdown parsing
   (s/replace c/bot-name #"_" "\\\\_")
   " version: " c/bot-ver " :  "
   (str
    (link "GitHub" "https://github.com/Bost/corona_cases") ", "
    (link "GitLab" "https://gitlab.com/rostislav.svoboda/corona_cases"))
   "\n"
   "- Percentage calculation: <cases> / confirmed.\n"

   #_(str
      "- Interpolation method: "
      (link "b-spline" "https://en.wikipedia.org/wiki/B-spline")
      "; degree of \"smoothness\" " (i/degree (interpolated-vals prm)) ".\n")

   #_(str
    "- Feb12-spike caused mainly by a change in the diagnosis classification"
    " of the Hubei province.\n")

   (str
    "- Data retrieved *CONTINUOUSLY* every " corona.api/time-to-live
    " minutes from " (link a/host a/url) ".\n")

   (str
    "- See also " (link "visual dashboard" "https://arcg.is/0fHmTX") ", "
    (link "worldometer"
          "https://www.worldometers.info/coronavirus/coronavirus-cases/")
    ".\n")

   (str
    "\n"
    "- Country *specific* information e.g.:\n"
    "/fr    /fra      /France\n"
    "/us    /usa    /UnitedStates   (without spaces)\n")
   #_(link "Country codes"
           "https://en.wikipedia.org/wiki/List_of_ISO_3166_country_codes")
   ;; TODO home page; average recovery time
   #_(str
      "\n"
      " - " (link "Home page" home-page))
   (msg-footer prm)))

(defn about-cmd-fn [{:keys [chat-id] :as prm}]
  (morse/send-text
   c/token chat-id {:parse_mode "Markdown" :disable_web_page_preview true}
   (about-msg prm))
  (morse/send-text
   c/token chat-id {:disable_web_page_preview false}
   #_"https://www.who.int/gpsc/clean_hands_protection/en/"
   "https://www.who.int/gpsc/media/how_to_handwash_lge.gif")
  #_(morse/send-photo
     token chat-id (io/input-stream "resources/pics/how_to_handwash_lge.gif")))

(defn keepcalm-cmd-fn [{:keys [chat-id]}]
  (morse/send-photo
   c/token chat-id (io/input-stream "resources/pics/keepcalm.jpg"))
  #_(morse/send-text
   token chat-id
   {:disable_web_page_preview false}
   "https://i.dailymail.co.uk/1s/2020/03/03/23/25501886-8071359-image-a-20_1583277118353.jpg"))

(def cmd-names ["refresh"
                #_"interpolate"
                cmd-s-about "whattodo"
                "<country>"])

(defn cmds-country-code [country]
  (->>
   [(fn [c] (->> c s/lower-case))  ;; /de
    (fn [c] (->> c s/upper-case))  ;; /DE
    (fn [c] (->> c s/capitalize))  ;; /De

    (fn [c] (->> c (get c/is-3166-abbrevs) s/lower-case)) ;; /deu
    (fn [c] (->> c (get c/is-3166-abbrevs) s/upper-case)) ;; /DEU
    (fn [c] (->> c (get c/is-3166-abbrevs) s/capitalize)) ;; /Deu

    (fn [c] (->> c (get c/is-3166-names) s/lower-case))   ;; /germany
    (fn [c] (->> c (get c/is-3166-names) s/upper-case))   ;; /GERMANY
    (fn [c] (->> c (get c/is-3166-names)))
    ]
   (mapv
    (fn [fun]
      {:name (fun country)
       :f (fn [chat-id]
            (if (in? (a/affected-countries) country)
              (refresh-cmd-fn {:cmd-names cmd-names
                               :chat-id chat-id
                               :country (get c/is-3166-names country)
                               :pred (fn [loc] (= country (:country_code loc)))})
              (morse/send-text
               c/token chat-id {:parse_mode "Markdown"
                                :disable_web_page_preview true}
               (str (get c/is-3166-names country) " not affected."))))}))))

(defn cmds-general []
  (let [prm {:cmd-names cmd-names
             :pred (fn [_] true)}]
    [
     {:name "refresh"
      :f (fn [chat-id] (refresh-cmd-fn (conj prm {:chat-id chat-id
                                                  :country "Worldwide"})))
      :desc "Start here"}
     #_{:name "interpolate"
        :f (fn [chat-id] (interpolate-cmd-fn (conj prm {:chat-id chat-id
                                                        :country "Worldwide"})))
        :desc "Smooth the data / leave out the noise"}
     {:name cmd-s-about
      :f (fn [chat-id] (about-cmd-fn (conj prm {:chat-id chat-id})))
      :desc "Bot version & some additional info"}
     {:name "whattodo"
      :f (fn [chat-id] (keepcalm-cmd-fn (conj prm {:chat-id chat-id})))
      :desc "Some personalized instructions"}]))

(defn cmds []
  (into (cmds-general) (->> (c/all-country-codes)
                            (mapv cmds-country-code)
                            flatten)))

(def bot-description
  "Keep it in sync with README.md"
  "Coronavirus disease 2019 (COVID-19) information on Telegram Messenger")

(defn bot-father-edit-cmds []
  (map (fn [{:keys [name desc]}] (println name "-" desc))
       (cmds-general)))
(defn bot-father-edit-description [] bot-description)
(defn bot-father-edit-about [] bot-description)
