;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.commands)

(ns corona.commands
  (:require [clojure.string :as cstr]
            [corona.common :as com]
            [corona.api.cache :as cache]
            [corona.countries :as ccr]
            [corona.country-codes :as ccc]
            [corona.lang :as lang]
            [corona.msg.text.common :as msgc]
            [corona.msg.text.details :as msgi]
            [corona.msg.text.messages :as msg]
            [corona.msg.text.lists :as msgl]
            [corona.macro :refer [defn-fun-id]]
            [corona.msg.graph.plot :as p]
            [morse.api :as morse]
            [corona.macro :refer [defn-fun-id debugf]]
            [utils.core :as u :refer [in?]]
            [corona.api.expdev07 :as data]
            [taoensso.timbre :as timbre]
            [corona.models.dbase :as dbase]))

;; (set! *warn-on-reflection* true)

(def log-morse-send-cmds false)

(defn send-text
  "!!! Can't be defined by defn-fun-id !!!"
  ([fun-id prm content] (send-text fun-id prm msg/options content))
  ([fun-id {:keys [chat-id]} options content]
   (let [resp-body (doall
                    (morse/send-text com/telegram-token chat-id options content))]
     (when log-morse-send-cmds
       (timbre/debugf "[%s] morse/send-text: resp-body %s" fun-id resp-body))
     (timbre/debugf "[%s] morse/send-text: %s sent" fun-id (com/measure content))
     resp-body)))

(defn-fun-id world "" [{:keys [chat-id ccode] :as prm-orig}]
  (let [prm
         ;; override default parse_mode
        (assoc prm-orig
               :parse_mode com/html
               :pred-hm (data/create-pred-hm ccode))]
    (send-text fun-id ;; defined by defn-fun-id macro
               prm
               (select-keys prm (keys msg/options))
               (msgi/message! ccode))
    (when-let [;; the plot is fetched from the cache, stats and report need not to be
                ;; specified
               content (p/message! ccode)]
      (let [options (if (msgc/worldwide? ccode)
                      (msg/reply-markup-btns (select-keys prm [:chat-id :ccode :message_id]))
                      {})
            resp-body (doall
                       (morse/send-photo com/telegram-token chat-id options content))]
        (when log-morse-send-cmds
          (debugf "morse/send-photo: resp-body %s" resp-body))
        (debugf "morse/send-photo: %s sent" (com/measure content))))))

(defn-fun-id explain "" [{:keys [parse_mode] :as prm}]
  (send-text fun-id prm (msg/explain parse_mode)))

(defn-fun-id feedback "" [prm]
  (send-text fun-id prm (msg/feedback)))

;; (defn language [{:keys [chat-id parse_mode]}]
;;   (doall
;;    (morse/send-text com/telegram-token chat-id msg/options (msg/language parse_mode))))

(defn-fun-id contributors "" [{:keys [parse_mode] :as prm}]
  (send-text fun-id prm (msg/contributors parse_mode)))

(defn- normalize
  "Country name w/o spaces: e.g. \"United States\" => \"UnitedStates\""
  [ccode]
  (cstr/replace (ccr/get-country-name ccode) " " ""))

(defn ccode-handlers
  "E.g.
  (for-country-code \"DE\") =>
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
   [identity                  ;; DE -> DE
    ccc/country-code-3-letter ;; DE -> DEU
    normalize]))              ;; United States -> UnitedStates

(defn-fun-id inline-handlers "" []
  (let [prm (assoc msg/options
                   :ccode (ccr/get-country-code ccc/worldwide))]
    [{:name lang/contributors
      :fun (fn [chat-id] (contributors (assoc prm :chat-id chat-id)))
      :desc "Give credit where credit is due"}
     {:name lang/world
      :fun (fn [chat-id] (world (assoc prm :chat-id chat-id)))
      :desc lang/world-desc}
     {:name lang/start
      :fun  (fn [chat-id] (world (assoc prm :chat-id chat-id)))
      :desc lang/world-desc}
     {:name lang/explain
      :fun (fn [chat-id] (explain (assoc prm :chat-id chat-id)))
      :desc "Explain abbreviations & some additional info"}
     {:name lang/feedback
      :fun (fn [chat-id] (feedback (assoc prm :chat-id chat-id)))

      :desc "Talk to the bot-creator"}
     #_{:name lang/settings
      :fun (fn [chat-id] (setting (assoc prm :chat-id chat-id)))
      :desc "Display user settings - stored in the dbase"}]))

(defn listing-handlers "Command map for listings" []
  ((comp
    (partial
     map
     (fn [case-kw]
       {:name (lang/list-sorted-by case-kw)
        :fun (fn [chat-id]
               ((comp
                 ;; mapping over results implies the knowledge that the type
                 ;; of `(msg-listing-fun case-kw)` is a collection.
                 doall
                 (partial map (fn [content]
                                (morse/send-text com/telegram-token chat-id
                                                 {:parse_mode com/html} content)
                                (timbre/debugf "%s chars sent" (count content))))
                 vals
                 (partial get-in @cache/cache)
                 (partial apply msgl/list-kw)
                 (fn [case-kw]
                   [(if (in? com/listing-cases-per-100k case-kw)
                      'corona.msg.text.lists/per-100k
                      'corona.msg.text.lists/absolute-vals)
                    case-kw]))
                case-kw))
        :desc (lang/list-sorted-by-desc case-kw)}))
    (partial into com/listing-cases-per-100k))
   com/listing-cases-absolute))

(def all-handlers
  "Create a vector of hash-maps for all available commands."
  (transduce (map ccode-handlers)
             into (into (inline-handlers)
                        (listing-handlers))
             ccc/all-country-codes))

(defn bot-father-edit
  "Evaluate this function and upload the results under:
     @BotFather -> ... -> Edit Bot -> Edit Commands

  TODO commands /re, /de collide with /recov, /deaths
  TODO /<char> show a list of countries under starting with this letter."
  []
  (->> (inline-handlers)
       (remove (fn [hm]
                 (in? [lang/start
                       ;; Need to save space on smartphones. Sorry guys.
                       lang/contributors
                       lang/feedback
                       ] (:name hm))))
       (reverse)
       (into (listing-handlers))
       (map (fn [{:keys [name desc]}] (println name "-" desc)))
       (doall)))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.commands)
