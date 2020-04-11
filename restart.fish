#!/usr/bin/env fish

# set up the environment
set hostEnvs --test --prod

set prmEnvName $argv[1]
set restArgs   $argv[2..-1]

# TODO see https://github.com/jorgebucaran/fish-getopts
switch $prmEnvName
    case $hostEnvs[1]
        set envName hokuspokus
    case $hostEnvs[2]
        set envName corona-cases
    case \*
        printf "ERR: Unknown parameter: %s\n" $prmEnvName
        # w/o the '--' every list element gets printed on a separate line
        # as if invoked in a for-loop. WTF?
        printf "Possible values: %s\n" (string join -- ", " $hostEnvs)
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

set cmd heroku ps:scale web=0 --app $APP
echo $cmd
eval $cmd

set cmd heroku ps:scale web=1 --app $APP
echo $cmd
eval $cmd

