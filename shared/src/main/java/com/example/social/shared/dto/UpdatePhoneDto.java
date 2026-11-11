package com.example.social.shared.dto;

import jakarta.validation.constraints.Size;

/** Новий номер телефону; порожній рядок або null — видалити номер. */
public record UpdatePhoneDto(@Size(max = 30) String phone) {
}