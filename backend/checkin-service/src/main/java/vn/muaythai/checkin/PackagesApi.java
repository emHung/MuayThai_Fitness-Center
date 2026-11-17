package vn.muaythai.checkin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.muaythai.shared.Actor;

@RestController
class PackagesApi {
  record TrainerChange(@Positive long trainerId, @NotBlank @Size(max = 240) String reason) {}
  enum Type { PT, GROUP10, GROUP30, MEMBERSHIP }

  record Form(@Positive long learnerId, @NotNull Type type, Long trainerId, Long classId,
      @NotNull LocalDate startDate, @NotNull LocalDate endDate,
      @Min(1) @Max(500) Integer totalSessions) {}

  record Pack(long id, UUID code, long learnerId, String learnerName, String memberCode, String type,
      Long trainerId, Long classId, String startDate, String endDate, Integer totalSessions, Integer remainingSessions) {}

  private final PackageService packages;

  PackagesApi(PackageService packages) {
    this.packages = packages;
  }

  @PostMapping("/api/packages")
  @ResponseStatus(HttpStatus.CREATED)
  Pack create(Actor actor, @Valid @RequestBody Form form) {
    actor.require("ADMIN");
    return packages.create(form);
  }

  @GetMapping("/api/packages")
  List<Pack> forLearner(Actor actor, @RequestParam long learnerId) {
    return packages.visible(actor, learnerId);
  }

  @GetMapping("/api/packages/for-session")
  List<Pack> forSession(Actor actor, @RequestParam long sessionId) {
    return packages.forSession(actor, sessionId);
  }

  @PutMapping("/api/packages/{id}/trainer")
  Pack changeTrainer(Actor actor, @PathVariable long id, @Valid @RequestBody TrainerChange form) {
    actor.require("ADMIN");
    return packages.changeTrainer(actor.id(), id, form);
  }

  @GetMapping("/api/packages/{id}")
  Pack one(Actor actor, @PathVariable long id) {
    actor.require("ADMIN");
    return packages.find(id, false);
  }

  @GetMapping("/api/packages/mine")
  List<Pack> mine(Actor actor) {
    actor.require("LEARNER");
    return packages.list(actor.id());
  }

  @GetMapping("/api/packages/mine/{id}")
  Pack mineDetail(Actor actor, @PathVariable long id) {
    actor.require("LEARNER");
    Pack pack = packages.find(id, false);
    actor.require(pack.learnerId() == actor.id());
    return pack;
  }

  @GetMapping("/internal/packages")
  List<Pack> internalList(@RequestParam long learnerId) {
    return packages.list(learnerId);
  }

  @GetMapping("/internal/packages/{id}")
  Pack internalPackage(@PathVariable long id) {
    return packages.find(id, false);
  }

  @GetMapping("/internal/assignments")
  List<Long> assignments(@RequestParam long trainerId) {
    return packages.assignments(trainerId);
  }

  @GetMapping("/internal/trainers/{id}/has-history")
  boolean hasHistory(@PathVariable long id) {
    return packages.hasTrainerHistory(id);
  }
}
