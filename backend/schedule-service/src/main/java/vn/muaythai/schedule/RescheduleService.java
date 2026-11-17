package vn.muaythai.schedule;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.muaythai.shared.Actor;
import vn.muaythai.shared.Problem;
import vn.muaythai.shared.ServiceHttp.Session;

@Service
class RescheduleService {
  private static final RowMapper<RescheduleApi.View> ROW = (rs, row) -> new RescheduleApi.View(
      rs.getLong("id"), rs.getLong("session_id"), rs.getLong("requested_by"), rs.getLong("recipient_id"),
      rs.getObject("proposed_start", OffsetDateTime.class).toString(),
      rs.getObject("expires_at", OffsetDateTime.class).toString(), rs.getString("status"));
  private final ScheduleRepository repository;
  private final ShiftService shifts;

  RescheduleService(ScheduleRepository repository, ShiftService shifts) {
    this.repository = repository;
    this.shifts = shifts;
  }

  @Transactional
  public List<RescheduleApi.View> list(Actor actor) {
    expire();
    return repository.jdbc().sql("""
        SELECT * FROM pt_changes WHERE :admin OR requested_by=:id OR recipient_id=:id
        ORDER BY created_at DESC LIMIT 150
        """).param("id", actor.id()).param("admin", actor.is("ADMIN")).query(ROW).list();
  }

  @Transactional
  public RescheduleApi.View propose(Actor actor, long id, OffsetDateTime start) {
    Session snapshot = repository.session(id, false);
    shifts.lock(snapshot.trainerId());
    if (snapshot.learnerId() != null) {
      repository.lockLearner(snapshot.learnerId());
    }
    Session session = repository.session(id, true);
    requireChangeable(session);
    long recipient;
    if (actor.is("TRAINER") && actor.id() == session.trainerId() || actor.is("ADMIN")) {
      recipient = session.learnerId();
    } else {
      actor.require(actor.is("LEARNER") && Objects.equals(session.learnerId(), actor.id()));
      recipient = session.trainerId();
    }
    expire();
    validateNewTime(session, start);
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime oldStart = OffsetDateTime.parse(session.startAt());
    OffsetDateTime expire = oldStart.isBefore(now.plusHours(24)) ? oldStart : now.plusHours(24);
    try {
      return repository.jdbc().sql("""
          INSERT INTO pt_changes(session_id,requested_by,recipient_id,proposed_start,expires_at,status)
          VALUES(:session,:author,:recipient,:start,:expire,'PENDING') RETURNING *
          """).param("session", id).param("author", actor.id()).param("recipient", recipient).param("start", start)
          .param("expire", expire).query(ROW).single();
    } catch (DataIntegrityViolationException conflict) {
      throw Problem.conflict("CHANGE_PENDING", "Lịch này đã có yêu cầu đổi đang chờ.");
    }
  }

  @Transactional
  public RescheduleApi.View decide(Actor actor, long id, boolean accept) {
    RescheduleApi.View snapshot = find(id, false);
    actor.require(actor.id() == snapshot.recipientId());
    Session old = repository.session(snapshot.sessionId(), false);
    shifts.lock(old.trainerId());
    if (old.learnerId() != null) {
      repository.lockLearner(old.learnerId());
    }
    Session session = repository.session(snapshot.sessionId(), true);
    RescheduleApi.View change = find(id, true);
    if (!change.status().equals("PENDING") || !OffsetDateTime.parse(change.expiresAt()).isAfter(OffsetDateTime.now())) {
      throw Problem.conflict("CHANGE_CLOSED", "Yêu cầu đổi đã hết hạn hoặc đã được xử lý.");
    }
    requireChangeable(session);
    if (accept) {
      OffsetDateTime start = OffsetDateTime.parse(change.proposedStart());
      OffsetDateTime end = validateNewTime(session, start);
      repository.jdbc().sql("UPDATE sessions SET start_at=:start,end_at=:end WHERE id=:id")
          .param("id", session.id()).param("start", start).param("end", end).update();
    }
    return repository.jdbc().sql("UPDATE pt_changes SET status=:status WHERE id=:id RETURNING *")
        .param("id", id).param("status", accept ? "ACCEPTED" : "REJECTED").query(ROW).single();
  }

  private RescheduleApi.View find(long id, boolean lock) {
    return repository.jdbc().sql("SELECT * FROM pt_changes WHERE id=:id" + (lock ? " FOR UPDATE" : ""))
        .param("id", id).query(ROW).optional().orElseThrow(() -> Problem.missing("Không tìm thấy yêu cầu đổi lịch."));
  }

  private void expire() {
    repository.jdbc().sql("UPDATE pt_changes SET status='EXPIRED' WHERE status='PENDING' AND expires_at<=now()").update();
  }

  private static void requireChangeable(Session s) {
    if (!s.type().equals("PT") || !s.status().equals("CONFIRMED")
        || !OffsetDateTime.parse(s.startAt()).isAfter(OffsetDateTime.now().plusMinutes(30))) {
      throw Problem.conflict("SESSION_CLOSED", "Chỉ đổi lịch PT đã xác nhận trước khi mở điểm danh.");
    }
  }

  private OffsetDateTime validateNewTime(Session session, OffsetDateTime start) {
    if (!start.isAfter(OffsetDateTime.now())) {
      throw Problem.invalid("Ngày giờ mới phải ở tương lai.");
    }
    Duration duration = Duration.between(OffsetDateTime.parse(session.startAt()), OffsetDateTime.parse(session.endAt()));
    OffsetDateTime end = start.plus(duration);
    shifts.requireOpen(session.trainerId(), start, end, false);
    boolean conflict = repository.jdbc().sql("""
        SELECT EXISTS (SELECT FROM sessions s WHERE id<>:id AND start_at<:end AND end_at>:start
          AND status IN ('PENDING_TRAINER','CONFIRMED') AND (trainer_id=:trainer OR learner_id=:learner
            OR EXISTS(SELECT FROM class_signups r WHERE r.session_id=s.id AND r.learner_id=:learner AND NOT r.cancelled)))
        """).param("id", session.id()).param("start", start).param("end", end).param("trainer", session.trainerId())
        .param("learner", session.learnerId()).query(Boolean.class).single();
    if (conflict) {
      throw Problem.conflict("SCHEDULE_CONFLICT", "Khung giờ mới đang bị trùng lịch.");
    }
    return end;
  }
}
