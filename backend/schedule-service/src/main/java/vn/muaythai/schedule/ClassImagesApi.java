package vn.muaythai.schedule;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.muaythai.shared.Actor;
import vn.muaythai.shared.ImageStore;

@RestController
@RequestMapping("/api/{kind:classes10|classes30}/{id}/image")
class ClassImagesApi {
  private final ScheduleService schedule;
  private final ImageStore images;

  ClassImagesApi(ScheduleService schedule, JdbcClient db,
      @Value("${app.images.max-pixels:24000000}") long pixels,
      @Value("${app.images.max-edge:1280}") int edge) {
    this.schedule = schedule;
    images = new ImageStore(db, "class_images", pixels, edge);
  }

  private long target(Actor actor, String kind, long id, boolean writing) {
    if (writing) actor.require("ADMIN");
    return schedule.classDetail(actor, kind.equals("classes10") ? "GROUP10" : "GROUP30", id).id();
  }

  @GetMapping
  ResponseEntity<byte[]> get(Actor actor, @PathVariable String kind, @PathVariable long id) {
    return images.get(target(actor, kind, id, false));
  }

  @PutMapping(consumes = "multipart/form-data")
  @Transactional
  public Map<String, String> put(Actor actor, @PathVariable String kind, @PathVariable long id,
      @RequestPart MultipartFile file) {
    return images.put(target(actor, kind, id, true), file);
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void delete(Actor actor, @PathVariable String kind, @PathVariable long id) {
    images.delete(target(actor, kind, id, true));
  }
}
