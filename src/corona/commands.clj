(printf "Current-ns [%s] loading %s\n" *ns* 'corona.commands)

(ns corona.commands
  (:require
   [clojure.string :as s]
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
                                       ;; info infof warn errorf fatalf
                                       ]]
   ))

;; (set! *warn-on-reflection* true)

(defn world
  ([prm] (world "world" prm))
  ([msg-id {:keys [chat-id country-code] :as prm}]
   (let [prm
         ;; override default parse_mode
         (assoc prm :parse_mode "HTML")]
     (let [options (select-keys prm (keys msg/options))
           ;; the message content is fetched from the cache
           content (msg/detailed-info country-code)]
       (doall
        (morse/send-text com/telegram-token chat-id options content))
       (debugf "[%s] send-text: %s chars sent" msg-id (count content)))
     (let [options (if (msg/worldwide? country-code)
                     (msg/reply-markup-btns {:chat-id chat-id :cc country-code})
                     {})
           ;; the plot is fetched from the cache, stats and day need not to be
           ;; specified
           content (p/plot-country country-code)]
       (doall
        (morse/send-photo com/telegram-token chat-id options content))
       (debugf "[%s] send-photo: %s bytes sent" msg-id (count content))))))

(def ^:const cnt-messages-in-listing
  "nr-countries / nr-patitions : 126 / 6, 110 / 5, 149 / 7"
  7)

(defn listing
  ([prm] (listing "listing" prm))
  ([msg-id {:keys [listing-fn chat-id sort-by-case] :as prm}]
   ;; this may be not needed in the end
   #_{:pre [(s/valid? #{
                        ;; data msg-idx cnt-msgs sort-by-case parse_mode pred
                        msg/list-countries

                        ;; data msg-idx cnt-msgs sort-by-case parse_mode pred
                        msg/list-per-100k
                        }
                      listing-fn)]}
   (let [coll (sort-by sort-by-case < (data/stats-countries))]
     #_(debugf "[%s] coll %s" msg-id (count coll))
     (let [
           ;; Split the long list of all countries into smaller subparts
           sub-msgs (partition-all (/ (count coll) cnt-messages-in-listing) coll)
           cnt-msgs (count sub-msgs)]
       (let [options (select-keys prm (keys msg/options))
             contents (map-indexed (fn [idx sub-msg]
                                     (listing-fn
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
  (listing "list-countries" (assoc prm :listing-fn msg/list-countries)))

(defn list-per-100k [prm]
  (listing "list-per-100k" (assoc prm :listing-fn msg/list-per-100k)))

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
  [country-code]
  (-> (ccr/country-name country-code)
      (s/replace " " "")))

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
  [country-code]
  (mapv
   (fn [fun]
     {:name (fun country-code)
      :fun
      (fn [chat-id]
        (world {:chat-id chat-id
                :country-code country-code
                :pred (msg/create-pred-hm country-code)}))})
   [#(s/lower-case %)  ;; /de
    #(s/upper-case %)  ;; /DE
    #(s/capitalize %)  ;; /De
    #(s/lower-case (ccc/country-code-3-letter %)) ;; /deu
    #(s/upper-case (ccc/country-code-3-letter %)) ;; /DEU
    #(s/capitalize (ccc/country-code-3-letter %)) ;; /Deu
    #(s/lower-case (normalize %))   ;; /unitedstates
    #(s/upper-case (normalize %))   ;; /UNITEDSTATES
    #(normalize %)]))

(defn cmds-general []
  (let [prm (conj {:pred (msg/create-pred-hm ccc/zz)
                   :country-code (ccr/country-code ccc/worldwide)}
             msg/options)]
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
              (let [prm (conj
                         {:pred (msg/create-pred-hm ccc/zz)}
                         msg/options)
                    prm-country-code {:country-code (ccr/country-code ccc/worldwide)}]
                {:name (l/list-sorted-by case-kw)
                 :fun (fn [chat-id]
                        (let [list-fn (if (in? com/listing-cases-per-100k case-kw)
                                        list-per-100k
                                        list-countries)]
                          (list-fn (conj (assoc prm
                                                :parse_mode "HTML"
                                                :chat-id chat-id
                                                :sort-by-case case-kw)
                                         prm-country-code))))
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
