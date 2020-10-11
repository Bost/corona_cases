(printf "Current-ns [%s] loading %s\n" *ns* 'corona.commands)

(ns corona.commands
  (:require
   [clojure.string :as s]
   [corona.api.expdev07 :as data]
   [corona.api.v1 :as v1]
   [corona.countries :as ccr]
   [corona.country-codes :as ccc]
   [corona.lang :as l]
   [corona.messages :as msg]
   [corona.plot :as p]
   [morse.api :as morse]
   [utils.core :as u :refer [in?] :exclude [id]]
   [corona.common :as com]
   [taoensso.timbre :as timbre :refer :all]
   ))

;; (debugf "Loading namespace %s" *ns*)

(defn deep-merge
  "Recursively merges maps. TODO see https://github.com/weavejester/medley
Thanks to https://gist.github.com/danielpcox/c70a8aa2c36766200a95#gistcomment-2711849"
  [& maps]
  (apply merge-with (fn [& args]
                      (if (every? map? args)
                        (apply deep-merge args)
                        (last args)))
         maps))

(defn rank [{:keys [rank-kw] :as prm}]
  #_(defn sort-fn [coll] (sort-by rank-kw > coll))
  (map-indexed
   (fn [idx hm]
     (update-in (select-keys hm [:cc]) [:rank rank-kw] (fn [_] idx)))
   (sort-by rank-kw >
            ;; data/stats-all-affected-countries-memo - not passing the prm
            (data/stats-all-affected-countries-memo {}))))

(defn calculate-rankings [prm]
  (let [rankings (u/transpose (map (fn [rank-kw]
                                     (rank (assoc prm :rank-kw rank-kw)))
                                   [:p :c100k :r100k :d100k :i100k]))]
    (map (fn [affected-cc]
           (apply deep-merge
                  (reduce into []
                          (map (fn [ranking]
                                 (filter (fn [{:keys [cc]}]
                                           (= cc affected-cc))
                                         ranking))
                               rankings))))
         (data/all-affected-country-codes-memo))))

(defn world [{:keys [chat-id country-code] :as prm}]
  #_(debug "world" prm)
  (let [prm (assoc prm :parse_mode "HTML")]
    (let [options (select-keys prm (keys msg/options))
          cnt-countries (count (data/all-affected-country-codes-memo))
          content (-> (assoc prm
                             :disable_web_page_preview true
                             :cnt-countries cnt-countries)
                      (conj
                       ;; the order of countries should be calculated only once
                       (first (map (fn [m] (select-keys m [:rank]))
                                   (filter (fn [{:keys [cc]}] (= cc country-code))
                                           (calculate-rankings prm)))))
                      (msg/detailed-info))]
      (morse/send-text com/telegram-token chat-id options content))

    (if com/env-devel? ;; don't show the graph when developing
      (debug "Plot not displayed. com/env-devel?" com/env-devel?)
      (let [options (if (msg/worldwide? country-code)
                      (msg/buttons {:chat-id chat-id :cc country-code})
                      {})
            content (msg/toByteArrayAutoClosable
                     (p/plot-country
                      {:day (count (data/raw-dates-unsorted))
                       :cc country-code
                       :stats (v1/pic-data)}))]
        (morse/send-photo com/telegram-token chat-id options content)))))

(def ^:const cnt-messages-in-listing
  "nr-countries / nr-patitions : 126 / 6, 110 / 5, 149 / 7"
  7)

(defn listing [{:keys [listing-fn chat-id sort-by-case] :as prm}]
  (let [coll (sort-by sort-by-case <
                      (data/stats-all-affected-countries-memo prm))
        ;; Split the long list of all countries into smaller subparts
        sub-msgs (partition-all (/ (count coll) cnt-messages-in-listing) coll)
        cnt-msgs (count sub-msgs)]
    (doall
     (map-indexed (fn [idx sub-msg]
                    (morse/send-text com/telegram-token chat-id
                                     (select-keys prm (keys msg/options))
                                     (listing-fn
                                      (assoc prm
                                             :data sub-msg
                                             :msg-idx (inc idx)
                                             :cnt-msgs cnt-msgs))))
                  sub-msgs))))


(defn list-countries [prm]
  (listing (assoc prm :listing-fn msg/list-countries-memo)))

(defn list-per-100k [prm]
  (listing (assoc prm :listing-fn msg/list-per-100k-memo)))

(defn explain [{:keys [chat-id] :as prm}]
  (morse/send-text com/telegram-token chat-id msg/options (msg/explain prm)))

(defn feedback [{:keys [chat-id] :as prm}]
  (morse/send-text com/telegram-token chat-id msg/options (msg/feedback prm)))

;; (defn language [{:keys [chat-id] :as prm}]
;;   (morse/send-text com/telegram-token chat-id msg/options (msg/language prm)))

(defn contributors [{:keys [chat-id] :as prm}]
  (morse/send-text
   com/telegram-token chat-id msg/options
   (msg/contributors prm)))

(defn- normalize
  "Country name w/o spaces: e.g. \"United States\" => \"UnitedStates\""
  [country-code]
  (-> (ccr/country-name country-code)
      (s/replace " " "")))

(defn cmds-country-code
  "E.g.
  (cmds-country-code \"DE\") =>
  [{:name \"de\"      :f #function[...]}
   {:name \"DE\"      :f #function[...]}
   {:name \"De\"      :f #function[...]}
   {:name \"deu\"     :f #function[...]}
   {:name \"DEU\"     :f #function[...]}
   {:name \"Deu\"     :f #function[...]}
   {:name \"germany\" :f #function[...]}
   {:name \"GERMANY\" :f #function[...]}
   {:name \"Germany\" :f #function[...]}]"
  [country-code]
  (mapv
   (fn [fun]
     {:name (fun country-code)
      :f
      (fn [chat-id]
        (world {:chat-id chat-id
                :country-code country-code
                :pred-q '(msg/pred-fn country-code)
                :pred (msg/pred-fn country-code)}))})
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
  (let [prm
        (conj
         {:pred-q '(fn [_] true)}
         {:pred (fn [_] true)}
         msg/options)

        prm-country-code {:country-code (ccr/country-code ccc/worldwide)}]
    [{:name l/contributors
      :f (fn [chat-id] (contributors (assoc prm :chat-id chat-id)))
      :desc "Give credit where credit is due"}
     {:name l/world
      :f (fn [chat-id] (world (conj (assoc prm :chat-id chat-id)
                                   prm-country-code)))
      :desc l/world-desc}
     {:name l/start
      :f (fn [chat-id] (world (conj (assoc prm :chat-id chat-id)
                                   prm-country-code)))
      :desc l/world-desc}
     {:name l/explain
      :f (fn [chat-id] (explain (assoc prm :chat-id chat-id)))
      :desc "Explain abbreviations & some additional info"}
     {:name l/feedback
      :f (fn [chat-id] (feedback (assoc prm :chat-id chat-id)))
      :desc "Talk to the bot-creator"}]))

(defn cmds-listing []
  "Command map for list-sort-by-case. See also `footer`, `list-countries`."
  (->> com/listing-cases-absolute
       (into com/listing-cases-per-100k)
       (map (fn [case-kw]
              (let [prm (conj
                         {:pred-q '(fn [_] true)}
                         {:pred (fn [_] true)}
                         msg/options)
                    prm-country-code {:country-code (ccr/country-code ccc/worldwide)}]
                {:name (l/list-sorted-by case-kw)
                 :f (fn [chat-id]
                      (let [list-fn (if (in? com/listing-cases-per-100k case-kw)
                                      list-per-100k
                                      list-countries)]
                        (list-fn (conj (assoc prm
                                              :parse_mode "HTML"
                                              :chat-id chat-id
                                              :sort-by-case case-kw)
                                       prm-country-code))))
                 :desc (l/list-sorted-by-desc case-kw)})))))

(defn cmds
  "Create a vector of hash-maps for all available commands."
  []
  (transduce (map cmds-country-code)
             into (into (cmds-general)
                        (cmds-listing))
             (ccc/all-country-codes)))

(defn bot-father-edit-cmds
  "Evaluate this function and upload the results under:
     @BotFather -> ... -> Edit Bot -> Edit Commands"
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
