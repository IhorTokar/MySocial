ALTER TABLE messages ADD COLUMN parent_message_id BIGINT;
ALTER TABLE messages ADD CONSTRAINT fk_messages_parent_message
    FOREIGN KEY (parent_message_id) REFERENCES messages(message_id);
ALTER TABLE messages ADD COLUMN edited BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE messages ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT false;