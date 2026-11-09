-- λ для MMR-різноманітності стрічки тепер версіонується разом з вагами і змінюється з адмін-панелі.
-- Версії, створені до появи MMR, працювали без перестановки, тому для них λ = 1.
ALTER TABLE recommendation_weights
    ADD COLUMN mmr_lambda DOUBLE PRECISION NOT NULL DEFAULT 1.0
        CHECK (mmr_lambda >= 0 AND mmr_lambda <= 1);