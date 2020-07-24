# R10 Boot Timeout https://tools.heroku.support/limits/boot_timeout
# TEST: boot timeout 60 seconds (default value)
# PROD: boot timeout 90 seconds (increased)
web: java $JVM_OPTS -cp target/corona_cases-standalone.jar clojure.main -m corona.web
