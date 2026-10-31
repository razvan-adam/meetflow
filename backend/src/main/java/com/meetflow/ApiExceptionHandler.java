package com.meetflow;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Answers every API error with RFC 9457 Problem Details (docs/conventions.md). Validation errors
 * also list the invalid fields, so forms can point at them. Rejected values are never echoed back:
 * they may be passwords.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  @Override
  protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    var errors =
        ex.getFieldErrors().stream()
            .map(
                error ->
                    Map.of(
                        "field",
                        error.getField(),
                        "message",
                        String.valueOf(error.getDefaultMessage())))
            .toList();
    ex.getBody().setProperty("errors", errors);
    return handleExceptionInternal(ex, ex.getBody(), headers, status, request);
  }

  /** Two people saved the same record at the same moment; the later save is rejected. */
  @ExceptionHandler(OptimisticLockingFailureException.class)
  ProblemDetail handleConcurrentEdit() {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "Someone else changed this at the same time. Reload and try again.");
  }
}
