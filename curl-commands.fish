#!/usr/bin/env fish

# echo curl --request POST '"https://api.telegram.org/bot$TELEGRAM_TOKEN/deleteWebhook"'
echo curl --request GET '"https://api.telegram.org/bot$TELEGRAM_TOKEN/getWebhookInfo"'

# echo curl --request POST '"http://localhost:5000/$TELEGRAM_TOKEN"'

# echo curl '"https://api.telegram.org/bot$TELEGRAM_TOKEN/getMe"'

# Fetch bot updates and look for the chat_id:
# echo curl '"https://api.telegram.org/bot$TELEGRAM_TOKEN/getUpdates"' "|" jq .message.chat.id

# Send a message via their HTTP API: https://core.telegram.org/bots/api#sendmessage
# echo curl --request POST -H 'Content-Type: application/json' -d '{"chat_id":112885364,"text":"curl test msg","disable_notification": true}' '"https://api.telegram.org/bot$TELEGRAM_TOKEN/sendMessage"'

# curl --request POST --form chat_id=112885364 --form photo=@resources/pics/how_to_handwash_lge.gif "https://api.telegram.org/bot$TELEGRAM_TOKEN/sendPhoto"
# curl --request POST -H 'Content-Type: application/json' -d '{"chat_id":112885364,"message_id":"...", "media":{"type":"photo","media":"AgACAgIAAxkDAAJeFl_rmiJ_GCp2wdL0uLe6bOFzEbYfAAIxsjEbd5xhS03Kn3bxUoYGzjgomy4AAwEAAwIAA20AA361AAIeBA"}}' https://api.telegram.org/bot$TELEGRAM_TOKEN/editMessageMedia

# curl --request POST -H 'Content-Type: application/json' -d '{"chat_id":112885364,"parse_mode":"Markdown","text":"_curl test msg_","disable_notification": true}' "https://api.telegram.org/bot$TELEGRAM_TOKEN/sendMessage"

# curl --request POST -H 'Content-Type: application/json' -d '{"chat_id":112885364, "parse_mode" : "MarkdownV2" , "text" : "notification" , "disable_notification": false}' "https://api.telegram.org/bot$TELEGRAM_TOKEN/sendMessage"
# curl --request POST -H 'Content-Type: application/json' -d '{"chat_id":112885364,"text":"notification","disable_notification":false}' "https://api.telegram.org/bot$TELEGRAM_TOKEN/sendMessage"
# curl --request POST -H 'Content-Type: application/json' -d '{"chat_id":112885364,"text":"notification","disable_notification":true}' "https://api.telegram.org/bot$TELEGRAM_TOKEN/sendMessage"

# curl --request POST -H 'Content-Type: application/json' -d '{"chat_id":112885364,"text":"typing"}' "https://api.telegram.org/bot$TELEGRAM_TOKEN/getMe"

# curl --request POST -H 'Content-Type: application/json' -d '{"chat_id":112885364,"message_id":2043,"caption":""}' "https://api.telegram.org/bot$TELEGRAM_TOKEN/editMessageCaption"
# curl --request POST -H 'Content-Type: application/json' -d '{"chat_id":112885364,"text":"notification","caption":"this is caption","disable_notification":true}' "https://api.telegram.org/bot$TELEGRAM_TOKEN/sendMessage"


# curl --request POST \
#     -H 'Content-Type: application/json' \
#     -d '{"chat_id":112885364, "message_id" : "24470", "reply_markup" : {"inline_keyboard":[[{"text":"AcA","callback_data":"{:chat-id 112885364, :ccode \"ZZ\", :case-kw :a, :type :abs}"},{"text":"DeA","callback_data":"{:chat-id 112885364, :ccode \"ZZ\", :case-kw :d, :type :abs}"},{"text":"ReA","callback_data":"{:chat-id 112885364, :ccode \"ZZ\", :case-kw :r, :type :abs}"},{"text":"CoA","callback_data":"{:chat-id 112885364, :ccode \"ZZ\", :case-kw :c, :type :abs}"},{"text":"Co\u03a3","callback_data":"{:chat-id 112885364, :ccode \"ZZ\", :case-kw :c, :type :sum}"},{"text":"Re\u03a3","callback_data":"{:chat-id 112885364, :ccode \"ZZ\", :case-kw :r, :type :sum}"},{"text":"De\u03a3","callback_data":"{:chat-id 112885364, :ccode \"ZZ\", :case-kw :d, :type :sum}"},{"text":"Ac\u03a3","callback_data":"{:chat-id 112885364, :ccode \"ZZ\", :case-kw :a, :type :sum}"}]]}, "media":{"type":"photo","media":"AgACAgIAAxkDAAJeFl_rmiJ_GCp2wdL0uLe6bOFzEbYfAAIxsjEbd5xhS03Kn3bxUoYGzjgomy4AAwEAAwIAA20AA361AAIeBA"}}' \
#     https://api.telegram.org/bot$TELEGRAM_TOKEN/editMessageMedia

# set img "iVBORw0KGgoAAAANSUhEUgAAAGQAAABkCAYAAABw4pVUAAAAnElEQVR42u3RAQ0AAAgDIE1u9FvDOahApzLFGS1ECEKEIEQIQoQgRIgQIQgRghAhCBGCECEIQYgQhAhBiBCECEEIQoQgRAhChCBECEIQIgQhQhAiBCFCEIIQIQgRghAhCBGCEIQIQYgQhAhBiBCEIEQIQoQgRAhChCAEIUIQIgQhQhAiBCEIEYIQIQgRghAhCBEiRAhChCBECEK+W99M+TnxqRsqAAAAAElFTkSuQmCC"

# curl --request POST \
#     --form chat_id=112885364 \
#     --form message_id=24473 \
#     --form media='{"type":"photo","media":"iVBORw0KGgoAAAANSUhEUgAAAGQAAABkCAYAAABw4pVUAAAAnElEQVR42u3RAQ0AAAgDIE1u9FvDOahApzLFGS1ECEKEIEQIQoQgRIgQIQgRghAhCBGCECEIQYgQhAhBiBCECEEIQoQgRAhChCBECEIQIgQhQhAiBCFCEIIQIQgRghAhCBGCEIQIQYgQhAhBiBCEIEQIQoQgRAhChCAEIUIQIgQhQhAiBCEIEYIQIQgRghAhCBEiRAhChCBECEK+W99M+TnxqRsqAAAAAElFTkSuQmCC"}' \
#     --form reply_markup='{"inline_keyboard":[[{"text":"AcA","callback_data":"{:chat-id 112885364, :ccode \"ZZ\", :case-kw :a, :type :abs}"},{"text":"DeA","callback_data":"{:chat-id 112885364, :ccode \"ZZ\", :case-kw :d, :type :abs}"},{"text":"ReA","callback_data":"{:chat-id 112885364, :ccode \"ZZ\", :case-kw :r, :type :abs}"},{"text":"CoA","callback_data":"{:chat-id 112885364, :ccode \"ZZ\", :case-kw :c, :type :abs}"},{"text":"Co\u03a3","callback_data":"{:chat-id 112885364, :ccode \"ZZ\", :case-kw :c, :type :sum}"},{"text":"Re\u03a3","callback_data":"{:chat-id 112885364, :ccode \"ZZ\", :case-kw :r, :type :sum}"},{"text":"De\u03a3","callback_data":"{:chat-id 112885364, :ccode \"ZZ\", :case-kw :d, :type :sum}"},{"text":"Ac\u03a3","callback_data":"{:chat-id 112885364, :ccode \"ZZ\", :case-kw :a, :type :sum}"}]]}' \
#     --form photo=@resources/pics/s2.png \
#     https://api.telegram.org/bot$TELEGRAM_TOKEN/editMessageMedia
