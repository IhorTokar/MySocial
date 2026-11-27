package com.example.social.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Плановий перерахунок векторів node2vec за розкладом graph-embeddings.cron ("-" — вимкнено).
 * Спершу граф підписок у Neo4j синхронізується з PostgreSQL (нові й скасовані підписки),
 * потім node2vec перераховує вектори користувачів — так «близькість у графі» в стрічці
 * враховує нові підписки без участі адміністратора.
 */
@Component
public class GraphEmbeddingScheduler {

    private static final Logger log = LoggerFactory.getLogger(GraphEmbeddingScheduler.class);

    private final GraphSyncService graphSyncService;
    private final GraphEmbeddingService graphEmbeddingService;
    private final int dimension;

    public GraphEmbeddingScheduler(GraphSyncService graphSyncService,
                                   GraphEmbeddingService graphEmbeddingService,
                                   @Value("${graph-embeddings.dimension:16}") int dimension) {
        this.graphSyncService = graphSyncService;
        this.graphEmbeddingService = graphEmbeddingService;
        this.dimension = dimension;
    }

    @Scheduled(cron = "${graph-embeddings.cron:-}", zone = "Europe/Kyiv")
    public void recompute() {
        try {
            graphSyncService.syncAll();
            log.info("Плановий перерахунок node2vec: {}", graphEmbeddingService.generateEmbeddings(dimension));
        } catch (Exception e) {
            // напр., Neo4j недоступний — наступна спроба за розкладом, стрічка працює зі старими векторами
            log.warn("Плановий перерахунок node2vec не вдався: {}", e.getMessage());
        }
    }
}
