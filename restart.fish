#!/usr/bin/env fish

# prod environment
set --local REMOTE heroku-corona-cases-bot
set --local APP corona-cases-bot

# test environment
# set --local REMOTE heroku-hokuspokus-bot
# set --local APP hokuspokus-bot

heroku ps:scale web=0 --app $APP; and \
heroku ps:scale web=1 --app $APP
