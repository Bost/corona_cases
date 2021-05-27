;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.models.migration)

(ns corona.models.migration
  (:require [next.jdbc :as jdbc]
            [corona.models.dbase :as dbase]
            [corona.models.common :as mcom]
            [corona.macro :refer [defn-fun-id debugf]]))

(defn-fun-id migrated? "" []
  (with-open [connection
              (jdbc/get-connection mcom/datasource)]
    ((comp
      ;; not checking the indexes etc.
      (fn [tables]
        #_(debugf "Tables found:\n%s" tables)
        (clojure.set/subset?
         #{"user" "message" "chat" "callback_query" "thresholds"}
         tables))
      set
      (partial map :tables/table_name)
      (fn [result] (debugf "result:\n    %s" result) result)
      (fn [cmd] (jdbc/execute! connection [cmd]))
      #_(reduce my-fn init-value (jdbc/plan connection [...]))
      (fn [cmd] (debugf "\n  %s" cmd)
        cmd))
     (str "select table_name from information_schema.tables"
          " where table_schema = 'public'"))))

(defn-fun-id migrate "" []
  (when-not (migrated?)
    (debugf "Starting ...")
    (with-open [connection (jdbc/get-connection mcom/datasource)]
      (jdbc/execute! connection ["
DO $$
BEGIN
CREATE TABLE IF NOT EXISTS \"user\" (
  -- Unique identifier for this user or bot
  id bigint,
  -- True, if this user is a bot
  is_bot boolean DEFAULT false,
  -- User's or bot's first name
  first_name VARCHAR(255) NOT NULL DEFAULT '',
  -- User's or bot's last name
  last_name VARCHAR(255) DEFAULT NULL,
  -- User's or bot's username
  username VARCHAR(191) DEFAULT NULL,
  -- IETF language tag of the user's language
  language_code VARCHAR(10) DEFAULT NULL,
  -- Entry date creation
  created_at timestamp(0) NULL DEFAULT NULL,
  -- Entry date update
  updated_at timestamp(0) NULL DEFAULT NULL,

  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS username ON \"user\" (username);

IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'chat_type') THEN
  CREATE TYPE chat_type AS ENUM('private', 'group', 'supergroup', 'channel');
END IF;

CREATE TABLE IF NOT EXISTS chat (
  -- Unique identifier for this chat
  id bigint,
  -- Type of chat, can be either private, group, supergroup or channel
  type chat_type NOT NULL,
  -- Title, for supergroups, channels and group chats
  title VARCHAR(255) DEFAULT '',
  -- Username, for private chats, supergroups and channels if available
  username VARCHAR(255) DEFAULT NULL,
  -- First name of the other party in a private chat
  first_name VARCHAR(255) DEFAULT NULL,
  -- Last name of the other party in a private chat
  last_name VARCHAR(255) DEFAULT NULL,
  -- True if a all members of this group are admins
  all_members_are_administrators boolean DEFAULT false,
  -- Entry date creation
  created_at timestamp(0) NULL DEFAULT NULL,
  -- Entry date update
  updated_at timestamp(0) NULL DEFAULT NULL,
  -- Unique chat identifier, this is filled when a group is converted to a
  -- supergroup
  old_id bigint DEFAULT NULL,

  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS old_id ON chat (old_id);

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

  -- For messages forwarded from channels, signature of the post author if
  -- present
  forward_signature TEXT NULL DEFAULT NULL,

  -- Sender's name for messages forwarded from users who disallow adding a link
  -- to their account in forwarded messages
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
  -- For text messages, the actual UTF-8 text of the message max message length
  -- 4096 char utf8mb4
  text TEXT,
  -- For text messages, special entities like usernames, URLs, bot commands,
  -- etc. that appear in the text
  entities TEXT,
  -- For messages with a caption, special entities like usernames, URLs, bot
  -- commands, etc. that appear in the caption
  caption_entities TEXT,
  -- Audio object. Message is an audio file, information about the file
  audio TEXT,
  -- Document object. Message is a general file, information about the file
  document TEXT,
  -- Message is an animation, information about the animation
  animation TEXT,
  -- Game object. Message is a game, information about the game
  game TEXT,
  -- Array of PhotoSize objects. Message is a photo, available sizes of the
  -- photo
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
  -- Location object. Message is a shared location, information about the
  -- location
  location TEXT,
  -- Venue object. Message is a Venue, information about the Venue
  venue TEXT,
  -- Poll object. Message is a native poll, information about the poll
  poll TEXT,
  -- Message is a dice with random value from 1 to 6
  dice TEXT,
  -- List of unique user identifiers, new member(s) were added to the group,
  -- information about them (one of these members may be the bot itself)
  new_chat_members TEXT,
  -- Unique user identifier, a member was removed from the group, information
  -- about them (this member may be the bot itself)
  left_chat_member bigint NULL DEFAULT NULL,
  -- A chat title was changed to this value
  new_chat_title VARCHAR(255) DEFAULT NULL,
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
  -- Migrate to chat identifier. The group has been migrated to a supergroup
  -- with the specified identifier
  migrate_to_chat_id bigint NULL DEFAULT NULL,
  -- Migrate from chat identifier. The supergroup has been migrated from a group
  -- with the specified identifier
  migrate_from_chat_id bigint NULL DEFAULT NULL,
  -- Message object. Specified message was pinned
  pinned_message TEXT NULL,
  -- Message is an invoice for a payment, information about the invoice
  invoice TEXT NULL,
  -- Message is a service message about a successful payment, information about
  -- the payment
  successful_payment TEXT NULL,
  -- The domain name of the website on which the user has logged in.
  connected_website TEXT NULL,
  -- Telegram Passport data
  passport_data TEXT NULL,
  -- Service message. A user in the chat triggered another user's proximity
  -- alert while sharing Live Location.
  proximity_alert_triggered TEXT NULL,
  -- Inline keyboard attached to the message
  reply_markup TEXT NULL,

  PRIMARY KEY (chat_id, id),

  FOREIGN KEY (user_id) REFERENCES \"user\" (id),
  FOREIGN KEY (chat_id) REFERENCES chat (id),
  FOREIGN KEY (forward_from) REFERENCES \"user\" (id),
  FOREIGN KEY (forward_from_chat) REFERENCES chat (id),
  FOREIGN KEY (reply_to_chat, reply_to_message)
              REFERENCES message (chat_id, id),
  FOREIGN KEY (via_bot) REFERENCES \"user\" (id),
  FOREIGN KEY (left_chat_member) REFERENCES \"user\" (id)
);

CREATE INDEX IF NOT EXISTS message_user_id ON message (user_id);
CREATE INDEX IF NOT EXISTS forward_from ON message (forward_from);
CREATE INDEX IF NOT EXISTS forward_from_chat ON message (forward_from_chat);
CREATE INDEX IF NOT EXISTS reply_to_chat ON message (reply_to_chat);
CREATE INDEX IF NOT EXISTS reply_to_message ON message (reply_to_message);
CREATE INDEX IF NOT EXISTS via_bot ON message (via_bot);
CREATE INDEX IF NOT EXISTS left_chat_member ON message (left_chat_member);
CREATE INDEX IF NOT EXISTS migrate_from_chat_id
                           ON message (migrate_from_chat_id);
CREATE INDEX IF NOT EXISTS migrate_to_chat_id ON message (migrate_to_chat_id);

CREATE TABLE IF NOT EXISTS callback_query (
  -- Unique identifier for this query
  id bigint CHECK (id > 0),
  -- Unique user identifier
  user_id bigint NULL,
  -- Unique chat identifier
  chat_id bigint NULL,
  -- Unique message identifier
  message_id bigint CHECK (message_id > 0),
  -- Identifier of the message sent via the bot in inline mode, that originated
  -- the query
  inline_message_id VARCHAR(255) NULL DEFAULT NULL,
  -- Global identifier, uniquely corresponding to the chat to which the message
  -- with the callback button was sent
  chat_instance VARCHAR(255) NOT NULL DEFAULT '',
  -- Data associated with the callback button
  data VARCHAR(255) NOT NULL DEFAULT '',
  -- Short name of a Game to be returned, serves as the unique identifier for
  -- the game
  game_short_name VARCHAR(255) NOT NULL DEFAULT '',
  -- Entry date creation
  created_at timestamp(0) NULL DEFAULT NULL,

  PRIMARY KEY (id),

  FOREIGN KEY (user_id) REFERENCES \"user\" (id),
  FOREIGN KEY (chat_id, message_id) REFERENCES message (chat_id, id)
);

CREATE INDEX IF NOT EXISTS callback_query_user_id ON callback_query (user_id);
CREATE INDEX IF NOT EXISTS chat_id ON callback_query (chat_id);
CREATE INDEX IF NOT EXISTS message_id ON callback_query (message_id);

-- migration 001
CREATE TABLE IF NOT EXISTS thresholds (
  kw VARCHAR(255) NOT NULL UNIQUE,
  inc integer,
  val integer,
  updated_at timestamp(0) NULL DEFAULT NULL,
  PRIMARY KEY (kw)
);

END
$$;
"]))
    (debugf "Starting ... done")))

;; (printf "Current-ns [%s] loading %s ... done\n"
;;         *ns* 'corona.models.migration)
