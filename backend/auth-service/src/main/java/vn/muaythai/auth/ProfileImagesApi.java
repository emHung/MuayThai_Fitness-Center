package vn.muaythai.auth;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.muaythai.shared.ImageStore;

@RestController
class ProfileImagesApi {
  private final AuthService auth;
  private final ProfileService profiles;
  private final JdbcClient db;
  private final ImageStore images;

  ProfileImagesApi(AuthService auth, ProfileService profiles, JdbcClient db,
      @Value("${app.images.max-pixels:24000000}") long pixels,
      @Value("${app.images.max-edge:1280}") int edge) {
    this.auth = auth;
    this.profiles = profiles;
    this.db = db;
    images = new ImageStore(db, "profile_images", pixels, edge);
  }

  private long target(Jwt jwt, String id, boolean writing) {
    var actor = auth.me(jwt);
    if (id.equals("me") || id.equals(Long.toString(actor.id()))) {
      return actor.id();
    }
    long userId;
    try { userId = Long.parseLong(id); }
    catch (NumberFormatException error) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
    if (writing && actor.role() != UserAccount.Role.ADMIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    var role = db.sql("SELECT role FROM users WHERE id=:id AND deleted_at IS NULL")
        .param("id", userId).query(String.class).optional()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    profiles.detail(actor, UserAccount.Role.valueOf(role), userId);
    return userId;
  }

  @GetMapping("/api/users/{id}/image")
  ResponseEntity<byte[]> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
    return images.get(target(jwt, id, false));
  }

  @PutMapping(value = "/api/users/{id}/image", consumes = "multipart/form-data")
  @Transactional
  public Map<String, String> put(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
      @RequestPart MultipartFile file) {
    return images.put(target(jwt, id, true), file);
  }

  @DeleteMapping("/api/users/{id}/image")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
    images.delete(target(jwt, id, true));
  }
}
