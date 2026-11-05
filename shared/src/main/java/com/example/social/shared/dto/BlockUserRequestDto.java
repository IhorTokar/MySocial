package com.example.social.shared.dto;

import jakarta.validation.constraints.Size;

public record BlockUserRequestDto(@Size(max = 255) String reason) {
}