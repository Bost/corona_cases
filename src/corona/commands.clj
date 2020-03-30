(ns corona.commands
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [corona.common :as com]
            [corona.core :as c :refer [in?]]
            [corona.countries :as cr]
            [corona.defs :as d]
            [corona.messages :as msg]
            [morse.api :as morse]))

(defn world [{:keys [chat-id country-code] :as prm}]
  (let [prm (assoc prm :parse_mode "HTML")]
    (morse/send-text
     c/token chat-id (select-keys prm (keys msg/options))
     (msg/info (assoc prm :disable_web_page_preview true)))

    (when (in? (->> (com/all-affected-country-codes)
                  (into cr/default-affected-country-codes)) country-code)
      (morse/send-photo c/token chat-id (msg/absolute-vals prm))
      #_(morse/send-text
         c/token chat-id (select-keys prm (keys msg/options))
         (format
          (str
           "<code>"
           "ATTENTION PLEASE! THE DATA PROVIDER 'JHU CSSE' DECIDED:"
           "\n\n"
           "</code>"
           "\"%s No reliable data source reporting recovered cases for many countries, such as the US.\""
           "\n\n"
           "Hence, the bot will be showing 0 until an alternative "
           "information source is found.\n\n"
           "Sorry about that."
           )
          (msg/link "We will no longer provide recovered cases."
                    "https://github.com/CSSEGISandData/COVID-19/issues/1250" prm))
         ))))

(defn partition-in-chunks
  "nr-countries / nr-patitions : 126 / 6, 110 / 5, 149 / 7"
  [col]
  (partition-all (/ (count col) 7) col))

(defn list-countries [{:keys [chat-id] :as prm}]
  (->> (msg/all-affected-country-codes prm)
       (sort-by :i <)
       partition-in-chunks
       #_(take 3)
       #_(take-last 1)
       (map (fn [chunk]
              (->> (assoc prm :data chunk)
                   msg/list-countries-memo
                   (morse/send-text c/token chat-id
                                    (select-keys prm (keys msg/options))))))
       doall))

(defn list-stuff [{:keys [chat-id] :as prm}]
  (let [prm (assoc prm :parse_mode "HTML")]
    (list-countries prm)
    (morse/send-text c/token chat-id com/sorry)))

(defn snapshot [{:keys [chat-id] :as prm}]
  (morse/send-text
   c/token chat-id msg/options
   "I'm sending you ~40MB file. Patience please...")
  (morse/send-document
   c/token chat-id
   (io/input-stream "resources/COVID-19/master.zip")))

(defn about [{:keys [chat-id] :as prm}]
  #_(morse/send-photo c/token chat-id
                      (io/input-stream "resources/pics/keepcalm.jpg"))
  (morse/send-text c/token chat-id msg/options (msg/about prm))
  (morse/send-text c/token chat-id
                   #_msg/options
                   (-> msg/options
                       (dissoc :parse_mode)
                       (assoc :disable_web_page_preview false))
                   (msg/remember-20-seconds prm))
  #_(morse/send-photo
   c/token chat-id (io/input-stream "resources/pics/how_to_handwash_lge.gif")))

(defn feedback [{:keys [chat-id] :as prm}]
  (morse/send-text c/token chat-id msg/options (msg/feedback prm)))

(defn references [{:keys [chat-id] :as prm}]
  (morse/send-text c/token chat-id msg/options (msg/references prm)))

(defn language [{:keys [chat-id] :as prm}]
  (morse/send-text c/token chat-id msg/options (msg/language prm)))

(defn contributors [{:keys [chat-id] :as prm}]
  (morse/send-text
   c/token chat-id msg/options
   (msg/contributors prm)))

(defn- normalize
  "Country name w/o spaces: e.g. \"United States\" => \"UnitedStates\""
  [country-code]
  (-> (com/country-name country-code)
      (s/replace " " "")))

(defn cmds-country-code [country-code]
  (->>
   [(fn [c] (->> c s/lower-case))  ;; /de
    (fn [c] (->> c s/upper-case))  ;; /DE
    (fn [c] (->> c s/capitalize))  ;; /De

    (fn [c] (->> c cr/country-code-3-letter s/lower-case)) ;; /deu
    (fn [c] (->> c cr/country-code-3-letter s/upper-case)) ;; /DEU
    (fn [c] (->> c cr/country-code-3-letter s/capitalize)) ;; /Deu

    (fn [c] (->> c (normalize) s/lower-case))   ;; /unitedstates
    (fn [c] (->> c (normalize) s/upper-case))   ;; /UNITEDSTATES
    (fn [c] (->> c (normalize)))]
   (mapv
    (fn [fun]
      {:name (fun country-code)
       :f
       (fn [chat-id]
         (world {:cmd-names msg/cmd-names
                 :chat-id chat-id
                 :country-code country-code
                 :pred (msg/pred-fn country-code)}))}))))

(defn cmds-general []
  (let [prm
        (conj
         {:cmd-names msg/cmd-names
          :pred (fn [_] true)}
         msg/options)

        prm-country-code {:country-code (com/country-code d/worldwide)}]
    [{:name msg/s-contributors
      :f (fn [chat-id] (contributors (assoc prm :chat-id chat-id)))
      :desc "Give credit where credit is due"}
     {:name msg/s-snapshot
      :f (fn [chat-id] (snapshot (assoc prm :chat-id chat-id)))
      :desc
      "Get a snapshot of https://github.com/CSSEGISandData/COVID-19.git master branch"}
     {:name msg/s-world
      :f (fn [chat-id] (world (-> (assoc prm :chat-id chat-id)
                                 (conj prm-country-code))))
      :desc msg/s-world-desc}

     {:name msg/s-list
      :f (fn [chat-id] (list-stuff (-> (assoc prm :chat-id chat-id)
                                      (conj prm-country-code))))
      :desc msg/s-list-desc}
     {:name msg/s-start
      :f (fn [chat-id] (world (-> (assoc prm :chat-id chat-id)
                                 (conj prm-country-code))))
      :desc msg/s-world-desc}
     {:name msg/s-about
      :f (fn [chat-id] (about (assoc prm :chat-id chat-id)))
      :desc "Bot version & some additional info"}
     {:name msg/s-feedback
      :f (fn [chat-id] (feedback (assoc prm :chat-id chat-id)))
      :desc "Talk to the bot-creator"}
     {:name msg/s-language
      :f (fn [chat-id] (language (assoc prm :chat-id chat-id)))
      :desc "Change language"}
     {:name msg/s-references
      :f (fn [chat-id] (references (assoc prm :chat-id chat-id)))
      :desc "Knowledge is power - educate yourself"}]))

(defn cmds []
  (->> (into (cr/all-country-codes))
       (mapv cmds-country-code)
       flatten
       (into (cmds-general))))

(defn bot-father-edit-cmds []
  (->> (cmds-general)
       (remove (fn [hm]
                 (in? [msg/s-start
                       ;; Need to save space it the mobile app. Sorry guys.
                       msg/s-contributors] (:name hm))))
       (sort-by :name)
       (reverse)
       (map (fn [{:keys [name desc]}] (println name "-" desc)))
       doall))
