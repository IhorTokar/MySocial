package com.example.social.server.service;

import java.util.Collection;
import java.util.Map;

/** Граф підписок у Neo4j з погляду аналізу спільнот: синхронізація, виявлення спільнот, їхній склад. */
public interface CommunityGraph {

    /** Перенести користувачів і підписки з PostgreSQL у Neo4j. */
    void sync();

    /** Заново визначити спільноти (Louvain або Leiden) і записати їх у вузли. */
    void detect(boolean leiden);

    /** userId → номер спільноти з останнього визначення. */
    Map<Long, Long> labels(boolean leiden);

    /**
     * Спільноти лише серед заданих користувачів (за підписками між ними), без запису у вузли:
     * решта графа не впливає на результат, а спільноти основної мережі не змінюються.
     */
    Map<Long, Long> detectWithin(boolean leiden, Collection<Long> userIds);
}