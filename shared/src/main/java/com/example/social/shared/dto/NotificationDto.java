package com.example.social.shared.dto;

import java.time.LocalDateTime;

/**
 * Сповіщення в шапці клієнта.
 *
 * @param type    follow — новий читач, like — вподобання поста, comment — коментар до поста,
 *                reply — відповідь на мій коментар
 * @param postId  пост, якого стосується (null для follow)
 * @param snippet заголовок / початок поста або текст коментаря
 * @param unread  новіше за момент, коли користувач востаннє відкривав сповіщення
 */
public record NotificationDto(String type, long actorId, String actorUsername, String actorDisplayName,
                              String actorAvatarUrl, Long postId, String snippet,
                              LocalDateTime createdAt, boolean unread) {
}