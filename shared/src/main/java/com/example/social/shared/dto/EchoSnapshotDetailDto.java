package com.example.social.shared.dto;

import java.util.List;

/**
 * Знімок з усіма спільнотами та їх зіставленням з попереднім знімком того ж алгоритму.
 *
 * @param previousSnapshotId попередній знімок, з яким порівнювались спільноти; null — це перший знімок
 */
public record EchoSnapshotDetailDto(EchoSnapshotDto snapshot, Long previousSnapshotId, List<Community> communities) {

    /**
     * @param previousCommunity номер «тієї ж» спільноти в попередньому знімку (найбільший збіг складу);
     *                          null — спільнота нова або змінилась занадто сильно
     * @param overlap           збіг складу з previousCommunity за Жаккаром, 0..1
     * @param scoreDelta        зміна echo-score відносно previousCommunity
     */
    public record Community(long community, int memberCount, int postCount,
                            double topicHomogeneity, Double semanticCohesion,
                            double avgSentiment, double sentimentHomogeneity, double echoChamberScore,
                            Long previousCommunity, Double overlap, Double scoreDelta) {
    }
}