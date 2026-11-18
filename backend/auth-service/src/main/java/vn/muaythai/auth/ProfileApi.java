package vn.muaythai.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
class ProfileApi {
  record Create(
      @NotBlank @Email @Size(max = 120) String username,
      @NotBlank @Size(max = 120) String displayName,
      @Size(max = 32) String phone,
      @Size(max = 200) String specialty,
      @Size(min = 8, max = 72, message = "Mật khẩu tạm phải từ 8 đến 72 ký tự.") String temporaryPassword) {}

  record Edit(
      @NotBlank @Size(max = 120) String displayName,
      @Size(max = 32) String phone,
      @Size(max = 200) String specialty) {}

  record Profile(long id, String username, String displayName, String role, String status,
      String memberCode, String phone, String specialty) {}

  record Page(List<Profile> items, int page, int size, long total) {}

  record Created(Profile profile, String initialPassword) {}

  private final ProfileService profiles;
  private final AuthService auth;
  private final TrainerDirectory trainers;

  ProfileApi(ProfileService profiles, AuthService auth, TrainerDirectory trainers) {
    this.profiles = profiles;
    this.auth = auth;
    this.trainers = trainers;
  }

  @GetMapping("/api/{directory:learners|trainers|receptionists}")
  Page list(@AuthenticationPrincipal Jwt jwt, @PathVariable String directory,
      @RequestParam(defaultValue = "") @Size(max = 120) String q,
      @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return profiles.list(auth.me(jwt), role(directory), q, page, size);
  }

  @GetMapping("/api/{directory:learners|trainers|receptionists}/{id}")
  Profile detail(@AuthenticationPrincipal Jwt jwt, @PathVariable String directory, @PathVariable long id) {
    return profiles.detail(auth.me(jwt), role(directory), id);
  }

  @PostMapping("/api/{directory:learners|trainers|receptionists}")
  @ResponseStatus(HttpStatus.CREATED)
  Created create(@AuthenticationPrincipal Jwt jwt, @PathVariable String directory, @Valid @RequestBody Create form) {
    return profiles.create(auth.me(jwt), role(directory), form);
  }

  @PutMapping("/api/{directory:learners|trainers}/{id}")
  Profile edit(@AuthenticationPrincipal Jwt jwt, @PathVariable String directory, @PathVariable long id,
      @Valid @RequestBody Edit form) {
    return profiles.edit(auth.me(jwt), role(directory), id, form);
  }

  @DeleteMapping("/api/trainers/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteTrainer(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    trainers.remove(auth.me(jwt), id);
  }

  @DeleteMapping("/api/{directory:learners|receptionists}/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteAccount(@AuthenticationPrincipal Jwt jwt, @PathVariable String directory, @PathVariable long id) {
    profiles.removeAccount(auth.me(jwt), role(directory), id);
  }

  @PutMapping("/internal/users/{id}/trainer-use")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void useTrainer(@RequestHeader("X-Service-Key") String key, @PathVariable long id) {
    profiles.requireInternal(key);
    trainers.markInUse(id);
  }

  @GetMapping("/api/users/me/profile")
  Profile self(@AuthenticationPrincipal Jwt jwt) {
    AuthApi.User actor = auth.me(jwt);
    return profiles.internalUserForSelf(actor);
  }

  @PutMapping("/api/users/me")
  Profile editSelf(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Edit form) {
    return profiles.editSelf(auth.me(jwt), form);
  }

  @GetMapping("/internal/users/{id}")
  Profile internalUser(@RequestHeader("X-Service-Key") String key, @PathVariable long id) {
    return profiles.internalUser(key, id);
  }

  @GetMapping("/internal/users/by-member-code")
  Profile internalMember(@RequestHeader("X-Service-Key") String key, @RequestParam String code) {
    return profiles.internalMember(key, code);
  }

  private static UserAccount.Role role(String directory) {
    return switch (directory) {
      case "learners" -> UserAccount.Role.LEARNER;
      case "trainers" -> UserAccount.Role.TRAINER;
      case "receptionists" -> UserAccount.Role.RECEPTION;
      default -> throw new IllegalArgumentException("Unknown directory");
    };
  }
}
