package vn.muaythai.checkin;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.muaythai.shared.Actor;
import vn.muaythai.shared.Problem;
import vn.muaythai.shared.ServiceHttp;
import vn.muaythai.shared.ServiceHttp.Session;

@RestController
class RosterApi {
  record Row(long learnerId, String learnerName, String memberCode, String status, Integer remainingSessions) {}

  private final RosterService roster;

  RosterApi(RosterService roster) {
    this.roster = roster;
  }

  @GetMapping("/api/checkins/roster")
  List<Row> roster(Actor actor, @RequestParam long sessionId) {
    return roster.list(actor, sessionId);
  }

  @PutMapping("/api/checkins/roster/{sessionId}/absent/{learnerId}")
  List<Row> absent(Actor actor, @PathVariable long sessionId, @PathVariable long learnerId) {
    return roster.absent(actor, sessionId, learnerId);
  }
}

@Component
class RosterService {
  private final AttendanceRepository db;
  private final AttendanceService attendance;
  private final PackageService packages;
  private final ServiceHttp http;
  private final String schedule;

  RosterService(AttendanceRepository db, AttendanceService attendance, PackageService packages, ServiceHttp http,
      @Value("${app.schedule-uri}") String schedule) {
    this.db = db;
    this.attendance = attendance;
    this.packages = packages;
    this.http = http;
    this.schedule = schedule;
  }

  List<RosterApi.Row> list(Actor actor, long id) {
    Session session = session(actor, id);
    return rows(actor, session);
  }

  @Transactional
  public List<RosterApi.Row> absent(Actor actor, long id, long learner) {
    Session session = session(actor, id);
    if (!"CONFIRMED".equals(session.status()) || OffsetDateTime.parse(session.endAt()).isAfter(OffsetDateTime.now())) {
      throw Problem.conflict("SESSION_NOT_ENDED", "Chỉ ghi vắng sau khi buổi đã kết thúc.");
    }
    RosterApi.Row row = rows(actor, session).stream().filter(item -> item.learnerId() == learner)
        .findFirst().orElseThrow(() -> Problem.missing("Học viên không có tên trong buổi."));
    if (row.status().equals("RECORDED")) {
      throw Problem.conflict("ALREADY_CHECKED_IN", "Học viên đã điểm danh.");
    }
    db.jdbc().sql("""
        INSERT INTO absence_records(session_id,learner_id,actor_id) VALUES(:session,:learner,:actor)
        ON CONFLICT DO NOTHING
        """).param("session", id).param("learner", learner).param("actor", actor.id()).update();
    return rows(actor, session);
  }

  private List<RosterApi.Row> rows(Actor actor, Session session) {
    var confirmed = new HashMap<Long, AttendanceApi.Entry>();
    attendance.list(actor, null, session.id(), null).forEach(row -> confirmed.put(row.learnerId(), row));
    var absent = new HashSet<>(db.jdbc().sql("SELECT learner_id FROM absence_records WHERE session_id=:id")
        .param("id", session.id()).query(Long.class).list());
    Long[] ids = (session.type().equals("GROUP10") || session.type().equals("PT"))
        ? http.to(schedule, client -> client.get().uri("/internal/sessions/{id}/participants", session.id())
            .retrieve().body(Long[].class)) : null;
    var result = new ArrayList<RosterApi.Row>();
    var seen = new HashSet<Long>();
    for (PackagesApi.Pack pack : packages.forSession(actor, session.id())) {
      if (!seen.add(pack.learnerId()) || ids != null && !java.util.Arrays.asList(ids).contains(pack.learnerId())) {
        continue;
      }
      AttendanceApi.Entry entry = confirmed.get(pack.learnerId());
      String status = entry != null && entry.status().equals("RECORDED") ? "RECORDED"
          : absent.contains(pack.learnerId()) ? "ABSENT" : entry == null ? "WAITING" : "ADJUSTED";
      result.add(new RosterApi.Row(pack.learnerId(), pack.learnerName(), pack.memberCode(), status, pack.remainingSessions()));
    }
    for (AttendanceApi.Entry recorded : confirmed.values()) {
      if (seen.add(recorded.learnerId())) {
        result.add(new RosterApi.Row(recorded.learnerId(), recorded.learnerName(), "", recorded.status(),
            recorded.remainingAfterEvent()));
      }
    }
    return result;
  }

  private Session session(Actor actor, long id) {
    actor.require(actor.is("ADMIN") || actor.is("TRAINER"));
    Session session = http.to(schedule, client -> client.get().uri("/internal/sessions/{id}", id)
        .retrieve().body(Session.class));
    actor.require(actor.is("ADMIN") || Objects.equals(session.trainerId(), actor.id()));
    return session;
  }
}
