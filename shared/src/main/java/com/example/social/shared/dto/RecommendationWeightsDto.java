package com.example.social.shared.dto;

import java.time.LocalDateTime;

/**
 * Версія вагових коефіцієнтів стрічки.
 * id == null означає ваги за замовчуванням з application.yml (у базі ще немає жодної версії).
 * outsideShare — частка постів з інших спільнот у стрічці (0 — вимкнено).
 */
public record RecommendationWeightsDto(Long id,
                                       double popularity, double itemCf, double graphSim,
                                       double semantic, double recency, double followedBonus,
                                       double mmrLambda, double outsideShare,
                                       String comment, String createdBy, LocalDateTime createdAt) {
}