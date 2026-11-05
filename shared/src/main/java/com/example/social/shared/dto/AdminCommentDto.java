package com.example.social.shared.dto;

import java.time.LocalDateTime;

/** Коментар для адмін-панелі: з ID поста і кількістю прямих відповідей (видаляться разом з ним). */
public record AdminCommentDto(long commentId, long postId, long authorId, String authorUsername,
                              String text, LocalDateTime createdAt, Long parentCommentId,
                              long repliesCount) {
}