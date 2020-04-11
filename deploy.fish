#!/usr/bin/env fish

# set up environment
set botEnvs --test --prod

set prmEnvName $argv[1]

# TODO see https://github.com/jorgebucaran/fish-getopts
switch $prmEnvName
    case $botEnvs[1]
        set envName hokuspokus
    case $botEnvs[2]
        set envName corona-cases
    case \*
        printf "ERROR: Unknown parameter: %s\n" $prmEnvName
        # w/o the '--' every list element gets printed on a separate line
        # as if invoked in a for-loop. WTF?
        printf "Possible values: %s\n" (string join -- ", " $botEnvs)
        printf "\n"
        printf "Examples:\n"
        for botEnv in $botEnvs
            printf "%s %s\n" (status --current-filename) $botEnv
        end
        exit 1
end

set APP $envName"-bot"
set REMOTE "heroku-"$APP

# TODO accept --force from the command line
# set --local pushFlags "--force"

printf "APP: %s\n"    $APP
printf "REMOTE: %s\n" $REMOTE
printf "\n"

# need to define LEIN_SNAPSHOTS_IN_RELEASE=true because of
# cljplot "0.0.2-SNAPSHOT"
# heroku config:set LEIN_SNAPSHOTS_IN_RELEASE=true --app $APP
# heroku config:unset LEIN_SNAPSHOTS_IN_RELEASE --app $APP
# heroku config:set CORONA_ENV_TYPE="PROD"  --app $APP
# heroku config:set CORONA_ENV_TYPE="TEST"  --app $APP
# heroku config:set CORONA_ENV_TYPE="DEVEL" --app $APP
# heroku config:set PORT="..."              --app $APP # optional
# heroku config:set HOME_PAGE="..."         --app $APP

# set dataSoure COVID-19_repo
set dataSoure coronavirus-tracker-api

if test $dataSoure = COVID-19_repo
    # git clone https://github.com/CSSEGISandData/COVID-19.git ../COVID-19
    set --local repo ../COVID-19/.git; and \
    git --git-dir=$repo pull --rebase origin master; and \
    cp -r ../COVID-19/csse_covid_19_data/csse_covid_19_daily_reports/*.csv \
        resources/csv; and \
    git add resources/csv/*.csv

    if test $status = 0
        git commit -m "Add new csv file(s)"
    end
else if test $dataSoure = coronavirus-tracker-api
    #
else
    echo "Unrecognized dataSoure" $dataSoure
    exit 1
end

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
    # seems like `git push --tags` pushes only tags w/o the code
    set gitTag $botVerNr"-"$botVerSHA; and \
    git tag $gitTag; and \
    git push $pushFlags origin; and \
    git push $pushFlags gitlab; and \
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

# resize screnshot from CLI
# set screenshot path/to/screenshot.jpg
# convert -resize 40% $screenshot resources/pics/screenshot_40-percents.jpg
