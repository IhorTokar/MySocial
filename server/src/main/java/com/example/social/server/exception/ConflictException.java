package com.example.social.server.exception;

import org.springframework.http.HttpStatus;

/** 409 — конфлікт зі станом даних (вже існує, ще не підготовлено). */
public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}