package com.haeni.carrot.settle.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handle(BusinessException e) {
    return ResponseEntity.status(e.getStatus()).body(ErrorResponse.of(e.getErrorCode()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handle(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .findFirst()
            .orElse(ErrorCode.INVALID_INPUT.getMessage());
    return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_INPUT, message));
  }
}