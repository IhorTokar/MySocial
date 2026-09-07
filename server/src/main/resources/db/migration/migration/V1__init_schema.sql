-- Тип даних для ролей
CREATE TYPE user_role AS ENUM ('admin', 'user', 'guest');

-- Користувачі
CREATE TABLE "user" (
    user_id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uid                 VARCHAR(10) UNIQUE NOT NULL,
    username            VARCHAR(50) UNIQUE NOT NULL,
    display_name        VARCHAR(100),
    user_avatar_url     VARCHAR(255), -- Маленька іконка/мініатюра для чатів та коментарів
    profile_picture_url VARCHAR(255), -- Поточне головне фото сторінки
    about_me            TEXT,
    last_logout         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Приватна інформація
CREATE TABLE user_private (
    private_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT UNIQUE NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    email           VARCHAR(255) UNIQUE NOT NULL,
    phone_num       VARCHAR(50) UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            user_role NOT NULL DEFAULT 'user',
    date_of_birth   DATE
);

-- Підписки
CREATE TABLE followers (
    follow_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    follower_id   BIGINT NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    following_id  BIGINT NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_follow UNIQUE (follower_id, following_id),
    CONSTRAINT chk_not_self_follow CHECK (follower_id <> following_id)
);

-- Пости
CREATE TABLE posts (
    post_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    label         VARCHAR(255),
    text          TEXT,
    media_url     VARCHAR(255),
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_date   TIMESTAMPTZ
);

-- Теги
CREATE TABLE tags (
    tag_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name     VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE post_tags (
    post_id  BIGINT NOT NULL REFERENCES posts(post_id) ON DELETE CASCADE,
    tag_id   BIGINT NOT NULL REFERENCES tags(tag_id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, tag_id)
);

-- Повідомлення
CREATE TABLE messages (
    message_id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sender_id              BIGINT REFERENCES "user"(user_id) ON DELETE SET NULL,
    receiver_id            BIGINT REFERENCES "user"(user_id) ON DELETE SET NULL,
    message                TEXT,
    message_file_content   VARCHAR(255),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Коментарі
CREATE TABLE comments (
    comment_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id           BIGINT NOT NULL REFERENCES posts(post_id) ON DELETE CASCADE,
    user_id           BIGINT NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    text              TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    parent_comment_id BIGINT REFERENCES comments(comment_id) ON DELETE CASCADE
);

-- Лайки
CREATE TABLE post_likes (
    like_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    post_id    BIGINT NOT NULL REFERENCES posts(post_id) ON DELETE CASCADE,
    liked_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_like UNIQUE (user_id, post_id)
);

-- Збережені пости
CREATE TABLE saved_posts (
    save_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    post_id    BIGINT NOT NULL REFERENCES posts(post_id) ON DELETE CASCADE,
    saved_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_save UNIQUE (user_id, post_id)
);

-- Агреговані дані поста (лічильники)
CREATE TABLE post_data (
    post_id       BIGINT PRIMARY KEY REFERENCES posts(post_id) ON DELETE CASCADE,
    likes         INT NOT NULL DEFAULT 0,
    shares        INT NOT NULL DEFAULT 0,
    comments      INT NOT NULL DEFAULT 0
);

-- Додаткові індекси
CREATE INDEX idx_posts_user_created ON posts(user_id, created_date DESC);
CREATE INDEX idx_followers_follower ON followers(follower_id);
CREATE INDEX idx_followers_following ON followers(following_id);
CREATE INDEX idx_comments_post ON comments(post_id);
CREATE INDEX idx_messages_dialog ON messages(sender_id, receiver_id);