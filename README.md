# [@corona_cases_bot](https://t.me/corona_cases_bot)

## Raison d´être
> The Internet interprets censorship as damage and routes around it.
> - John Gilmore

Coronavirus disease 2019 (COVID-19) information on Telegram Messenger
[@corona_cases_bot](https://t.me/corona_cases_bot)

![Screenshot](/resources/pics/screenshot_1-50-percents.jpg)
![Screenshot](/resources/pics/screenshot_2-50-percents.jpg)

## Develop
Emacs Cider `M-x cider-jack-in-clj`

Or start the nREPL:
```fish
/usr/local/bin/clojure -Sdeps '{:deps {nrepl {:mvn/version "0.8.2"} refactor-nrepl {:mvn/version "2.5.0"} cider/cider-nrepl {:mvn/version "0.25.4"}}}' -m nrepl.cmdline --middleware '["refactor-nrepl.middleware/wrap-refactor", "cider.nrepl/cider-middleware"]'
```
And connect to it from the editor of your choice.

Start telegram chatbot long-polling:
```clojure
(require '[corona.telegram])
(corona.telegram/start)
```
Start web server:
```clojure
(require '[corona.web])
(alter-var-root #'system component/start)
```
Then check the [http://localhost:5050/](http://localhost:5050/)

## Run locally

```fish
bin/build; and heroku local --env=.heroku-local.env
```
TODO `heroku local` - ask about setting environment variables on the CLI, i.e.:
```fish
bin/build; and heroku local --env=.heroku-local.env --set COMMIT=...`
```

## Deploy to Heroku
Using [deploy.clj](./deploy.clj).

## TODOs
- Graphs:
  -- better user experience - show:
     only interpolated graphs
     relative numbers (e.g. percentage)
     doubling time as in the spiegel.de
     logarithmic scale
- Prediction - extrapolate graphs
- Use buttons instead of `/<command-name>`
- Don't panic: Compare data: Corona vs. Flu vs. World population and Show deaths
  rates distribution by age / age-groups; probability calculator
- Create home page providing extensive information
- Tables - show: average recovery time, cases in % of population
