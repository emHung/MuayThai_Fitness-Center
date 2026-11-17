package vn.muaythai.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_sessions")
class UserSession {
  @Id UUID id;
  Long userId;
  Instant expiresAt;
  Instant revokedAt;

  protected UserSession() {}

  UserSession(Long userId, Instant expiresAt) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.expiresAt = expiresAt;
  }

  boolean activeAt(Instant now) {
    return revokedAt == null && expiresAt.isAfter(now);
  }
}
