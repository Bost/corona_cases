#!/usr/bin/env fish

# set up environment
# set --local envName corona-cases  # prod
set --local envName hokuspokus    # test

set --local APP $envName"-bot"
set --local REMOTE "heroku-"$APP

heroku ps:scale web=0 --app $APP; and \
heroku ps:scale web=1 --app $APP
