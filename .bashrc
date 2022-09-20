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

# start_db () {
#     port=5432
#     # pgrep -fa -- -D
#     # pg_ctl --pgdata=./var/pg/data status
#     # set -x  # Print commands and their arguments as they are executed.
#     ss -tulpn | grep "127\.0\.0\.1:$port"
#     { retval="$?"; set +x; } 2>/dev/null
#     if [ $retval -eq 0 ] || [ -e $pgdata/postmasted.pid ]; then
#         echo "Could not bind 127.0.0.1:$port - address already in use"
#     else
#         echo "Starting postgres server..."
#         pg_ctl --pgdata=$pgdata --log=./var/log/postgres.log start
#     fi
# }

start_db () {
    set -x  # Print commands and their arguments as they are executed.
    pg_ctl --pgdata=$pgdata --log=./var/log/postgres.log start
    { retval="$?"; set +x; } 2>/dev/null
}

test_db () {
    set -x  # Print commands and their arguments as they are executed.
    psql --dbname=postgres << EOF
select count(*) as "count-of-thresholds (should be 4):" from thresholds;
EOF
    { retval="$?"; set +x; } 2>/dev/null
}

start_mockup_server () {
    set -x  # Print commands and their arguments as they are executed.
    ./heroku.clj getMockData && clj -X:mockup-server
    { retval="$?"; set +x; } 2>/dev/null
}

start_repl () {
    # keep the -Xmx value in sync with ./Procfile and .dir-locals.el
    heapSize=8192m # (* 8 1024)
    set -x  # Print commands and their arguments as they are executed.
    clojure \
        -J-Xmx$heapSize \
        -J-XX:+HeapDumpOnOutOfMemoryError \
        -J-Djdk.attach.allowAttachSelf \
        -Sdeps \
        '{:deps {nrepl/nrepl {:mvn/version "0.9.0"} refactor-nrepl/refactor-nrepl {:mvn/version "3.5.5"} cider/cider-nrepl {:mvn/version "0.28.3"}}}' \
        -m nrepl.cmdline \
        --middleware '["refactor-nrepl.middleware/wrap-refactor", "cider.nrepl/cider-middleware"]'
    { retval="$?"; set +x; } 2>/dev/null
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
    # TODO make a logging monad
    set -x  # Print commands and their arguments as they are executed.
    initdb --pgdata=$pgdata # dropdb postgres && rm -rf ./var/pg
    { retval="$?"; set +x; } 2>/dev/null

    start_db

    set -x  # Print commands and their arguments as they are executed.
    psql --dbname=postgres --quiet --file=dbase/my.sql
    { retval="$?"; set +x; } 2>/dev/null
    test_db
else
    start_db
fi
# start_mockup_server

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
