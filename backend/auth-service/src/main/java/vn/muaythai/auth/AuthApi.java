package vn.muaythai.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
class AuthApi {
  record Login(@NotBlank @Size(max = 120) String username, @NotBlank @Size(max = 72) String password) {}

  record ChangePassword(
      @NotBlank @Size(max = 72) String currentPassword,
      @NotBlank @Size(min = 8, max = 72) String newPassword) {}

  record UpdateStatus(@NotNull Status status, @NotBlank @Size(max = 240) String reason) {}
  record ResetPassword(@NotBlank @Size(max = 240) String reason) {}
  record TemporaryPassword(String password) {}

  enum Status {
    ACTIVE,
    LOCKED
  }

  record User(long id, String username, String displayName, UserAccount.Role role, Status status, boolean canAdjustCheckins) {
    static User from(UserAccount account) {
      return new User(account.id, account.username, account.displayName, account.role,
          account.locked ? Status.LOCKED : Status.ACTIVE, account.canAdjustCheckins);
    }
  }

  record Session(String token, UUID sessionId, Instant expiresAt, User user, boolean mustChangePassword) {}

  private final AuthService auth;

  AuthApi(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/api/auth/login")
  Session login(@Valid @RequestBody Login input) {
    return auth.login(input);
  }

  @PostMapping("/api/auth/change-password")
  Session changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePassword input) {
    return auth.changePassword(jwt, input);
  }

  @PostMapping("/api/auth/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void logout(@AuthenticationPrincipal Jwt jwt) {
    auth.logout(jwt);
  }

  @GetMapping("/api/users/me")
  User me(@AuthenticationPrincipal Jwt jwt) {
    return auth.me(jwt);
  }

  @PatchMapping("/api/users/{id}/status")
  User status(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody UpdateStatus input) {
    return auth.updateStatus(jwt, id, input);
  }

  @PostMapping("/api/users/{id}/password-reset")
  ResponseEntity<TemporaryPassword> resetPassword(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
      @Valid @RequestBody ResetPassword input) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(new TemporaryPassword(auth.resetPassword(jwt, id, input.reason())));
  }
}
