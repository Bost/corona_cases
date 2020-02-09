#!/usr/bin/env fish

# prod environment
set --local REMOTE heroku-corona-cases-bot
set --local APP corona-cases-bot

# test environment
# set --local REMOTE heroku-hokuspokus-bot
# set --local APP hokuspokus-bot

echo "APP" $APP
echo "REMOTE" $REMOTE
echo ""

heroku addons:open papertrail --app $APP; and \
heroku ps:scale web=0 --app $APP; and \
git push $REMOTE master; and \
heroku config:set BOT_VER=(git rev-parse --short master) --app $APP; and \
heroku ps:scale web=1 --app $APP

# heroku ps:scale web=0 --app $APP; and \
# heroku ps:scale web=1 --app $APP
# heroku open --app $APP
# heroku logs --tail --app $APP
