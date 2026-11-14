-- Відповідь на повідомлення посилається на батьківське (parent_message_id). Ключ із V4 був без ON DELETE,
-- тож видалити повідомлення, на яке хтось відповів, було неможливо — і разом з ним падало видалення акаунта.
-- Тепер відповідь лишається, але втрачає цитату видаленого повідомлення.
ALTER TABLE messages DROP CONSTRAINT fk_messages_parent_message;
ALTER TABLE messages ADD CONSTRAINT fk_messages_parent_message
    FOREIGN KEY (parent_message_id) REFERENCES messages(message_id) ON DELETE SET NULL;