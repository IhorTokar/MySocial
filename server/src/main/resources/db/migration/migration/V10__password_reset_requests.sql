-- «Забув пароль»: користувач залишає заявку, адміністратор перевіряє особу й встановлює тимчасовий пароль.
CREATE TABLE password_reset_request (
    request_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    contact     VARCHAR(200),                       -- як зв'язатися з користувачем (вказав сам)
    message     VARCHAR(1000),
    status      VARCHAR(10) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'resolved', 'rejected')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    resolved_by BIGINT REFERENCES "user"(user_id) ON DELETE SET NULL
);

-- не більше однієї відкритої заявки на користувача
CREATE UNIQUE INDEX uq_password_reset_pending ON password_reset_request (user_id) WHERE status = 'pending';
CREATE INDEX idx_password_reset_status ON password_reset_request (status, created_at DESC);