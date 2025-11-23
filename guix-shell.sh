#!/usr/bin/env bash

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
        { retval="$?"; set +x; } 2>/dev/null
    fi
done

isGuix=$(command -v guix > /dev/null 2>&1 && echo "t" || echo "f")
if [ "${isGuix}" == "t" ]; then
    # Install Heroku on Guix
    # https://www.reddit.com/r/GUIX/comments/uom3vs/heroku_cli/
    # https://gitlab.com/nonguix/nonguix/-/issues/180

    # --preserve=REGEX
    #   preserve environment variables matching REGEX
    #
    # The $DISPLAY is needed by clojure.inspector, however the
    #   --preserve='^DISPLAY$'
    # leads to an error in the REPL:
    #   Authorization required, but no authorization protocol specified
    # and:
    #   error in process filter: cljr--maybe-nses-in-bad-state: \
        #   Some namespaces are in a bad state: ...
    # TODO: Develop corona_cases project in the guix shell container
    # try adding:
    #  --preserve='^XAUTHORITY$' --share=$XAUTHORITY
    #  --preserve='^.*$' --share=$XAUTHORITY
    #  export DISPLAY=:0.0
    # ~/.config/guix/shell-authorized-directories
    # See https://guix.gnu.org/manual/devel/en/html_node/Invoking-guix-shell.html

    # No shell is started when the '--search-paths' parameter is used. Only the
    # variables making up the environment are displayed.
    #   guix shell --search-paths

    # --preserve=^TERM$ \
        #   Avoid following warning:
    # $ clj --version
    # rlwrap: warning: environment variable TERM not set, assuming vt100

    set -x
    guix shell \
         --manifest=manifest.scm \
         --container --network \
         --preserve=^CORONA \
         --preserve=^DISPLAY \
         --preserve='^XAUTHORITY$' --share=$XAUTHORITY \
         --preserve=^TELEGRAM \
         --preserve=^TERM$ \
         --share=/usr/bin \
         --share=$HOME/.bash_history=$HOME/.bash_history \
         --share=$HOME/.config/fish=$HOME/.config/fish \
         --share=$HOME/.gitconfig=$HOME/.gitconfig \
         --share=$HOME/.m2=$HOME/.m2 \
         --share=$HOME/bin=$HOME/bin \
         --share=$HOME/dec=$HOME/dec \
         --share=$HOME/der=$HOME/der \
         --share=$HOME/dev/dotfiles=$HOME/dev/dotfiles \
         --share=$HOME/dev=$HOME/dev \
         --share=$wd/.bash_profile=$HOME/.bash_profile \
         --share=$wd/.bashrc=$HOME/.bashrc \
         --share=$wd/var/log=/var/log \
         --share=$wd/var/pg=/var/pg \
         --share=$wd/var/run/postgresql=/var/run/postgresql \
         --share=$wd \
         -- bash
else
    source .bashrc
fi
