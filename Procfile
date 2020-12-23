# R10 Boot Timeout https://tools.heroku.support/limits/boot_timeout
# default boot timeout 60 seconds
# TEST: boot timeout increased to 120 seconds
# PROD: boot timeout increased to 120 seconds
web: java $JVM_OPTS -cp target/corona_cases-standalone.jar clojure.main -m corona.web.core
