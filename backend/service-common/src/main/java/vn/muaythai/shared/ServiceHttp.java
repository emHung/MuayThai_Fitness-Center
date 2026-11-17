package vn.muaythai.shared;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ServiceHttp {
  @FunctionalInterface
  public interface Request<T> {
    T execute(RestClient client);
  }

  private final JdkClientHttpRequestFactory factory;
  private final String key;

  ServiceHttp(@Value("${app.internal-key}") String key) {
    this.key = key;
    Duration timeout = Duration.ofSeconds(3);
    factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
    factory.setReadTimeout(timeout);
  }

  public <T> T to(String origin, Request<T> request) {
    try {
      return request.execute(RestClient.builder().baseUrl(origin).requestFactory(factory)
          .defaultHeader("X-Service-Key", key).build());
    } catch (RestClientResponseException error) {
      if (error.getStatusCode().value() == 404) {
        throw Problem.missing("Không tìm thấy dữ liệu liên quan.");
      }
      if (error.getStatusCode().value() == 409) {
        throw remoteConflict(error);
      }
      throw Problem.dependency();
    } catch (RestClientException error) {
      throw Problem.dependency();
    }
  }

  private static Problem remoteConflict(RestClientResponseException error) {
    try {
      Map<?, ?> body = error.getResponseBodyAs(Map.class);
      if (body != null && body.get("code") instanceof String code && body.get("message") instanceof String message) {
        return Problem.conflict(code, message);
      }
    } catch (RestClientException | IllegalStateException malformedResponse) {
      return Problem.dependency();
    }
    return Problem.dependency();
  }

  public record Profile(long id, String displayName, String role, String status, String memberCode) {}

  public Profile user(String auth, long id) {
    return to(auth, client -> client.get().uri("/internal/users/{id}", id).retrieve().body(Profile.class));
  }

  public void referenceTrainer(String auth, long id) {
    to(auth, client -> client.put().uri("/internal/users/{id}/trainer-use", id).retrieve().toBodilessEntity());
  }

  public record Notice(String id, String title, String message, String at, Long sessionId, String kind) {}

  public record Registration(boolean registered, Long packageId) {}

  public record Package(long id, long learnerId, String type, Long trainerId, Long classId,
      String startDate, String endDate, Integer remainingSessions) {}

  public record Session(long id, String type, String title, long trainerId, Long learnerId, Long classId,
      Long packageId, String startAt, String endAt, String status, int capacity, Long assignedTrainerId) {
    public long packageTrainerId() {
      return assignedTrainerId == null ? trainerId : assignedTrainerId;
    }
  }
}
