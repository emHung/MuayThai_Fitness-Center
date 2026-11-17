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
  static final RowMapper<ServiceHttp.Session> SESSION = (rs, row) -> new ServiceHttp.Session(
      rs.getLong("id"), rs.getString("type"), rs.getString("title"), rs.getLong("trainer_id"),
      rs.getObject("learner_id", Long.class), rs.getObject("class_id", Long.class),
      rs.getObject("package_id", Long.class), rs.getObject("start_at", OffsetDateTime.class).toString(),
      rs.getObject("end_at", OffsetDateTime.class).toString(), rs.getString("status"), rs.getInt("capacity"),
      rs.getObject("assigned_trainer_id", Long.class));

  static final RowMapper<ScheduleApi.ClassInfo> CLASS = (rs, row) -> new ScheduleApi.ClassInfo(
      rs.getLong("id"), rs.getString("title"), rs.getLong("trainer_id"), rs.getString("type"),
      rs.getString("status"), Arrays.asList((Integer[]) rs.getArray("weekdays").getArray()),
      rs.getString("starts_at"), rs.getString("ends_at"));

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
      return SESSION.mapRow(rs, row);
    }).optional().orElseThrow(() -> Problem.missing("Không tìm thấy buổi tập."));
  }

  static Problem mutationInProgress() {
    return Problem.conflict("SESSION_UPDATE_PENDING", "Buổi tập đang được cập nhật. Vui lòng thử lại.");
  }

  List<ServiceHttp.Session> sessions(String filter, Map<String, ?> parameters) {
    return db.sql("SELECT s.* FROM sessions s WHERE " + filter + " ORDER BY start_at, id LIMIT 500")
        .params(parameters).query(SESSION).list();
  }

  ServiceHttp.Session setStatus(long id, String status, String reason) {
    return db.sql("UPDATE sessions SET status=:status, reason=:reason WHERE id=:id RETURNING *")
        .param("id", id).param("status", status).param("reason", reason).query(SESSION).single();
  }

  void lockLearner(long id) {
    // A dedicated namespace keeps these locks distinct from the check-in database locks.
    db.sql("SELECT pg_advisory_xact_lock(81103, hashint8(:id)) AS locked")
        .param("id", id).query((rs, row) -> true).single();
  }
}
