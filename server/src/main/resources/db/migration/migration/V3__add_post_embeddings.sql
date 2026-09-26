CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE posts ADD COLUMN embedding vector(768);

-- індекс для швидкого пошуку найближчих сусідів за косинусною відстанню
CREATE INDEX IF NOT EXISTS idx_posts_embedding_cosine
    ON posts USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);