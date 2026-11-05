-- Блокування акаунтів адміністратором
ALTER TABLE user_private ADD COLUMN blocked BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE user_private ADD COLUMN blocked_reason VARCHAR(255);
ALTER TABLE user_private ADD COLUMN blocked_at TIMESTAMPTZ;

-- Версія токенів: записується в JWT при вході. Збільшення версії робить недійсними
-- всі раніше видані токени користувача (використовується при скиданні пароля).
ALTER TABLE user_private ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;