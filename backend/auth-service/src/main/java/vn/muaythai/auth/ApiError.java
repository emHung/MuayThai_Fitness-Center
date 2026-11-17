package vn.muaythai.auth;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

final class ApiError extends RuntimeException {
  final HttpStatus status;
  final String code;

  ApiError(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

@RestControllerAdvice
class ApiErrorHandler {
  record Body(String code, String message, UUID requestId) {}

  @ExceptionHandler(ApiError.class)
  ResponseEntity<Body> handle(ApiError error) {
    return ResponseEntity.status(error.status)
        .body(new Body(error.code, error.getMessage(), UUID.randomUUID()));
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
  ResponseEntity<Body> invalidInput() {
    return handle(new ApiError(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Thông tin gửi lên không hợp lệ."));
  }
}
