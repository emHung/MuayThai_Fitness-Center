package vn.muaythai.schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.muaythai.schedule.ScheduleRepository.SessionSelection;
import vn.muaythai.shared.Problem;
import vn.muaythai.shared.ServiceHttp;
import vn.muaythai.shared.ServiceHttp.Session;

@Service
class ShiftService {
  private final ScheduleRepository repository;
  private final ServiceHttp http;
  private final String auth;

  ShiftService(ScheduleRepository repository, ServiceHttp http, @Value("${app.auth-uri}") String auth) {
    this.repository = repository;
    this.http = http;
    this.auth = auth;
  }

  void lock(long trainer) {
    repository.jdbc().sql("SELECT pg_advisory_xact_lock(81104, hashint8(:trainer))")
        .param("trainer", trainer).query((rs, row) -> true).single();
  }

  List<ShiftsApi.Shift> week(long trainer) {
    return repository.jdbc().sql("SELECT * FROM trainer_shifts WHERE trainer_id=:id ORDER BY weekday,starts_at")
        .param("id", trainer).query((rs, row) -> new ShiftsApi.Shift(rs.getInt("weekday"),
            rs.getObject("starts_at", LocalTime.class), rs.getObject("ends_at", LocalTime.class))).list();
  }

  @Transactional
  public List<ShiftsApi.Shift> replace(long trainer, List<ShiftsApi.Shift> requested) {
    requireTrainer(trainer);
    lock(trainer);
    List<ShiftsApi.Shift> ordered = new ArrayList<>(requested);
    ordered.sort(Comparator.comparingInt(ShiftsApi.Shift::weekday).thenComparing(ShiftsApi.Shift::startsAt));
    ShiftsApi.Shift previous = null;
    for (var shift : ordered) {
      if (!shift.startsAt().isBefore(shift.endsAt()) || previous != null && previous.weekday() == shift.weekday()
          && previous.endsAt().isAfter(shift.startsAt())) {
        throw Problem.invalid("Ca làm bị chồng giờ hoặc kết thúc trước khi bắt đầu.");
      }
      previous = shift;
    }
    List<Session> affected = future(trainer).stream().filter(session -> !fits(ordered,
        OffsetDateTime.parse(session.startAt()), OffsetDateTime.parse(session.endAt()))).toList();
    if (!affected.isEmpty()) {
      throw Problem.conflict("SHIFT_HAS_BOOKINGS", "Ca mới bỏ ngoài giờ một số buổi. Xử lý các buổi này trước.",
          Map.of("sessions", affected));
    }
    if (!ordered.isEmpty()) {
      http.referenceTrainer(auth, trainer);
    }
    repository.jdbc().sql("DELETE FROM trainer_shifts WHERE trainer_id=:id").param("id", trainer).update();
    for (var shift : ordered) {
      repository.jdbc().sql("""
          INSERT INTO trainer_shifts(trainer_id,weekday,starts_at,ends_at) VALUES(:id,:weekday,:starts,:ends)
          """).param("id", trainer).param("weekday", shift.weekday()).param("starts", shift.startsAt())
          .param("ends", shift.endsAt()).update();
    }
    return week(trainer);
  }

  List<ShiftsApi.BlockView> blocks(long trainer) {
    return repository.jdbc().sql("SELECT * FROM trainer_blocks WHERE trainer_id=:id AND end_at>now() ORDER BY start_at")
        .param("id", trainer).query(ShiftService::blockRow).list();
  }

  @Transactional
  public ShiftsApi.BlockView close(long trainer, ShiftsApi.Block input) {
    requireTrainer(trainer);
    if (!input.startAt().isBefore(input.endAt()) || !input.endAt().isAfter(OffsetDateTime.now())) {
      throw Problem.invalid("Khoảng thời gian đóng không hợp lệ.");
    }
    lock(trainer);
    List<Session> conflicts = repository.sessions(SessionSelection.TRAINER_BLOCK_CONFLICTS,
        Map.of("id", trainer, "start", input.startAt(), "end", input.endAt()));
    if (!conflicts.isEmpty()) {
      throw Problem.conflict("BLOCK_HAS_BOOKINGS", "Cần xử lý các buổi đã đặt trước khi đóng khung.",
          Map.of("sessions", conflicts));
    }
    http.referenceTrainer(auth, trainer);
    return repository.jdbc().sql("""
        INSERT INTO trainer_blocks(trainer_id,start_at,end_at,reason) VALUES(:id,:start,:end,:reason) RETURNING *
        """).param("id", trainer).param("start", input.startAt()).param("end", input.endAt())
        .param("reason", input.reason().strip()).query(ShiftService::blockRow).single();
  }

  @Transactional
  public void reopen(long trainer, long block) {
    lock(trainer);
    repository.jdbc().sql("DELETE FROM trainer_blocks WHERE id=:block AND trainer_id=:id")
        .param("id", trainer).param("block", block).update();
  }

  boolean available(long trainer, OffsetDateTime start, OffsetDateTime end, boolean outsideShift) {
    List<ShiftsApi.Shift> entries = week(trainer);
    if (!outsideShift && !fits(entries, start, end)) {
      return false;
    }
    return !repository.jdbc().sql("""
        SELECT EXISTS (SELECT FROM trainer_blocks WHERE trainer_id=:id AND start_at<:end AND end_at>:start)
        """).param("id", trainer).param("start", start).param("end", end).query(Boolean.class).single();
  }

  void requireOpen(long trainer, OffsetDateTime start, OffsetDateTime end, boolean outsideShift) {
    if (!available(trainer, start, end, outsideShift)) {
      throw Problem.conflict("TRAINER_CLOSED", "Khung này ngoài ca làm hoặc đã được đóng.");
    }
  }

  List<ShiftsApi.Shift> shiftsFor(long trainer, LocalDate date) {
    return week(trainer).stream().filter(s -> s.weekday() == date.getDayOfWeek().getValue()).toList();
  }

  private List<Session> future(long trainer) {
    return repository.sessions(SessionSelection.TRAINER_FUTURE,
        Map.of("id", trainer));
  }

  private void requireTrainer(long id) {
    if (!"TRAINER".equals(http.user(auth, id).role())) {
      throw Problem.invalid("Chọn tài khoản huấn luyện viên.");
    }
  }

  private static boolean fits(List<ShiftsApi.Shift> week, OffsetDateTime start, OffsetDateTime end) {
    var localStart = start.atZoneSameInstant(ScheduleService.ZONE);
    var localEnd = end.atZoneSameInstant(ScheduleService.ZONE);
    return localStart.toLocalDate().equals(localEnd.toLocalDate()) && week.stream().anyMatch(shift ->
        shift.weekday() == localStart.getDayOfWeek().getValue() && !shift.startsAt().isAfter(localStart.toLocalTime())
            && !shift.endsAt().isBefore(localEnd.toLocalTime()));
  }

  private static ShiftsApi.BlockView blockRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    return new ShiftsApi.BlockView(rs.getLong("id"), rs.getObject("start_at", OffsetDateTime.class).toString(),
        rs.getObject("end_at", OffsetDateTime.class).toString(), rs.getString("reason"));
  }
}
