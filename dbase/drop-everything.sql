--() { :; }; exec psql -f "$0"

-- shebang - see also:
-- https://rosettacode.org/wiki/Multiline_shebang#PostgreSQL

-- spacemacs - select Postgres dialect
-- SPC m h k

-- Usage: also --echo-all
-- psql --dbname=postgres --quiet --file=dbase/drop-everything.sql

-- Don't display notices. Thanks to https://stackoverflow.com/a/3531274
SET client_min_messages TO WARNING;

drop table if exists chosen_inline_result cascade;
drop table if exists poll_answer cascade;
drop table if exists poll cascade;
drop table if exists pre_checkout_query cascade;
drop table if exists request_limiter cascade;
drop table if exists shipping_query cascade;
drop table if exists "user" cascade;
drop table if exists callback_query cascade;
drop table if exists chat cascade;
drop table if exists edited_message cascade;
drop table if exists message cascade;
drop table if exists user_chat cascade;
drop table if exists conversation cascade;
drop table if exists inline_query cascade;
drop table if exists telegram_update cascade;

drop sequence if exists chosen_inline_result_seq;
drop sequence if exists conversation_seq;
drop sequence if exists edited_message_seq;
drop sequence if exists request_limiter_seq;

drop TYPE if exists chat_type cascade;
drop TYPE if exists status_type cascade;

DROP INDEX if exists old_id cascade;
DROP INDEX if exists user_id cascade;
DROP INDEX if exists forward_from cascade;
DROP INDEX if exists forward_from_chat cascade;
DROP INDEX if exists reply_to_chat cascade;
DROP INDEX if exists reply_to_message cascade;
DROP INDEX if exists via_bot cascade;
DROP INDEX if exists left_chat_member cascade;
DROP INDEX if exists migrate_from_chat_id cascade;
DROP INDEX if exists migrate_to_chat_id cascade;
DROP INDEX if exists chat_id cascade;
DROP INDEX if exists message_id cascade;
DROP INDEX if exists chat_message_id cascade;
DROP INDEX if exists edited_message_id cascade;
DROP INDEX if exists channel_post_id cascade;
DROP INDEX if exists edited_channel_post_id cascade;
DROP INDEX if exists inline_query_id cascade;
DROP INDEX if exists chosen_inline_result_id cascade;
DROP INDEX if exists callback_query_id cascade;
DROP INDEX if exists shipping_query_id cascade;
DROP INDEX if exists pre_checkout_query_id cascade;
DROP INDEX if exists poll_id cascade;
DROP INDEX if exists poll_answer_poll_id cascade;

