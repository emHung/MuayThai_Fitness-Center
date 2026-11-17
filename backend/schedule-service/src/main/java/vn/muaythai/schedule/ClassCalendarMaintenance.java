package vn.muaythai.schedule;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.muaythai.shared.Problem;

@Component
class ClassCalendarMaintenance {
  private record Enrollment(long sessionId, long learnerId, long packageId) {}

  private static final Logger log = LoggerFactory.getLogger(ClassCalendarMaintenance.class);
  private static final Set<String> ENROLLMENT_CONFLICTS = Set.of(
      "WRONG_PACKAGE", "CLASS_FULL", "LEARNER_BUSY", "INVALID_INPUT");
  private final ScheduleRepository repository;
  private final ScheduleService schedule;

  ClassCalendarMaintenance(ScheduleRepository repository, ScheduleService schedule) {
    this.repository = repository;
    this.schedule = schedule;
  }

  @Scheduled(cron = "${app.class-calendar-cron:0 10 * * * *}", zone = "Asia/Ho_Chi_Minh")
  void extendCalendars() {
    for (long id : repository.jdbc().sql("SELECT id FROM classes WHERE status='OPEN' ORDER BY id")
        .query(Long.class).list()) {
      try {
        schedule.extendClassCalendar(id);
      } catch (RuntimeException failure) {
        log.error("Không thể bổ sung lịch cho lớp {}", id, failure);
      }
    }
    enrollFixedGroups();
  }

  private void enrollFixedGroups() {
    var enrollments = repository.jdbc().sql("""
        SELECT s.id, f.learner_id, f.package_id
        FROM fixed_group_schedules f JOIN sessions s ON s.class_id=f.class_id
        WHERE s.status='CONFIRMED' AND s.start_at > now()
          AND (s.start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date <= f.end_date
          AND NOT EXISTS (SELECT 1 FROM class_signups r WHERE r.session_id=s.id AND r.learner_id=f.learner_id)
        ORDER BY s.start_at, f.learner_id
        """).query((rs, row) -> new Enrollment(rs.getLong(1), rs.getLong(2), rs.getLong(3))).list();
    for (Enrollment enrollment : enrollments) {
      try {
        schedule.applyFixedEnrollment(enrollment.sessionId(), enrollment.learnerId(), enrollment.packageId());
      } catch (Problem rejected) {
        if (!ENROLLMENT_CONFLICTS.contains(rejected.code)) {
          log.warn("Không thể gán lịch cố định cho buổi {} ({}).", enrollment.sessionId(), rejected.code);
        }
      } catch (RuntimeException failure) {
        log.error("Không thể xử lý lịch cố định của buổi {}", enrollment.sessionId(), failure);
      }
    }
  }
}
