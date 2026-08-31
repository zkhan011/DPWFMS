package com.dpworld.fms.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
  ResponseEntity<Map<String, Object>> invalidRequest(RuntimeException exception) {
    return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException exception) {
    String detail = exception.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + " " + error.getDefaultMessage()).findFirst()
        .orElse("request validation failed");
    return problem(HttpStatus.BAD_REQUEST, detail);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Map<String, Object>> forbidden(AccessDeniedException exception) {
    return problem(HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
  }

  private ResponseEntity<Map<String, Object>> problem(HttpStatus status, String detail) {
    return ResponseEntity.status(status).body(Map.of("status", status.value(), "title",
        status.getReasonPhrase(), "detail", detail == null ? status.getReasonPhrase() : detail,
        "timestamp", Instant.now()));
  }
}
