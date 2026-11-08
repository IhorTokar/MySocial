-- Тональність поста в [-1, 1], обчислюється один раз при створенні / зміні тексту.
-- sentiment_source: 'model'   — ML-модель NLP-сервісу;
--                   'lexicon' — запасний словниковий підрахунок (сервіс був недоступний);
--                   'empty'   — у поста немає тексту (тільки зображення).
-- NULL — ще не обчислено (старі пости); заповнюються при першому аналізі спільнот або backfill.
ALTER TABLE posts ADD COLUMN sentiment DOUBLE PRECISION;
ALTER TABLE posts ADD COLUMN sentiment_source VARCHAR(10);