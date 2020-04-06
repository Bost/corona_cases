#!/usr/bin/env fish

# set up environment
set botEnvs $botEnvs --test
set botEnvs $botEnvs --prod

set prmEnvName $argv[1]

if test $prmEnvName = $botEnvs[1]
    set envName hokuspokus
else if test $prmEnvName = $botEnvs[2]
    set envName corona-cases
else
    echo "ERROR: Unknown parameter:" $prmEnvName
    echo "Possible values:" $botEnvs
    echo ""
    echo "Examples:"
    echo (status --current-filename) "--prod"
    echo (status --current-filename) "--test"
    exit 1
end

set APP $envName"-bot"
set REMOTE "heroku-"$APP

set cmd heroku ps:scale web=0 --app $APP
echo $cmd
eval $cmd

set cmd heroku ps:scale web=1 --app $APP
echo $cmd
eval $cmd

