package vn.muaythai.auth;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class InternalServices {
  private final JdkClientHttpRequestFactory factory;
  private final String key;

  InternalServices(@Value("${app.internal-key:}") String key) {
    this.key = key;
    Duration timeout = Duration.ofSeconds(3);
    factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
    factory.setReadTimeout(timeout);
  }

  RestClient client(String uri) {
    return RestClient.builder().baseUrl(uri).requestFactory(factory)
        .defaultHeader("X-Service-Key", key).build();
  }

  void requireKey(String presented) {
    if (key.isEmpty() || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),
        presented.getBytes(StandardCharsets.UTF_8))) {
      throw new ApiError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Yêu cầu nội bộ không hợp lệ.");
    }
  }
}
