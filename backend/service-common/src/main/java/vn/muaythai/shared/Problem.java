package vn.muaythai.shared;

import java.util.Map;
import org.springframework.http.HttpStatus;

public final class Problem extends RuntimeException {
  public final HttpStatus status;
  public final String code;
  public final Map<String, ?> details;

  public Problem(HttpStatus status, String code, String message, Map<String, ?> details) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }

  public static Problem invalid(String message) {
    return new Problem(HttpStatus.BAD_REQUEST, "INVALID_INPUT", message, Map.of());
  }

  public static Problem forbidden() {
    return new Problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này.", Map.of());
  }

  public static Problem missing(String message) {
    return new Problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message, Map.of());
  }

  public static Problem conflict(String code, String message) {
    return conflict(code, message, Map.of());
  }

  public static Problem conflict(String code, String message, Map<String, ?> details) {
    return new Problem(HttpStatus.CONFLICT, code, message, details);
  }

  static Problem dependency() {
    return new Problem(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
        "Dịch vụ cần thiết đang bận. Vui lòng thử lại.", Map.of());
  }
}
