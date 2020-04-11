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

set cmd heroku ps:scale web=0 --app $APP
echo $cmd
eval $cmd

set cmd heroku ps:scale web=1 --app $APP
echo $cmd
eval $cmd

