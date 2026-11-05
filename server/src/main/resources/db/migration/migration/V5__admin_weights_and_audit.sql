-- Ваги гібридної стрічки: кожна зміна додає новий рядок, чинні ваги — останній рядок.
-- Таблиця одночасно є історією змін і дозволяє повернутися до будь-якої попередньої версії.
CREATE TABLE recommendation_weights (
    weights_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    popularity     DOUBLE PRECISION NOT NULL,
    item_cf        DOUBLE PRECISION NOT NULL,
    graph_sim      DOUBLE PRECISION NOT NULL,
    semantic       DOUBLE PRECISION NOT NULL,
    recency        DOUBLE PRECISION NOT NULL,
    followed_bonus DOUBLE PRECISION NOT NULL,
    comment        VARCHAR(255),
    created_by     BIGINT REFERENCES "user"(user_id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Журнал дій адміністраторів
CREATE TABLE admin_audit_log (
    audit_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admin_id    BIGINT REFERENCES "user"(user_id) ON DELETE SET NULL,
    action      VARCHAR(40) NOT NULL,
    target_type VARCHAR(20),
    target_id   BIGINT,
    details     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_admin_audit_log_created_at ON admin_audit_log (created_at DESC);