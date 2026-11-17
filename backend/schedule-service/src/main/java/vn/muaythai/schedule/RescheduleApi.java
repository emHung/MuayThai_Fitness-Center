package vn.muaythai.schedule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.muaythai.shared.Actor;

@RestController
class RescheduleApi {
  record Change(@NotNull OffsetDateTime proposedStart) {}
  record View(long id, long sessionId, long requestedBy, long recipientId, String proposedStart,
      String expiresAt, String status) {}

  private final RescheduleService changes;

  RescheduleApi(RescheduleService changes) {
    this.changes = changes;
  }

  @GetMapping("/api/pt/changes")
  List<View> list(Actor actor) {
    return changes.list(actor);
  }

  @PostMapping("/api/pt/{id}/changes")
  @ResponseStatus(HttpStatus.CREATED)
  View propose(Actor actor, @PathVariable long id, @Valid @RequestBody Change change) {
    return changes.propose(actor, id, change.proposedStart());
  }

  @PostMapping("/api/pt/changes/{id}/{action:accept|reject}")
  View decide(Actor actor, @PathVariable long id, @PathVariable String action) {
    return changes.decide(actor, id, action.equals("accept"));
  }
}
