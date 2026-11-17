package vn.muaythai.progress;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import vn.muaythai.shared.Actor;
import vn.muaythai.shared.Problem;
import vn.muaythai.shared.ServiceHttp;
import vn.muaythai.shared.ServiceHttp.Session;

@Service
class ProgressService {
  private static final RowMapper<ProgressApi.Note> NOTE = (rs, row) -> new ProgressApi.Note(
      rs.getLong("id"), rs.getLong("session_id"), rs.getObject("learner_id", Long.class), rs.getLong("trainer_id"),
      rs.getString("trainer_name"), rs.getString("session_title"),
      rs.getObject("session_at", OffsetDateTime.class).toString(), rs.getString("content"), rs.getString("comment"),
      rs.getObject("rating", Integer.class), rs.getObject("updated_at", OffsetDateTime.class).toString());

  private static final RowMapper<ProgressApi.Goal> GOAL = (rs, row) -> new ProgressApi.Goal(
      rs.getLong("id"), rs.getLong("learner_id"), rs.getLong("trainer_id"),
      rs.getString("target_month").substring(0, 7), rs.getString("title"), rs.getString("description"),
      rs.getInt("progress"), ProgressApi.GoalStatus.valueOf(rs.getString("status")),
      rs.getObject("updated_at", OffsetDateTime.class).toString());

  private final JdbcClient db;
  private final ServiceHttp http;
  private final String schedule;
  private final String checkin;
  private final String auth;

  boolean hasTrainerHistory(long id) {
    return db.sql("""
        SELECT EXISTS (SELECT FROM session_notes WHERE trainer_id=:id)
            OR EXISTS (SELECT FROM monthly_goals WHERE trainer_id=:id)
        """).param("id", id).query(Boolean.class).single();
  }

  ProgressService(JdbcClient db, ServiceHttp http, @Value("${app.schedule-uri}") String schedule,
      @Value("${app.checkin-uri}") String checkin, @Value("${app.auth-uri}") String auth) {
    this.db = db;
    this.http = http;
    this.schedule = schedule;
    this.checkin = checkin;
    this.auth = auth;
  }

  ProgressApi.Note detail(Actor actor, long id) {
    Session session = session(id);
    actor.require(actor.is("ADMIN") || actor.is("TRAINER") && session.trainerId() == actor.id()
        || actor.is("LEARNER") && Objects.equals(session.learnerId(), actor.id()));
    return db.sql("SELECT * FROM session_notes WHERE session_id=:id")
        .param("id", id).query(NOTE).optional().orElseThrow(() -> Problem.missing("Buổi này chưa có ghi chú."));
  }

  ProgressApi.Note save(Actor actor, long id, ProgressApi.NoteForm form) {
    actor.require("TRAINER");
    Session session = session(id);
    actor.require(session.trainerId() == actor.id());
    if (!session.status().equals("CONFIRMED") || OffsetDateTime.parse(session.endAt()).isAfter(OffsetDateTime.now())) {
      throw Problem.conflict("SESSION_NOT_ENDED", "Chỉ ghi đánh giá sau khi buổi tập kết thúc.");
    }
    if (session.type().equals("PT") && form.rating() == null) {
      throw Problem.invalid("Nhập điểm đánh giá buổi PT.");
    }
    ServiceHttp.Profile trainer = http.user(auth, actor.id());
    return db.sql("""
        INSERT INTO session_notes(session_id,learner_id,trainer_id,trainer_name,session_title,session_at,
            content,comment,rating,updated_at)
          VALUES(:id,:learner,:trainer,:name,:title,:time,:content,:comment,:rating,now())
          ON CONFLICT(session_id) DO UPDATE SET content=EXCLUDED.content,comment=EXCLUDED.comment,
            rating=EXCLUDED.rating,updated_at=now() RETURNING *
        """).param("id", id).param("learner", session.learnerId()).param("trainer", actor.id())
        .param("name", trainer.displayName()).param("title", session.title())
        .param("time", OffsetDateTime.parse(session.startAt())).param("content", form.content().strip())
        .param("comment", form.comment()).param("rating", session.type().equals("PT") ? form.rating() : null)
        .query(NOTE).single();
  }

  List<ProgressApi.Note> history(Actor actor, Long learner) {
    if (actor.is("LEARNER")) {
      actor.require(learner == null || learner == actor.id());
      return db.sql("SELECT * FROM session_notes WHERE learner_id=:learner ORDER BY session_at DESC LIMIT 200")
          .param("learner", actor.id()).query(NOTE).list();
    }
    if (actor.is("TRAINER")) {
      if (learner != null) {
        requireAssigned(actor, learner);
      }
      return db.sql("""
          SELECT * FROM session_notes WHERE trainer_id=:trainer AND (:learner IS NULL OR learner_id=:learner)
          ORDER BY session_at DESC LIMIT 200
          """).param("trainer", actor.id()).param("learner", learner, java.sql.Types.BIGINT).query(NOTE).list();
    }
    return db.sql("""
        SELECT * FROM session_notes WHERE (:learner IS NULL OR learner_id=:learner)
        ORDER BY session_at DESC LIMIT 200
        """).param("learner", learner, java.sql.Types.BIGINT).query(NOTE).list();
  }

  List<ProgressApi.Goal> goals(Actor actor, Long learner) {
    if (actor.is("LEARNER")) {
      actor.require(learner == null || learner == actor.id());
      learner = actor.id();
    }
    if (actor.is("TRAINER") && learner != null) {
      requireAssigned(actor, learner);
    }
    return db.sql("""
        SELECT * FROM monthly_goals WHERE (:learner IS NULL OR learner_id=:learner)
          AND (:trainer IS NULL OR trainer_id=:trainer) ORDER BY target_month DESC,id DESC LIMIT 200
        """).param("learner", learner, java.sql.Types.BIGINT)
        .param("trainer", actor.is("TRAINER") ? actor.id() : null, java.sql.Types.BIGINT).query(GOAL).list();
  }

  ProgressApi.Goal saveGoal(Actor actor, long learner, ProgressApi.GoalForm form) {
    actor.require("TRAINER");
    requireAssigned(actor, learner);
    var status = form.status();
    if (status == null) {
      status = switch (form.progress()) {
        case 0 -> ProgressApi.GoalStatus.NOT_STARTED;
        case 100 -> ProgressApi.GoalStatus.ACHIEVED;
        default -> ProgressApi.GoalStatus.IN_PROGRESS;
      };
    }
    return db.sql("""
        INSERT INTO monthly_goals(learner_id,trainer_id,target_month,title,description,progress,status,updated_at)
          VALUES(:learner,:trainer,:month,:title,:description,:progress,:status,now())
        ON CONFLICT(learner_id,trainer_id,target_month) DO UPDATE SET title=EXCLUDED.title,
          description=EXCLUDED.description,progress=EXCLUDED.progress,status=EXCLUDED.status,updated_at=now() RETURNING *
        """).param("learner", learner).param("trainer", actor.id()).param("month", YearMonth.parse(form.month()).atDay(1))
        .param("title", form.title().strip()).param("description", form.description().strip())
        .param("progress", form.progress()).param("status", status.name()).query(GOAL).single();
  }

  private void requireAssigned(Actor actor, long learner) {
    Long[] assignments = http.to(checkin, client -> client.get()
        .uri("/internal/assignments?trainerId={id}", actor.id()).retrieve().body(Long[].class));
    actor.require(assignments != null && Arrays.asList(assignments).contains(learner));
  }

  private Session session(long id) {
    return http.to(schedule, client -> client.get().uri("/internal/sessions/{id}", id)
        .retrieve().body(Session.class));
  }
}
