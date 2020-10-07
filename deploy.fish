#!/usr/bin/env fish

# set up the environment
set hostEnvs --test --prod

set prmEnvName $argv[1]
set restArgs   $argv[2..-1]

# see https://github.com/jorgebucaran/fish-getopts
switch $prmEnvName
    case $hostEnvs[1]
        set envName hokuspokus
    case $hostEnvs[2]
        set envName corona-cases
    case \*
        if test -z "$prmEnvName"
            printf "ERR: Undefined parameter\n"
        else
            printf "ERR: Unknown parameter: %s\n" $prmEnvName
        end
        # w/o the '--' every list element gets printed on a separate line
        # as if invoked in a for-loop. WTF?
        printf "Usage: %s {%s}\n" (basename (status --current-filename)) \
                                  (string join -- " | " $hostEnvs)
        printf "\n"
        printf "Examples:\n"
        for hostEnv in $hostEnvs
            printf "%s %s\n" (status --current-filename) $hostEnv
        end
        exit 1
end

set APP $envName"-bot"
set REMOTE "heroku-"$APP

printf "DBG: APP: %s\n"    $APP
printf "DBG: REMOTE: %s\n" $REMOTE
printf "DBG: restArgs: %s\n" (string join -- " " $restArgs)
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

set commit (git rev-parse --short master)
set version-number (grep --max-count=1 --only-matching '\([0-9]\+\.\)\+[0-9]\+' project.clj)

# `heroku logs --tail --app $APP` blocks the execution
heroku addons:open papertrail --app $APP; and \
heroku ps:scale web=0 --app $APP; and \
git push $restArgs $REMOTE master; and \
heroku config:set BOT_VER=$botVerSHA --app $APP; and \
heroku ps:scale web=1 --app $APP

# publish source code only when deploying to production
if test $envName = corona-cases
    # seems like `git push --tags` pushes only tags w/o the code
    set gitTag $botVerNr"-"$botVerSHA
    git tag --annotate --message "" $gitTag
    set pushFlags --follow-tags --verbose

    set remotes origin gitlab
    for remote in $remotes
        set cmd git push $pushFlags $remote
        echo $cmd
        eval $cmd
        if test $status != 0
            break
        end
        # TODO this should not be needed
        # set cmd git push --tags $pushFlags $remote
        # echo $cmd
        # eval $cmd
        # if test $status != 0
        #     break
        # end
    end
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
