package vn.muaythai.shared;

import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class ErrorResponses {
  @ExceptionHandler(Problem.class)
  ResponseEntity<Map<String, ?>> problem(Problem error) {
    return ResponseEntity.status(error.status)
        .body(Map.of("code", error.code, "message", error.getMessage(),
            "requestId", UUID.randomUUID().toString(), "details", error.details));
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
      MethodArgumentTypeMismatchException.class, ConstraintViolationException.class})
  ResponseEntity<Map<String, ?>> invalid() {
    return problem(Problem.invalid("Thông tin gửi lên không hợp lệ."));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Map<String, ?>> conflict() {
    return problem(Problem.conflict("DATA_CONFLICT", "Dữ liệu vừa thay đổi hoặc đã tồn tại. Vui lòng tải lại."));
  }
}
