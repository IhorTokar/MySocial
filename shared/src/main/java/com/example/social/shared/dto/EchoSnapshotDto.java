package com.example.social.shared.dto;

import java.time.LocalDateTime;

/**
 * Знімок стану ехо-камер мережі на певний момент.
 *
 * @param algorithm         louvain або leiden
 * @param source            manual (запустив адміністратор) або scheduled (планувальник)
 * @param weightedEchoScore середній echo-score спільнот, зважений за кількістю учасників
 * @param createdBy         логін адміністратора; null для планових знімків
 */
public record EchoSnapshotDto(long snapshotId, String algorithm, String source,
                              int communityCount, int userCount,
                              double weightedEchoScore, double maxEchoScore,
                              String createdBy, LocalDateTime createdAt) {
}