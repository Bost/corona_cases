Coronavirus disease 2019 (COVID-19) information on Telegram Messenger
[@corona_cases_bot](https://t.me/corona_cases_bot)

## Running locally

```fish
lein repl
```

```clojure
(require 'corona.web)
(def server (corona.web/-main))

(require '[corona.telegram])
(corona.telegram/-main)
```

See [localhost:5000](http://localhost:5000/).

## Deploy to Heroku

Using fish-shell: see [deploy.fish](./deploy.fish).
