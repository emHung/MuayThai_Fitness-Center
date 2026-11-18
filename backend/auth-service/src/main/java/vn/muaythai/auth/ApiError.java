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

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Body> invalidFields(MethodArgumentNotValidException error) {
    String message = error.getBindingResult().getFieldErrors("temporaryPassword").isEmpty()
        ? "Kiểm tra thông tin đã nhập và các trường bắt buộc."
        : "Mật khẩu tạm phải từ 8 đến 72 ký tự, tối đa 72 byte UTF-8.";
    return handle(new ApiError(HttpStatus.BAD_REQUEST, "INVALID_INPUT", message));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Body> invalidInput() {
    return handle(new ApiError(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Thông tin gửi lên không hợp lệ."));
  }
}
