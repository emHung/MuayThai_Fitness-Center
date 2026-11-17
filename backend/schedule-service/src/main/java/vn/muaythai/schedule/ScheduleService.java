package vn.muaythai.schedule;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.muaythai.shared.Actor;
import vn.muaythai.shared.Problem;
import vn.muaythai.shared.ServiceHttp;
import vn.muaythai.shared.ServiceHttp.Session;

@Service
class ScheduleService {
  static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final Duration PT_DURATION = Duration.ofHours(1);
  private static final int CLASS_HORIZON_DAYS = 28;

  private final ScheduleRepository repository;
  private final ShiftService shifts;
  private final ServiceHttp http;
  private final String auth;
  private final String checkin;

  ScheduleService(ScheduleRepository repository, ShiftService shifts, ServiceHttp http,
      @Value("${app.auth-uri}") String auth, @Value("${app.checkin-uri}") String checkin) {
    this.repository = repository;
    this.shifts = shifts;
    this.http = http;
    this.auth = auth;
    this.checkin = checkin;
  }

  List<ScheduleApi.ClassInfo> classes(Actor actor, String type) {
    List<ScheduleApi.ClassInfo> visible = repository.jdbc().sql("""
        SELECT * FROM classes WHERE type = :type
          AND (:role = 'ADMIN' OR (:role = 'TRAINER' AND trainer_id=:user) OR (:role='LEARNER' AND status='OPEN'))
        ORDER BY title,id
        """).param("type", type).param("role", actor.role()).param("user", actor.id())
        .query(ScheduleRepository.CLASS).list();
    if (!actor.is("LEARNER")) {
      return visible;
    }
    List<Long> ids = learnerClassIds(actor.id());
    return visible.stream().filter(info -> ids.contains(info.id())).toList();
  }

  ScheduleApi.ClassInfo classInfo(long id) {
    return repository.classInfo(id, false);
  }

  ScheduleApi.ClassInfo classDetail(Actor actor, String type, long id) {
    ScheduleApi.ClassInfo info = requireClassType(classInfo(id), type);
    actor.require(actor.is("ADMIN") || actor.is("TRAINER") && info.trainerId() == actor.id()
        || actor.is("LEARNER") && learnerClassIds(actor.id()).contains(id));
    return info;
  }

  @Transactional
  public ScheduleApi.ClassInfo createClass(Actor actor, String type, ScheduleApi.ClassForm form) {
    long trainer = classTrainer(actor, type, form.trainerId());
    checkClassTimes(form);
    String status = actor.is("ADMIN") ? "OPEN" : "PENDING";
    http.referenceTrainer(auth, trainer);
    ScheduleApi.ClassInfo info = repository.jdbc().sql("""
        INSERT INTO classes(title,trainer_id,type,status,weekdays,starts_at,ends_at)
          VALUES(:title,:trainer,:type,:status,cast(:days AS integer[]),:starts,:ends) RETURNING *
        """).param("title", form.title().strip()).param("trainer", trainer).param("type", type).param("status", status)
        .param("days", days(form.weekdays())).param("starts", form.startsAt()).param("ends", form.endsAt())
        .query(ScheduleRepository.CLASS).single();
    if (status.equals("OPEN")) {
      generate(info, false);
    }
    return info;
  }

  @Transactional
  public ScheduleApi.ClassInfo editClass(Actor actor, String type, long id, ScheduleApi.ClassForm form) {
    actor.require("ADMIN");
    long trainer = classTrainer(actor, type, form.trainerId());
    checkClassTimes(form);
    requireClassType(repository.classInfo(id, true), type);
    http.referenceTrainer(auth, trainer);
    shifts.lock(trainer);
    retireFutureSessions(id);
    ScheduleApi.ClassInfo info = repository.jdbc().sql("""
        UPDATE classes SET title=:title,trainer_id=:trainer,weekdays=cast(:days AS integer[]),
          starts_at=:starts,ends_at=:ends WHERE id=:id RETURNING *
        """).param("id", id).param("title", form.title().strip()).param("trainer", trainer)
        .param("days", days(form.weekdays())).param("starts", form.startsAt()).param("ends", form.endsAt())
        .query(ScheduleRepository.CLASS).single();
    if (info.status().equals("OPEN")) {
      generate(info, false);
    }
    return info;
  }

  @Transactional
  public ScheduleApi.ClassInfo changeClass(Actor actor, String type, long id, String action) {
    actor.require("ADMIN");
    ScheduleApi.ClassInfo original = requireClassType(repository.classInfo(id, true), type);
    String status = switch (action) {
      case "approve" -> "OPEN";
      case "reject" -> "REJECTED";
      default -> "STOPPED";
    };
    if (original.status().equals(status)) {
      return original;
    }
    if (!action.equals("stop") && (!type.equals("GROUP10") || !original.status().equals("PENDING"))) {
      throw Problem.conflict("CLASS_NOT_PENDING", "Lớp này không còn chờ duyệt.");
    }
    ScheduleApi.ClassInfo info = repository.jdbc().sql("UPDATE classes SET status=:status WHERE id=:id RETURNING *")
        .param("id", id).param("status", status).query(ScheduleRepository.CLASS).single();
    if (status.equals("OPEN")) {
      generate(info, false);
    } else {
      retireFutureSessions(id);
    }
    return info;
  }

  private void retireFutureSessions(long id) {
    OffsetDateTime cutoff = now().plusMinutes(30);
    var pending = repository.jdbc().sql("""
        SELECT mutation_id IS NOT NULL FROM sessions WHERE class_id=:id AND start_at>:cutoff
          AND status='CONFIRMED' ORDER BY id FOR UPDATE
        """).param("id", id).param("cutoff", cutoff).query(Boolean.class).list();
    if (pending.contains(true)) {
      throw ScheduleRepository.mutationInProgress();
    }
    repository.jdbc().sql("""
        UPDATE sessions SET status='CANCELLED', reason='Thay đổi lịch lớp'
        WHERE class_id=:id AND start_at > :cutoff AND status='CONFIRMED'
        """).param("id", id).param("cutoff", cutoff).update();
  }

  private static ScheduleApi.ClassInfo requireClassType(ScheduleApi.ClassInfo info, String type) {
    if (!info.type().equals(type)) {
      throw Problem.missing("Không tìm thấy lớp.");
    }
    return info;
  }

  private long classTrainer(Actor actor, String type, Long trainerId) {
    actor.require(actor.is("ADMIN") || actor.is("TRAINER") && type.equals("GROUP10"));
    if (actor.is("TRAINER")) {
      return actor.id();
    }
    if (trainerId == null) {
      throw Problem.invalid("Chọn huấn luyện viên.");
    }
    ServiceHttp.Profile trainer = http.user(auth, trainerId);
    if (!trainer.role().equals("TRAINER") || !trainer.status().equals("ACTIVE")) {
      throw Problem.invalid("Huấn luyện viên đã chọn không khả dụng.");
    }
    return trainer.id();
  }

  @Transactional
  public void extendClassCalendar(long id) {
    var info = repository.classInfo(id, true);
    if (info.status().equals("OPEN")) generate(info, true);
  }

  private void generate(ScheduleApi.ClassInfo info, boolean extending) {
    shifts.lock(info.trainerId());
    LocalDate first = LocalDate.now(ZONE);
    for (int offset = 0; offset < CLASS_HORIZON_DAYS; offset++) {
      LocalDate day = first.plusDays(offset);
      if (!info.weekdays().contains(day.getDayOfWeek().getValue())) {
        continue;
      }
      OffsetDateTime start = day.atTime(LocalTime.parse(info.startsAt())).atZone(ZONE).toOffsetDateTime();
      OffsetDateTime end = day.atTime(LocalTime.parse(info.endsAt())).atZone(ZONE).toOffsetDateTime();
      if (start.isBefore(now())) {
        continue;
      }
      if (extending) {
        if (!shifts.available(info.trainerId(), start, end, false)) continue;
      } else {
        shifts.requireOpen(info.trainerId(), start, end, false);
      }
      String conflictRule = extending ? " ON CONFLICT DO NOTHING" : """
          ON CONFLICT (class_id,start_at) DO UPDATE SET
            trainer_id=EXCLUDED.trainer_id,assigned_trainer_id=NULL,title=EXCLUDED.title,end_at=EXCLUDED.end_at,
            status='CONFIRMED',reason=NULL
          WHERE sessions.status='CANCELLED' AND sessions.reason='Thay đổi lịch lớp'
          """;
      repository.jdbc().sql("""
          INSERT INTO sessions(type,title,trainer_id,class_id,start_at,end_at,capacity,status)
          VALUES(:type,:title,:trainer,:class,:start,:end,:capacity,'CONFIRMED')
          """ + conflictRule).param("type", info.type()).param("title", info.title()).param("trainer", info.trainerId())
          .param("class", info.id()).param("start", start).param("end", end)
          .param("capacity", info.type().equals("GROUP10") ? 10 : 30).update();
    }
  }

  private static String days(List<Integer> weekdays) {
    return weekdays.stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
  }

  private static void checkClassTimes(ScheduleApi.ClassForm form) {
    if (!form.startsAt().isBefore(form.endsAt())) {
      throw Problem.invalid("Giờ kết thúc phải sau giờ bắt đầu.");
    }
  }

  List<Session> sessions(Actor actor, OffsetDateTime from, OffsetDateTime to) {
    OffsetDateTime start = from == null ? LocalDate.now(ZONE).atStartOfDay(ZONE).toOffsetDateTime() : from;
    OffsetDateTime end = to == null ? start.plusDays(28) : to;
    if (!end.isAfter(start) || Duration.between(start, end).toDays() > 93) {
      throw Problem.invalid("Khoảng thời gian xem lịch không hợp lệ.");
    }
    if (actor.is("ADMIN")) {
      return repository.sessions("s.start_at >= :start AND s.start_at < :end",
          Map.of("start", start, "end", end));
    }
    if (actor.is("TRAINER")) {
      return repository.sessions("s.trainer_id=:id AND s.start_at >= :start AND s.start_at < :end",
          Map.of("id", actor.id(), "start", start, "end", end));
    }
    List<Long> classes = new ArrayList<>(learnerClassIds(actor.id()));
    classes.add(-1L);
    return repository.sessions("""
        s.start_at >= :start AND s.start_at < :end
          AND (s.learner_id=:id OR (s.class_id IN (:classes) AND s.status = 'CONFIRMED')
            OR EXISTS (SELECT 1 FROM class_signups r WHERE r.session_id=s.id AND r.learner_id=:id AND NOT r.cancelled))
        """, Map.of("id", actor.id(), "start", start, "end", end, "classes", classes));
  }

  Session findSession(long id) {
    return repository.session(id, false);
  }

  Session visibleSession(Actor actor, long id) {
    Session session = findSession(id);
    boolean allowed = actor.is("ADMIN") || actor.is("TRAINER") && session.trainerId() == actor.id()
        || actor.is("LEARNER") && (Objects.equals(session.learnerId(), actor.id())
        || session.classId() != null && (registered(id, actor.id()) || learnerClassIds(actor.id()).contains(session.classId())));
    actor.require(allowed);
    return session;
  }

  ScheduleApi.TrainerSummary trainer(Actor actor, long id) {
    Session session = visibleSession(actor, id);
    var trainer = http.user(auth, session.trainerId());
    return new ScheduleApi.TrainerSummary(trainer.id(), trainer.displayName());
  }

  boolean available(Actor actor, long packageId, OffsetDateTime start) {
    actor.require("LEARNER");
    ServiceHttp.Package pack = requirePackage(actor, packageId, "PT", start.toLocalDate());
    checkFuture(start);
    return shifts.available(pack.trainerId(), start, start.plus(PT_DURATION), false)
        && !hasConflict(pack.trainerId(), actor.id(), start, start.plus(PT_DURATION));
  }

  @Transactional
  public Session book(Actor actor, ScheduleApi.Booking form) {
    actor.require("LEARNER");
    OffsetDateTime start = form.startAt().atZoneSameInstant(ZONE).toOffsetDateTime();
    checkFuture(start);
    ServiceHttp.Package pack = requirePackage(actor, form.packageId(), "PT", start.toLocalDate());
    return createPt(actor.id(), pack.trainerId(), pack.id(), start, "PENDING_TRAINER", false);
  }

  List<ShiftsApi.Slot> slots(Actor actor, LocalDate date, Long trainerId, Long packageId) {
    long trainer;
    if (actor.is("ADMIN") && trainerId != null) {
      trainer = trainerId;
    } else if (actor.is("LEARNER") && packageId != null) {
      trainer = requirePackage(actor, packageId, "PT", date).trainerId();
    } else {
      throw Problem.forbidden();
    }
    List<ShiftsApi.Slot> slots = new ArrayList<>();
    for (var shift : shifts.shiftsFor(trainer, date)) {
      for (LocalTime time = shift.startsAt(); Duration.between(time, shift.endsAt()).compareTo(PT_DURATION) >= 0; time = time.plusHours(1)) {
        OffsetDateTime start = date.atTime(time).atZone(ZONE).toOffsetDateTime();
        OffsetDateTime end = start.plus(PT_DURATION);
        boolean available = start.isAfter(now()) && shifts.available(trainer, start, end, false)
            && !hasConflict(trainer, actor.is("LEARNER") ? actor.id() : -1, start, end);
        slots.add(new ShiftsApi.Slot(start.toString(), available));
      }
    }
    return slots;
  }

  @Transactional
  public List<Session> direct(Actor actor, ScheduleApi.DirectPt form) {
    actor.require(actor.is("ADMIN") || actor.is("TRAINER"));
    OffsetDateTime first = form.startAt().atZoneSameInstant(ZONE).toOffsetDateTime();
    checkFuture(first);
    Actor learner = new Actor(form.learnerId(), "LEARNER", false);
    ServiceHttp.Package pack = requirePackage(learner, form.packageId(), "PT", first.toLocalDate());
    long trainer = form.trainerId() == null ? pack.trainerId() : form.trainerId();
    actor.require(!actor.is("TRAINER") || trainer == actor.id() && pack.trainerId() == actor.id());
    if (actor.is("TRAINER") && form.weeks() > 1) {
      throw Problem.invalid("HLV chỉ xếp một buổi đã thỏa thuận ngoài ca.");
    }
    ServiceHttp.Profile trainerProfile = http.user(auth, trainer);
    if (!trainerProfile.role().equals("TRAINER") || !trainerProfile.status().equals("ACTIVE")) {
      throw Problem.invalid("Chọn huấn luyện viên đang hoạt động.");
    }
    List<Session> bookings = new ArrayList<>();
    for (int week = 0; week < form.weeks(); week++) {
      OffsetDateTime start = first.plusWeeks(week);
      if (!active(pack, start.toLocalDate())) {
        throw Problem.conflict("PACKAGE_EXPIRED", "Một buổi lặp nằm ngoài thời hạn gói.");
      }
      bookings.add(createPt(learner.id(), trainer, pack.id(), start, "CONFIRMED", actor.is("TRAINER")));
    }
    return bookings;
  }

  private Session createPt(long learner, long trainer, long packageId, OffsetDateTime start, String status, boolean outsideShift) {
    OffsetDateTime end = start.plus(PT_DURATION);
    shifts.lock(trainer);
    repository.lockLearner(learner);
    shifts.requireOpen(trainer, start, end, outsideShift);
    if (hasConflict(trainer, learner, start, end)) {
      throw Problem.conflict("SCHEDULE_CONFLICT", "HLV hoặc học viên đã có lịch trong khung giờ này.");
    }
    http.referenceTrainer(auth, trainer);
    try {
      return repository.jdbc().sql("""
          INSERT INTO sessions(type,title,trainer_id,learner_id,package_id,start_at,end_at,capacity,status)
            VALUES('PT','Buổi PT',:trainer,:learner,:package,:start,:end,1,:status) RETURNING *
          """).param("trainer", trainer).param("learner", learner).param("package", packageId)
          .param("start", start).param("end", end).param("status", status).query(ScheduleRepository.SESSION).single();
    } catch (DataIntegrityViolationException conflict) {
      throw Problem.conflict("SCHEDULE_CONFLICT", "Khung giờ vừa được đặt. Hãy chọn giờ khác.");
    }
  }

  private boolean hasConflict(long trainer, long learner, OffsetDateTime start, OffsetDateTime end) {
    return repository.jdbc().sql("""
        SELECT EXISTS (SELECT 1 FROM sessions s
          WHERE s.status IN ('PENDING_TRAINER','CONFIRMED') AND s.start_at < :end AND s.end_at > :start
            AND (s.trainer_id=:trainer OR s.learner_id=:learner OR EXISTS (
              SELECT 1 FROM class_signups r WHERE r.session_id=s.id AND r.learner_id=:learner AND NOT r.cancelled)))
        """).param("trainer", trainer).param("learner", learner).param("start", start).param("end", end)
        .query(Boolean.class).single();
  }

  List<Session> requests(Actor actor) {
    actor.require("TRAINER");
    return repository.sessions("s.trainer_id=:id AND s.status='PENDING_TRAINER' AND s.start_at >= :now",
        Map.of("id", actor.id(), "now", now()));
  }

  @Transactional
  public Session decide(Actor actor, long id, boolean approve, String reason) {
    actor.require("TRAINER");
    Session session = repository.session(id, true);
    actor.require(session.trainerId() == actor.id());
    if (!session.status().equals("PENDING_TRAINER") || !OffsetDateTime.parse(session.startAt()).isAfter(now())) {
      throw Problem.conflict("REQUEST_CLOSED", "Yêu cầu này đã được xử lý hoặc đã hết hạn.");
    }
    return repository.setStatus(id, approve ? "CONFIRMED" : "REJECTED", reason);
  }

  @Transactional
  public Session signup(Actor actor, long id, long packageId) {
    actor.require("LEARNER");
    return enroll(actor, id, packageId, false);
  }

  @Transactional
  public Session walkIn(long id, long learner, long packageId) {
    return enroll(new Actor(learner, "LEARNER", false), id, packageId, true);
  }

  @Transactional
  public Session moveSignup(Actor actor, long oldId, long targetId, long packageId) {
    actor.require("LEARNER");
    if (oldId == targetId) {
      throw Problem.invalid("Chọn một buổi khác.");
    }
    repository.lockLearner(actor.id());
    repository.session(Math.min(oldId, targetId), true);
    repository.session(Math.max(oldId, targetId), true);
    Session old = repository.session(oldId, false);
    Session target = repository.session(targetId, false);
    if (!old.type().equals("GROUP10") || !target.type().equals("GROUP10")
        || !Objects.equals(registration(oldId, actor.id()).packageId(), packageId)) {
      throw Problem.conflict("WRONG_CLASS", "Chọn một buổi khác thuộc HLV phụ trách của bạn.");
    }
    withdraw(actor, oldId);
    return enroll(actor, targetId, packageId, false);
  }

  @Transactional
  public List<Session> fixedClass(Actor admin, long classId, long learnerId, long packageId) {
    admin.require("ADMIN");
    Actor learner = new Actor(learnerId, "LEARNER", false);
    ServiceHttp.Package pack = requirePackage(learner, packageId, "GROUP10", LocalDate.now(ZONE));
    var group = requireClassType(repository.classInfo(classId, true), "GROUP10");
    if (!(pack.trainerId() != null ? Objects.equals(pack.trainerId(), group.trainerId())
        : Objects.equals(pack.classId(), classId))) {
      throw Problem.conflict("WRONG_PACKAGE", "Gói này không dùng được cho lớp đã chọn.");
    }
    repository.lockLearner(learnerId);
    List<Session> enrolled = new ArrayList<>();
    boolean hasEligibleSession = false;
    for (Session session : repository.sessions(
        "s.class_id=:class AND coalesce(s.assigned_trainer_id,s.trainer_id)=:trainer AND s.status='CONFIRMED' AND s.start_at>now()",
        Map.of("class", classId, "trainer", group.trainerId()))) {
      if (!active(pack, OffsetDateTime.parse(session.startAt()).atZoneSameInstant(ZONE).toLocalDate())) {
        continue;
      }
      hasEligibleSession = true;
      if (!hasSignupRecord(session.id(), learnerId)) {
        enroll(learner, session.id(), packageId, false);
        enrolled.add(session);
        continue;
      }
      int assigned = repository.jdbc().sql("""
          UPDATE class_signups SET package_id=:package
          WHERE session_id=:session AND learner_id=:learner AND NOT cancelled
          """).param("package", packageId).param("session", session.id()).param("learner", learnerId).update();
      if (assigned > 0) enrolled.add(session);
    }
    if (!hasEligibleSession) {
      throw Problem.conflict("NO_SESSIONS", "Không có buổi nào của lớp nằm trong thời hạn gói.");
    }
    repository.jdbc().sql("""
        INSERT INTO fixed_group_schedules(class_id,learner_id,package_id,end_date) VALUES(:class,:learner,:package,:end)
        ON CONFLICT(class_id,learner_id) DO UPDATE SET package_id=EXCLUDED.package_id,end_date=EXCLUDED.end_date
        """).param("class", classId).param("learner", learnerId).param("package", packageId)
        .param("end", LocalDate.parse(pack.endDate())).update();
    return enrolled;
  }

  @Transactional
  public void applyFixedEnrollment(long sessionId, long learnerId, long packageId) {
    repository.lockLearner(learnerId);
    boolean assigned = repository.jdbc().sql("""
        SELECT EXISTS (SELECT 1 FROM sessions s JOIN fixed_group_schedules f ON f.class_id=s.class_id
          WHERE s.id=:session AND f.learner_id=:learner AND f.package_id=:package)
        """).param("session", sessionId).param("learner", learnerId).param("package", packageId)
        .query(Boolean.class).single();
    if (assigned && !hasSignupRecord(sessionId, learnerId)) {
      enroll(new Actor(learnerId, "LEARNER", false), sessionId, packageId, false);
    }
  }

  private boolean hasSignupRecord(long session, long learner) {
    return repository.jdbc().sql("""
        SELECT EXISTS (SELECT 1 FROM class_signups WHERE session_id=:session AND learner_id=:learner)
        """).param("session", session).param("learner", learner).query(Boolean.class).single();
  }

  private Session enroll(Actor actor, long id, long packageId, boolean walkIn) {
    repository.lockLearner(actor.id());
    Session session = repository.session(id, true);
    OffsetDateTime start = OffsetDateTime.parse(session.startAt());
    if (!walkIn) {
      checkFuture(start);
    }
    ServiceHttp.Package pack = requirePackage(actor, packageId, "GROUP10",
        start.atZoneSameInstant(ZONE).toLocalDate());
    if (!"GROUP10".equals(session.type()) || !"CONFIRMED".equals(session.status())
                || !(pack.trainerId() != null ? Objects.equals(pack.trainerId(), session.packageTrainerId())
            : Objects.equals(pack.classId(), session.classId()))) {
      throw Problem.conflict("WRONG_PACKAGE", "Gói này không dùng được cho lớp đã chọn.");
    }
    if (registered(id, actor.id())) {
      return session;
    }
    if (walkIn && (start.isBefore(now().minusMinutes(20)) || start.isAfter(now().plusMinutes(30))
        || OffsetDateTime.parse(session.endAt()).isBefore(now()))) {
      throw Problem.conflict("NO_SESSION_NOW", "Buổi walk-in đã qua 20 phút hoặc chưa mở điểm danh.");
    }
    int count = repository.jdbc().sql("SELECT count(*) FROM class_signups WHERE session_id=:id AND NOT cancelled")
        .param("id", id).query(Integer.class).single();
    if (count >= 10) {
      throw Problem.conflict("CLASS_FULL", "Lớp đã đủ 10 chỗ.");
    }
    if (walkIn) {
      List<Session> candidates = eligible(actor.id(), packageId, pack.type(), pack.classId(), pack.trainerId());
      if (candidates.isEmpty() || candidates.getFirst().id() != id) {
        throw Problem.conflict("NO_SESSION_NOW", "Hãy chờ đến buổi phù hợp hoặc quét lại để kiểm tra lịch mới.");
      }
    }
    boolean overlap = repository.jdbc().sql("""
        SELECT EXISTS (SELECT 1 FROM sessions s WHERE s.status IN ('PENDING_TRAINER','CONFIRMED')
          AND s.start_at < :end AND s.end_at > :start AND s.id<>:id
          AND (s.learner_id=:learner OR EXISTS (SELECT 1 FROM class_signups r
            WHERE r.session_id=s.id AND r.learner_id=:learner AND NOT r.cancelled)))
        """).param("learner", actor.id()).param("id", id).param("start", start)
        .param("end", OffsetDateTime.parse(session.endAt())).query(Boolean.class).single();
    if (overlap) {
      throw Problem.conflict("LEARNER_BUSY", "Bạn đã có lịch trong khung giờ này.");
    }
    repository.jdbc().sql("""
        INSERT INTO class_signups(session_id,learner_id,package_id,cancelled) VALUES(:id,:learner,:package,false)
          ON CONFLICT (session_id,learner_id) DO UPDATE SET package_id=EXCLUDED.package_id,cancelled=false
        """).param("id", id).param("learner", actor.id()).param("package", packageId).update();
    return session;
  }

  @Transactional
  public void withdraw(Actor actor, long id) {
    actor.require("LEARNER");
    repository.lockLearner(actor.id());
    Session session = repository.session(id, true);
    if (OffsetDateTime.parse(session.startAt()).isBefore(now().plusHours(3))) {
      throw Problem.conflict("CANCEL_TOO_LATE", "Hủy lớp 10 ít nhất 3 giờ trước buổi tập.");
    }
    repository.jdbc().sql("UPDATE class_signups SET cancelled=true WHERE session_id=:id AND learner_id=:learner")
        .param("id", id).param("learner", actor.id()).update();
  }

  boolean registered(long id, long learnerId) {
    return registration(id, learnerId).registered();
  }

  ServiceHttp.Registration registration(long id, long learnerId) {
    return repository.jdbc().sql("""
        SELECT package_id FROM class_signups WHERE session_id=:id AND learner_id=:learner AND NOT cancelled
        """).param("id", id).param("learner", learnerId).query(Long.class).optional()
        .map(pack -> new ServiceHttp.Registration(true, pack)).orElseGet(() -> new ServiceHttp.Registration(false, null));
  }

  List<Session> eligible(long learner, long packageId, String type, Long classId, Long trainerId) {
    OffsetDateTime now = now();
    Map<String, Object> params = Map.of("now", now, "open", now.plusMinutes(30), "learner", learner);
    if (type.equals("PT")) {
      return repository.sessions("""
          s.type='PT' AND s.learner_id=:learner AND s.status='CONFIRMED' AND s.start_at <= :open AND s.end_at >= :now
          """, params);
    }
    if (classId == null && trainerId == null || !List.of("GROUP30", "GROUP10").contains(type)) {
      return List.of();
    }
    List<Session> booked = repository.sessions("""
        s.type=:type AND s.status='CONFIRMED' AND s.start_at <= :open AND s.end_at >= :now
          AND ((s.type='GROUP30' AND s.class_id=:class) OR EXISTS (SELECT 1 FROM class_signups r WHERE r.session_id=s.id
            AND r.learner_id=:learner AND r.package_id=:package AND NOT r.cancelled))
        """, Map.of("type", type, "class", classId == null ? -1L : classId, "open", now.plusMinutes(30), "now", now,
            "learner", learner, "package", packageId));
    if (!booked.isEmpty() || !type.equals("GROUP10")) {
      return booked;
    }
    var alternatives = Map.of("class", classId == null ? -1L : classId,
        "trainer", trainerId == null ? -1L : trainerId, "learner", learner,
        "earliest", now.minusMinutes(20), "latest", now.plusMinutes(30), "lastHour", now.minusHours(1),
        "nextHour", now.plusHours(1), "now", now);
    return repository.sessions("""
        s.type='GROUP10' AND s.status='CONFIRMED' AND (s.class_id=:class OR coalesce(s.assigned_trainer_id,s.trainer_id)=:trainer)
          AND s.start_at>=:earliest AND s.start_at<=:latest AND s.end_at>=:now
          AND (SELECT count(*) FROM class_signups r WHERE r.session_id=s.id AND NOT r.cancelled)<10
          AND NOT EXISTS (SELECT FROM sessions booked JOIN class_signups r ON r.session_id=booked.id
            WHERE booked.type='GROUP10' AND r.learner_id=:learner AND NOT r.cancelled
              AND booked.status='CONFIRMED'
              AND (booked.class_id=:class OR coalesce(booked.assigned_trainer_id,booked.trainer_id)=:trainer)
              AND booked.start_at>:lastHour AND booked.start_at<:nextHour
              AND (booked.start_at>:latest OR booked.end_at<:now))
        """, alternatives);
  }

  private List<Long> learnerClassIds(long learner) {
    ServiceHttp.Package[] packages = http.to(checkin, client -> client.get()
        .uri("/internal/packages?learnerId={id}", learner).retrieve().body(ServiceHttp.Package[].class));
    if (packages == null) {
      return List.of();
    }
    LocalDate today = LocalDate.now(ZONE);
    List<Long> result = new ArrayList<>();
    for (ServiceHttp.Package pack : packages) {
      if (!active(pack, today)) continue;
      if (pack.classId() != null) result.add(pack.classId());
      if (pack.type().equals("GROUP10") && pack.trainerId() != null) {
        result.addAll(repository.jdbc().sql("SELECT id FROM classes WHERE type='GROUP10' AND status='OPEN' AND trainer_id=:id")
            .param("id", pack.trainerId()).query(Long.class).list());
      }
    }
    return result.stream().distinct().toList();
  }

  private ServiceHttp.Package requirePackage(Actor actor, long id, String type, LocalDate day) {
    ServiceHttp.Package pack = http.to(checkin, client -> client.get().uri("/internal/packages/{id}", id)
        .retrieve().body(ServiceHttp.Package.class));
    if (pack == null || pack.learnerId() != actor.id() || !pack.type().equals(type)
        || !active(pack, day) || pack.remainingSessions() == null || pack.remainingSessions() < 1) {
      throw Problem.conflict("WRONG_PACKAGE", "Gói đã chọn không còn hiệu lực hoặc không đúng loại.");
    }
    return pack;
  }

  private static boolean active(ServiceHttp.Package pack, LocalDate day) {
    return !day.isBefore(LocalDate.parse(pack.startDate())) && !day.isAfter(LocalDate.parse(pack.endDate()));
  }

  private static void checkFuture(OffsetDateTime start) {
    if (!start.isAfter(now())) {
      throw Problem.invalid("Chọn thời gian trong tương lai.");
    }
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.now(ZONE);
  }
}
