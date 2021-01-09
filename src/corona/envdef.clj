;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.envdef)

(ns corona.envdef
  (:require [clojure.string :as cstr]))

(def ^:const ^String prod "prod")
(def ^:const ^String corona-cases "corona-cases")
(def ^:const ^String hokuspokus "hokuspokus")

(def token-corona-cases (System/getenv "TELEGRAM_TOKEN_CORONA_CASES"))
(def token-hokuspokus   (System/getenv "TELEGRAM_TOKEN_HOKUSPOKUS"))

(def fmt-bot-name-fn (comp
                      (fn [s] (cstr/replace s #"-" "\\_"))
                      (partial format "%s_bot")))

(def ^:const ^String hokuspokus-bot (fmt-bot-name-fn hokuspokus))

(def ^:const ^String corona-cases-bot (fmt-bot-name-fn corona-cases))

(def api-servers
  "TODO iterate over the list in case of status 503 - service not available"
  (second
   ["covid-tracker-us.herokuapp.com" ; is down
    "coronavirus-tracker-api.herokuapp.com"]))
(def environment
  "Mapping env-type -> bot-name"
  {(keyword corona-cases)
   {:level 0
    :bot-name corona-cases-bot
    :web-server "https://corona-cases-bot.herokuapp.com"
    :json-server api-servers
    :cli {"--prod" "corona-cases"}
    :telegram-token token-corona-cases}

   (keyword hokuspokus)
   {:level 1
    :bot-name hokuspokus-bot
    :web-server "https://hokuspokus-bot.herokuapp.com"
    :json-server api-servers
    :cli {"--test" "hokuspokus"}
    :telegram-token token-hokuspokus}

   (keyword "local")
   {:level 2
    :bot-name hokuspokus-bot
    ;; :web-server nil ;; intentionally undefined
    :json-server api-servers
    :telegram-token token-hokuspokus}

   (keyword "devel")
   {:level 3
    :bot-name hokuspokus-bot
    ;; :web-server nil ;; intentionally undefined
    :json-server "localhost:8000"
    :telegram-token token-hokuspokus}})

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.envdef)
