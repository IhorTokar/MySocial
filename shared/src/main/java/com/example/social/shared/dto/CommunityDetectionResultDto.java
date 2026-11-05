package com.example.social.shared.dto;

/** Результат запуску Louvain або Leiden. algorithm — властивість вузла, куди записано номер спільноти. */
public record CommunityDetectionResultDto(String algorithm, long communityCount,
                                          double modularity, long nodePropertiesWritten) {}