package vn.muaythai.auth;

import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
class TrainerDirectory {
  private record State(boolean removed, boolean used) {}

  private final JdbcClient db;
  private final UserSessions sessions;
  private final Clock clock;
  private final List<RestClient> owners;

  TrainerDirectory(JdbcClient db, UserSessions sessions, Clock clock, InternalServices internal,
      @Value("${app.schedule-uri:http://schedule:8080}") String schedule,
      @Value("${app.checkin-uri:http://checkin:8080}") String checkin,
      @Value("${app.progress-uri:http://progress:8080}") String progress) {
    this.db = db;
    this.sessions = sessions;
    this.clock = clock;
    owners = List.of(internal.client(schedule), internal.client(checkin), internal.client(progress));
  }

  @Transactional
  public void remove(AuthApi.User actor, long id) {
    ProfileService.requireAdmin(actor);
    State state = db.sql("""
        SELECT deleted_at IS NOT NULL, trainer_used_at IS NOT NULL
        FROM users WHERE id=:id AND role='TRAINER' FOR UPDATE
        """).param("id", id).query((rs, row) -> new State(rs.getBoolean(1), rs.getBoolean(2)))
        .optional().orElseThrow(ProfileService::notFound);
    if (state.removed()) {
      return;
    }
    if (state.used() || hasLegacyHistory(id)) {
      throw new ApiError(HttpStatus.CONFLICT, "TRAINER_HAS_DATA",
          "HLV đã có dữ liệu nghiệp vụ. Bạn có thể khóa tài khoản để giữ lịch sử.");
    }
    db.sql("""
        UPDATE users SET deleted_at=now(), locked=true, username='deleted-trainer:' || id
        WHERE id=:id
        """).param("id", id).update();
    sessions.revokeForUser(id, clock.instant());
  }

  void markInUse(long id) {
    // Record the intent before a different service commits its first reference to this account.
    int changed = db.sql("""
        UPDATE users SET trainer_used_at=coalesce(trainer_used_at, now())
        WHERE id=:id AND role='TRAINER' AND deleted_at IS NULL
        """).param("id", id).update();
    if (changed != 1) {
      throw ProfileService.notFound();
    }
  }

  private boolean hasLegacyHistory(long id) {
    try {
      return owners.stream().anyMatch(owner -> {
        Boolean history = owner.get().uri("/internal/trainers/{id}/has-history", id)
            .retrieve().body(Boolean.class);
        if (history == null) {
          throw new RestClientException("Missing trainer history result");
        }
        return history;
      });
    } catch (RestClientException unavailable) {
      throw new ApiError(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
          "Chưa kiểm tra được dữ liệu HLV. Vui lòng thử lại.");
    }
  }
}
