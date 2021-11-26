#!/bin/sh
#
# Reproducible Development Environment
#
# TODO Add nginx php-fpm as in the guix-lemp-container
# https://notabug.org/hackware/guix-lemp-container.git
#
# Wishlist: Include XDebug

if [ ! $# -eq 2 ]; then
   cat << EOF
usage: $0 <shared_path> <public_path>
       shared_path: exchange directory
       public_path: public facing location, must be within shared_path

   ex: $0 /home/user/dev public_html
EOF
   exit 1
fi

shared_path="$1"
public_path="$1/$2"
file_path=$(dirname "$0") # path to this file

# TODO replace busybox with env
cliTools="busybox rsync openssh bash ripgrep less postgres"
cliTools="$cliTools grep git coreutils sed which"
guix shell \
     --container --network --no-cwd --check \
     nss-certs curl $cliTools \
     --preserve=^fdk \
     --share=$file_path/.bash_profile=$HOME/.bash_profile \
     --share=$file_path/.bashrc=$HOME/.bashrc \
     --share=$HOME/.gitconfig=$HOME/.gitconfig \
     --share=$file_path/etc=/usr/etc \
     --share=$file_path/var/log=/var/log \
     --share=$shared_path \
     -- bash
