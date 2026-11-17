package vn.muaythai.schedule;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import vn.muaythai.shared.ServiceHttp.Session;

@RestController
class ParticipantsApi {
  private final ScheduleRepository repository;

  ParticipantsApi(ScheduleRepository repository) {
    this.repository = repository;
  }

  @GetMapping("/internal/trainers/{id}/has-history")
  boolean hasHistory(@PathVariable long id) {
    return repository.jdbc().sql("""
        SELECT EXISTS (SELECT FROM classes WHERE trainer_id=:id)
            OR EXISTS (SELECT FROM sessions WHERE trainer_id=:id)
            OR EXISTS (SELECT FROM trainer_shifts WHERE trainer_id=:id)
            OR EXISTS (SELECT FROM trainer_blocks WHERE trainer_id=:id)
            OR EXISTS (SELECT FROM session_trainer_changes WHERE previous_trainer_id=:id OR new_trainer_id=:id)
        """).param("id", id).query(Boolean.class).single();
  }

  @GetMapping("/internal/sessions/{id}/participants")
  List<Long> participants(@PathVariable long id) {
    Session session = repository.session(id, false);
    if (session.type().equals("PT")) {
      return List.of(session.learnerId());
    }
    return repository.jdbc().sql("SELECT learner_id FROM class_signups WHERE session_id=:id AND NOT cancelled")
        .param("id", id).query(Long.class).list();
  }

  @GetMapping("/internal/sessions/{id}/registered-packages")
  List<Long> registeredPackages(@PathVariable long id) {
    return repository.jdbc().sql("SELECT package_id FROM class_signups WHERE session_id=:id AND NOT cancelled")
        .param("id", id).query(Long.class).list();
  }
}
