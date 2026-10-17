package com.example.chat_app_backend.security.jwt;

import com.example.chat_app_backend.advice.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.stereotype.Component;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException, ServletException {
    response.setContentType("application/json");
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);

    String traceId = UUID.randomUUID().toString();
    boolean csrfFailure =
        accessDeniedException instanceof InvalidCsrfTokenException
            || accessDeniedException instanceof MissingCsrfTokenException;
    ApiError apiError =
        new ApiError(
            Instant.now(),
            HttpServletResponse.SC_FORBIDDEN,
            csrfFailure ? "CSRF_INVALID" : "FORBIDDEN",
            csrfFailure
                ? "CSRF token is missing or invalid; refresh /api/v1/auth/csrf and retry"
                : "Access is denied",
            request.getRequestURI(),
            traceId,
            List.of());

    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.writeValue(response.getOutputStream(), apiError);
  }
}
