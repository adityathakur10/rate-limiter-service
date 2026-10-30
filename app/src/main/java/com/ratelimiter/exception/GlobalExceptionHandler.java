package com.ratelimiter.exception;

import com.ratelimiter.admin_service.exception.ConflictException;
import com.ratelimiter.admin_service.exception.ResourceNotFoundException;
import com.ratelimiter.common.dto.ErrorResponseDto;
import com.ratelimiter.common.exception.RateLimiterException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.MissingRequestValueException;
import org.springframework.web.server.ServerWebInputException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

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

    private ResponseEntity<ErrorResponseDto> build(HttpStatus status, String message, Map<String, String> fields) {
        ErrorResponseDto body = new ErrorResponseDto(
                Instant.now(), status.value(), status.getReasonPhrase(), message, fields);
        return ResponseEntity.status(status).body(body);
    }
}
