package vn.muaythai.checkin;

import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.muaythai.shared.Actor;
import vn.muaythai.shared.ImageStore;

@RestController
@RequestMapping("/api/packages/types/{type}/image")
class PackageImagesApi {
  private static final Set<String> TYPES = Set.of("PT", "GROUP10", "GROUP30", "MEMBERSHIP");
  private final ImageStore images;

  PackageImagesApi(JdbcClient db, @Value("${app.images.max-pixels:24000000}") long pixels,
      @Value("${app.images.max-edge:1280}") int edge) {
    images = new ImageStore(db, "package_kind_images", pixels, edge);
  }

  private String target(Actor actor, String type, boolean writing) {
    if (writing) actor.require("ADMIN");
    if (!TYPES.contains(type)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return type;
  }

  @GetMapping
  ResponseEntity<byte[]> get(Actor actor, @PathVariable String type) {
    return images.get(target(actor, type, false));
  }

  @PutMapping(consumes = "multipart/form-data")
  @Transactional
  public Map<String, String> put(Actor actor, @PathVariable String type, @RequestPart MultipartFile file) {
    return images.put(target(actor, type, true), file);
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void delete(Actor actor, @PathVariable String type) {
    images.delete(target(actor, type, true));
  }
}
