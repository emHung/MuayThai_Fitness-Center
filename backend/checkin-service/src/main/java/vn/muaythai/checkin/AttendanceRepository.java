package vn.muaythai.checkin;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.muaythai.shared.Problem;

@Repository
class AttendanceRepository {
  static final String ENTRIES = """
      SELECT c.*, CASE WHEN a.id IS NULL THEN 'RECORDED' ELSE 'ADJUSTED' END AS event_status
      FROM checkins c LEFT JOIN checkin_adjustments a ON c.id=a.checkin_id
      """;
  static final RowMapper<AttendanceApi.Entry> ENTRY = (rs, row) -> new AttendanceApi.Entry(
      rs.getLong("id"), rs.getObject("session_id", Long.class), rs.getLong("package_id"), rs.getLong("learner_id"),
      rs.getObject("trainer_id", Long.class), rs.getString("learner_name"), rs.getString("session_title"),
      rs.getObject("checked_in_at", OffsetDateTime.class).toString(), rs.getLong("actor_id"), rs.getString("method"),
      rs.getString("reason"), rs.getString("event_status"), rs.getObject("remaining_after_event", Integer.class),
      rs.getObject("request_key", UUID.class));

  static final RowMapper<AttendanceApi.Adjustment> ADJUSTMENT = (rs, row) -> new AttendanceApi.Adjustment(
      rs.getLong("id"), rs.getLong("checkin_id"), rs.getLong("actor_id"), rs.getString("reason"),
      rs.getObject("adjusted_at", OffsetDateTime.class).toString(), rs.getObject("remaining_before", Integer.class),
      rs.getObject("remaining_after", Integer.class), rs.getObject("request_key", UUID.class));

  private final JdbcClient db;

  AttendanceRepository(JdbcClient db) {
    this.db = db;
  }

  public JdbcClient jdbc() {
    return db;
  }

  Optional<AttendanceApi.Entry> keyedEntry(long actor, UUID key) {
    return db.sql(ENTRIES + " WHERE c.actor_id=:actor AND c.request_key=:key")
        .param("actor", actor).param("key", key).query(ENTRY).optional();
  }

  Optional<AttendanceApi.Adjustment> keyedAdjustment(long actor, UUID key) {
    return db.sql("SELECT * FROM checkin_adjustments WHERE actor_id=:actor AND request_key=:key")
        .param("actor", actor).param("key", key).query(ADJUSTMENT).optional();
  }

  AttendanceApi.Entry entry(long id) {
    return db.sql(ENTRIES + " WHERE c.id=:id").param("id", id).query(ENTRY).optional()
        .orElseThrow(() -> Problem.missing("Không tìm thấy lượt điểm danh."));
  }

  List<AttendanceApi.Adjustment> adjustments(long id) {
    return db.sql("SELECT * FROM checkin_adjustments WHERE checkin_id=:id").param("id", id)
        .query(ADJUSTMENT).list();
  }

  void lock(int namespace, long id) {
    db.sql("SELECT pg_advisory_xact_lock(:namespace, hashint8(:id))")
        .param("namespace", namespace).param("id", id).query((rs,row) -> true).single();
  }
}
