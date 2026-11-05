package com.example.social.shared.dto;

/** Кількість вузлів і ребер графа в Neo4j. */
public record GraphStatsDto(long userNodes, long followRelations) {}