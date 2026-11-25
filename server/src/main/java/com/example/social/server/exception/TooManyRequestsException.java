package com.example.social.server.exception;

import org.springframework.http.HttpStatus;

/** 429 — забагато спроб за короткий час (наприклад, невдалих входів). */
public class TooManyRequestsException extends ApiException {
    public TooManyRequestsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}