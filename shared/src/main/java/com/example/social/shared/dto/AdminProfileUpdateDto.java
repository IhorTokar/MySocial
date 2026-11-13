package com.example.social.shared.dto;

import jakarta.validation.constraints.Size;

/**
 * Зміна профілю адміністратором. null — поле не змінюється, порожній рядок — поле очищується.
 * removeAvatar / removeCover = true прибирає аватар / обкладинку (наприклад, неприйнятне зображення).
 */
public record AdminProfileUpdateDto(@Size(max = 100) String displayName,
                                    @Size(max = 2000) String aboutMe,
                                    boolean removeAvatar,
                                    boolean removeCover) {
}