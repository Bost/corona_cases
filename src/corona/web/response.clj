;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.web.response)

(ns corona.web.response
  (:require
   [clojure.data.json :as json]
   [clojure.string :as cstr]
   [corona.common :as com]
   [corona.macro :refer [defn-fun-id infof]]))

(def ^:const pom-version "See `pom/pom-version`" nil)
(def ^:const telegram-hook "telegram")

(defn webhook-url [telegram-token]
  (format "%s/%s" com/webapp-server telegram-token))

(def url-telegram       "https://api.telegram.org/bot$TELEGRAM_TOKEN")
(def url-getUpdates     (str url-telegram "/getUpdates"))
(def url-getMe          (str url-telegram "/getMe"))
(def url-deleteWebhook  (str url-telegram "/deleteWebhook"))
(def url-getWebhookInfo (str url-telegram "/getWebhookInfo"))
(def url-setWebhook     (str url-telegram "/setWebhook"))
(def url-sendMessage    (str url-telegram "/sendMessage"))
(def url-sendPhoto      (str url-telegram "/sendPhoto"))

(defn links []
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body
   (cstr/join
    "\n"
    (into
     (com/show-env)
     [""
      "Send out these commands from shell:"
      ""
      (when com/webapp-server
        (format "curl --request POST \"%s/%s/$TELEGRAM_TOKEN\""
                com/webapp-server telegram-hook))
      #_(format "curl --request POST \"%s/%s/$TELEGRAM_TOKEN\""
                com/webapp-server google-hook)
      ""
      (format "curl %s %s \"%s\""
              (str "--form \"url=\"" (webhook-url "$TELEGRAM_TOKEN"))
              "--form \"drop_pending_updates=true\""
              url-setWebhook)

      (format "curl --request POST %s \"%s\""
              "--form \"drop_pending_updates=true\""
              url-deleteWebhook)
      (format "curl --request POST \"%s\"" url-getWebhookInfo)
      ""
      (format "curl --request GET  \"%s\"" url-getMe)
      (format "curl --request POST \"%s\"" url-getMe)
      (format "curl --request GET  \"%s\"" url-getUpdates)
      (format "curl --request POST \"%s\"" url-getUpdates)
      (format "curl --request GET  \"%s\" | jq .message.chat.id" url-getUpdates)
      (format "curl --request POST \"%s\" | jq .message.chat.id" url-getUpdates)
      ""
      (format "curl --request POST -H '%s' -d '%s' \"%s\""
              "Content-Type: application/json"
              (format (str "{\"chat_id\":%s,\"text\":\"curl test msg\","
                           "\"disable_notification\":true}")
                      com/chat-id)
              url-sendMessage)
      ""
      (format "curl --request POST --form %s --form %s \"%s\""
              (format "chat_id=%s" com/chat-id)
              "photo=@/tmp/pic.png"
              url-sendPhoto)]))})

#_(defn-fun-id web-service "" [{:keys [type] :as prm}]
  (infof "%s" prm)
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body
   (json/write-str
    (->>
     (condp = type
       :names (conj {"desc" com/desc-ws})
       :codes (conj {"desc" com/desc-ws})
       (format "Error. Wrong type %s" type))
     (conj (when-not pom-version
             {"warn" "Under construction. Don't use it in PROD env"}))
     (conj {"source" "https://github.com/Bost/corona_cases"})
     ;; swapped order x y -> y x
     (into (sorted-map-by (fn [x y] (compare y x))))))})

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.web.response)
