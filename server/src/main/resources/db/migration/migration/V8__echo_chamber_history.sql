-- Історія показника ехо-камер. Знімок = результат аналізу всіх спільнот на певний момент.
-- Номери спільнот Louvain/Leiden між запусками не стабільні, тому зберігається склад кожної спільноти
-- (member_ids): за ним спільноти зіставляються між знімками і будується траєкторія користувача.
CREATE TABLE echo_snapshot (
    snapshot_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    algorithm           VARCHAR(10) NOT NULL CHECK (algorithm IN ('louvain', 'leiden')),
    source              VARCHAR(10) NOT NULL CHECK (source IN ('manual', 'scheduled')),
    community_count     INT NOT NULL,
    user_count          INT NOT NULL,
    weighted_echo_score DOUBLE PRECISION NOT NULL,   -- середнє по спільнотах, зважене за кількістю учасників
    max_echo_score      DOUBLE PRECISION NOT NULL,
    created_by          BIGINT REFERENCES "user"(user_id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_echo_snapshot_algorithm ON echo_snapshot (algorithm, snapshot_id DESC);

CREATE TABLE echo_snapshot_community (
    snapshot_id           BIGINT NOT NULL REFERENCES echo_snapshot(snapshot_id) ON DELETE CASCADE,
    community             BIGINT NOT NULL,
    member_count          INT NOT NULL,
    post_count            INT NOT NULL,
    topic_homogeneity     DOUBLE PRECISION NOT NULL,
    semantic_cohesion     DOUBLE PRECISION,
    avg_sentiment         DOUBLE PRECISION NOT NULL,
    sentiment_homogeneity DOUBLE PRECISION NOT NULL,
    echo_chamber_score    DOUBLE PRECISION NOT NULL,
    member_ids            BIGINT[] NOT NULL,
    PRIMARY KEY (snapshot_id, community)
);

-- «У якій спільноті був користувач»: member_ids @> ARRAY[id]
CREATE INDEX idx_echo_snapshot_community_members ON echo_snapshot_community USING GIN (member_ids);