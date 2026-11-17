package vn.muaythai.schedule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.muaythai.shared.Actor;
import vn.muaythai.shared.ServiceHttp;

@RestController
class ScheduleApi {
  record ClassForm(
      @NotBlank @Size(max = 120) String title,
      Long trainerId,
      @NotEmpty @Size(max = 7) List<@Min(1) @Max(7) Integer> weekdays,
      @NotNull LocalTime startsAt,
      @NotNull LocalTime endsAt) {}

  record ClassInfo(long id, String title, long trainerId, String type, String status,
      List<Integer> weekdays, String startsAt, String endsAt) {}

  record Booking(@Positive long packageId, @NotNull OffsetDateTime startAt) {}
  record DirectPt(@Positive long learnerId, @Positive long packageId, Long trainerId,
      @NotNull OffsetDateTime startAt, @Min(1) @Max(12) int weeks) {}
  record Reason(@NotBlank @Size(max = 240) String reason) {}
  record Substitute(@Positive long trainerId, @NotBlank @Size(max = 240) String reason) {}
  record TrainerSummary(long id, String displayName) {}
  record Signup(@Positive long packageId) {}
  record MoveSignup(@Positive long targetSessionId, @Positive long packageId) {}
  record WalkIn(@Positive long learnerId, @Positive long packageId) {}

  private final ScheduleService schedule;
  private final SessionMutations mutations;

  ScheduleApi(ScheduleService schedule, SessionMutations mutations) {
    this.schedule = schedule;
    this.mutations = mutations;
  }

  @GetMapping("/api/{kind:classes30|classes10}")
  List<ClassInfo> classes(Actor actor, @PathVariable String kind,
      @RequestParam(defaultValue = "") @Size(max = 120) String q) {
    return schedule.classes(actor, type(kind), q.strip());
  }

  @GetMapping("/api/{kind:classes30|classes10}/{id}")
  ClassInfo classDetail(Actor actor, @PathVariable String kind, @PathVariable long id) {
    return schedule.classDetail(actor, type(kind), id);
  }

  @PostMapping("/api/{kind:classes30|classes10}")
  @ResponseStatus(HttpStatus.CREATED)
  ClassInfo createClass(Actor actor, @PathVariable String kind, @Valid @RequestBody ClassForm form) {
    return schedule.createClass(actor, type(kind), form);
  }

  @PutMapping("/api/{kind:classes30|classes10}/{id}")
  ClassInfo editClass(Actor actor, @PathVariable String kind, @PathVariable long id,
      @Valid @RequestBody ClassForm form) {
    return schedule.editClass(actor, type(kind), id, form);
  }

  @PostMapping("/api/{kind:classes30|classes10}/{id}/{action:stop|approve|reject}")
  ClassInfo changeClass(Actor actor, @PathVariable String kind, @PathVariable long id, @PathVariable String action) {
    return schedule.changeClass(actor, type(kind), id, action);
  }

  @GetMapping("/api/sessions")
  List<ServiceHttp.Session> sessions(Actor actor,
      @RequestParam(required = false) OffsetDateTime from, @RequestParam(required = false) OffsetDateTime to,
      @RequestParam(defaultValue = "") @Size(max = 120) String q) {
    return schedule.sessions(actor, from, to, q.strip());
  }

  @GetMapping("/api/sessions/{id}")
  ServiceHttp.Session session(Actor actor, @PathVariable long id) {
    return schedule.visibleSession(actor, id);
  }

  @GetMapping("/api/pt/availability")
  Map<String, Boolean> availability(Actor actor, @RequestParam long packageId, @RequestParam OffsetDateTime startAt) {
    return Map.of("available", schedule.available(actor, packageId, startAt));
  }

  @PostMapping("/api/pt/bookings")
  @ResponseStatus(HttpStatus.CREATED)
  ServiceHttp.Session book(Actor actor, @Valid @RequestBody Booking form) {
    return schedule.book(actor, form);
  }

  @PostMapping("/api/pt/direct")
  @ResponseStatus(HttpStatus.CREATED)
  List<ServiceHttp.Session> direct(Actor actor, @Valid @RequestBody DirectPt form) {
    return schedule.direct(actor, form);
  }

  @GetMapping("/api/pt/requests")
  List<ServiceHttp.Session> requests(Actor actor) {
    return schedule.requests(actor);
  }

  @PostMapping("/api/pt/{id}/confirm")
  ServiceHttp.Session approvePt(Actor actor, @PathVariable long id) {
    return schedule.decide(actor, id, true, null);
  }

  @PostMapping("/api/pt/{id}/reject")
  ServiceHttp.Session rejectPt(Actor actor, @PathVariable long id, @Valid @RequestBody Reason reason) {
    return schedule.decide(actor, id, false, reason.reason());
  }

  @PostMapping("/api/sessions/{id}/cancel")
  ServiceHttp.Session cancel(Actor actor, @PathVariable long id, @Valid @RequestBody Reason reason) {
    return mutations.cancel(actor, id, reason.reason());
  }

  @PutMapping("/api/sessions/{id}/trainer")
  ServiceHttp.Session substitute(Actor actor, @PathVariable long id, @Valid @RequestBody Substitute form) {
    return mutations.substitute(actor, id, form.trainerId(), form.reason());
  }

  @GetMapping("/api/sessions/{id}/trainer")
  TrainerSummary trainer(Actor actor, @PathVariable long id) {
    return schedule.trainer(actor, id);
  }

  @PutMapping("/api/sessions/{id}/signup")
  ServiceHttp.Session signup(Actor actor, @PathVariable long id, @Valid @RequestBody Signup signup) {
    return schedule.signup(actor, id, signup.packageId());
  }

  @PostMapping("/api/sessions/{id}/move-signup")
  ServiceHttp.Session move(Actor actor, @PathVariable long id, @Valid @RequestBody MoveSignup input) {
    return schedule.moveSignup(actor, id, input.targetSessionId(), input.packageId());
  }

  @PutMapping("/api/classes10/{id}/fixed/{learnerId}")
  List<ServiceHttp.Session> fixedClass(Actor actor, @PathVariable long id, @PathVariable long learnerId,
      @Valid @RequestBody Signup input) {
    return schedule.fixedClass(actor, id, learnerId, input.packageId());
  }

  @PostMapping("/internal/sessions/{id}/walk-in")
  ServiceHttp.Session walkIn(@PathVariable long id, @Valid @RequestBody WalkIn input) {
    return schedule.walkIn(id, input.learnerId(), input.packageId());
  }

  @DeleteMapping("/api/sessions/{id}/signup")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void withdraw(Actor actor, @PathVariable long id) {
    schedule.withdraw(actor, id);
  }

  @GetMapping("/internal/classes/{id}")
  ClassInfo internalClass(@PathVariable long id) {
    return schedule.classInfo(id);
  }

  @GetMapping("/internal/sessions/{id}")
  ServiceHttp.Session internalSession(@PathVariable long id) {
    return schedule.findSession(id);
  }

  @GetMapping("/internal/sessions/{id}/attendance")
  ServiceHttp.Session forAttendance(@PathVariable long id) {
    return mutations.forAttendance(id);
  }

  @GetMapping("/internal/sessions/eligible")
  List<ServiceHttp.Session> eligible(@RequestParam long learnerId, @RequestParam long packageId,
      @RequestParam String type, @RequestParam(required = false) Long classId,
      @RequestParam(required = false) Long trainerId) {
    return schedule.eligible(learnerId, packageId, type, classId, trainerId);
  }

  @GetMapping("/internal/sessions/{id}/signup")
  ServiceHttp.Registration registered(@PathVariable long id, @RequestParam long learnerId) {
    return schedule.registration(id, learnerId);
  }

  private static String type(String kind) {
    return "classes30".equals(kind) ? "GROUP30" : "GROUP10";
  }
}
