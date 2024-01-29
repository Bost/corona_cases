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
[ ! -d /run ] && mkdir /run

# Quick access to $GUIX_ENVIRONMENT, for usage on config files
# (currently only /etc/nginx/nginx.conf)
[ ! -L /env ] && ln -s $GUIX_ENVIRONMENT /env

# # Link every file in /usr/etc on /etc
# ls -1d /usr/etc/* | while read filepath; do
#     bname=/etc/$(basename $filepath)
#     [ ! -L $bname ] && ln -s $filepath $bname
# done

expedir_basename="corona_cases"
prjdir=$(pwd)
currdir_basename=$(basename $prjdir)
if [ "$currdir_basename" != "$expedir_basename" ]; then
    printf "ERR: Basename of current directory is '%s', expecting '%s'\n" \
           $currdir_basename $expedir_basename
    exit 1
fi
pgdata=./var/pg/data

test_db () {
    # for long names of keywords, use:
    #   select * from thresholds where kw in ('rec', 'dea', 'act', 'new') order by kw;
    set -x  # Print commands and their arguments as they are executed.
    psql --dbname=postgres << EOF
select count(*) as "count-of-thresholds (should be 4):" from thresholds;
EOF
    { retval="$?"; set +x; } 2>/dev/null
}

start_db () {
    port=5432

    set -x  # Print commands and their arguments as they are executed.
    # 1. alternative
    pgrep --full --list-full bin/postgres

    # 2. alternative
    # ss -tulpn | grep $port

    # 3. alternative
    # pg_ctl --silent --pgdata=$pgdata status # --silent : prints only errors
    # However, pg_ctl reports 'pg_ctl: no server running' even when a server is
    # running

    { retval="$?"; set +x; } 2>/dev/null

    if [ $retval -eq 0 ] && [ -e $pgdata/postmaster.pid ]; then
        echo "INF: Could not bind to port $port - already in use"
    else
        set -x  # Print commands and their arguments as they are executed.
        pg_ctl --pgdata=$pgdata --log=./var/log/postgres.log start
        { retval="$?"; set +x; } 2>/dev/null
        test_db
    fi
}

start_mockup_server () {
    set -x  # Print commands and their arguments as they are executed.
    # ./heroku.clj getMockData && clj -X:mockup-server
    clj -X:mockup-server
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
        '{:deps {nrepl/nrepl {:mvn/version "1.1.0"} refactor-nrepl/refactor-nrepl {:mvn/version "3.9.1"} cider/cider-nrepl {:mvn/version "0.45.0"}}}}}' \
        -M -m nrepl.cmdline \
        --middleware \
        '["refactor-nrepl.middleware/wrap-refactor", "cider.nrepl/cider-middleware"]'

    { retval="$?"; set +x; } 2>/dev/null
}

guix_prompt () {
    cat << "EOF"
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
