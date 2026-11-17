package vn.muaythai.checkin;

import java.time.Period;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class AttendanceMaintenance {
  private final JdbcClient db;
  private final String retention;

  AttendanceMaintenance(JdbcClient db, @Value("${app.attendance-retention:P1Y}") String retention) {
    this.db = db;
    Period period = Period.parse(retention);
    if (period.isZero() || period.isNegative()) {
      throw new IllegalArgumentException("Attendance retention must be a positive period.");
    }
    this.retention = period.toString();
  }

  @Scheduled(cron = "${app.retention-cron:0 20 3 * * *}", zone = "Asia/Ho_Chi_Minh")
  @Transactional
  public void purgeExpiredHistory() {
    db.sql("""
        DELETE FROM checkin_adjustments a USING checkins c
        WHERE a.checkin_id=c.id AND c.checked_in_at<now()-CAST(:retention AS interval)
        """).param("retention", retention).update();
    db.sql("DELETE FROM checkins WHERE checked_in_at<now()-CAST(:retention AS interval)")
        .param("retention", retention).update();
    db.sql("DELETE FROM absence_records WHERE recorded_at<now()-CAST(:retention AS interval)")
        .param("retention", retention).update();
  }
}
