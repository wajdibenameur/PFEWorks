-- Manual production hardening for chat reply relation.
-- Run once on the application database.

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS reply_to_message_id BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_chat_messages_reply_to
    ON chat_messages (reply_to_message_id);

ALTER TABLE chat_messages
    ADD CONSTRAINT fk_chat_messages_reply_to
    FOREIGN KEY (reply_to_message_id)
    REFERENCES chat_messages (id)
    ON DELETE SET NULL;
