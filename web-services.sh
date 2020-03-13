#!/usr/bin/env bash

# A list of web services providing JSON data
webServices = (
    # https://github.com/ExpDev07/coronavirus-tracker-api
    coronavirus-tracker-api.herokuapp.com

    # https://github.com/nat236919/Covid2019API
    covid2019-api.herokuapp.com

    # https://github.com/open-covid19/covid19-api
    api.opencovid19.com

    # https://bachbauer.eu/projects/corona/create_csv_de.php
    # provides downloadable csv files; not a proper web service
    bachbauer.eu

    # https://github.com/axisbits/covid-api
    covid-api.com

    # https://github.com/Laeyoung/COVID-19-API
    wuhan-coronavirus-api.laeyoung.endpoint.ainize.ai
)

for srvc in "${webServices[@]}"; do
    # mtr - a network diagnostic tool; speed tests and such
    mtr --report $srvc
done
