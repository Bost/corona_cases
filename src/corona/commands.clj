(printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.commands)

(ns corona.commands
  (:require
   ;; [clojure.spec.alpha :as spec]
   [clojure.string :as cstr]
   [corona.api.expdev07 :as data]
   [corona.countries :as ccr]
   [corona.country-codes :as ccc]
   [corona.lang :as l]
   [corona.messages :as msg]
   [corona.plot :as p]
   [morse.api :as morse]
   [utils.core :as u :refer [in?] :exclude [id]]
   [corona.common :as com]
   [taoensso.timbre :as timbre :refer [debugf
                                       ;; infof warnf errorf fatalf
                                       ]]
   ))

;; (set! *warn-on-reflection* true)

(defn world
  ([prm] (world "world" prm))
  ([msg-id {:keys [chat-id cc] :as prm-orig}]
   (let [ccode cc
         prm
         ;; override default parse_mode
         (assoc prm-orig
                :parse_mode "HTML"
                :pred-hm (msg/create-pred-hm ccode))]
     (let [options (select-keys prm (keys msg/options))
           ;; the message content is fetched from the cache
           content (msg/detailed-info ccode)]
       (doall
        (morse/send-text com/telegram-token chat-id options content))
       (debugf "[%s] send-text: %s chars sent" msg-id (count content)))
     (let [options (if (msg/worldwide? ccode)
                     (msg/reply-markup-btns (select-keys prm [:chat-id :cc]))
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

(defn listing
  ([prm] (listing "listing" prm))
  ([msg-id {:keys [msg-listing-fun chat-id sort-by-case] :as prm}]
   (let [coll (sort-by sort-by-case < (data/stats-countries))]
     #_(debugf "[%s] coll %s" msg-id (count coll))
     (let [
           ;; Split the long list of all countries into smaller subparts
           sub-msgs (partition-all (/ (count coll) cnt-messages-in-listing) coll)
           cnt-msgs (count sub-msgs)]
       (let [options (select-keys prm (keys msg/options))
             contents (map-indexed (fn [idx sub-msg]
                                     (msg-listing-fun
                                      msg-id
                                      (assoc prm
                                             :data sub-msg
                                             :msg-idx (inc idx)
                                             :cnt-msgs cnt-msgs)))
                                   sub-msgs)]
         (doall
          (map (fn [content]
                 (morse/send-text com/telegram-token chat-id options content)
                 #_(debugf "[%s] send-text: %s chars sent" msg-id (count content)))
               contents)))))))

(defn list-countries [prm]
  (listing "list-countries" (assoc prm :msg-listing-fun msg/list-countries)))

(defn list-per-100k [prm]
  (listing "list-per-100k" (assoc prm :msg-listing-fun msg/list-per-100k)))

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
                                :cc ccode}))})
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
                   :cc (ccr/get-country-code ccc/worldwide))]
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
  "Command map for list-sort-by-case. See also `footer`, `list-countries`."
  []
  (->> com/listing-cases-absolute
       (into com/listing-cases-per-100k)
       (map (fn [case-kw]
              {:name (l/list-sorted-by case-kw)
               :fun (fn [chat-id]
                      (let [cmd-list-fun (if (in? com/listing-cases-per-100k case-kw)
                                           list-per-100k
                                           list-countries)]
                        (cmd-list-fun (assoc msg/options
                                             :parse_mode "HTML"
                                             :chat-id chat-id
                                             :sort-by-case case-kw))))
               :desc (l/list-sorted-by-desc case-kw)}))))

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
