(printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.commands)

(ns corona.commands
  (:require [clojure.string :as cstr]
            [corona.common :as com]
            [corona.countries :as ccr]
            [corona.country-codes :as ccc]
            [corona.lang :as l]
            [corona.msg.common :as msgc]
            [corona.msg.info :as msgi]
            [corona.msg.messages :as msg]
            [corona.msg.lists :as msgl]
            [corona.plot :as p]
            [morse.api :as morse]
            [taoensso.timbre :as timbre :refer [debugf]]
            [utils.core :as u :refer [in?]]))

;; (set! *warn-on-reflection* true)

(defn world
  ([prm] (world "world" prm))
  ([msg-id {:keys [chat-id ccode] :as prm-orig}]
   (let [prm
         ;; override default parse_mode
         (assoc prm-orig
                :parse_mode com/html
                :pred-hm (msg/create-pred-hm ccode))]
     (let [options (select-keys prm (keys msg/options))
           content (msgi/detailed-info ccode)]
       (doall
        (morse/send-text com/telegram-token chat-id options content))
       (debugf "[%s] send-text: %s chars sent" msg-id (count content)))
     (let [options (if (msgc/worldwide? ccode)
                     (msg/reply-markup-btns (select-keys prm [:chat-id :ccode]))
                     {})
           ;; the plot is fetched from the cache, stats and report need not to be
           ;; specified
           content (p/plot-country ccode)]
       (when content
         (doall
          (morse/send-photo com/telegram-token chat-id options content))
         (debugf "[%s] send-photo: %s bytes sent" msg-id (count content)))))))

(def ^:const cnt-messages-in-listing
  "nr-countries / nr-patitions : 126 / 6, 110 / 5, 149 / 7"
  7)

(defn explain
  ([prm] (explain "explain" prm))
  ([msg-id {:keys [chat-id parse_mode]}]
   (let [content (msg/explain parse_mode)]
     (doall
      (morse/send-text com/telegram-token chat-id msg/options content))
     (debugf "[%s] send-text: %s chars sent" msg-id (count content)))))

(defn feedback
  ([prm] (feedback "feedback" prm))
  ([msg-id {:keys [chat-id]}]
   (let [content (msg/feedback)]
     (doall
      (morse/send-text com/telegram-token chat-id msg/options content))
     (debugf "[%s] send-text: %s chars sent" msg-id (count content)))))

;; (defn language [{:keys [chat-id parse_mode]}]
;;   (doall
;;    (morse/send-text com/telegram-token chat-id msg/options (msg/language parse_mode))))

(defn contributors
  ([prm] (contributors "contributors" prm))
  ([msg-id {:keys [chat-id parse_mode]}]
   (let [content (msg/contributors parse_mode)]
     (doall
      (morse/send-text com/telegram-token chat-id msg/options content))
     (debugf "[%s] send-text: %s chars sent" msg-id (count content)))))

(defn- normalize
  "Country name w/o spaces: e.g. \"United States\" => \"UnitedStates\""
  [ccode]
  (cstr/replace (ccr/get-country-name ccode) " " ""))

(defn cmds-country-code
  "E.g.
  (cmds-country-code \"DE\") =>
  [{:name \"de\"      :fun #function[...]}
   {:name \"DE\"      :fun #function[...]}
   {:name \"De\"      :fun #function[...]}
   {:name \"deu\"     :fun #function[...]}
   {:name \"DEU\"     :fun #function[...]}
   {:name \"Deu\"     :fun #function[...]}
   {:name \"germany\" :fun #function[...]}
   {:name \"GERMANY\" :fun #function[...]}
   {:name \"Germany\" :fun #function[...]}]"
  [ccode]
  (mapv
   (fn [fun]
     {:name (fun ccode)
      :fun (fn [chat-id] (world {:chat-id chat-id
                                :ccode ccode}))})
   [#(cstr/lower-case %)  ;; /de
    #(cstr/upper-case %)  ;; /DE
    #(cstr/capitalize %)  ;; /De
    #(cstr/lower-case (ccc/country-code-3-letter %)) ;; /deu
    #(cstr/upper-case (ccc/country-code-3-letter %)) ;; /DEU
    #(cstr/capitalize (ccc/country-code-3-letter %)) ;; /Deu
    #(cstr/lower-case (normalize %))   ;; /unitedstates
    #(cstr/upper-case (normalize %))   ;; /UNITEDSTATES
    #(normalize %)]))

(defn cmds-general []
  (let [prm (assoc msg/options
                   :ccode (ccr/get-country-code ccc/worldwide))]
    [{:name l/contributors
      :fun (fn [chat-id] (contributors (assoc prm :chat-id chat-id)))
      :desc "Give credit where credit is due"}
     {:name l/world
      :fun (fn [chat-id] (world (assoc prm :chat-id chat-id)))
      :desc l/world-desc}
     {:name l/start
      :fun (fn [chat-id] (world (assoc prm :chat-id chat-id)))
      :desc l/world-desc}
     {:name l/explain
      :fun (fn [chat-id] (explain (assoc prm :chat-id chat-id)))
      :desc "Explain abbreviations & some additional info"}
     {:name l/feedback
      :fun (fn [chat-id] (feedback (assoc prm :chat-id chat-id)))
      :desc "Talk to the bot-creator"}]))

(defn cmds-listing
  "Command map for listings."
  []
  (let [msg-id "cmds-listing"]
    (->> com/listing-cases-absolute
         (into com/listing-cases-per-100k)
         (map (fn [case-kw]
                {:name (l/list-sorted-by case-kw)
                 :fun (fn [chat-id]
                        (let [msg-listing-fun (if (in? com/listing-cases-per-100k case-kw)
                                                msgl/list-per-100k
                                                msgl/list-countries)
                              contents (msg-listing-fun case-kw)]
                          (doall
                           ;; mapping over results implies the knowledge that
                           ;; the type of `(msg-listing-fun case-kw)` is a
                           ;; collection.
                           (map (fn [content]
                                  (morse/send-text com/telegram-token chat-id {:parse_mode com/html} content)
                                  (debugf "[%s] send-text: %s chars sent" msg-id (count content)))
                                contents))))
                 :desc (l/list-sorted-by-desc case-kw)})))))

(def cmds
  "Create a vector of hash-maps for all available commands."
  (transduce (map cmds-country-code)
             into (into (cmds-general)
                        (cmds-listing))
             ccc/all-country-codes))

(defn bot-father-edit-cmds
  "Evaluate this function and upload the results under:
     @BotFather -> ... -> Edit Bot -> Edit Commands

  TODO can't type the '/re' command - collides with '/recov'
  TODO /<char> show a list of countries under starting with this letter."
  []
  (->> (cmds-general)
       (remove (fn [hm]
                 (in? [l/start
                       ;; Need to save space on smartphones. Sorry guys.
                       l/contributors
                       l/feedback
                       ] (:name hm))))
       (reverse)
       (into (cmds-listing))
       (map (fn [{:keys [name desc]}] (println name "-" desc)))
       (doall)))

(printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.commands)
