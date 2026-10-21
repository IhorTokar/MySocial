package com.example.social.server.exception;

import org.springframework.http.HttpStatus;

/** 404 — об'єкт не знайдено. */
public class NotFoundException extends ApiException {
    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}