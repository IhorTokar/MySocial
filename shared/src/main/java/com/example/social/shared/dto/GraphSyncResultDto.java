package com.example.social.shared.dto;

/** Результат синхронізації графа підписок PostgreSQL → Neo4j. */
public record GraphSyncResultDto(int syncedUsers, int syncedRelations) {}