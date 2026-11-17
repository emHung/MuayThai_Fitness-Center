package vn.muaythai.checkin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.muaythai.shared.Actor;
import vn.muaythai.shared.ServiceHttp;

@RestController
class AttendanceApi {
  enum CodeKind { QR, PACKAGE_CODE, MEMBER_CODE }
  enum Method { QR, CODE_ENTRY, MANUAL }
  record PreviewForm(@NotBlank @Size(max = 120) String code, @NotNull CodeKind codeKind, Long packageId) {}
  record Preview(PackagesApi.Pack pack, ServiceHttp.Session session, List<PackagesApi.Pack> choices) {}
  record Confirm(Long sessionId, @Positive long packageId, @NotNull Method method) {}
  record Manual(@Positive long sessionId, @Positive long packageId, @NotBlank @Size(max = 240) String reason) {}
  record AdjustmentForm(@NotBlank @Size(max = 240) String reason) {}
  record Entry(long id, Long sessionId, long packageId, long learnerId, Long trainerId, String learnerName,
      String sessionTitle, String checkedInAt, long actorId, String method, String reason, String status,
      Integer remainingAfterEvent, UUID requestKey) {}
  record Adjustment(long id, long checkinId, long actorId, String reason, String adjustedAt,
      Integer remainingBefore, Integer remainingAfter, UUID requestKey) {}

  private final AttendanceService attendance;

  AttendanceApi(AttendanceService attendance) {
    this.attendance = attendance;
  }

  @PostMapping("/api/checkins/preview")
  Preview preview(Actor actor, @Valid @RequestBody PreviewForm form) {
    actor.require(actor.canCheckInAtDesk());
    return attendance.preview(form);
  }

  @PostMapping("/api/checkins/confirm")
  @ResponseStatus(HttpStatus.CREATED)
  Entry confirm(Actor actor, @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody Confirm form) {
    actor.require(actor.canCheckInAtDesk());
    if (form.method() == Method.MANUAL) {
      throw vn.muaythai.shared.Problem.invalid("Dùng chức năng điểm danh thủ công và nhập lý do.");
    }
    return attendance.record(actor, key, form.sessionId(), form.packageId(), form.method(), null);
  }

  @PostMapping("/api/checkins/manual")
  @ResponseStatus(HttpStatus.CREATED)
  Entry manual(Actor actor, @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody Manual form) {
    actor.require(actor.is("ADMIN") || actor.is("TRAINER"));
    return attendance.record(actor, key, form.sessionId(), form.packageId(), Method.MANUAL, form.reason().strip());
  }

  @GetMapping("/api/checkins")
  List<Entry> list(Actor actor, @RequestParam(required = false) Long learnerId,
      @RequestParam(required = false) Long sessionId, @RequestParam(required = false) LocalDate date) {
    return attendance.list(actor, learnerId, sessionId, date);
  }

  @GetMapping("/api/checkins/{id}")
  Entry detail(Actor actor, @PathVariable long id) {
    return attendance.detail(actor, id);
  }

  @GetMapping("/api/checkins/{id}/adjustments")
  List<Adjustment> adjustments(Actor actor, @PathVariable long id) {
    attendance.detail(actor, id);
    return attendance.adjustments(id);
  }

  @PostMapping("/api/checkins/{id}/adjustments")
  @ResponseStatus(HttpStatus.CREATED)
  Adjustment adjust(Actor actor, @PathVariable long id, @RequestHeader("Idempotency-Key") UUID key,
      @Valid @RequestBody AdjustmentForm form) {
    actor.require(actor.is("ADMIN") && actor.canAdjustCheckins());
    return attendance.adjust(actor, id, key, form.reason().strip());
  }

  @GetMapping("/internal/checkins/exists")
  Map<String, Boolean> exists(@RequestParam long sessionId) {
    return Map.of("exists", attendance.exists(sessionId));
  }
}
