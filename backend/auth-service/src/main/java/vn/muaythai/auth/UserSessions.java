package vn.muaythai.auth;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface UserSessions extends JpaRepository<UserSession, UUID> {
  @Modifying
  @Query("update UserSession s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
  void revokeForUser(Long userId, Instant now);
}
