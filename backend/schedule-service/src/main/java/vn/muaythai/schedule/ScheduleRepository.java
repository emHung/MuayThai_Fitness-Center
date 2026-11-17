package vn.muaythai.schedule;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.muaythai.shared.Problem;
import vn.muaythai.shared.ServiceHttp;

@Repository
class ScheduleRepository {
  static final RowMapper<ServiceHttp.Session> SESSION_ROW = (rs, row) -> new ServiceHttp.Session(
      rs.getLong("id"), rs.getString("type"), rs.getString("title"), rs.getLong("trainer_id"),
      rs.getObject("learner_id", Long.class), rs.getObject("class_id", Long.class),
      rs.getObject("package_id", Long.class), rs.getObject("start_at", OffsetDateTime.class).toString(),
      rs.getObject("end_at", OffsetDateTime.class).toString(), rs.getString("status"), rs.getInt("capacity"),
      rs.getObject("assigned_trainer_id", Long.class));

  static final RowMapper<ScheduleApi.ClassInfo> CLASS = (rs, row) -> new ScheduleApi.ClassInfo(
      rs.getLong("id"), rs.getString("title"), rs.getLong("trainer_id"), rs.getString("type"),
      rs.getString("status"), Arrays.asList((Integer[]) rs.getArray("weekdays").getArray()),
      rs.getString("starts_at"), rs.getString("ends_at"));

  private static final String CALENDAR_WINDOW =
      "s.start_at>=:start AND s.start_at<:end AND (:query='' OR strpos(lower(s.title),lower(:query))>0)";

  enum SessionSelection {
    CENTER_CALENDAR(CALENDAR_WINDOW),
    TRAINER_CALENDAR(CALENDAR_WINDOW + " AND s.trainer_id=:id"),
    LEARNER_CALENDAR(CALENDAR_WINDOW + """
          AND (s.learner_id=:id OR (s.class_id IN (:classes) AND s.status='CONFIRMED')
            OR EXISTS (SELECT 1 FROM class_signups r WHERE r.session_id=s.id AND r.learner_id=:id AND NOT r.cancelled))
        """),
    PT_REQUESTS("s.trainer_id=:id AND s.status='PENDING_TRAINER' AND s.start_at>=:now"),
    FIXED_CLASS("""
        s.class_id=:class AND coalesce(s.assigned_trainer_id,s.trainer_id)=:trainer
          AND s.status='CONFIRMED' AND s.start_at>now()
        """),
    ELIGIBLE_PT("""
        s.type='PT' AND s.learner_id=:learner AND s.status='CONFIRMED' AND s.start_at<=:open AND s.end_at>=:now
        """),
    ELIGIBLE_GROUP("""
        s.type=:type AND s.status='CONFIRMED' AND s.start_at<=:open AND s.end_at>=:now
          AND ((s.type='GROUP30' AND s.class_id=:class) OR EXISTS (SELECT 1 FROM class_signups r WHERE r.session_id=s.id
            AND r.learner_id=:learner AND r.package_id=:package AND NOT r.cancelled))
        """),
    GROUP10_WALK_IN("""
        s.type='GROUP10' AND s.status='CONFIRMED' AND (s.class_id=:class OR coalesce(s.assigned_trainer_id,s.trainer_id)=:trainer)
          AND s.start_at>=:earliest AND s.start_at<=:latest AND s.end_at>=:now
          AND (SELECT count(*) FROM class_signups r WHERE r.session_id=s.id AND NOT r.cancelled)<10
          AND NOT EXISTS (SELECT FROM sessions booked JOIN class_signups r ON r.session_id=booked.id
            WHERE booked.type='GROUP10' AND r.learner_id=:learner AND NOT r.cancelled
              AND booked.status='CONFIRMED'
              AND (booked.class_id=:class OR coalesce(booked.assigned_trainer_id,booked.trainer_id)=:trainer)
              AND booked.start_at>:lastHour AND booked.start_at<:nextHour
              AND (booked.start_at>:latest OR booked.end_at<:now))
        """),
    TRAINER_BLOCK_CONFLICTS("""
        trainer_id=:id AND start_at<:end AND end_at>:start AND status IN ('PENDING_TRAINER','CONFIRMED')
        """),
    TRAINER_FUTURE("trainer_id=:id AND end_at>now() AND status IN ('PENDING_TRAINER','CONFIRMED')");

    private final String sql;

    SessionSelection(String where) {
      sql = "SELECT s.* FROM sessions s WHERE " + where + " ORDER BY start_at, id LIMIT 500";
    }
  }

  private final JdbcClient db;

  ScheduleRepository(JdbcClient db) {
    this.db = db;
  }

  public JdbcClient jdbc() {
    return db;
  }

  ScheduleApi.ClassInfo classInfo(long id, boolean lock) {
    return db.sql("SELECT * FROM classes WHERE id = :id" + (lock ? " FOR UPDATE" : ""))
        .param("id", id).query(CLASS).optional().orElseThrow(() -> Problem.missing("Không tìm thấy lớp."));
  }

  ServiceHttp.Session session(long id, boolean lock) {
    return readSession(id, lock, lock);
  }

  ServiceHttp.Session readSession(long id, boolean lock) {
    return readSession(id, lock, false);
  }

  ServiceHttp.Session sessionForAttendance(long id) {
    return readSession(id, false, true);
  }

  private ServiceHttp.Session readSession(long id, boolean lock, boolean validateMutation) {
    return db.sql("SELECT * FROM sessions WHERE id=:id" + (lock ? " FOR UPDATE" : ""))
        .param("id", id).query((rs, row) -> {
      if (validateMutation && rs.getObject("mutation_id") != null) {
        throw mutationInProgress();
      }
      return SESSION_ROW.mapRow(rs, row);
    }).optional().orElseThrow(() -> Problem.missing("Không tìm thấy buổi tập."));
  }

  static Problem mutationInProgress() {
    return Problem.conflict("SESSION_UPDATE_PENDING", "Buổi tập đang được cập nhật. Vui lòng thử lại.");
  }

  List<ServiceHttp.Session> sessions(SessionSelection selection, Map<String, ?> parameters) {
    return db.sql(selection.sql).params(parameters).query(SESSION_ROW).list();
  }

  ServiceHttp.Session setStatus(long id, String status, String reason) {
    return db.sql("UPDATE sessions SET status=:status, reason=:reason WHERE id=:id RETURNING *")
        .param("id", id).param("status", status).param("reason", reason).query(SESSION_ROW).single();
  }

  void lockLearner(long id) {
    // A dedicated namespace keeps these locks distinct from the check-in database locks.
    db.sql("SELECT pg_advisory_xact_lock(81103, hashint8(:id)) AS locked")
        .param("id", id).query((rs, row) -> true).single();
  }
}
