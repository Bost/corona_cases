# Bash initialization for interactive non-login shells and
# for remote shells (info "(bash) Bash Startup Files").

# Export 'SHELL' to child processes.  Programs such as 'screen'
# honor it and otherwise use /bin/sh.
export SHELL

if [[ $- != *i* ]]; then
    # We are being invoked from a non-interactive shell. If this is an SSH
    # session (as in "ssh host command"), source /etc/profile so we get PATH and
    # other essential variables ...
    [[ -n "$SSH_CLIENT" ]] && source /etc/profile
    return # ... and don't do anything else.
fi

# /run is not automatically created by guix
mkdir /run

# Quick access to $GUIX_ENVIRONMENT, for usage on config files
# (currently only /etc/nginx/nginx.conf)
ln -s $GUIX_ENVIRONMENT /env

# # Link every file in /usr/etc on /etc
# ls -1d /usr/etc/* | while read filepath; do
#     ln -s $filepath /etc/
# done

prjdir=~/dec/corona_cases
pgdata=./var/pg/data

start_db () {
    pg_ctl --pgdata=$pgdata --log=./var/log/postgres.log start
}

test_db () {
    psql --dbname=postgres << EOF
select count(*) as "count-of-thresholds (should be 4):" from thresholds;
EOF
}

# TODO start_mockup_server doesn't work in the guix shell
# bash: ./heroku.clj: /usr/bin/env: bad interpreter: No such file or directory
# clojure an clj are not installed
start_mockup_server () {
    ./heroku.clj getMockData && clj -X:mockup-server
}

start_repl () {
    clojure \
        -Sdeps \
        '{:deps {nrepl/nrepl {:mvn/version "0.9.0"} refactor-nrepl/refactor-nrepl {:mvn/version "3.5.2"} cider/cider-nrepl {:mvn/version "0.28.3"}}}' \
        -m nrepl.cmdline \
        --middleware '["refactor-nrepl.middleware/wrap-refactor", "cider.nrepl/cider-middleware"]'
}

guix_prompt () {
    cat << EOF
    ░░░                                     ░░░
    ░░▒▒░░░░░░░░░               ░░░░░░░░░▒▒░░
     ░░▒▒▒▒▒░░░░░░░           ░░░░░░░▒▒▒▒▒░
         ░▒▒▒░░▒▒▒▒▒         ░░░░░░░▒▒░
               ░▒▒▒▒░       ░░░░░░
                ▒▒▒▒▒      ░░░░░░
                 ▒▒▒▒▒     ░░░░░
                 ░▒▒▒▒▒   ░░░░░
                  ▒▒▒▒▒   ░░░░░
                   ▒▒▒▒▒ ░░░░░
                   ░▒▒▒▒▒░░░░░
                    ▒▒▒▒▒▒░░░
                     ▒▒▒▒▒▒░
     _____ _   _ _    _    _____       _
    / ____| \ | | |  | |  / ____|     (_)
   | |  __|  \| | |  | | | |  __ _   _ ___  __
   | | |_ | . ' | |  | | | | |_ | | | | \ \/ /
   | |__| | |\  | |__| | | |__| | |_| | |>  <
    \_____|_| \_|\____/   \_____|\__,_|_/_/\_\

Available commands:
  start_repl start_db test_db start_mockup_server
EOF
    }

## Initialize database on the first run
## '--directory' - do not list implied . and ..
if [ -z "$(ls --almost-all $pgdata)" ]; then
    set -x  # Print commands and their arguments as they are executed.
    initdb --pgdata=$pgdata # dropdb postgres && rm -rf ./var/pg
    start_db
    psql --dbname=postgres --quiet --file=dbase/my.sql
    test_db
else
    set -x  # Print commands and their arguments as they are executed.
    start_db
fi
# start_mockup_server
{ retval="$?"; set +x; } 2>/dev/null

guix_prompt
# start_repl

# Adjust the prompt depending on whether we're in 'guix environment'.
if [ -n "$GUIX_ENVIRONMENT" ]; then
    PS1='\u@\h \w [env]\$ '
else
    PS1='\u@\h \w\$ '
fi
alias ls='ls -p --color=auto'
alias ll='ls -l'
alias grep='grep --color=auto'
