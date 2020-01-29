
# coronavirus

## Running Locally

```sh
(require 'coronavirus.web)
(def server (coronavirus.web/-main))

(require '[coronavirus.telegram])
(coronavirus.telegram/-main)
```

Your app should now be running on [localhost:5000](http://localhost:5000/).

## Deploying to Heroku

```fish
# set up remotes - prod environment
set --local REMOTE heroku-corona-cases-bot
set --local APP corona-cases-bot
```

```fish
# set up remotes - test environment
set --local REMOTE heroku-hokuspokus-bot
set --local APP hokuspokus-bot
```

```fish
heroku create

# heroku ps:scale web=0 --app $APP; and \
# git push $REMOTE master; and \
# heroku config:set BOT_VER=(git rev-parse --short master) --app $APP; \
# heroku ps:scale web=1 --app $APP

# heroku ps:scale web=0 --app $APP; and \
# heroku ps:scale web=1 --app $APP

git push $REMOTE master; and \
heroku config:set BOT_VER=(git rev-parse --short master) --app $APP

heroku open --app $APP
```
