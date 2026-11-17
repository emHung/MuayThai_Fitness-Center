package vn.muaythai.schedule;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.muaythai.shared.Actor;
import vn.muaythai.shared.Problem;
import vn.muaythai.shared.ServiceHttp;
import vn.muaythai.shared.ServiceHttp.Session;

@Service
class SessionMutations {
  private static final Logger log = LoggerFactory.getLogger(SessionMutations.class);
  private static final Duration LEARNER_CANCEL_NOTICE = Duration.ofHours(12);
  private record Pending(UUID id, long actorId, String reason, Long trainerId) {}
  private record Attendance(boolean exists) {}

  private final ScheduleRepository repo;
  private final ServiceHttp http;
  private final ShiftService shifts;
  private final String checkin;
  private final String auth;
  private final TransactionTemplate tx;

  SessionMutations(ScheduleRepository repo, ServiceHttp http, ShiftService shifts, PlatformTransactionManager manager,
      @Value("${app.checkin-uri}") String checkin, @Value("${app.auth-uri}") String auth) {
    this.repo = repo;
    this.http = http;
    this.shifts = shifts;
    this.checkin = checkin;
    this.auth = auth;
    tx = new TransactionTemplate(manager);
  }

  Session cancel(Actor actor, long id, String reason) {
    return submit(actor, id, reason.strip(), null);
  }

  Session substitute(Actor actor, long id, long trainer, String reason) {
    actor.require("ADMIN");
    return submit(actor, id, reason.strip(), trainer);
  }

  private Session submit(Actor actor, long id, String reason, Long trainer) {
    Pending pending = tx.execute(status -> {
      Session session = repo.readSession(id, true);
      if (trainer == null) {
        requireCancellation(actor, session);
      } else {
        requireSubstitutable(session);
      }
      Pending existing = pending(id);
      if (existing != null) {
        if (existing.actorId() == actor.id() && existing.reason().equals(reason)
            && Objects.equals(existing.trainerId(), trainer)) {
          return existing;
        }
        throw ScheduleRepository.mutationInProgress();
      }
      UUID key = UUID.randomUUID();
      repo.jdbc().sql("""
          UPDATE sessions SET mutation_id=:key, mutation_actor=:actor, mutation_reason=:reason,
            mutation_trainer=:trainer WHERE id=:id
          """).param("id", id).param("key", key).param("actor", actor.id()).param("reason", reason)
          .param("trainer", trainer).update();
      return new Pending(key, actor.id(), reason, trainer);
    });
    return finish(id, pending);
  }

  Session forAttendance(long id) {
    return repo.sessionForAttendance(id);
  }

  private Session finish(long id, Pending request) {
    // The committed flag blocks new attendance. Check-in waits for any confirmation already holding its lock.
    Attendance existing = http.to(checkin, client -> client.get().uri("/internal/checkins/exists?sessionId={id}", id)
        .retrieve().body(Attendance.class));
    if (existing == null) {
      throw new Problem(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
          "Chưa kiểm tra được điểm danh của buổi.", Map.of());
    }
    Session result;
    try {
      result = tx.execute(status -> apply(id, request, existing.exists()));
    } catch (Problem rejected) {
      if (!rejected.status.is5xxServerError()) {
        clear(id, request);
      }
      throw rejected;
    } catch (DataIntegrityViolationException conflict) {
      clear(id, request);
      throw Problem.conflict("SCHEDULE_CONFLICT", "HLV thay thế đã có lịch trong khung giờ này.");
    }
    boolean applied = request.trainerId() == null ? result.status().equals("CANCELLED")
        : result.trainerId() == request.trainerId();
    if (applied) {
      return result;
    }
    if (existing.exists()) {
      throw Problem.conflict("CHECKIN_EXISTS", "Buổi đã có điểm danh. Cần xử lý lịch sử trước khi cập nhật.");
    }
    throw ScheduleRepository.mutationInProgress();
  }

  private Session apply(long id, Pending request, boolean recorded) {
    Long trainer = request.trainerId();
    if (trainer != null) {
      shifts.lock(trainer);
    }
    Session current = repo.readSession(id, true);
    Pending active = pending(id);
    if (active == null || !request.id().equals(active.id())) {
      return current;
    }
    clear(id, request);
    if (recorded) {
      return current;
    }
    if (trainer == null) {
      return repo.setStatus(id, "CANCELLED", request.reason());
    }
    if (current.trainerId() == trainer) {
      return current;
    }
    requireSubstitutable(current);
    var profile = http.user(auth, trainer);
    if (!profile.role().equals("TRAINER") || !profile.status().equals("ACTIVE")) {
      throw Problem.invalid("Chọn HLV thay thế đang hoạt động.");
    }
    shifts.requireOpen(trainer, OffsetDateTime.parse(current.startAt()), OffsetDateTime.parse(current.endAt()), false);
    http.referenceTrainer(auth, trainer);
    Session replacement = repo.jdbc().sql("""
        UPDATE sessions SET trainer_id=:trainer,
          assigned_trainer_id=coalesce(assigned_trainer_id,trainer_id) WHERE id=:id RETURNING *
        """).param("id", id).param("trainer", trainer).query(ScheduleRepository.SESSION_ROW).single();
    repo.jdbc().sql("""
        INSERT INTO session_trainer_changes(session_id,previous_trainer_id,new_trainer_id,changed_by,reason)
        VALUES(:id,:previous,:trainer,:actor,:reason)
        """).param("id", id).param("previous", current.trainerId()).param("trainer", trainer)
        .param("actor", request.actorId()).param("reason", request.reason()).update();
    return replacement;
  }

  private void clear(long id, Pending request) {
    repo.jdbc().sql("""
        UPDATE sessions SET mutation_id=NULL,mutation_actor=NULL,mutation_reason=NULL,mutation_trainer=NULL
        WHERE id=:id AND mutation_id=:key
        """).param("id", id).param("key", request.id()).update();
  }

  private Pending pending(long id) {
    return repo.jdbc().sql("""
        SELECT mutation_id,mutation_actor,mutation_reason,mutation_trainer FROM sessions
        WHERE id=:id AND mutation_id IS NOT NULL
        """).param("id", id).query((rs, row) -> new Pending(
            rs.getObject(1, UUID.class), rs.getLong(2), rs.getString(3), rs.getObject(4, Long.class)))
        .optional().orElse(null);
  }

  @Scheduled(fixedDelayString = "${app.session-mutation-retry:PT10S}")
  void resumePending() {
    for (long id : repo.jdbc().sql("SELECT id FROM sessions WHERE mutation_id IS NOT NULL ORDER BY id")
        .query(Long.class).list()) {
      Pending request = pending(id);
      if (request == null) {
        continue;
      }
      try {
        finish(id, request);
      } catch (Problem rejected) {
        if (!List.of("CHECKIN_EXISTS", "SESSION_UPDATE_PENDING").contains(rejected.code)) {
          log.warn("Chưa xử lý được yêu cầu cập nhật buổi {} ({}).", id, rejected.code);
        }
      } catch (RuntimeException failure) {
        log.error("Lỗi khi xử lý lại yêu cầu cập nhật buổi {}", id, failure);
      }
    }
  }

  private static void requireCancellation(Actor actor, Session session) {
    OffsetDateTime now = OffsetDateTime.now(ScheduleService.ZONE);
    if (actor.is("LEARNER")) {
      actor.require(Objects.equals(session.learnerId(), actor.id()));
      if (OffsetDateTime.parse(session.startAt()).isBefore(now.plus(LEARNER_CANCEL_NOTICE))) {
        throw Problem.conflict("CANCEL_TOO_LATE", "Hủy PT ít nhất 12 giờ trước giờ tập.");
      }
    } else {
      actor.require(actor.is("ADMIN") || session.trainerId() == actor.id());
    }
    if (session.type().equals("GROUP30")) {
      actor.require("ADMIN");
    }
    requireActiveSession(session, now);
  }

  private static void requireActiveSession(Session session, OffsetDateTime now) {
    if (OffsetDateTime.parse(session.endAt()).isBefore(now)
        || !List.of("CONFIRMED", "PENDING_TRAINER").contains(session.status())) {
      throw Problem.conflict("SESSION_CLOSED", "Buổi này đã kết thúc hoặc đã hủy.");
    }
  }

  private static void requireSubstitutable(Session session) {
    OffsetDateTime now = OffsetDateTime.now(ScheduleService.ZONE);
    requireActiveSession(session, now);
    if (!OffsetDateTime.parse(session.startAt()).isAfter(now)) {
      throw Problem.conflict("SESSION_STARTED", "Chỉ đổi HLV trước khi buổi bắt đầu.");
    }
  }
}
