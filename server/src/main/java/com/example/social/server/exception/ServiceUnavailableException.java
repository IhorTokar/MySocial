package com.example.social.server.exception;

import org.springframework.http.HttpStatus;

/** 503 — недоступний зовнішній сервіс (NLP). */
public class ServiceUnavailableException extends ApiException {
    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}