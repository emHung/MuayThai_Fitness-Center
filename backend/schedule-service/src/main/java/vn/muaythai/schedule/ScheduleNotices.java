package vn.muaythai.schedule;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.muaythai.shared.ServiceHttp;

@RestController
class ScheduleNotices {
  private final ScheduleRepository repository;
  private final ScheduleService schedule;

  ScheduleNotices(ScheduleRepository repository, ScheduleService schedule) {
    this.repository = repository;
    this.schedule = schedule;
  }

  @GetMapping("/internal/notices")
  List<ServiceHttp.Notice> notices(@RequestParam long userId, @RequestParam String role) {
    List<ServiceHttp.Notice> result = new ArrayList<>();
    if (role.equals("TRAINER")) {
      result.addAll(repository.jdbc().sql("""
          SELECT * FROM sessions WHERE trainer_id=:id AND end_at>now()-interval '14 day'
            AND start_at<now()+interval '28 day' ORDER BY start_at DESC LIMIT 80
          """).param("id", userId).query((rs, row) -> {
        long id = rs.getLong("id");
        String status = rs.getString("status");
        String type = rs.getString("type");
        OffsetDateTime start = rs.getObject("start_at", OffsetDateTime.class);
        OffsetDateTime end = rs.getObject("end_at", OffsetDateTime.class);
        boolean review = type.equals("PT") && status.equals("CONFIRMED") && end.isBefore(OffsetDateTime.now());
        String title = review ? "Nhắc ghi đánh giá PT" : switch (status) {
          case "PENDING_TRAINER" -> "Có yêu cầu đặt PT";
          case "CANCELLED" -> "Buổi tập đã hủy";
          case "REJECTED" -> "Yêu cầu đã từ chối";
          default -> "Lịch dạy đã xác nhận";
        };
        return new ServiceHttp.Notice("schedule-" + id + "-" + status + "-" + start.toEpochSecond(),
            title, rs.getString("title"), start.toString(), id, review ? "REVIEW" : "SCHEDULE");
      }).list());
      result.addAll(repository.jdbc().sql("""
          SELECT c.*,s.title FROM session_trainer_changes c JOIN sessions s ON s.id=c.session_id
          WHERE (c.previous_trainer_id=:id OR c.new_trainer_id=:id) AND c.changed_at>now()-interval '14 day'
          ORDER BY c.changed_at DESC LIMIT 50
          """).param("id", userId).query((rs, row) -> {
        boolean assigned = rs.getLong("new_trainer_id") == userId;
        return new ServiceHttp.Notice("substitution-" + rs.getLong("id"),
            assigned ? "Có lịch dạy thay thế" : "Buổi tập đã đổi HLV",
            rs.getString("title") + " · " + rs.getString("reason"),
            rs.getObject("changed_at", OffsetDateTime.class).toString(),
            assigned ? rs.getLong("session_id") : null, "SCHEDULE");
      }).list());
    } else if (role.equals("LEARNER")) {
      List<Long> classes = new ArrayList<>(schedule.learnerClassIds(userId));
      classes.add(-1L);
      result.addAll(repository.jdbc().sql("""
          SELECT s.* FROM sessions s WHERE status='CONFIRMED'
            AND start_at BETWEEN now() AND now()+interval '1 day'
            AND (learner_id=:id OR (type='GROUP30' AND class_id IN (:classes))
              OR (type='GROUP10' AND EXISTS (SELECT 1 FROM class_signups r
                WHERE r.session_id=s.id AND r.learner_id=:id AND NOT r.cancelled)))
          ORDER BY start_at LIMIT 50
          """).param("id", userId).param("classes", classes).query((rs, row) -> {
        long id = rs.getLong("id");
        OffsetDateTime start = rs.getObject("start_at", OffsetDateTime.class);
        return new ServiceHttp.Notice("reminder-" + id + "-" + start.toEpochSecond(), "Sắp đến buổi tập",
            rs.getString("title"), start.toString(), id, "SCHEDULE");
      }).list());
    }
    if (role.equals("TRAINER") || role.equals("LEARNER")) {
      result.addAll(repository.jdbc().sql("""
          SELECT n.id,n.session_id,n.created_at,s.title FROM schedule_notifications n
          JOIN sessions s ON s.id=n.session_id WHERE n.recipient_id=:id
            AND n.created_at>now()-interval '14 day' ORDER BY n.created_at DESC LIMIT 50
          """).param("id", userId).query((rs, row) -> new ServiceHttp.Notice(
              "direct-pt-" + rs.getLong("id"), "Lịch PT mới được xếp", rs.getString("title"),
              rs.getObject("created_at", OffsetDateTime.class).toString(), rs.getLong("session_id"), "SCHEDULE")).list());
      result.addAll(repository.jdbc().sql("""
          SELECT * FROM pt_changes WHERE recipient_id=:id AND status='PENDING' AND expires_at>now()
          ORDER BY created_at DESC LIMIT 50
          """).param("id", userId).query((rs,row) -> new ServiceHttp.Notice(
              "change-" + rs.getLong("id"), "Có đề xuất đổi lịch PT", "Mở yêu cầu đổi lịch để phản hồi.",
              rs.getObject("created_at", OffsetDateTime.class).toString(), rs.getLong("session_id"), "CHANGE")).list());
    }
    return result;
  }
}
