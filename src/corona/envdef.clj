(printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.envdef)

(ns corona.envdef)

(def ^:const project-name "corona_cases")

(def prod "PROD")
(def hokuspokus "HOKUSPOKUS")

(def environment
  "Mapping env-type -> bot-name"
  {
   prod
   {:level 0
    :bot-name project-name
    :web-server "https://corona-cases-bot.herokuapp.com"
    :json-server "covid-tracker-us.herokuapp.com"
    :cli {"--prod" "corona-cases"}}

   hokuspokus
   {:level 1
    :bot-name "hokuspokus"
    :web-server "https://hokuspokus-bot.herokuapp.com"
    :json-server "covid-tracker-us.herokuapp.com"
    :cli {"--test" "hokuspokus"}}

   "LOCAL"
   {:level 2
    :bot-name "hokuspokus"
    :web-server nil ;; intentionally undefined
    :json-server "covid-tracker-us.herokuapp.com"}

   "DEVEL"
   {:level 3
    :bot-name "hokuspokus"
    :web-server nil ;; intentionally undefined
    :json-server "localhost:8000"}})

(printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.envdef)
