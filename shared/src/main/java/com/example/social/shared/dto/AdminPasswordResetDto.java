package com.example.social.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminPasswordResetDto(@NotBlank @Size(min = 8, max = 100) String newPassword) {
}