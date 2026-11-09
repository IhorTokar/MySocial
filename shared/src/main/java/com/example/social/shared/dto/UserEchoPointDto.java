package com.example.social.shared.dto;

import java.time.LocalDateTime;

/**
 * Точка траєкторії користувача: у якій спільноті він був на момент знімка і наскільки вона була ехо-камерою.
 *
 * @param networkEchoScore зважений echo-score всієї мережі в тому ж знімку — для порівняння
 */
public record UserEchoPointDto(long snapshotId, LocalDateTime createdAt,
                               long community, int memberCount,
                               double echoChamberScore, double networkEchoScore) {
}