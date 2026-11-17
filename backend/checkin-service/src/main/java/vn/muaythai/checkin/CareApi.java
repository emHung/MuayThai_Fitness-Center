package vn.muaythai.checkin;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import vn.muaythai.shared.Actor;
import vn.muaythai.shared.ServiceHttp;

@RestController
class CareApi {
  record Care(long learnerId, String learnerName, String memberCode, String detail) {}

  private final JdbcClient db;

  CareApi(JdbcClient db) {
    this.db = db;
  }

  @GetMapping("/api/care/renewals")
  List<Care> renewals(Actor actor) {
    actor.require("ADMIN");
    return db.sql("""
        SELECT DISTINCT ON (learner_id) learner_id,learner_name,member_code,end_date,
          total_sessions-used_sessions AS remaining FROM packages
        WHERE end_date>=:today AND start_date<=:today
          AND (end_date<:soon OR total_sessions-used_sessions<=3)
        ORDER BY learner_id,end_date,id DESC LIMIT 250
        """).param("today", LocalDate.now(PackageService.ZONE)).param("soon", LocalDate.now(PackageService.ZONE).plusDays(7))
        .query((rs,row) -> new Care(rs.getLong("learner_id"), rs.getString("learner_name"), rs.getString("member_code"),
            "Hạn " + rs.getString("end_date") + (rs.getObject("remaining") == null ? ""
                : " · " + rs.getInt("remaining") + " buổi"))).list();
  }

  @GetMapping("/api/care/inactive")
  List<Care> inactive(Actor actor) {
    actor.require("ADMIN");
    LocalDate today = LocalDate.now(PackageService.ZONE);
    return db.sql("""
        SELECT DISTINCT ON (p.learner_id) p.learner_id,p.learner_name,p.member_code
        FROM packages p WHERE p.start_date<=:cutoff AND p.end_date>=:today
          AND NOT EXISTS(SELECT FROM checkins c WHERE c.learner_id=p.learner_id
            AND c.checked_in_at>=:threshold
            AND NOT EXISTS(SELECT FROM checkin_adjustments a WHERE a.checkin_id=c.id))
        ORDER BY p.learner_id,p.id DESC LIMIT 250
        """).param("today", today).param("cutoff", today.minusDays(14))
        .param("threshold", OffsetDateTime.now(PackageService.ZONE).minusDays(14))
        .query((rs,row) -> new Care(rs.getLong("learner_id"), rs.getString("learner_name"),
            rs.getString("member_code"), "Từ 14 ngày chưa điểm danh")).list();
  }

  @GetMapping("/internal/notices")
  List<ServiceHttp.Notice> reminders(@RequestParam long userId) {
    LocalDate today = LocalDate.now(PackageService.ZONE);
    return db.sql("""
        SELECT id,end_date,total_sessions-used_sessions AS remaining FROM packages
        WHERE learner_id=:user AND start_date<=:today AND end_date>=:today
          AND (end_date<:soon OR total_sessions-used_sessions<=3)
        ORDER BY end_date,id LIMIT 60
        """).param("user", userId).param("today", today).param("soon", today.plusDays(7))
        .query((rs,row) -> {
          long id = rs.getLong("id");
          var expires = rs.getObject("end_date", LocalDate.class);
          String count = rs.getObject("remaining") == null ? "" : " · Còn " + rs.getInt("remaining") + " buổi";
          return new ServiceHttp.Notice("package-" + id + "-" + expires + "-" + rs.getString("remaining"),
              "Gói sắp hết", "Hạn " + expires + count, expires.atStartOfDay(PackageService.ZONE).toOffsetDateTime().toString(),
              null, "PACKAGE");
        }).list();
  }
}
