(ns corona.commands
  (:require [clojure.string :as s]
            [corona.api.expdev07 :as data]
            [corona.api.v1 :as v1]
            [corona.core :as c]
            [corona.countries :as cr]
            [corona.defs :as d]
            [corona.lang :refer :all]
            [corona.messages :as msg]
            [corona.plot :as p]
            [morse.api :as morse]
            [utils.core :refer :all]))

(defn world [{:keys [chat-id country-code] :as prm}]
  (let [prm (assoc prm :parse_mode "HTML")]
    (morse/send-text c/token chat-id (select-keys prm (keys msg/options))
                     (msg/info (assoc prm :disable_web_page_preview true)))
    (morse/send-photo
     c/token chat-id
     (if (msg/worldwide? country-code)
       (msg/buttons chat-id country-code)
       {})
     (msg/toByteArrayAutoClosable
      (p/plot-country
       {:day (count (v1/raw-dates-unsorted)) :cc country-code
        :stats (v1/pic-data)})))))

(defn partition-in-chunks
  "nr-countries / nr-patitions : 126 / 6, 110 / 5, 149 / 7"
  [col]
  (partition-all (/ (count col) 7) col))

(defn- by-case-asc      [case-kw coll] (sort-by case-kw < coll))
(def by-ill-asc       (fn [coll] (by-case-asc :i coll)))
(def by-recovered-asc (fn [coll] (by-case-asc :r coll)))
(def by-deaths-asc    (fn [coll] (by-case-asc :d coll)))

(defn list-countries [{:keys [chat-id sort-fn] :as prm}]
  (->> (data/stats-all-affected-countries prm)
       (sort-fn)
       (partition-in-chunks)
       #_(take 3)
       #_(take-last 1)
       (map (fn [chunk]
              (->> (assoc prm :data chunk)
                   (msg/list-countries-memo)
                   (morse/send-text c/token chat-id
                                    (select-keys prm (keys msg/options))))))
       doall))

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
  (-> (cr/country-name country-code)
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

        prm-country-code {:country-code (cr/country-code d/worldwide)}]
    [{:name s-contributors
      :f (fn [chat-id] (contributors (assoc prm :chat-id chat-id)))
      :desc "Give credit where credit is due"}
     {:name s-world
      :f (fn [chat-id] (world (conj (assoc prm :chat-id chat-id)
                                   prm-country-code)))
      :desc s-world-desc}
     {:name s-list
      ;; TODO implement also sort by recovered & deaths
      :f (fn [chat-id] (list-countries
                       (conj (assoc prm
                                    :parse_mode "HTML"
                                    :chat-id chat-id
                                    :sort-fn by-ill-asc)
                             prm-country-code)))
      :desc s-list-desc}
     {:name s-start
      :f (fn [chat-id] (world (conj (assoc prm :chat-id chat-id)
                                   prm-country-code)))
      :desc s-world-desc}
     {:name s-about
      :f (fn [chat-id] (about (assoc prm :chat-id chat-id)))
      :desc "Bot version & some additional info"}
     {:name s-feedback
      :f (fn [chat-id] (feedback (assoc prm :chat-id chat-id)))
      :desc "Talk to the bot-creator"}
     ;; {:name s-language
     ;;  :f (fn [chat-id] (language (assoc prm :chat-id chat-id)))
     ;;  :desc "Change language"}
     {:name s-references
      :f (fn [chat-id] (references (assoc prm :chat-id chat-id)))
      :desc "Knowledge is power - educate yourself"}]))

(defn cmds []
  (transduce (map cmds-country-code)
             into (cmds-general)
             (cr/all-country-codes)))

(defn bot-father-edit-cmds []
  (->> (cmds-general)
       (remove (fn [hm]
                 (in? [s-start
                       ;; Need to save space it the mobile app. Sorry guys.
                       s-contributors] (:name hm))))
       (sort-by :name)
       (reverse)
       (map (fn [{:keys [name desc]}] (println name "-" desc)))
       doall))
