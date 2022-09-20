#!/bin/sh
#
# Reproducible Development Environment
#
# TODO Add nginx php-fpm as in the guix-lemp-container
# https://notabug.org/hackware/guix-lemp-container.git
#
# Wishlist: Include XDebug

wd=$(pwd) # WD=$(dirname "$0") # i.e. path to this file

prj_dirs=(
    $wd/var/log
    $wd/var/pg
    $wd/var/run/postgresql
)

# `git clean --force -dx` destroys the prj_dirs. Recreate it:
for prjd in ${prj_dirs[@]}; do
    if [ ! -d $prjd ]; then
        set -x  # Print commands and their arguments as they are executed.
        mkdir --parent $prjd
        { retval="$?";
          set +x; } 2>/dev/null
    fi
done

cliTools=""

# TODO replace busybox with env
cliTools="$cliTools busybox"
cliTools="$cliTools ncurses"
# See README.md for PostgreSQL 13.3 vs. 13.6 configuration
# Heroku currently offers Postgres version 13 as the default.
# https://devcenter.heroku.com/articles/heroku-postgresql#version-support
cliTools="$cliTools postgresql@13.6"
cliTools="$cliTools rsync openssh bash fish ripgrep less"
cliTools="$cliTools grep git coreutils sed which guile"
cliTools="$cliTools openjdk@18:jdk"
cliTools="$cliTools clojure-tools" # clojure-tools not clojure must be installed

# TODO --preserve=
#   preserve environment variables matching REGEX
set -x
guix shell \
     --container --network \
     nss-certs curl $cliTools \
     --preserve=^CORONA \
     --share=$wd/.bash_profile=$HOME/.bash_profile \
     --share=$wd/.bashrc=$HOME/.bashrc \
     --share=$HOME/.m2=$HOME/.m2 \
     --share=$HOME/.gitconfig=$HOME/.gitconfig \
     --share=$HOME/.config/fish=$HOME/.config/fish \
     --share=$HOME/dev=$HOME/dev \
     --share=$HOME/dec=$HOME/dec \
     --share=$HOME/der=$HOME/der \
     --share=$HOME/bin=$HOME/bin \
     --share=$HOME/local-stuff.fish=$HOME/local-stuff.fish \
     --share=$HOME/dev/dotfiles=$HOME/dev/dotfiles \
     --share=$wd/var/log=/var/log \
     --share=$wd/var/pg=/var/pg \
     --share=$wd/var/run/postgresql=/var/run/postgresql \
     --share=$wd \
     -- bash
