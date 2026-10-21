package com.example.social.server.exception;

import org.springframework.http.HttpStatus;

/** Базовий клас для очікуваних помилок: кожна знає свій HTTP-статус. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}