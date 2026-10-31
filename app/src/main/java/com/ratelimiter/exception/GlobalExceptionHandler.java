package com.ratelimiter.exception;

import com.ratelimiter.admin.exception.ConflictException;
import com.ratelimiter.admin.exception.ResourceNotFoundException;
import com.ratelimiter.common.dto.ErrorResponseDto;
import com.ratelimiter.common.exception.RateLimiterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.MissingRequestValueException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponseDto> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), Map.of());
    }

    // any other RateLimiterException is treated as a bad request
    @ExceptionHandler(RateLimiterException.class)
    public ResponseEntity<ErrorResponseDto> handleBadRequest(RateLimiterException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(WebExchangeBindException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", fields);
    }

    @ExceptionHandler(MissingRequestValueException.class)
    public ResponseEntity<ErrorResponseDto> handleMissingParameter(MissingRequestValueException ex) {
        return build(HttpStatus.BAD_REQUEST, "Missing required parameter '" + ex.getName() + "'", Map.of());
    }

    // everything else WebFlux rejects while reading the request: bad JSON, empty body, wrong parameter type
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ErrorResponseDto> handleUnreadableInput(ServerWebInputException ex) {
        if (ex.getCause() instanceof TypeMismatchException) {
            MethodParameter parameter = ex.getMethodParameter();
            String name = parameter != null && parameter.getParameterName() != null
                    ? parameter.getParameterName() : "request";
            return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + name + "'", Map.of());
        }
        return build(HttpStatus.BAD_REQUEST, "Malformed or missing request body", Map.of());
    }

    // Spring's own HTTP errors (unknown path 404, wrong method 405, wrong content type 415) keep their
    // status; without this the catch-all below would turn them into 500s. More specific handlers
    // above (ServerWebInputException) still win for their own types.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponseDto> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return build(status, status.getReasonPhrase(), Map.of());
    }

    // last resort: log the stack trace, never leak the exception message to the caller
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", Map.of());
    }

    private ResponseEntity<ErrorResponseDto> build(HttpStatus status, String message, Map<String, String> fields) {
        ErrorResponseDto body = new ErrorResponseDto(
                Instant.now(), status.value(), status.getReasonPhrase(), message, fields);
        return ResponseEntity.status(status).body(body);
    }
}
