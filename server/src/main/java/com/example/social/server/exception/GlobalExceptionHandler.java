package com.example.social.server.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Наші винятки: статус береться з самого винятку. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException e, HttpServletRequest request) {
        return build(e.getStatus(), e.getMessage(), request);
    }

    /** Перевірки вхідних даних, які сервіси позначають IllegalArgumentException. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    /** Брак прав. */
    @ExceptionHandler({SecurityException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponse> handleForbidden(RuntimeException e, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, e.getMessage() != null ? e.getMessage() : "Недостатньо прав", request);
    }

    /** Neo4j не запущено. */
    @ExceptionHandler(org.neo4j.driver.exceptions.ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleNeo4jUnavailable(RuntimeException e, HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE,
                "Neo4j недоступний — перевірте, чи запущено контейнер (docker compose up -d)", request);
    }

    /** Усе непередбачене: деталі лише в лог, клієнту — загальне повідомлення. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Неочікувана помилка під час {} {}", request.getMethod(), request.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Внутрішня помилка сервера", request);
    }

    /** Помилки @Valid: "username: must not be blank; password: ...". */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (message.isEmpty()) {
            message = "Некоректні дані запиту";
        }
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), message, pathOf(request)));
    }

    /** Стандартні помилки Spring (неправильний JSON, невідома адреса, завеликий файл) — у нашому форматі. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             @Nullable Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        String message = (body instanceof ProblemDetail pd && pd.getDetail() != null)
                ? pd.getDetail()
                : ex.getMessage();
        return ResponseEntity.status(statusCode)
                .headers(headers)
                .body(ErrorResponse.of(statusCode.value(), message, pathOf(request)));
    }

    private ResponseEntity<ErrorResponse> build(HttpStatusCode status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), message, request.getRequestURI()));
    }

    private static String pathOf(WebRequest request) {
        return request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : null;
    }
}