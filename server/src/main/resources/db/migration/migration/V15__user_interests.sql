-- Теми, які людина обирає при першому вході: поки в неї немає вподобань і підписок,
-- стрічка «Для вас» підбирає пости за цими темами (холодний старт).
CREATE TABLE user_interest_tags (
    user_id  BIGINT NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    tag_id   BIGINT NOT NULL REFERENCES tags(tag_id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, tag_id)
);

-- Коли людині вперше запропонували обрати теми (навіть якщо вона пропустила) — щоб не питати щоразу.
ALTER TABLE "user" ADD COLUMN interests_asked_at TIMESTAMPTZ;
