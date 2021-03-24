Coronavirus disease 2019 (COVID-19) information on Telegram Messenger
[@corona_cases_bot](https://t.me/corona_cases_bot)

## Raison d´être
> The Internet interprets censorship as damage and routes around it.
> - John Gilmore

![Screenshot](/resources/pics/screenshot_1-50-percents.jpg)
![Screenshot](/resources/pics/screenshot_2-50-percents.jpg)

## Setup environment

### Install
<!-- ```bash -->
<!-- nix-env -iA nixpkgs.clojure -->
<!-- nix-env -iA nixpkgs.babashka -->
<!-- nix-env -iA nixpkgs.python3 -->
<!-- nix-env -iA nixpkgs.pipenv -->
<!-- nix-env -iA nixpkgs.jdk -->
<!-- # nixpkgs.postgresql is version 11 -->
<!-- # nix-env -iA nixpkgs.postgresql_12 -->
<!-- ``` -->

* [Clojure](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools)
* [Babashka](https://github.com/babashka/babashka#installer-script)
* postgresql:
```bash
sudo apt install postgresql postgresql-contrib
sudo systemctl status postgresql.service
sudo systemctl stop postgresql.service
# set --export PATH /usr/lib/postgresql/*/bin $PATH
initdb pg
sudo chown --recursive postgres:postgres pg/
sudo chmod --recursive u=rwx,g=---,o=--- pg/
sudo -su postgres
fish # start he fish-shell for the postgres user
set --export PATH /usr/lib/postgresql/*/bin $PATH
postgres -D pg &   # this doesn't work: pg_ctl -D pg -l logfile start
```
Open new console and log in
```bash
psql --dbname=postgres
```
```postgres
\conninfo
-- list databases:
\l
\l+
SELECT datname FROM pg_database;
```

### Create
* [Telegram Chatbot](https://core.telegram.org/bots#3-how-do-i-create-a-bot)
* [Heroku App](https://www.heroku.com/), optionally add the Papertrail add-on

### Configure
* Local environment variables. Create `.env` file in your project root directory:
```bash
$ touch .env
```
containing:
```bash
# Value must be lower-cased, without the "" chars
CORONA_ENV_TYPE=devel
#
# https://clojure.org/guides/getting_started#_installation_on_linux
# See also `.heroku-local.env` and the output of `clojure --help | grep Version`
CLOJURE_CLI_VERSION=1.10.1.763
#
# HEROKU tokens for:
PAPERTRAIL_API_TOKEN=<...>
TELEGRAM_TOKEN="<...>"
```
* Heroku Config Vars. See [https://dashboard.heroku.com/apps/\<YOUR-HEROKU-APP-NAME\>/settings](). See also:
```bash
$ heroku config --app <YOUR-HEROKU-APP-NAME>
CLOJURE_CLI_VERSION:  1.10.1.763
COMMIT:               ...
CORONA_ENV_TYPE:      HOKUSPOKUS
PAPERTRAIL_API_TOKEN: ...
REPL_PASSWORD:        ...
REPL_USER:            ...
TELEGRAM_TOKEN:       ...
```

## Develop

1. Start the coronavirus-tracker-api:
```bash
$ pipenv run start
```

2. In Emacs Cider `M-x cider-jack-in-clj`, or start the nREPL from the command line:
<!-- No line continuations '\' accepted -->
```bash
$ clojure -Sdeps '{:deps {nrepl/nrepl {:mvn/version "0.8.3"} refactor-nrepl/refactor-nrepl {:mvn/version "2.5.0"} cider/cider-nrepl {:mvn/version "0.25.5"}}}' -m nrepl.cmdline --middleware '["refactor-nrepl.middleware/wrap-refactor", "cider.nrepl/cider-middleware"]'
```
and connect to it from the editor of your choice.

3. Start the telegram chatbot long-polling:
```clojure
user> (require '[corona.telegram])
user> (corona.telegram/start)
```

4. Start the web server:
```bash
rlwrap /usr/local/bin/clojure -Sdeps '{:deps {nrepl/nrepl {:mvn/version "0.8.3"} com.billpiel/sayid {:mvn/version "0.1.0"} refactor-nrepl/refactor-nrepl {:mvn/version "2.5.1"} cider/cider-nrepl {:mvn/version "0.25.9"}} :aliases {:cider/nrepl {:main-opts ["-m" "nrepl.cmdline" "--middleware" "[com.billpiel.sayid.nrepl-middleware/wrap-sayid,refactor-nrepl.middleware/wrap-refactor,cider.nrepl/cider-middleware]"]}}}'
```
```clojure
user=> (load "corona/api/mockup")
user=> (corona.api.mockup/run-server)
```
and check the [http://localhost:5050/](http://localhost:5050/) if it's running.

## Run locally

```bash
$ bin/build; and heroku local --env=.heroku-local.env
# or:
# bin/build; and heroku local --env=.heroku-local.env --set COMMIT=...
```

## Deploy to Heroku
```bash
$ bb heroku.clj deploy --heroku-env <YOUR-HEROKU-APP-NAME>
```

## MySQL -> PostgreSQL script conversion

```bash
sudo apt install postgresql postgresql-contrib
# Switch over to the postgres account:
sudo su postgres
createdb postgres # or: dropdb postgres

# psql --dbname=postgres         --echo-all --file=dbase/my.sql | rg "ERROR\|NOTICE|WARN"
# psql --dbname=postgres --quiet            --file=dbase/drop-everything.sql
psql --dbname=postgres   --quiet            --file=dbase/my.sql

# get the psql prompt:
  psql --dbname=postgres
```

```bash
```
then
```postgres
-- help
\?
-- list roles / show users
\du
-- list tables
\dt
-- list sequences
\ds
-- list indices
\di
```
## Others

```bash
heroku pt ":type -'ssl-client-cert' -'$MY_TELEGRAM_ID'" --app <YOUR-HEROKU-APP-NAME> | grep -v -e '^[[:space:]]*$
```
