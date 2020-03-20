#!/usr/bin/env bash

# A list of web services providing JSON data
# see also https://github.com/soroushchehresa/awesome-coronavirus
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

    # https://www.micro-work.net/covid/ - as of now - work in progress
    www.micro-work.net

    # https://github.com/Omaroid/Covid-19-API
    covid19api.herokuapp.com

    # https://github.com/mathdroid/covid-19-api
    covid19.mathdro.id

    # https://github.com/NovelCOVID/API
    corona.lmao.ninja

    # https://github.com/amodm/api-covid19-in
    api.rootnet.in

    # https://github.com/rlindskog/covid19-graphql
    # GraphQL
    covid19-graphql.now.sh

    # https://github.com/isjeffcom/coronvirusFigureUK
    coronauk.isjeff.com

    # https://github.com/COVID19Tracking/website
    covidtracking.com

    # https://github.com/andreagrandi/covid-api
    # localhosting

    # https://micro-work.net/covid/jhutsconfirmedjson4.php/Swi/d:15
    # https://micro-work.net/covid/jhutsconfirmedjson4.php/US/d:15
    # TODO repo not found; see
    # https://github.com/CSSEGISandData/COVID-19/issues/492#issuecomment-601827214
    # wget --no-verbose https://github.com/matjung/covid
    # https://github.com/matjung/covid:
    # 2020-03-20 18:36:59 ERROR 404: Not Found.
    micro-work.net
)

for srvc in "${webServices[@]}"; do
    # mtr - a network diagnostic tool; speed tests and such
    mtr --report $srvc
done
