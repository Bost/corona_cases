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
/usr/local/bin/clojure \
    -Sdeps '{:deps {nrepl/nrepl {:mvn/version "0.8.3"} refactor-nrepl {:mvn/version "2.5.0"} cider/cider-nrepl {:mvn/version "0.25.5"}}}' \
    -m nrepl.cmdline \
    --middleware '["refactor-nrepl.middleware/wrap-refactor", "cider.nrepl/cider-middleware"]'
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
# or:
bin/build; and heroku local --env=.heroku-local.env --set COMMIT=...`
```

## Deploy to Heroku
Done using [babashka](https://github.com/borkdude/babashka). See also
[heroku.clj](./heroku.clj).

bb heroku.clj deploy --heroku-env <heroku-app-name>
