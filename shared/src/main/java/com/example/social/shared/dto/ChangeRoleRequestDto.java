package com.example.social.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeRoleRequestDto(
        @NotBlank @Pattern(regexp = "admin|user|guest", message = "Роль має бути admin, user або guest")
        String role) {
}