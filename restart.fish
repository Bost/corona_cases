#!/usr/bin/env fish

# examples:
# ./restart --prod
# ./restart --test

# set up environment
set botEnvs $botEnvs --test
set botEnvs $botEnvs --prod

set prmEnvName $argv[1]

if test $prmEnvName = $botEnvs[1]
    set --local envName hokuspokus
else if test $prmEnvName = $botEnvs[2]
    set --local envName corona-cases
else
    echo "ERROR: Unknown parameter:" $prmEnvName
    echo "Possible values:" $botEnvs
    exit 1
end

set --local APP $envName"-bot"
set --local REMOTE "heroku-"$APP

set cmd heroku ps:scale web=0 --app $APP; and heroku ps:scale web=1 --app $APP

echo $cmd
eval $cmd

