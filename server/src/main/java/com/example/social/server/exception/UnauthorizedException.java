package com.example.social.server.exception;

import org.springframework.http.HttpStatus;

/** 401 — неправильний логін або пароль. */
public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}