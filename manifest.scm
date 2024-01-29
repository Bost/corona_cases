;; What follows is a "manifest" equivalent to the command line you gave.
;; You can store it in a file that you may then pass to any 'guix' command
;; that accepts a '--manifest' (or '-m') option.

;; This file was initially created by
;;     guix shell PACKAGES --export-manifest

(use-modules
 (guix profiles)
 ((bost gnu packages babashka) #:prefix bst:)
 ((bost gnu packages clojure) #:prefix bst:)
 )

(use-package-modules
 bash
 certs
 curl
 databases
 guile
 java
 less
 linux
 ncurses
 rsync
 rust-apps
 shells
 shellutils
 ssh
 version-control)

(define (partial fun . args)
  (lambda x (apply fun (append args x))))

(define project-manifest
  (specifications->manifest
   (list
    ;; `guix shell openjdk@<version>:jdk PACKAGES --export-manifest' ignores the
    ;; '@<version>' if it matches the installed version. (The '@18' was added
    ;; manually.)
    ;; openjdk package removes javadoc libs, one has to use ONLY openjdk:jdk
    ;; https://github.com/clojure-emacs/orchard/issues/117#issuecomment-859987280
    ;; "openjdk@18:jdk"
    )))

((compose
  concatenate-manifests
  (partial list project-manifest)
  manifest
  ;; openjdk package removes javadoc libs, one has to use ONLY openjdk:jdk
  ;; https://github.com/clojure-emacs/orchard/issues/117#issuecomment-859987280
  (partial append (list (package->manifest-entry openjdk18 "jdk")))
  (partial map package->manifest-entry))
 (list
  ;; ./heroku.clj needs babashka. Also `guix shell ...` contain
  ;; '--share=/usr/bin' so that shebang (aka hashbang) #!/bin/env/bb works
  bst:babashka
  bash

  ;; 1. The `ls' from busybox is causing problems. However it is overshadowed
  ;; when this list is reversed. (Using Guile or even on the command line.)
  ;;
  ;; 2. It seems like busybox is not needed if invoked with:
  ;;     guix shell ... --share=/usr/bin
  #;busybox

  ;; CLI tools to start a Clojure repl, use Clojure and Java libraries, and
  ;; start Clojure programs. See https://clojure.org/releases/tools
  ;; clojure-tools not clojure must be installed so that clojure binary
  ;; available on the CLI
  bst:clojure-tools

  coreutils
  curl
  direnv
  fish
  git
  grep

  ;; specifying only 'guile' leads to "error: guile: unbound variable"
  guile-3.0
 
  iproute ; provides ss - socket statistics
  less
  ncurses
  nss-certs
  openssh

  ;; Heroku currently offers Postgres version 14 as the default.
  ;; https://devcenter.heroku.com/articles/heroku-postgresql#version-support
  ;; The ./var/log/postgres.log may contain:
  ;; FATAL:  database files are incompatible with server
  ;; DETAIL:  The data directory was initialized by PostgreSQL version 13, which is not compatible with this version 14.6.
  postgresql-13

  ;; provides: free pgrep pidof pkill pmap ps pwdx slabtoptload top vmstat w
  ;; watch sysctl
  procps

  ripgrep
  rsync
  sed
  which
  ))
