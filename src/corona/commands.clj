(ns corona.commands
  (:require
   [clojure.string :as s]
   [corona.api.expdev07 :as data]
   [corona.api.v1 :as v1]
   [corona.countries :as cr]
   [corona.country-codes :as cc]
   [corona.lang :as l]
   [corona.messages :as msg]
   [corona.plot :as p]
   [morse.api :as morse]
   [utils.core :as u :refer [in?] :exclude [id]]
   [corona.common :as co]
   [taoensso.timbre :as timbre :refer :all]
   ))

#_(defn deep-merge
  "Recursively merges maps.
  Thanks to https://dnaeon.github.io/recursively-merging-maps-in-clojure/"
  [& maps]
  (letfn [(m [& xs]
            (if (some #(and (map? %) (not (record? %))) xs)
              (apply merge-with m xs)
              (last xs)))]
    (reduce m maps)))

(defn deep-merge
  "Recursively merges maps.
Thanks to https://gist.github.com/danielpcox/c70a8aa2c36766200a95#gistcomment-2711849"
  [& maps]
  (apply merge-with (fn [& args]
                      (if (every? map? args)
                        (apply deep-merge args)
                        (last args)))
         maps))

#_(defn deep-merge
  "Might be buggy.
See https://gist.github.com/danielpcox/c70a8aa2c36766200a95#gistcomment-2845162"
  [a & maps]
  (if (map? a)
    (apply merge-with deep-merge a maps)
    (apply merge-with deep-merge maps)))

(defn rank [{:keys [rank-kw] :as prm}]
  (->> (data/stats-all-affected-countries-memo prm)
       #_(take 2)
       ;; create and execute sorting function
       ((fn [coll] (sort-by rank-kw > coll)))
       (map-indexed (fn [idx hm]
                      (-> (select-keys hm [:cc])
                          (update-in [:rank rank-kw] (fn [_] idx)))))))

(defn calculate-rankings [prm]
  (let [rakings (->> [:p :c100k :r100k :d100k :i100k]
                     #_(take 3)
                     (map (fn [rank-kw] (rank (assoc prm :rank-kw rank-kw))))
                     (u/transpose))]
    (->> (data/all-affected-country-codes-memo)
         #_(take 2)
         (map (fn [affected-cc]
                (->> rakings
                     (map (fn [ranking]
                            (filter (fn [{:keys [cc]}] (= cc affected-cc))
                                    ranking)))
                     (reduce into [])
                     (apply deep-merge)))))))

(defn world [{:keys [chat-id country-code] :as prm}]
  (let [prm (assoc prm :parse_mode "HTML")]
    (let [options (select-keys prm (keys msg/options))
          cnt-countries (count (data/all-affected-country-codes-memo))
          content (-> (assoc prm
                             :disable_web_page_preview true
                             :cnt-countries cnt-countries)
                      (conj
                       ;; the order of countries should be calculated only once
                       (->> (calculate-rankings prm)
                            (filter (fn [{:keys [cc]}] (= cc country-code)))
                            (map (fn [m] (select-keys m [:rank])))
                            (first)))
                      (msg/detailed-info))]
      (morse/send-text co/token chat-id options content))

    (if-not co/env-devel? ;; don't show the graph when developing
      (let [options (if (msg/worldwide? country-code)
                      (msg/buttons {:chat-id chat-id :cc country-code})
                      {})
            content (msg/toByteArrayAutoClosable
                     (p/plot-country
                      {:day (count (data/raw-dates-unsorted))
                       :cc country-code
                       :stats (v1/pic-data)}))]
        (morse/send-photo co/token chat-id options content))
      (debug "Plot not displayed. co/env-devel?" co/env-devel?))))

(def ^:const cnt-messages-in-listing
  "nr-countries / nr-patitions : 126 / 6, 110 / 5, 149 / 7"
  7)

(defn partition-in-sub-msgs
  "Split the long list of all countries into smaller subparts"
  [col]
  (partition-all (/ (count col) cnt-messages-in-listing) col))

(defn list-countries [{:keys [chat-id sort-by-case] :as prm}]
  (let [sub-msgs (->> (data/stats-all-affected-countries-memo prm)
                      ;; create and execute sorting function
                      ((fn [coll] (sort-by sort-by-case < coll)))
                      (partition-in-sub-msgs))
        cnt-msgs (count sub-msgs)]
    (->> sub-msgs
         #_(take 3)
         #_(take-last 1)
         (map-indexed
          (fn [idx sub-msg]
            (->> (assoc prm :data sub-msg :msg-idx (inc idx) :cnt-msgs cnt-msgs)
                 (msg/list-countries-memo)
                 (morse/send-text co/token chat-id
                                  (select-keys prm (keys msg/options))))))
         doall)))

(defn list-per-100k [{:keys [chat-id sort-by-case] :as prm}]
  (let [sub-msgs (->> (data/stats-all-affected-countries-memo prm)
                      ;; create and execute sorting function
                      ((fn [coll] (sort-by sort-by-case < coll)))
                      (partition-in-sub-msgs))
        cnt-msgs (count sub-msgs)]
    (->> sub-msgs
         #_(take 3)
         #_(take-last 1)
         (map-indexed
          (fn [idx sub-msg]
            (->> (assoc prm :data sub-msg :msg-idx (inc idx) :cnt-msgs cnt-msgs)
                 (msg/list-per-100k-memo)
                 (morse/send-text co/token chat-id
                                  (select-keys prm (keys msg/options))))))
         doall)))

(defn explain [{:keys [chat-id] :as prm}]
  (morse/send-text co/token chat-id msg/options (msg/explain prm)))

(defn feedback [{:keys [chat-id] :as prm}]
  (morse/send-text co/token chat-id msg/options (msg/feedback prm)))

;; (defn language [{:keys [chat-id] :as prm}]
;;   (morse/send-text co/token chat-id msg/options (msg/language prm)))

(defn contributors [{:keys [chat-id] :as prm}]
  (morse/send-text
   co/token chat-id msg/options
   (msg/contributors prm)))

(defn- normalize
  "Country name w/o spaces: e.g. \"United States\" => \"UnitedStates\""
  [country-code]
  (-> (cr/country-name country-code)
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
                :pred (msg/pred-fn country-code)}))})
   [#(s/lower-case %)  ;; /de
    #(s/upper-case %)  ;; /DE
    #(s/capitalize %)  ;; /De
    #(s/lower-case (cc/country-code-3-letter %)) ;; /deu
    #(s/upper-case (cc/country-code-3-letter %)) ;; /DEU
    #(s/capitalize (cc/country-code-3-letter %)) ;; /Deu
    #(s/lower-case (normalize %))   ;; /unitedstates
    #(s/upper-case (normalize %))   ;; /UNITEDSTATES
    #(normalize %)]))

(defn cmds-general []
  (let [prm
        (conj
         {:pred (fn [_] true)}
         msg/options)

        prm-country-code {:country-code (cr/country-code cc/worldwide)}]
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
  (->> co/listing-cases-absolute
       (into co/listing-cases-per-100k)
       (map (fn [case-kw]
              (let [prm (conj {:pred (fn [_] true)} msg/options)
                    prm-country-code {:country-code (cr/country-code cc/worldwide)}]
                {:name (l/list-sorted-by case-kw)
                 :f (fn [chat-id]
                      (let [list-fn (if (in? co/listing-cases-per-100k case-kw)
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
             (cc/all-country-codes)))

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
