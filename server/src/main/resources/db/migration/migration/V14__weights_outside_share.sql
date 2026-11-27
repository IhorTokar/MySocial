-- Частка постів з інших спільнот у стрічці «Для вас» версіонується разом з вагами.
-- Версії, створені до появи цього налаштування, працювали без розбавлення, тому для них 0.
ALTER TABLE recommendation_weights
    ADD COLUMN outside_share DOUBLE PRECISION NOT NULL DEFAULT 0.0
        CHECK (outside_share >= 0 AND outside_share <= 0.5);