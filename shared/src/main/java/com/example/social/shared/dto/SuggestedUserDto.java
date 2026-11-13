package com.example.social.shared.dto;

/**
 * Кого почитати: користувач, на якого поточний ще не підписаний.
 *
 * @param reason чому запропоновано: «Схожі інтереси», «Читають 2 ваші підписки», «Популярний автор»
 */
public record SuggestedUserDto(long userId, String username, String displayName, String avatarUrl,
                               long followersCount, String reason) {
}