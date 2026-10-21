package com.example.social.server.exception;

import java.time.Instant;

/** {"status": 404, "error": "Post not found: 5", "path": "/api/posts/5", "timestamp": "..."} */
public record ErrorResponse(int status, String error, String path, Instant timestamp) {

    public static ErrorResponse of(int status, String error, String path) {
        return new ErrorResponse(status, error, path, Instant.now());
    }
}