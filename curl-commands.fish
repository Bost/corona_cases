#!/usr/bin/env fish

# certificate is optional
# echo curl --form "url=https://shielded-inlet-72499.herokuapp.com/webhook" --form "certificate=/var/ssl/telebot.pem" https://api.telegram.org/bot$TELEGRAM_TOKEN/setWebhook

# echo curl --form '"url=https://shielded-inlet-72499.herokuapp.com/$TELEGRAM_TOKEN"' '"https://api.telegram.org/bot$TELEGRAM_TOKEN/setWebhook"'
# echo curl --request POST '"https://api.telegram.org/bot$TELEGRAM_TOKEN/deleteWebhook"'

# echo curl --request POST '"http://localhost:5000/$TELEGRAM_TOKEN"'

# echo curl '"https://api.telegram.org/bot$TELEGRAM_TOKEN/getMe"'

# Fetch bot updates and look for the chat id:
# echo curl '"https://api.telegram.org/bot$TELEGRAM_TOKEN/getUpdates"' "|" jq .message.chat.id

# Send a message via their HTTP API: https://core.telegram.org/bots/api#sendmessage
# echo curl --request POST -H 'Content-Type: application/json' -d '{"chat_id":112885364,"text":"curl test msg","disable_notification": true}' '"https://api.telegram.org/bot$TELEGRAM_TOKEN/sendMessage"'

# echo curl --request POST --form chat_id=112885364 --form photo=@/tmp/pic.png '"https://api.telegram.org/bot$TELEGRAM_TOKEN/sendPhoto"'

# curl --request POST -H 'Content-Type: application/json' -d '{"chat_id":112885364,"parse_mode":"Markdown","text":"_curl test msg_","disable_notification": true}' "https://api.telegram.org/bot$TELEGRAM_TOKEN/sendMessage"

# curl --request POST -H 'Content-Type: application/json' -d '{"chat_id":112885364,"parse_mode":"Markdown","text":"| Tables   |      Are      |  Cool |\n|----------|:-------------:|------:|\n| col 1 is |  left-aligned | $1600 |\n| col 2 is |    centered   |   $12 |\n| col 3 is | right-aligned |    $1 |\n","disable_notification": true}' "https://api.telegram.org/bot$TELEGRAM_TOKEN/sendMessage"

# curl --request POST -H 'Content-Type: application/json' -d '{"chat_id":112885364,"parse_mode":"Markdown","text":"```\n| Tables   |      Are      |  Cool |\n|----------|---------------|-------|\n| col 1 is |  left-aligned | $1600 |\n| col 2 is |    centered   |   $12 |\n| col 3 is | right-aligned |    $1 |\n```","disable_notification": true}' "https://api.telegram.org/bot$TELEGRAM_TOKEN/sendMessage"

# curl --request POST -H 'Content-Type: application/json' -d '{"chat_id":112885364, "parse_mode" : "Markdown" , "text" : "
# | Tables   |      Are      |  Cool |
# |----------|---------------|-------|
# | col 1 is |  left-aligned | $1600 |
# | col 2 is |    centered   |   $12 |
# | col 3 is | right-aligned |    $1 |
# " , "disable_notification": false}' "https://api.telegram.org/bot$TELEGRAM_TOKEN/sendMessage"

# curl --request POST -H 'Content-Type: application/json' -d '{"chat_id":112885364, "parse_mode" : "Markdown" , "text" : "
# This is a huge header
# ==================

# this is a smaller header
# ------------------
# " , "disable_notification": false}' "https://api.telegram.org/bot$TELEGRAM_TOKEN/sendMessage"

curl --request POST -H 'Content-Type: application/json' -d '{"chat_id":112885364, "parse_mode" : "MarkdownV2" , "text" : "
| Tables   |      Are      |  Cool |
|----------|---------------|-------|
| col 1 is |  left-aligned | $1600 |
| col 2 is |    centered   |   $12 |
| col 3 is | right-aligned |    $1 |
" , "disable_notification": false}' "https://api.telegram.org/bot$TELEGRAM_TOKEN/sendMessage"

