# Bash initialization for interactive non-login shells and
# for remote shells (info "(bash) Bash Startup Files").

# Export 'SHELL' to child processes.  Programs such as 'screen'
# honor it and otherwise use /bin/sh.
export SHELL

if [[ $- != *i* ]]
then
    # We are being invoked from a non-interactive shell.  If this
    # is an SSH session (as in "ssh host command"), source
    # /etc/profile so we get PATH and other essential variables.
    [[ -n "$SSH_CLIENT" ]] && source /etc/profile

    # Don't do anything else.
    return
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

# TODO start_mockup_server doesn't work in the guix shell
# bash: ./heroku.clj: /usr/bin/env: bad interpreter: No such file or directory
# clojure an clj are not installed
start_mockup_server () {
    ./heroku.clj getMockData && clj -X:mockup-server
}


## Initialize database on the first run
## '--directory' - do not list implied . and ..
if [ -z "$(ls --almost-all $pgdata)" ]; then
    set -x  # Print commands and their arguments as they are executed.
    initdb --pgdata=$pgdata # dropdb postgres && rm -rf ./var/pg
    start_db
    psql --dbname=postgres --quiet --file=dbase/my.sql
else
    set -x  # Print commands and their arguments as they are executed.
    start_db
fi
# start_mockup_server
{ retval="$?";
  set +x; } 2>/dev/null

guix_prompt () {
    cat << EOF
=========================================
  ____ _   _ _   _    ____       _
 / ___| \ | | | | |  / ___|_   _(_)_  __
| |  _|  \| | | | | | |  _| | | | \ \/ /
| |_| | |\  | |_| | | |_| | |_| | |>  <
 \____|_| \_|\___/   \____|\__,_|_/_/\_\\

=========================================
EOF
    }

if [[ $- != *i* ]]
then
    # We are being invoked from a non-interactive shell.  If this
    # is an SSH session (as in "ssh host command"), source
    # /etc/profile so we get PATH and other essential variables.
    [[ -n "$SSH_CLIENT" ]] && guix_prompt

    # Don't do anything else.
    return
fi

guix_prompt

# Adjust the prompt depending on whether we're in 'guix environment'.
if [ -n "$GUIX_ENVIRONMENT" ]
then
    PS1='\u@\h \w [env]\$ '
else
    PS1='\u@\h \w\$ '
fi
alias ls='ls -p --color=auto'
alias ll='ls -l'
alias grep='grep --color=auto'
