package vn.muaythai.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthService {
  private final UserAccounts users;
  private final UserSessions sessions;
  private final PasswordEncoder passwords;
  private final JwtEncoder tokens;
  private final Clock clock;
  private final JdbcClient jdbc;
  private final String issuer;
  private final Duration lifetime;

  AuthService(UserAccounts users, UserSessions sessions, PasswordEncoder passwords, JwtEncoder tokens,
      Clock clock, JdbcClient jdbc, @Value("${app.jwt.issuer}") String issuer,
      @Value("${app.jwt.lifetime}") Duration lifetime) {
    this.users = users;
    this.sessions = sessions;
    this.passwords = passwords;
    this.tokens = tokens;
    this.clock = clock;
    this.jdbc = jdbc;
    this.issuer = issuer;
    this.lifetime = lifetime;
  }

  @Transactional
  public AuthApi.Session login(AuthApi.Login input) {
    String username = input.username().trim().toLowerCase(Locale.ROOT);
    UserAccount user = users.lockByUsername(username).orElseThrow(AuthService::badCredentials);
    verifyPassword(input.password(), user);
    requireUnlocked(user);
    return createSession(user);
  }

  @Transactional(readOnly = true)
  public AuthApi.User me(Jwt jwt) {
    UserAccount user = users.findById(userId(jwt)).orElseThrow(AuthService::expiredSession);
    requireReady(jwt, user);
    return AuthApi.User.from(user);
  }

  @Transactional
  public AuthApi.Session changePassword(Jwt jwt, AuthApi.ChangePassword input) {
    UserAccount user = lockCurrentUser(jwt);
    requireActiveSession(jwt, user);
    verifyPassword(input.currentPassword(), user);
    if (tooLongForBcrypt(input.newPassword()) || passwords.matches(input.newPassword(), user.passwordHash)) {
      throw new ApiError(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
          "Mật khẩu mới phải khác mật khẩu hiện tại và dài tối đa 72 byte.");
    }
    user.passwordHash = passwords.encode(input.newPassword());
    user.mustChangePassword = false;
    sessions.revokeForUser(user.id, clock.instant());
    return createSession(user);
  }

  @Transactional
  public void logout(Jwt jwt) {
    UserAccount user = lockCurrentUser(jwt);
    requireActiveSession(jwt, user).revokedAt = clock.instant();
  }

  @Transactional
  public AuthApi.User updateStatus(Jwt jwt, long targetId, AuthApi.UpdateStatus input) {
    UserAccount admin = users.findById(userId(jwt)).orElseThrow(AuthService::expiredSession);
    requireReady(jwt, admin);
    if (admin.role != UserAccount.Role.ADMIN) {
      throw new ApiError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền đổi trạng thái tài khoản.");
    }
    UserAccount target = users.lockById(targetId).orElseThrow(() -> new ApiError(
        HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Không tìm thấy tài khoản."));
    if (target.deletedAt != null) {
      throw new ApiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Không tìm thấy tài khoản.");
    }
    boolean locked = input.status() == AuthApi.Status.LOCKED;
    if (target.locked != locked) {
      target.locked = locked;
      Instant now = clock.instant();
      if (locked) {
        sessions.revokeForUser(target.id, now);
      }
      jdbc.sql("insert into account_status_events (user_id, actor_id, locked, reason, recorded_at) values (?, ?, ?, ?, ?)")
          .params(target.id, admin.id, locked, input.reason().trim(), java.sql.Timestamp.from(now)).update();
    }
    return AuthApi.User.from(target);
  }

  private UserAccount lockCurrentUser(Jwt jwt) {
    return users.lockById(userId(jwt)).orElseThrow(AuthService::expiredSession);
  }

  private void requireReady(Jwt jwt, UserAccount user) {
    requireActiveSession(jwt, user);
    if (user.mustChangePassword) {
      throw new ApiError(HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
          "Bạn cần đổi mật khẩu tạm trước.");
    }
  }

  private UserSession requireActiveSession(Jwt jwt, UserAccount user) {
    requireUnlocked(user);
    return sessions.findById(UUID.fromString(jwt.getId()))
        .filter(session -> session.userId.equals(user.id) && session.activeAt(clock.instant()))
        .orElseThrow(AuthService::expiredSession);
  }

  private void requireUnlocked(UserAccount user) {
    if (user.deletedAt != null) {
      throw expiredSession();
    }
    if (user.locked) {
      throw new ApiError(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", "Tài khoản đã bị khóa.");
    }
  }

  private AuthApi.Session createSession(UserAccount user) {
    Instant now = clock.instant();
    UserSession session = sessions.save(new UserSession(user.id, now.plus(lifetime)));
    JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(user.id.toString())
        .id(session.id.toString()).issuedAt(now).expiresAt(session.expiresAt).build();
    String token = tokens.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
    return new AuthApi.Session(token, session.id, session.expiresAt, AuthApi.User.from(user), user.mustChangePassword);
  }

  private void verifyPassword(String password, UserAccount user) {
    if (tooLongForBcrypt(password) || !passwords.matches(password, user.passwordHash)) {
      throw badCredentials();
    }
  }

  private static long userId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }

  private static boolean tooLongForBcrypt(String value) {
    return value.getBytes(StandardCharsets.UTF_8).length > 72;
  }

  private static ApiError badCredentials() {
    return new ApiError(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu chưa đúng.");
  }

  private static ApiError expiredSession() {
    return new ApiError(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "Phiên đăng nhập đã hết hiệu lực.");
  }
}
