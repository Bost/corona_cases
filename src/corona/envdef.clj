;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.envdef)

(ns corona.envdef
  (:require [clojure.string :as cstr]
            [corona.common :as com]))

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
  (first
   ["covid-tracker-us.herokuapp.com"
    "coronavirus-tracker-api.herokuapp.com"]))

(def owid-prod "https://covid.ourworldindata.org/data/owid-covid-data.json")
(def owid-mockup (format "http://localhost:%s/owid-covid-data.json" com/mockup-port))

(def v1-mockup
  (format "localhost:%s" com/mockup-port)
  #_"localhost:8000")

(def environment
  "Mapping env-type -> bot-name"
  {(keyword corona-cases)
   {:level 0
    :bot-name corona-cases-bot
    :web-server "https://corona-cases-bot.herokuapp.com"
    :json-server {:v1 api-servers :owid owid-prod}
    :cli {"--prod" "corona-cases"}
    :telegram-token token-corona-cases}

   (keyword hokuspokus)
   {:level 1
    :bot-name hokuspokus-bot
    :web-server "https://hokuspokus-bot.herokuapp.com"
    :json-server {:v1 api-servers :owid owid-prod}
    :cli {"--test" "hokuspokus"}
    :telegram-token token-hokuspokus}

   (keyword "local")
   {:level 2
    :bot-name hokuspokus-bot
    ;; :web-server nil ;; intentionally undefined
    :json-server {:v1 api-servers :owid owid-prod}
    :telegram-token token-hokuspokus}

   (keyword "devel")
   {:level 3
    :bot-name hokuspokus-bot
    ;; :web-server nil ;; intentionally undefined
    :json-server {:v1 v1-mockup :owid owid-mockup}
    :telegram-token token-hokuspokus}})

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.envdef)
