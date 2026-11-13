-- Сповіщення не зберігаються окремо: їх складає запит із підписок, вподобань і коментарів.
-- Тут лише момент, коли користувач востаннє відкривав список: усе новіше — «непрочитане».
ALTER TABLE "user" ADD COLUMN notifications_seen_at TIMESTAMPTZ;

-- для пошуку відповідей на мої коментарі
CREATE INDEX IF NOT EXISTS idx_comments_parent ON comments (parent_comment_id);