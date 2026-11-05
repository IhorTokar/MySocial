package com.example.social.shared.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

/** Нові ваги від адміністратора. Сума п'яти основних ваг має дорівнювати 1. */
public record WeightsUpdateRequestDto(
        @DecimalMin("0.0") @DecimalMax("1.0") double popularity,
        @DecimalMin("0.0") @DecimalMax("1.0") double itemCf,
        @DecimalMin("0.0") @DecimalMax("1.0") double graphSim,
        @DecimalMin("0.0") @DecimalMax("1.0") double semantic,
        @DecimalMin("0.0") @DecimalMax("1.0") double recency,
        @DecimalMin("0.0") @DecimalMax("1.0") double followedBonus,
        @Size(max = 255) String comment) {
}