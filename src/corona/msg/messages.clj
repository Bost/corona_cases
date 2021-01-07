;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.msg.messages)

(ns corona.msg.messages
  (:require [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.string :as cstr]
            [corona.api.expdev07 :as data]
            [corona.common :as com]
            [corona.lang :as lang]
            [corona.estimate :as est]
            [corona.msg.common :as msgc]
            [corona.plot :as plot]
            [morse.api :as morse]
            [taoensso.timbre :as timbre :refer [debugf]]))

;; (set! *warn-on-reflection* true)

(defn bot-name-formatted []
  (cstr/replace com/bot-name #"_" "\\\\_"))

(def ^:const options {:parse_mode com/markdown :disable_web_page_preview true})

(defn create-pred-hm [ccode] (data/create-pred-hm ccode))

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
    (partial reduce into))
   (map (fn [aggregation-kw]
          (map (fn [case-kw]
                 (conj
                  {:text (lang/button-text case-kw aggregation-kw)
                   :callback_data (pr-str (assoc prm
                                                 :case-kw case-kw
                                                 :type aggregation-kw))}
                  ;; when used the Telegram Web doesn't display the picture
                  ;; see also https://core.telegram.org/bots/api#sendphoto
                  #_{:caption "Foo"}))
               com/absolute-cases))
        com/aggregation-cases)))

(defn worldwide-plots
  ([prm] (worldwide-plots "worldwide-plots" prm))
  ([fun-id {:keys [data]}]
   (let [data-hm (edn/read-string data)
         chat-id (:chat-id data-hm)
         options ((comp reply-markup-btns (partial select-keys data-hm))
                  [:chat-id :ccode])
         content (let [plot-fn (if (= (:type data-hm) :sum)
                                 plot/plot-sum plot/plot-absolute)]
                   ;; the plot is fetched from the cache, stats and report need
                   ;; not to be specified
                   (plot-fn (:case-kw data-hm)))]
     (doall
      (morse/send-photo com/telegram-token chat-id options content))
     (debugf "[%s] send-photo: %s bytes sent" fun-id (count content)))))

;; (defn language [prm]
;;   (format
;;    "/lang:%s\n/lang:%s\n/lang:%s\n"
;;    "sk"
;;    "de"
;;    "en"
;;    (footer prm)))

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
   (format "• %s %s = %s + %s\n"
           lang/closed lang/cases lang/recovered lang/deaths)
   (format "• %s: <%s> / %s\n" lang/percentage-calc lang/cases lang/confirmed)
   (format (str "• %s:\n"
                "  %s\n")
           lang/active-max
           (:doc (meta #'lang/active-max)))
   (format (str "• %s:\n"
                "  %s\n")
           lang/active-last-7
           (:doc (meta #'lang/active-last-7)))
   (format (str "• %s:\n"
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
   (format (str "• %s:\n"
                "  %s: %s\n")
           lang/recov-estim
           lang/recov-estim-explained
           est/shift-recovery)
   (format (str "• %s:\n"
                "  %s: %s\n")
           lang/activ-estim
           lang/activ-estim-explained
           est/shift-deaths)
   "\n"
   (format "🙏 Thanks goes to %s. Please ✍️ write %s\n"
           (com/encode-cmd lang/contributors)
           (com/encode-cmd lang/feedback))
   "\n"
   (msgc/footer parse_mode)))

(def ^:const bot-description
  "Keep it in sync with README.md"
  "Coronavirus disease 2019 (COVID-19) information on Telegram Messenger")

(defn bot-father-edit-description [] bot-description)
(defn bot-father-edit-about [] bot-description)
(defn bot-father-edit-inline-placeholder
  "Appears when a user types: @<botname>
  See https://core.telegram.org/bots/inline"
  [] "Coronavirus Information")

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.msg.messages)
