# [@corona_cases_bot](https://t.me/corona_cases_bot)

> The Internet interprets censorship as damage and routes around it.
> - John Gilmore

Coronavirus disease 2019 (COVID-19) information on Telegram Messenger
[@corona_cases_bot](https://t.me/corona_cases_bot)

![Screenshot](/resources/pics/screenshot_40-percents.jpg)

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

## TODOs
- Show graphs for relative numbers (e.g. percentage)
- Add `/feedback` commands to get an instant/immediate information from users. Tight feedback-loop
- Prediction - extrapolate graphs
- Use buttons instead of `/<command-name>`
- Don't panic: Compare data: Corona vs. Flu vs. World population and Show deaths
  rates distribution by age / age-groups; probability calculator
- Improve user experience - show only interpolated graphs
- Automate repo CSSEGISandData/COVID-19 snapshot download
