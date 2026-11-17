package vn.muaythai.checkin;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class AttendanceMaintenance {
  private final JdbcClient db;

  AttendanceMaintenance(JdbcClient db) {
    this.db = db;
  }

  @Scheduled(cron = "${app.retention-cron:0 20 3 * * *}", zone = "Asia/Ho_Chi_Minh")
  @Transactional
  public void purgeExpiredHistory() {
    db.sql("""
        DELETE FROM checkin_adjustments a USING checkins c
        WHERE a.checkin_id=c.id AND c.checked_in_at<now()-interval '1 year'
        """).update();
    db.sql("DELETE FROM checkins WHERE checked_in_at<now()-interval '1 year'").update();
    db.sql("DELETE FROM absence_records WHERE recorded_at<now()-interval '1 year'").update();
  }
}
