# R10 Boot Timeout https://tools.heroku.support/limits/boot_timeout
# default boot timeout 60 seconds
# Heroku App "hokuspokus-bot":   boot timeout increased to 180 seconds
# Heroku App "corona-cases-bot": boot timeout increased to 180 seconds

# Line continuation '\' doesn't work

# -Xmx<size> : increase max heap size from default 300MB
# Keep in sync with .dir-locals.el and .bashrc
web: java -Xmx1024m -XX:+HeapDumpOnOutOfMemoryError -Djdk.attach.allowAttachSelf $JVM_OPTS -cp target/corona_cases-standalone.jar clojure.main -m corona.web.core
