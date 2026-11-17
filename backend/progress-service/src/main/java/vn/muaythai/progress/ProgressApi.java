package vn.muaythai.progress;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import vn.muaythai.shared.Actor;

@RestController
class ProgressApi {
  enum GoalStatus { NOT_STARTED, IN_PROGRESS, ACHIEVED, NOT_ACHIEVED }
  record NoteForm(@NotBlank @Size(max = 3000) String content, @Size(max = 3000) String comment,
      @Min(1) @Max(5) Integer rating) {}
  record Note(long id, long sessionId, Long learnerId, long trainerId, String trainerName, String sessionTitle,
      String sessionAt, String content, String comment, Integer rating, String updatedAt) {}

  record GoalForm(@NotBlank @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])") String month,
      @NotBlank @Size(max = 160) String title, @NotBlank @Size(max = 2000) String description,
      @Min(0) @Max(100) int progress, GoalStatus status) {}
  record Goal(long id, long learnerId, long trainerId, String month, String title, String description,
      int progress, GoalStatus status, String updatedAt) {}

  private final ProgressService progress;

  ProgressApi(ProgressService progress) {
    this.progress = progress;
  }

  @GetMapping("/internal/trainers/{id}/has-history")
  boolean hasHistory(@PathVariable long id) {
    return progress.hasTrainerHistory(id);
  }

  @GetMapping("/api/notes/session/{id}")
  Note one(Actor actor, @PathVariable long id) {
    return progress.detail(actor, id);
  }

  @PutMapping("/api/notes/session/{id}")
  Note save(Actor actor, @PathVariable long id, @Valid @RequestBody NoteForm form) {
    return progress.save(actor, id, form);
  }

  @GetMapping("/api/notes")
  List<Note> history(Actor actor, @RequestParam(required = false) Long learnerId) {
    return progress.history(actor, learnerId);
  }

  @GetMapping("/api/goals")
  List<Goal> goals(Actor actor, @RequestParam(required = false) Long learnerId) {
    return progress.goals(actor, learnerId);
  }

  @PutMapping("/api/goals/{learnerId}")
  Goal goal(Actor actor, @PathVariable long learnerId, @Valid @RequestBody GoalForm form) {
    return progress.saveGoal(actor, learnerId, form);
  }
}
