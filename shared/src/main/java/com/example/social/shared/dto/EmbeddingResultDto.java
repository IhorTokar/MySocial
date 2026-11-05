package com.example.social.shared.dto;

/** Результат запуску node2vec. */
public record EmbeddingResultDto(long nodeCount, long nodePropertiesWritten) {}