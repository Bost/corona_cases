# Coronavirus Cases - COVID-19

Coronavirus numbers via Telegram Chatbot
[@corona_cases_bot](https://t.me/corona_cases_bot)

## Running locally

```clojure
(require 'coronavirus.web)
(def server (coronavirus.web/-main))

(require '[coronavirus.telegram])
(coronavirus.telegram/-main)
```

See [localhost:5000](http://localhost:5000/).

## Deploy to Heroku

See [deploy.fish](./deploy.fish).
