# Bash initialization for interactive non-login shells and
# for remote shells (info "(bash) Bash Startup Files").

# Export 'SHELL' to child processes.  Programs such as 'screen'
# honor it and otherwise use /bin/sh.
export SHELL

# if [[ $- != *i* ]]; then
#     echo "We are being invoked from a non-interactive shell."
#     # We are being invoked from a non-interactive shell. If this is an SSH
#     # session (as in "ssh host command"), source /etc/profile so we get PATH and
#     # other essential variables ...
#     [[ -n "$SSH_CLIENT" ]] && source /etc/profile
#     return # ... and don't do anything else.
# fi

if [ -n "$GUIX_ENVIRONMENT" ]; then
    # /run is not automatically created by guix
    [ ! -d /run ] && mkdir /run

    # Quick access to $GUIX_ENVIRONMENT, for usage on config files
    # (currently only /etc/nginx/nginx.conf)
    [ ! -L /env ] && ln -s $GUIX_ENVIRONMENT /env
fi

# # Link every file in /usr/etc on /etc
# ls -1d /usr/etc/* | while read filepath; do
#     bname=/etc/$(basename $filepath)
#     [ ! -L $bname ] && ln -s $filepath $bname
# done

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
    local pgdata=./var/pg/data

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
        if [ -n "$GUIX_ENVIRONMENT" ]; then
            pg_ctl --pgdata=$pgdata --log=./var/log/postgres.log start
        fi
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

guix_logo () {
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

EOF
    }

heroku_clj () {
    [ ! -d ./node_modules ] && npm install heroku
    local hbd=./node_modules/heroku/bin # heroku_bin_dir
    [ ! -L $hbd/heroku ] && ln -s $hbd/run $hbd/heroku

    [ ! $(command -v heroku) ] && export PATH=$hbd:$PATH

    set -x  # Print commands and their arguments as they are executed.
    ./heroku.clj "$@"
    { retval="$?"; set +x; } 2>/dev/null
}

do_run () {
    # TODO make a logging monad
    if [ ! -d $pgdata ]; then # Initialize database on the first run
        set -x  # Print commands and their arguments as they are executed.
        initdb --pgdata=$pgdata # dropdb postgres && rm -rf ./var/pg
        { retval="$?"; set +x; } 2>/dev/null

        start_db

        set -x  # Print commands and their arguments as they are executed.
        psql --dbname=postgres --quiet --file=dbase/my.sql
        { retval="$?"; set +x; } 2>/dev/null
    else
        start_db
    fi
    # start_mockup_server

    if [ -n "$GUIX_ENVIRONMENT" ]; then
        guix_logo
    elif [ $(command -v neofetch) ]; then
        neofetch
    fi

    cat << "EOF"
Available commands:
  start_repl start_db test_db start_mockup_server heroku_clj
EOF

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
}

do_run
