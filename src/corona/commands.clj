(ns corona.commands
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [corona.api.expdev07 :as data]
            [corona.common :as com]
            [corona.core :as c :refer [in?]]
            [corona.countries :as cr]
            [corona.defs :as d]
            [corona.messages :as msg]
            [corona.pic :as pic]
            [morse.api :as morse]))

(defn world [{:keys [chat-id country-code] :as prm}]
  (let [prm (assoc prm :parse_mode "HTML")]
    (morse/send-text
     c/token chat-id (select-keys prm (keys msg/options))
     (msg/info (assoc prm :disable_web_page_preview true)))

    (when (in? (->> (data/all-affected-country-codes)
                  (into cr/default-affected-country-codes)) country-code)
      (morse/send-photo c/token chat-id (msg/absolute-vals prm))
      (when (in? [d/worldwide-2-country-code
                  d/worldwide-3-country-code
                  d/worldwide]
                 country-code)
        #_(morse/send-photo c/token chat-id (pic/show-pic 20000))
        (pic/show-pic com/threshold)
        (morse/send-photo c/token chat-id
                          (io/input-stream
                           (io/file com/temp-file)))))))

(defn partition-in-chunks
  "nr-countries / nr-patitions : 126 / 6, 110 / 5, 149 / 7"
  [col]
  (partition-all (/ (count col) 7) col))

(defn list-countries [{:keys [chat-id] :as prm}]
  (->> (data/stats-all-affected-countries prm)
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
    (list-countries prm)))

(defn about [{:keys [chat-id] :as prm}]
  (morse/send-text c/token chat-id msg/options (msg/about prm)))

(defn feedback [{:keys [chat-id] :as prm}]
  (morse/send-text c/token chat-id msg/options (msg/feedback prm)))

(defn references [{:keys [chat-id] :as prm}]
  (morse/send-text c/token chat-id msg/options (msg/references prm)))

;; (defn language [{:keys [chat-id] :as prm}]
;;   (morse/send-text c/token chat-id msg/options (msg/language prm)))

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
  (mapv
   (fn [fun]
     {:name (fun country-code)
      :f
      (fn [chat-id]
        (world {:cmd-names msg/cmd-names
                :chat-id chat-id
                :country-code country-code
                :pred (msg/pred-fn country-code)}))})
   [#(s/lower-case %)  ;; /de
    #(s/upper-case %)  ;; /DE
    #(s/capitalize %)  ;; /De
    #(s/lower-case (cr/country-code-3-letter %)) ;; /deu
    #(s/upper-case (cr/country-code-3-letter %)) ;; /DEU
    #(s/capitalize (cr/country-code-3-letter %)) ;; /Deu
    #(s/lower-case (normalize %))   ;; /unitedstates
    #(s/upper-case (normalize %))   ;; /UNITEDSTATES
    #(normalize %)]))

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
     {:name msg/s-world
      :f (fn [chat-id] (world (conj (assoc prm :chat-id chat-id)
                                   prm-country-code)))
      :desc msg/s-world-desc}
     {:name msg/s-list
      :f (fn [chat-id] (list-stuff (conj (assoc prm :chat-id chat-id)
                                        prm-country-code)))
      :desc msg/s-list-desc}
     {:name msg/s-start
      :f (fn [chat-id] (world (conj (assoc prm :chat-id chat-id)
                                   prm-country-code)))
      :desc msg/s-world-desc}
     {:name msg/s-about
      :f (fn [chat-id] (about (assoc prm :chat-id chat-id)))
      :desc "Bot version & some additional info"}
     {:name msg/s-feedback
      :f (fn [chat-id] (feedback (assoc prm :chat-id chat-id)))
      :desc "Talk to the bot-creator"}
     ;; {:name msg/s-language
     ;;  :f (fn [chat-id] (language (assoc prm :chat-id chat-id)))
     ;;  :desc "Change language"}
     {:name msg/s-references
      :f (fn [chat-id] (references (assoc prm :chat-id chat-id)))
      :desc "Knowledge is power - educate yourself"}]))

(defn cmds []
  (transduce (map cmds-country-code)
             into (cmds-general)
             (cr/all-country-codes)))

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
