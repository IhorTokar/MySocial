package com.example.social.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Планові знімки історії ехо-камер за розкладом echo-history.cron ("-" — вимкнено). */
@Component
public class EchoHistoryScheduler {

    private static final Logger log = LoggerFactory.getLogger(EchoHistoryScheduler.class);

    private final EchoHistoryService echoHistoryService;

    public EchoHistoryScheduler(EchoHistoryService echoHistoryService) {
        this.echoHistoryService = echoHistoryService;
    }

    @Scheduled(cron = "${echo-history.cron:-}", zone = "Europe/Kyiv")
    public void snapshotBothAlgorithms() {
        for (boolean leiden : new boolean[]{false, true}) {
            String name = leiden ? "Leiden" : "Louvain";
            try {
                echoHistoryService.takeScheduledSnapshot(leiden)
                        .ifPresent(s -> log.info("Плановий знімок {} #{} збережено", name, s.snapshotId()));
            } catch (Exception e) {
                // напр., Neo4j недоступний — наступна спроба за розкладом
                log.warn("Плановий знімок {} не вдався: {}", name, e.getMessage());
            }
        }
    }
}