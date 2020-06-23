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

## Development klipse cljs repl

To get an interactive development environment run:

    lein fig:build

This will auto compile and send all changes to the browser without the
need to reload. After the compilation process is complete, you will
get a Browser Connected REPL. An easy way to try it is:

    (js/alert "Am I connected?")

and you should see an alert in the browser window.

To clean all compiled files:

	lein clean

To create a production build run:

	lein clean
	lein fig:min

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
