package com.example.chat_app_backend.advice;

import com.example.chat_app_backend.exception.BusinessRuleException;
import com.example.chat_app_backend.exception.ConflictException;
import com.example.chat_app_backend.exception.ForbiddenException;
import com.example.chat_app_backend.exception.NotFoundException;
import com.example.chat_app_backend.exception.RateLimitException;
import com.example.chat_app_backend.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler({
    org.springframework.web.servlet.resource.NoResourceFoundException.class,
    org.springframework.web.servlet.NoHandlerFoundException.class
  })
  public ResponseEntity<ApiError> handleNoResourceFound(Exception e, HttpServletRequest req) {
    return buildError(
        HttpStatus.NOT_FOUND,
        ERR_NOT_FOUND,
        "The requested resource was not found.",
        req,
        List.of());
  }

  private static final String ERR_VALIDATION = "VALIDATION_ERROR";
  private static final String MSG_VALIDATION = "Request validation failed";
  private static final String ERR_UNAUTHORIZED = "UNAUTHORIZED";
  private static final String MSG_BAD_CREDS = "Invalid username or password";
  private static final String ERR_FORBIDDEN = "FORBIDDEN";
  private static final String MSG_ACCESS_DENIED = "Access denied";
  private static final String ERR_NOT_FOUND = "NOT_FOUND";
  private static final String ERR_CONFLICT = "CONFLICT";
  private static final String ERR_BUSINESS_RULE = "BUSINESS_RULE";
  private static final String ERR_RATE_LIMITED = "RATE_LIMITED";
  private static final String ERR_INTERNAL = "INTERNAL_ERROR";
  private static final String MSG_INTERNAL = "Unexpected server error";
  private static final String LOG_UNEXPECTED = "Unexpected error";

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private ResponseEntity<ApiError> buildError(
      HttpStatus status,
      String code,
      String message,
      HttpServletRequest req,
      List<ApiError.Field> errors) {
    String traceId = UUID.randomUUID().toString(); // Ideally from MDC context
    ApiError error =
        new ApiError(
            Instant.now(), status.value(), code, message, req.getRequestURI(), traceId, errors);
    return ResponseEntity.status(status).body(error);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(
      MethodArgumentNotValidException e, HttpServletRequest req) {
    List<ApiError.Field> errors =
        e.getBindingResult().getFieldErrors().stream()
            .map(f -> new ApiError.Field(f.getField(), f.getDefaultMessage()))
            .collect(Collectors.toList());
    return buildError(HttpStatus.BAD_REQUEST, ERR_VALIDATION, MSG_VALIDATION, req, errors);
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ApiError> handleBadCredentials(
      BadCredentialsException e, HttpServletRequest req) {
    return buildError(HttpStatus.UNAUTHORIZED, ERR_UNAUTHORIZED, MSG_BAD_CREDS, req, List.of());
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<ApiError> handleUnauthorized(
      UnauthorizedException e, HttpServletRequest req) {
    return buildError(HttpStatus.UNAUTHORIZED, ERR_UNAUTHORIZED, e.getMessage(), req, List.of());
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> handleSpringAccessDenied(
      AccessDeniedException e, HttpServletRequest req) {
    return buildError(HttpStatus.FORBIDDEN, ERR_FORBIDDEN, MSG_ACCESS_DENIED, req, List.of());
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ApiError> handleForbidden(ForbiddenException e, HttpServletRequest req) {
    return buildError(HttpStatus.FORBIDDEN, ERR_FORBIDDEN, e.getMessage(), req, List.of());
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(NotFoundException e, HttpServletRequest req) {
    return buildError(HttpStatus.NOT_FOUND, ERR_NOT_FOUND, e.getMessage(), req, List.of());
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ApiError> handleConflict(ConflictException e, HttpServletRequest req) {
    return buildError(HttpStatus.CONFLICT, ERR_CONFLICT, e.getMessage(), req, List.of());
  }

  @ExceptionHandler(BusinessRuleException.class)
  public ResponseEntity<ApiError> handleBusinessRule(
      BusinessRuleException e, HttpServletRequest req) {
    return buildError(
        HttpStatus.UNPROCESSABLE_ENTITY, ERR_BUSINESS_RULE, e.getMessage(), req, List.of());
  }

  @ExceptionHandler(RateLimitException.class)
  public ResponseEntity<ApiError> handleRateLimit(RateLimitException e, HttpServletRequest req) {
    return buildError(
        HttpStatus.TOO_MANY_REQUESTS, ERR_RATE_LIMITED, e.getMessage(), req, List.of());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception e, HttpServletRequest req) {
    log.error(LOG_UNEXPECTED, e);
    return buildError(HttpStatus.INTERNAL_SERVER_ERROR, ERR_INTERNAL, MSG_INTERNAL, req, List.of());
  }
}
