-- Справжній розподіл згенерованих користувачів (tools/report/seed.py): до якої спільноти їх поклав
-- генератор і яку роль вони мають в еволюції мережі. Потрібен, щоб оцінити, наскільки точно
-- Louvain і Leiden відновлюють спільноти (NMI, ARI). Звичайних користувачів тут немає.
CREATE TABLE seed_ground_truth (
    user_id          BIGINT PRIMARY KEY REFERENCES "user"(user_id) ON DELETE CASCADE,
    community        INT         NOT NULL,
    narrow           BOOLEAN     NOT NULL,
    role             VARCHAR(20) NOT NULL DEFAULT 'regular',
    target_community INT
);