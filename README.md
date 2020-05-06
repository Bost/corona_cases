# [@corona_cases_bot](https://t.me/corona_cases_bot)

## Raison d´être
> The Internet interprets censorship as damage and routes around it.
> - John Gilmore

Coronavirus disease 2019 (COVID-19) information on Telegram Messenger
[@corona_cases_bot](https://t.me/corona_cases_bot)

![Screenshot](/resources/pics/screenshot_1-50-percents.jpg)
![Screenshot](/resources/pics/screenshot_2-50-percents.jpg)

## Running locally
Start Clojure REPL:
```fish
lein repl
```
Start telegram chatbot:
```clojure
(require '[corona.telegram])
(corona.telegram/-main)
```
Start web server:
```clojure
(require 'corona.web)
(corona.web/-main)
```
Then check the [http://localhost:5050/](http://localhost:5050/)

## Deploy to Heroku
Using fish-shell: see [deploy.fish](./deploy.fish).

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
- Create API web service(s) even for own use
- Create home page providing extensive information
- Tables - show: average recovery time, cases in % of population
