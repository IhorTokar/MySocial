package com.example.social.shared.dto;

import java.time.LocalDateTime;

/**
 * Версія вагових коефіцієнтів стрічки.
 * id == null означає ваги за замовчуванням з application.yml (у базі ще немає жодної версії).
 */
public record RecommendationWeightsDto(Long id,
                                       double popularity, double itemCf, double graphSim,
                                       double semantic, double recency, double followedBonus,
                                       String comment, String createdBy, LocalDateTime createdAt) {
}