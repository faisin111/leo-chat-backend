package com.example.chat_app_backend.advice;

import com.example.chat_app_backend.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<ApiError> buildError(HttpStatus status, String code, String message, HttpServletRequest req, List<ApiError.Field> errors) {
        String traceId = UUID.randomUUID().toString(); // Ideally from MDC context
        ApiError error = new ApiError(Instant.now(), status.value(), code, message, req.getRequestURI(), traceId, errors);
        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<ApiError.Field> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.Field(f.getField(), f.getDefaultMessage()))
                .collect(Collectors.toList());
        return buildError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", req, errors);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException e, HttpServletRequest req) {
        return buildError(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid username or password", req, List.of());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException e, HttpServletRequest req) {
        return buildError(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage(), req, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleSpringAccessDenied(AccessDeniedException e, HttpServletRequest req) {
        return buildError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", req, List.of());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException e, HttpServletRequest req) {
        return buildError(HttpStatus.FORBIDDEN, "FORBIDDEN", e.getMessage(), req, List.of());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException e, HttpServletRequest req) {
        return buildError(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage(), req, List.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException e, HttpServletRequest req) {
        return buildError(HttpStatus.CONFLICT, "CONFLICT", e.getMessage(), req, List.of());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException e, HttpServletRequest req) {
        return buildError(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE", e.getMessage(), req, List.of());
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiError> handleRateLimit(RateLimitException e, HttpServletRequest req) {
        return buildError(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", e.getMessage(), req, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e, HttpServletRequest req) {
        log.error("Unexpected error", e);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", req, List.of());
    }
}
