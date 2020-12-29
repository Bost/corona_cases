(printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.envdef)

(ns corona.envdef)

(def prod "prod")
(def corona-cases "corona-cases")
(def hokuspokus "hokuspokus")

(def token-corona-cases (System/getenv "TELEGRAM_TOKEN_CORONA_CASES"))
(def token-hokuspokus   (System/getenv "TELEGRAM_TOKEN_HOKUSPOKUS"))

(def environment
  "Mapping env-type -> bot-name"
  {(keyword corona-cases)
   {:level 0
    :bot-name (clojure.string/replace corona-cases #"-" "\\_")
    :web-server "https://corona-cases-bot.herokuapp.com"
    :json-server "covid-tracker-us.herokuapp.com"
    :cli {"--prod" "corona-cases"}
    :telegram-token token-corona-cases}

   (keyword hokuspokus)
   {:level 1
    :bot-name hokuspokus
    :web-server "https://hokuspokus-bot.herokuapp.com"
    :json-server "covid-tracker-us.herokuapp.com"
    :cli {"--test" "hokuspokus"}
    :telegram-token token-hokuspokus}

   (keyword "local")
   {:level 2
    :bot-name hokuspokus
    :web-server nil ;; intentionally undefined
    :json-server "covid-tracker-us.herokuapp.com"
    :telegram-token token-hokuspokus}

   (keyword "devel")
   {:level 3
    :bot-name hokuspokus
    :web-server nil ;; intentionally undefined
    :json-server "localhost:8000"
    :telegram-token token-hokuspokus}})

(printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.envdef)
