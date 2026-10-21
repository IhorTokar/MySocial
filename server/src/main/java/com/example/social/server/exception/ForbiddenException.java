package com.example.social.server.exception;

import org.springframework.http.HttpStatus;

/** 403 — дія заборонена цьому користувачу. */
public class ForbiddenException extends ApiException {
    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}