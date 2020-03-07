#!/usr/bin/env fish

# set up environment
set --local envName corona-cases  # prod
# set --local envName hokuspokus    # test

set --local APP $envName"-bot"
set --local REMOTE "heroku-"$APP

# set --local pushFlags "--force"

echo "APP" $APP
echo "REMOTE" $REMOTE
echo ""

# git clone https://github.com/CSSEGISandData/COVID-19.git ../COVID-19
# set --local repo ../COVID-19/.git; and \
# git --git-dir=$repo pull --rebase origin master; and \
# cp -r ../COVID-19/csse_covid_19_data/csse_covid_19_daily_reports/*.csv \
#     resources/csv; and \
# git add resources/csv/*.csv

# if test $status = 0
#     git commit -m "Add new csv file(s)"
# end

# heroku logs --tail --app $APP blocks the execution
heroku addons:open papertrail --app $APP; and \
heroku ps:scale web=0 --app $APP; and \
git push $pushFlags $REMOTE master; and \
set botVerSHA (git rev-parse --short master); and \
set botVerNr (grep --max-count=1 --only-matching '\([0-9]\+\.\)\+[0-9]\+' project.clj); and \
heroku config:set BOT_VER=$botVerSHA --app $APP; and \
heroku ps:scale web=1 --app $APP

# publish source code only when deploying to production
if test $envName = corona-cases
    set gitTag $botVerNr"-"$botVerSHA; and \
    git tag $gitTag; and \
    git push --tags $pushFlags origin; and \
    git push --tags $pushFlags gitlab
end

# heroku ps:scale web=0 --app $APP; and \
# heroku ps:scale web=1 --app $APP
# heroku open --app $APP
# heroku addons:open papertrail --app $APP
# heroku logs --tail --app $APP
# heroku maintenance:on  --app $APP
# heroku config:unset type private_key_id private_key client_id client_email \
    # --app $APP
# heroku maintenance:off --app $APP

# Fix: Push rejected, submodule install failed
# Thanks to https://stackoverflow.com/a/31545903
# heroku plugins:install https://github.com/heroku/heroku-repo.git
# heroku repo:reset --app $APP

# heroku run bash --app $APP

# run locally:
# lein uberjar; and \
# set --export BOT_VER $botVerSHA; and \
# java $JVM_OPTS -cp target/corona_cases-standalone.jar:$cljjar:$cljsjar \
    # clojure.main -m corona.web
