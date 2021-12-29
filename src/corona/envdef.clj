;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.envdef)

(ns corona.envdef
  (:require [clojure.string :as cstr]))

(def ^:const ^String prod-opt "prod")
(def ^:const ^String test-opt "test")
(def ^:const ^String corona-cases "corona-cases")
(def ^:const ^String hokuspokus "hokuspokus")
(def ^:const ^String wicki "wicki")
(def ^:const ^String heroku-app-domain "herokuapp.com")

(def token-corona-cases (System/getenv "TELEGRAM_TOKEN_CORONA_CASES"))
(def token-hokuspokus   (System/getenv "TELEGRAM_TOKEN_HOKUSPOKUS"))
(def token-wicki        (System/getenv "TELEGRAM_TOKEN_WICKI"))

(def fmt-telegram-bot-name-fn
  (comp
   (fn [s] (cstr/replace s #"-" "\\_"))
   (partial format "%s_bot")))

(def fmt-web-server-name-fn
  (comp
   (fn [s] (cstr/replace s #"_" "-"))
   (partial format "%s_bot")))

(def ^:const ^String corona-cases-telegram-bot
  (fmt-telegram-bot-name-fn corona-cases))
(def ^:const ^String hokuspokus-telegram-bot
  (fmt-telegram-bot-name-fn hokuspokus))
(def ^:const ^String wicki-telegram-bot
  (fmt-telegram-bot-name-fn wicki))

(def ^:const ^String corona-cases-web-server
  (fmt-web-server-name-fn corona-cases))
(def ^:const ^String hokuspokus-web-server
  (fmt-web-server-name-fn hokuspokus))
(def ^:const ^String wicki-web-server
  (fmt-web-server-name-fn wicki))

(def api-servers
  "List of API servers to iterate if status 503 - service not available, etc."
  ["coronavirus-tracker-api.herokuapp.com"
   "covid-tracker-us.herokuapp.com"])

(def ^:const ^Long webapp-port
  (if-let [env-port (System/getenv "PORT")]
    (read-string env-port)
    ;; keep port-nr in sync with README.md
    5050))

(def ^:const ^Long mockup-port (inc webapp-port))

(def ^:const ^String owid-prod
  "https://covid.ourworldindata.org/data/owid-covid-data.json")

(def ^:const ^String owid-mockup
  (format "http://localhost:%s/owid-covid-data.json" mockup-port))

(def v1-mockups
  [
   (format "localhost:%s" mockup-port)
   #_"localhost:8000"])

(def environment
  "Mapping env-type -> bot-name"
  {(keyword corona-cases)
   {:level 0
    :bot-name corona-cases-telegram-bot
    :web-server (format "https://%s.%s" corona-cases-web-server heroku-app-domain)
    :json-server {:v1 api-servers :owid owid-prod}
    :cli {(format "--%s" prod-opt) corona-cases}
    :telegram-token token-corona-cases}

   (keyword hokuspokus)
   {:level 1
    :bot-name hokuspokus-telegram-bot
    :web-server (format "https://%s.%s" hokuspokus-web-server heroku-app-domain)
    :json-server {:v1 api-servers :owid owid-prod}
    :cli {(format "--%s" test-opt) hokuspokus}
    :telegram-token token-hokuspokus}

   (keyword "local")
   {:level 2
    :bot-name hokuspokus-telegram-bot
    ;; :web-server nil ;; intentionally undefined
    :json-server {:v1 api-servers :owid owid-prod}
    :telegram-token token-hokuspokus}

   (keyword "devel")
   {:level 3
    :bot-name wicki-telegram-bot
    ;; :web-server nil ;; intentionally undefined
    :json-server {:v1 v1-mockups :owid owid-mockup}
    :telegram-token token-wicki}})

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.envdef)
