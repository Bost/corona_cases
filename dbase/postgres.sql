--() { :; }; exec psql -f "$0"

-- shebang - see also:
-- https://rosettacode.org/wiki/Multiline_shebang#PostgreSQL

-- spacemacs - select Postgres dialect
-- SPC m h k

-- See types - e.g.:
-- https://core.telegram.org/bots/api#chat

-- Convert MySQL to PostgreSQL:
-- https://github.com/php-telegram-bot/core/blob/master/structure.sql

-- 1. evil replacements in the structure.sql
-- %s/\(.*\) COMMENT '\(.*\)',/-- \2\n\1,/gc
-- %s/''s/'s/g

-- 2. use http://www.sqlines.com/online

-- 3. then use this emacs-macro to restore comments
-- (fset 'restore-comment
--    (kmacro-lambda-form [?y ?y ?\s-q ?V ?P down down ?\s-q down down] 0 "%d"))

CREATE TABLE IF NOT EXISTS "user" (
  -- Unique identifier for this user or bot
  id bigint,
  -- True, if this user is a bot
  is_bot smallint DEFAULT 0,
  -- User's or bot's first name
  first_name CHAR(255) NOT NULL DEFAULT '',
  -- User's or bot's last name
  last_name CHAR(255) DEFAULT NULL,
  -- User's or bot's username
  username CHAR(191) DEFAULT NULL,
  -- IETF language tag of the user's language
  language_code CHAR(10) DEFAULT NULL,
  -- Entry date creation
  created_at timestamp(0) NULL DEFAULT NULL,
  -- Entry date update
  updated_at timestamp(0) NULL DEFAULT NULL,

  PRIMARY KEY (id)
)  ;

CREATE INDEX username ON "user" (username);

CREATE TYPE chat_type AS ENUM('private', 'group', 'supergroup', 'channel');

CREATE TABLE IF NOT EXISTS chat (
  -- Unique identifier for this chat
  id bigint,
  -- Type of chat, can be either private, group, supergroup or channel
  type chat_type NOT NULL,
  -- Title, for supergroups, channels and group chats
  title CHAR(255) DEFAULT '',
  -- Username, for private chats, supergroups and channels if available
  username CHAR(255) DEFAULT NULL,
  -- First name of the other party in a private chat
  first_name CHAR(255) DEFAULT NULL,
  -- Last name of the other party in a private chat
  last_name CHAR(255) DEFAULT NULL,
  -- True if a all members of this group are admins
  all_members_are_administrators smallint DEFAULT 0,
  -- Entry date creation
  created_at timestamp(0) NULL DEFAULT NULL,
  -- Entry date update
  updated_at timestamp(0) NULL DEFAULT NULL,
  -- Unique chat identifier, this is filled when a group is converted to a supergroup
  old_id bigint DEFAULT NULL,

  PRIMARY KEY (id)
)  ;

CREATE INDEX old_id ON chat (old_id);

CREATE TABLE IF NOT EXISTS user_chat (
  -- Unique user identifier
  user_id bigint,
  -- Unique user or chat identifier
  chat_id bigint,

  PRIMARY KEY (user_id, chat_id),

  FOREIGN KEY (user_id) REFERENCES "user" (id) ON DELETE CASCADE ON UPDATE CASCADE,
  FOREIGN KEY (chat_id) REFERENCES chat (id) ON DELETE CASCADE ON UPDATE CASCADE
)  ;

CREATE TABLE IF NOT EXISTS inline_query (
  -- Unique identifier for this query
  id bigint CHECK (id > 0),
  -- Unique user identifier
  user_id bigint NULL,
  -- Location of the user
  location CHAR(255) NULL DEFAULT NULL,
  -- Text of the query
  query TEXT NOT NULL,
  -- Offset of the result
  "offset" CHAR(255) NULL DEFAULT NULL,
  -- Entry date creation
  created_at timestamp(0) NULL DEFAULT NULL,

  PRIMARY KEY (id)
 ,

 FOREIGN KEY (user_id) REFERENCES "user" (id)
)  ;

CREATE INDEX user_id ON inline_query (user_id);

CREATE SEQUENCE chosen_inline_result_seq;

CREATE TABLE IF NOT EXISTS chosen_inline_result (
  -- Unique identifier for this entry
  id bigint CHECK (id > 0) DEFAULT NEXTVAL ('chosen_inline_result_seq'),
  -- The unique identifier for the result that was chosen
  result_id CHAR(255) NOT NULL DEFAULT '',
  -- The user that chose the result
  user_id bigint NULL,
  -- Sender location, only for bots that require user location
  location CHAR(255) NULL DEFAULT NULL,
  -- Identifier of the sent inline message
  inline_message_id CHAR(255) NULL DEFAULT NULL,
  -- The query that was used to obtain the result
  query TEXT NOT NULL,
  -- Entry date creation
  created_at timestamp(0) NULL DEFAULT NULL,

  PRIMARY KEY (id)
 ,

 FOREIGN KEY (user_id) REFERENCES "user" (id)
)  ;

CREATE INDEX user_id ON chosen_inline_result (user_id);

CREATE TABLE IF NOT EXISTS message (
  -- Unique chat identifier
  chat_id bigint,
  -- Sender of the message, sent on behalf of a chat
  sender_chat_id bigint,
  -- Unique message identifier
  id bigint CHECK (id > 0),
  -- Unique user identifier
  user_id bigint NULL,
  -- Date the message was sent in timestamp format
  date timestamp(0) NULL DEFAULT NULL,
  -- Unique user identifier, sender of the original message
  forward_from bigint NULL DEFAULT NULL,
  -- Unique chat identifier, chat the original message belongs to
  forward_from_chat bigint NULL DEFAULT NULL,
  -- Unique chat identifier of the original message in the channel
  forward_from_message_id bigint NULL DEFAULT NULL,
  -- For messages forwarded from channels, signature of the post author if present
  forward_signature TEXT NULL DEFAULT NULL,
  -- Sender's name for messages forwarded from users who disallow adding a link to their account in forwarded messages
  forward_sender_name TEXT NULL DEFAULT NULL,
  -- date the original message was sent in timestamp format
  forward_date timestamp(0) NULL DEFAULT NULL,
  -- Unique chat identifier
  reply_to_chat bigint NULL DEFAULT NULL,
  -- Message that this message is reply to
  reply_to_message bigint CHECK (reply_to_message > 0) DEFAULT NULL,
  -- Optional. Bot through which the message was sent
  via_bot bigint NULL DEFAULT NULL,
  -- Date the message was last edited in Unix time
  edit_date bigint CHECK (edit_date > 0) DEFAULT NULL,
  -- The unique identifier of a media message group this message belongs to
  media_group_id TEXT,
  -- Signature of the post author for messages in channels
  author_signature TEXT,
  -- For text messages, the actual UTF-8 text of the message max message length 4096 char utf8mb4
  text TEXT,
  -- For text messages, special entities like usernames, URLs, bot commands, etc. that appear in the text
  entities TEXT,
  -- For messages with a caption, special entities like usernames, URLs, bot commands, etc. that appear in the caption
  caption_entities TEXT,
  -- Audio object. Message is an audio file, information about the file
  audio TEXT,
  -- Document object. Message is a general file, information about the file
  document TEXT,
  -- Message is an animation, information about the animation
  animation TEXT,
  -- Game object. Message is a game, information about the game
  game TEXT,
  -- Array of PhotoSize objects. Message is a photo, available sizes of the photo
  photo TEXT,
  -- Sticker object. Message is a sticker, information about the sticker
  sticker TEXT,
  -- Video object. Message is a video, information about the video
  video TEXT,
  -- Voice Object. Message is a Voice, information about the Voice
  voice TEXT,
  -- VoiceNote Object. Message is a Video Note, information about the Video Note
  video_note TEXT,
  caption TEXT  ,
  -- Contact object. Message is a shared contact, information about the contact
  contact TEXT,
  -- Location object. Message is a shared location, information about the location
  location TEXT,
  -- Venue object. Message is a Venue, information about the Venue
  venue TEXT,
  -- Poll object. Message is a native poll, information about the poll
  poll TEXT,
  -- Message is a dice with random value from 1 to 6
  dice TEXT,
  -- List of unique user identifiers, new member(s) were added to the group, information about them (one of these members may be the bot itself)
  new_chat_members TEXT,
  -- Unique user identifier, a member was removed from the group, information about them (this member may be the bot itself)
  left_chat_member bigint NULL DEFAULT NULL,
  -- A chat title was changed to this value
  new_chat_title CHAR(255) DEFAULT NULL,
  -- Array of PhotoSize objects. A chat photo was change to this value
  new_chat_photo TEXT,
  -- Informs that the chat photo was deleted
  delete_chat_photo smallint DEFAULT 0,
  -- Informs that the group has been created
  group_chat_created smallint DEFAULT 0,
  -- Informs that the supergroup has been created
  supergroup_chat_created smallint DEFAULT 0,
  -- Informs that the channel chat has been created
  channel_chat_created smallint DEFAULT 0,
  -- Migrate to chat identifier. The group has been migrated to a supergroup with the specified identifier
  migrate_to_chat_id bigint NULL DEFAULT NULL,
  -- Migrate from chat identifier. The supergroup has been migrated from a group with the specified identifier
  migrate_from_chat_id bigint NULL DEFAULT NULL,
  -- Message object. Specified message was pinned
  pinned_message TEXT NULL,
  -- Message is an invoice for a payment, information about the invoice
  invoice TEXT NULL,
  -- Message is a service message about a successful payment, information about the payment
  successful_payment TEXT NULL,
  -- The domain name of the website on which the user has logged in.
  connected_website TEXT NULL,
  -- Telegram Passport data
  passport_data TEXT NULL,
  -- Service message. A user in the chat triggered another user's proximity alert while sharing Live Location.
  proximity_alert_triggered TEXT NULL,
  -- Inline keyboard attached to the message
  reply_markup TEXT NULL,

  PRIMARY KEY (chat_id, id)
 ,

 FOREIGN KEY (user_id) REFERENCES "user" (id),
 FOREIGN KEY (chat_id) REFERENCES chat (id),
 FOREIGN KEY (forward_from) REFERENCES "user" (id),
 FOREIGN KEY (forward_from_chat) REFERENCES chat (id),
 FOREIGN KEY (reply_to_chat, reply_to_message) REFERENCES message (chat_id, id),
 FOREIGN KEY (via_bot) REFERENCES "user" (id),
 FOREIGN KEY (left_chat_member) REFERENCES "user" (id)
)  ;

CREATE INDEX user_id ON message (user_id);
CREATE INDEX forward_from ON message (forward_from);
CREATE INDEX forward_from_chat ON message (forward_from_chat);
CREATE INDEX reply_to_chat ON message (reply_to_chat);
CREATE INDEX reply_to_message ON message (reply_to_message);
CREATE INDEX via_bot ON message (via_bot);
CREATE INDEX left_chat_member ON message (left_chat_member);
CREATE INDEX migrate_from_chat_id ON message (migrate_from_chat_id);
CREATE INDEX migrate_to_chat_id ON message (migrate_to_chat_id);

CREATE SEQUENCE edited_message_seq;

CREATE TABLE IF NOT EXISTS edited_message (
  -- Unique identifier for this entry
  id bigint CHECK (id > 0) DEFAULT NEXTVAL ('edited_message_seq'),
  -- Unique chat identifier
  chat_id bigint,
  -- Unique message identifier
  message_id bigint CHECK (message_id > 0),
  -- Unique user identifier
  user_id bigint NULL,
  -- Date the message was edited in timestamp format
  edit_date timestamp(0) NULL DEFAULT NULL,
  -- For text messages, the actual UTF-8 text of the message max message length 4096 char utf8
  text TEXT,
  -- For text messages, special entities like usernames, URLs, bot commands, etc. that appear in the text
  entities TEXT,
  caption TEXT  ,

  PRIMARY KEY (id)
 ,

  FOREIGN KEY (chat_id) REFERENCES chat (id),
  FOREIGN KEY (chat_id, message_id) REFERENCES message (chat_id, id),
  FOREIGN KEY (user_id) REFERENCES "user" (id)
)  ;

CREATE INDEX chat_id ON edited_message (chat_id);
CREATE INDEX message_id ON edited_message (message_id);
CREATE INDEX user_id ON edited_message (user_id);

CREATE TABLE IF NOT EXISTS callback_query (
  -- Unique identifier for this query
  id bigint CHECK (id > 0),
  -- Unique user identifier
  user_id bigint NULL,
  -- Unique chat identifier
  chat_id bigint NULL,
  -- Unique message identifier
  message_id bigint CHECK (message_id > 0),
  -- Identifier of the message sent via the bot in inline mode, that originated the query
  inline_message_id CHAR(255) NULL DEFAULT NULL,
  -- Global identifier, uniquely corresponding to the chat to which the message with the callback button was sent
  chat_instance CHAR(255) NOT NULL DEFAULT '',
  -- Data associated with the callback button
  data CHAR(255) NOT NULL DEFAULT '',
  -- Short name of a Game to be returned, serves as the unique identifier for the game
  game_short_name CHAR(255) NOT NULL DEFAULT '',
  -- Entry date creation
  created_at timestamp(0) NULL DEFAULT NULL,

  PRIMARY KEY (id)
 ,

 FOREIGN KEY (user_id) REFERENCES "user" (id),
  FOREIGN KEY (chat_id, message_id) REFERENCES message (chat_id, id)
)  ;

CREATE INDEX user_id ON callback_query (user_id);
CREATE INDEX chat_id ON callback_query (chat_id);
CREATE INDEX message_id ON callback_query (message_id);

CREATE TABLE IF NOT EXISTS shipping_query (
  -- Unique query identifier
  id bigint CHECK (id > 0),
  -- User who sent the query
  user_id bigint,
  -- Bot specified invoice payload
  invoice_payload CHAR(255) NOT NULL DEFAULT '',
  -- User specified shipping address
  shipping_address CHAR(255) NOT NULL DEFAULT '',
  -- Entry date creation
  created_at timestamp(0) NULL DEFAULT NULL,

  PRIMARY KEY (id)
 ,

 FOREIGN KEY (user_id) REFERENCES "user" (id)
)  ;

CREATE INDEX user_id ON shipping_query (user_id);

CREATE TABLE IF NOT EXISTS pre_checkout_query (
  -- Unique query identifier
  id bigint CHECK (id > 0),
  -- User who sent the query
  user_id bigint,
  -- Three-letter ISO 4217 currency code
  currency CHAR(3),
  -- Total price in the smallest units of the currency
  total_amount bigint,
  -- Bot specified invoice payload
  invoice_payload CHAR(255) NOT NULL DEFAULT '',
  -- Identifier of the shipping option chosen by the user
  shipping_option_id CHAR(255) NULL,
  -- Order info provided by the user
  order_info TEXT NULL,
  -- Entry date creation
  created_at timestamp(0) NULL DEFAULT NULL,

  PRIMARY KEY (id)
 ,

 FOREIGN KEY (user_id) REFERENCES "user" (id)
)  ;

CREATE INDEX user_id ON pre_checkout_query (user_id);

CREATE TABLE IF NOT EXISTS poll (
  -- Unique poll identifier
  id bigint CHECK (id > 0),
  -- Poll question
  question text NOT NULL,
  -- List of poll options
  options text NOT NULL,
  -- Total number of users that voted in the poll
  total_voter_count int CHECK (total_voter_count > 0),
  -- True, if the poll is closed
  is_closed smallint DEFAULT 0,
  -- True, if the poll is anonymous
  is_anonymous smallint DEFAULT 1,
  -- Poll type, currently can be “regular” or “quiz”
  type char(255),
  -- True, if the poll allows multiple answers
  allows_multiple_answers smallint DEFAULT 0,
  -- 0-based identifier of the correct answer option. Available only for polls in the quiz mode, which are closed, or was sent (not forwarded) by the bot or to the private chat with the bot.
  correct_option_id int CHECK (correct_option_id > 0),
  -- Text that is shown when a user chooses an incorrect answer or taps on the lamp icon in a quiz-style poll, 0-200 characters
  explanation varchar(255) DEFAULT NULL,
  -- Special entities like usernames, URLs, bot commands, etc. that appear in the explanation
  explanation_entities text DEFAULT NULL,
  -- Amount of time in seconds the poll will be active after creation
  open_period int CHECK (open_period > 0) DEFAULT NULL,
  -- Point in time (Unix timestamp) when the poll will be automatically closed
  close_date timestamp(0) NULL DEFAULT NULL,
  -- Entry date creation
  created_at timestamp(0) NULL DEFAULT NULL,

  PRIMARY KEY (id)
)  ;

CREATE TABLE IF NOT EXISTS poll_answer (
  -- Unique poll identifier
  poll_id bigint CHECK (poll_id > 0),
  -- The user, who changed the answer to the poll
  user_id bigint NOT NULL,
  -- 0-based identifiers of answer options, chosen by the user. May be empty if the user retracted their vote.
  option_ids text NOT NULL,
  -- Entry date creation
  created_at timestamp(0) NULL DEFAULT NULL,

  PRIMARY KEY (poll_id, user_id),
  FOREIGN KEY (poll_id) REFERENCES poll (id)
)  ;

CREATE TABLE IF NOT EXISTS telegram_update (
  -- Update's unique identifier
  id bigint CHECK (id > 0),
  -- Unique chat identifier
  chat_id bigint NULL DEFAULT NULL,
  -- New incoming message of any kind - text, photo, sticker, etc.
  message_id bigint CHECK (message_id > 0) DEFAULT NULL,
  -- New version of a message that is known to the bot and was edited
  edited_message_id bigint CHECK (edited_message_id > 0) DEFAULT NULL,
  -- New incoming channel post of any kind - text, photo, sticker, etc.
  channel_post_id bigint CHECK (channel_post_id > 0) DEFAULT NULL,
  -- New version of a channel post that is known to the bot and was edited
  edited_channel_post_id bigint CHECK (edited_channel_post_id > 0) DEFAULT NULL,
  -- New incoming inline query
  inline_query_id bigint CHECK (inline_query_id > 0) DEFAULT NULL,
  -- The result of an inline query that was chosen by a user and sent to their chat partner
  chosen_inline_result_id bigint CHECK (chosen_inline_result_id > 0) DEFAULT NULL,
  -- New incoming callback query
  callback_query_id bigint CHECK (callback_query_id > 0) DEFAULT NULL,
  -- New incoming shipping query. Only for invoices with flexible price
  shipping_query_id bigint CHECK (shipping_query_id > 0) DEFAULT NULL,
  -- New incoming pre-checkout query. Contains full information about checkout
  pre_checkout_query_id bigint CHECK (pre_checkout_query_id > 0) DEFAULT NULL,
  -- New poll state. Bots receive only updates about polls, which are sent or stopped by the bot
  poll_id bigint CHECK (poll_id > 0) DEFAULT NULL,
  -- A user changed their answer in a non-anonymous poll. Bots receive new votes only in polls that were sent by the bot itself.
  poll_answer_poll_id bigint CHECK (poll_answer_poll_id > 0) DEFAULT NULL,

  PRIMARY KEY (id)
 ,

  FOREIGN KEY (chat_id, message_id) REFERENCES message (chat_id, id),
  FOREIGN KEY (edited_message_id) REFERENCES edited_message (id),
  FOREIGN KEY (chat_id, channel_post_id) REFERENCES message (chat_id, id),
  FOREIGN KEY (edited_channel_post_id) REFERENCES edited_message (id),
  FOREIGN KEY (inline_query_id) REFERENCES inline_query (id),
  FOREIGN KEY (chosen_inline_result_id) REFERENCES chosen_inline_result (id),
  FOREIGN KEY (callback_query_id) REFERENCES callback_query (id),
  FOREIGN KEY (shipping_query_id) REFERENCES shipping_query (id),
  FOREIGN KEY (pre_checkout_query_id) REFERENCES pre_checkout_query (id),
  FOREIGN KEY (poll_id) REFERENCES poll (id)
  -- TODO this differs from MySQL
  -- , FOREIGN KEY (poll_answer_poll_id) REFERENCES poll_answer (poll_id)
);

CREATE INDEX message_id ON telegram_update (message_id);
CREATE INDEX chat_message_id ON telegram_update (chat_id, message_id);
CREATE INDEX edited_message_id ON telegram_update (edited_message_id);
CREATE INDEX channel_post_id ON telegram_update (channel_post_id);
CREATE INDEX edited_channel_post_id ON telegram_update (edited_channel_post_id);
CREATE INDEX inline_query_id ON telegram_update (inline_query_id);
CREATE INDEX chosen_inline_result_id ON telegram_update (chosen_inline_result_id);
CREATE INDEX callback_query_id ON telegram_update (callback_query_id);
CREATE INDEX shipping_query_id ON telegram_update (shipping_query_id);
CREATE INDEX pre_checkout_query_id ON telegram_update (pre_checkout_query_id);
CREATE INDEX poll_id ON telegram_update (poll_id);
CREATE INDEX poll_answer_poll_id ON telegram_update (poll_answer_poll_id);

CREATE SEQUENCE conversation_seq;

CREATE TYPE status_type AS ENUM('active', 'cancelled', 'stopped');

CREATE TABLE IF NOT EXISTS conversation (
  -- Unique identifier for this entry
  id bigint check (id > 0) DEFAULT NEXTVAL ('conversation_seq'),
  -- Unique user identifier
  user_id bigint NULL DEFAULT NULL,
  -- Unique user or chat identifier
  chat_id bigint NULL DEFAULT NULL,
  -- Conversation state
  status status_type NOT NULL DEFAULT 'active',
  -- Default command to execute
  command varchar(160) DEFAULT '',
  -- Data stored from command
  notes text DEFAULT NULL,
  -- Entry date creation
  created_at timestamp NULL DEFAULT NULL,
  -- Entry date update
  updated_at timestamp NULL DEFAULT NULL,

  PRIMARY KEY (id),
  -- TODO this differs from MySQL
  -- PRIMARY KEY (user_id),
  -- PRIMARY KEY (chat_id),
  -- PRIMARY KEY (status),

  FOREIGN KEY (user_id) REFERENCES "user" (id),
  FOREIGN KEY (chat_id) REFERENCES chat (id)
);

CREATE SEQUENCE request_limiter_seq;

CREATE TABLE IF NOT EXISTS request_limiter (
  -- Unique identifier for this entry
  id bigint CHECK (id > 0) DEFAULT NEXTVAL ('request_limiter_seq'),
  -- Unique chat identifier
  chat_id char(255) NULL DEFAULT NULL,
  -- Identifier of the sent inline message
  inline_message_id char(255) NULL DEFAULT NULL,
  -- Request method
  method char(255) DEFAULT NULL,
  -- Entry date creation
  created_at timestamp(0) NULL DEFAULT NULL,

  PRIMARY KEY (id)
)  ;
