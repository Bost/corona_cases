;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.msg.text.messages)

(ns corona.msg.text.messages
  (:require [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.string :as cstr]
            [corona.api.cache :as cache]
            [corona.common :as com :refer [lense kact kd kest kmax krep k1e5
                                           kls7 kabs kavg]]
            [corona.cases :as cases]
            [corona.estimate :as est]
            [corona.lang :as lang]
            [corona.macro :refer [defn-fun-id]]
            [corona.msg.text.common :as msgc]
            [corona.msg.graph.plot :as plot]
            [morse.api :as morse]
            [corona.macro :refer [defn-fun-id debugf]]))

;; (set! *warn-on-reflection* true)

(defn bot-name-formatted []
  (cstr/replace com/bot-name #"_" "\\\\_"))

(def ^:const options {:parse_mode com/markdown :disable_web_page_preview true})

(defn link [name url parse_mode]
  (if (= parse_mode com/html)
    (format "<a href=\"%s\">%s</a>" url name)
    (format "[%s](%s)" name url)))

(defn reply-markup-btns [prm]
  ((comp
    (partial hash-map :reply_markup)
    json/write-str
    (partial hash-map :inline_keyboard)
    vector
    (partial
     mapv
     (partial
      apply
      (fn [aggregation-kw case-kw]
        (conj
         {:text (lang/button-text case-kw aggregation-kw)
          :callback_data (pr-str (assoc (dissoc prm :message_id)
                                        :case-kw case-kw
                                        :type aggregation-kw))}
         ;; when used the Telegram Web doesn't display the picture
         ;; see also https://core.telegram.org/bots/api#sendphoto
         #_{:caption "Foo"})))))
   cases/cartesian-product-all-case-types))

(defn-fun-id worldwide-plots ""
  [{:keys [data message]}]
  (let [data-hm (edn/read-string data)

        {chat-id :chat-id ccode :ccode plot-type :type case-kw :case-kw}
        data-hm

        message-id (:message_id message)
        options (reply-markup-btns {:chat-id chat-id :ccode ccode
                                    :message_id message-id})
        id (cache/aggregation-hash)
        url (format "%s/%s/%s/%s/%s"
                    com/webapp-server com/graphs-path
                    id (name plot-type) (name case-kw))]
    (doall
     (if com/use-webhook?
       ;; alternatively send file to a dump channel, get file id, edit message
       ;; media, delete message from channel
       (morse/edit-media com/telegram-token chat-id message-id options
                         {:type "photo" :media url})
       (morse/send-photo com/telegram-token chat-id options
                         ;; the plot is fetched from the cache, stats and report
                         ;; need not to be specified
                         (plot/aggregation! id (:type data-hm)
                                            (:case-kw data-hm)))))))

;; mapvals
;; https://clojurians.zulipchat.com/#narrow/stream/180378-slack-archive/topic/beginners/near/191238200

(defn feedback [] lang/write-a-message-to)

(defn header-simple [parse_mode]
  (format
   "🦠 @%s %s %s, %s\n"
   com/bot-name-in-markdown
   com/botver
   (link "👩🏼‍💻 GitHub" "https://github.com/Bost/corona_cases" parse_mode)
   (link "👨🏻‍💻 GitLab" "https://gitlab.com/rostislav.svoboda/corona_cases" parse_mode)))

(defn contributors [parse_mode]
  (format "%s\n%s\n\n%s\n\n%s"
          (header-simple parse_mode)
          (cstr/join "\n" ["DerAnweiser"
                           (link "maty535" "https://github.com/maty535" parse_mode)
                           "@kostanjsek"
                           "@DistrictBC"
                           "Michael J."
                           "Johannes D."])
          lang/contributors-text
          (msgc/footer parse_mode)))

(defn explain [parse_mode]
  (str
   (header-simple parse_mode)
   "\n"
   (format "%s\n"
           lang/data-source-text)
   "\n"
   (com/encode-pseudo-cmd
    (str
     (format "• %s %s = %s + %s\n"
             lang/closed lang/cases lang/recovered lang/deaths)
     (format "• %s: <%s> / %s\n" lang/percentage-calc lang/cases lang/confirmed)
     (format (str "• %s:\n"
                  "  %s\n")
             lang/active-last-7
             (:doc (meta #'lang/active-last-7)))
     (format (str "• %s:\n"
                  "  %s\n")
             lang/vaccin-last-7
             (:doc (meta #'lang/vaccin-last-7)))
     #_(format (str "• %s:\n"
                    "  %s\n")
               lang/active-last-7-med
               (:doc (meta #'lang/active-last-7-med)))
     (format (str "• %s:\n"
                  "  %s\n")
             lang/active-last-7-avg
             (:doc (meta #'lang/active-last-7-avg)))
     (format (str "• %s = (%s - %s) / 7\n"
                  "  %s\n")
             lang/active-change-last-7-avg
             lang/active lang/active-last-8th
             (:doc (meta #'lang/active-change-last-7-avg)))
     ;; (abbreviated) content of the former reference message
     (format (str "• %s, %s, %s, %s:\n"
                  "  %s\n")
             lang/active-per-1e5
             lang/recove-per-1e5
             lang/deaths-per-1e5
             lang/closed-per-1e5
             lang/cases-per-1e5)
     "\n"
     (format "%s:\n" lang/estim-motivation)
     (format (str "• %s:\n"
                  "  %s: %s\n")
             lang/recov-estim
             lang/recov-estim-explained
             est/shift-recovery)
     (format (str "• %s:\n"
                  "  %s: %s\n")
             lang/activ-estim
             lang/activ-estim-explained
             est/shift-deaths))
    parse_mode)
   "\n"
   (format "🙏 Thanks goes to %s. Please ✍️ write %s\n"
           (com/encode-cmd lang/contributors)
           (com/encode-cmd lang/feedback))
   "\n"
   (msgc/footer parse_mode false)))

(def ^:const bot-description
  "Keep it in sync with README.md"
  "Coronavirus disease 2019 (COVID-19) information on Telegram Messenger")

(defn bot-father-edit-description [] bot-description)
(defn bot-father-edit-about [] bot-description)
(defn bot-father-edit-inline-placeholder
  "Appears when a user types: @<botname>
  See https://core.telegram.org/bots/inline"
  [] "Coronavirus Information")

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.msg.text.messages)
