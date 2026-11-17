package vn.muaythai.checkin;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.muaythai.checkin.AttendanceApi.Adjustment;
import vn.muaythai.checkin.AttendanceApi.Entry;
import vn.muaythai.checkin.PackagesApi.Pack;
import vn.muaythai.shared.Actor;
import vn.muaythai.shared.Problem;
import vn.muaythai.shared.ServiceHttp;
import vn.muaythai.shared.ServiceHttp.Session;

@Service
class AttendanceService {
  private static final int SESSION_LOCK = 81107;
  private static final int MEMBER_LOCK = 81108;
  private static final int REQUEST_LOCK = 81109;

  private final AttendanceRepository repository;
  private final PackageService packages;
  private final ServiceHttp http;
  private final String schedule;
  private final String auth;

  AttendanceService(AttendanceRepository repository, PackageService packages, ServiceHttp http,
      @Value("${app.schedule-uri}") String schedule, @Value("${app.auth-uri}") String auth) {
    this.repository = repository;
    this.packages = packages;
    this.http = http;
    this.schedule = schedule;
    this.auth = auth;
  }

  AttendanceApi.Preview preview(AttendanceApi.PreviewForm form) {
    Pack pack;
    if (form.codeKind() == AttendanceApi.CodeKind.MEMBER_CODE) {
      long learner = packages.lookupMember(form.code());
      if (form.packageId() == null) {
        return new AttendanceApi.Preview(null, null, packages.list(learner));
      }
      pack = packages.find(form.packageId(), false);
      if (pack.learnerId() != learner) {
        throw Problem.conflict("WRONG_PACKAGE", "Gói này không thuộc học viên đã chọn.");
      }
    } else {
      pack = packages.scan(form.code());
    }
    requireActiveLearner(pack);
    packages.requireUsable(pack, LocalDate.now(PackageService.ZONE));
    if (pack.type().equals("MEMBERSHIP")) {
      return new AttendanceApi.Preview(pack, null, List.of());
    }
    Session[] candidates = http.to(schedule, client -> client.get()
        .uri(uri -> uri.path("/internal/sessions/eligible").queryParam("learnerId", pack.learnerId())
            .queryParam("packageId", pack.id()).queryParam("type", pack.type())
                        .queryParamIfPresent("classId", java.util.Optional.ofNullable(pack.classId()))
            .queryParamIfPresent("trainerId", java.util.Optional.ofNullable(pack.trainerId())).build())
        .retrieve().body(Session[].class));
    if (candidates == null || candidates.length == 0) {
      throw Problem.conflict("NO_SESSION_NOW", "Không có buổi phù hợp trong cửa sổ điểm danh.");
    }
    Session match = java.util.Arrays.stream(candidates)
        .filter(session -> "GROUP10".equals(pack.type()) || matches(pack, session)).findFirst()
        .orElseThrow(() -> Problem.conflict("WRONG_PACKAGE", "Mã gói không khớp với buổi đã đặt."));
    return new AttendanceApi.Preview(pack, match, List.of());
  }

  @Transactional
  public Entry record(Actor actor, UUID key, Long sessionId, long packageId, AttendanceApi.Method method,
      String reason) {
    repository.lock(REQUEST_LOCK, key.hashCode());
    var replay = repository.keyedEntry(actor.id(), key);
    if (replay.isPresent()) {
      Entry stored = replay.get();
      if (!Objects.equals(stored.sessionId(), sessionId) || stored.packageId() != packageId
          || !stored.method().equals(method.name()) || !Objects.equals(stored.reason(), reason)) {
        throw Problem.conflict("IDEMPOTENCY_CONFLICT", "Mã lần gửi đã gắn với nội dung khác.");
      }
      return stored;
    }
    Pack snapshot = packages.find(packageId, false);
    repository.lock(sessionId == null ? MEMBER_LOCK : SESSION_LOCK,
        sessionId == null ? snapshot.learnerId() : sessionId);
    Pack pack = packages.find(packageId, true);
    OffsetDateTime now = OffsetDateTime.now(PackageService.ZONE);
    Session session = sessionId == null ? null : http.to(schedule, client -> client.get()
        .uri("/internal/sessions/{id}/attendance", sessionId).retrieve().body(Session.class));
    validateSession(actor, pack, session, now);
    var other = duplicate(pack.learnerId(), sessionId, now.toLocalDate());
    if (other.isPresent()) {
      Entry stored = other.get();
      throw Problem.conflict("ALREADY_CHECKED_IN", "Học viên đã có lượt điểm danh.",
          Map.of("entry", stored, "checkedInAt", stored.checkedInAt()));
    }
    if (session != null && session.capacity() > 1 && counted(session.id()) >= session.capacity()) {
      throw Problem.conflict("CLASS_FULL", "Buổi này đã đủ chỗ.");
    }
    requireActiveLearner(pack);
    packages.requireUsable(pack, now.toLocalDate());
    if (session != null && session.type().equals("GROUP10")) {
      ensureGroupRegistration(actor, pack, session);
    }
    Integer remaining = pack.remainingSessions() == null ? null : pack.remainingSessions() - 1;
    if (remaining != null) {
      repository.jdbc().sql("UPDATE packages SET used_sessions=used_sessions+1 WHERE id=:id")
          .param("id", pack.id()).update();
    }
    long id = repository.jdbc().sql("""
        INSERT INTO checkins(session_id,package_id,learner_id,trainer_id,learner_name,session_title,
          service_date,actor_id,method,reason,checked_in_at,remaining_after_event,request_key)
        VALUES(:session,:package,:learner,:trainer,:name,:title,:day,:actor,:method,:reason,:now,:remaining,:key)
        RETURNING id
        """).param("session", sessionId).param("package", packageId).param("learner", pack.learnerId())
        .param("trainer", session == null ? null : session.trainerId()).param("name", pack.learnerName())
        .param("title", session == null ? "Tập tự do" : session.title()).param("day", now.toLocalDate())
        .param("actor", actor.id()).param("method", method.name()).param("reason", reason).param("now", now)
        .param("remaining", remaining).param("key", key).query(Long.class).single();
    return repository.entry(id);
  }

  private void validateSession(Actor actor, Pack pack, Session session, OffsetDateTime now) {
    if (session == null) {
      actor.require("ADMIN");
      if (!pack.type().equals("MEMBERSHIP")) {
        throw Problem.invalid("Chọn buổi tập cho gói này.");
      }
      return;
    }
    actor.require(actor.is("ADMIN") || actor.is("TRAINER") && actor.id() == session.trainerId());
    if (!pack.type().equals(session.type()) || !session.type().equals("GROUP10") && !matches(pack, session)) {
      throw Problem.conflict("WRONG_PACKAGE", "Mã gói không khớp với buổi đã đặt.");
    }
    if (!session.status().equals("CONFIRMED") || now.isBefore(OffsetDateTime.parse(session.startAt()).minusMinutes(30))
        || now.isAfter(OffsetDateTime.parse(session.endAt()))) {
      throw Problem.conflict("NO_SESSION_NOW", "Buổi đã hủy hoặc ngoài cửa sổ điểm danh.");
    }
  }

  private void ensureGroupRegistration(Actor actor, Pack pack, Session session) {
    ServiceHttp.Registration signup = http.to(schedule, client -> client.get()
        .uri("/internal/sessions/{id}/signup?learnerId={learner}", session.id(), pack.learnerId())
        .retrieve().body(ServiceHttp.Registration.class));
    if (signup != null && signup.registered() && Objects.equals(signup.packageId(), pack.id())) {
      return;
    }
    if (!matches(pack, session)) {
      throw Problem.conflict("WRONG_PACKAGE", "Mã gói không khớp với buổi đã đặt.");
    }
    if (signup != null && signup.registered()) {
      return;
    }
    actor.require("ADMIN");
    http.to(schedule, client -> client.post().uri("/internal/sessions/{id}/walk-in", session.id())
        .body(Map.of("learnerId", pack.learnerId(), "packageId", pack.id())).retrieve().body(Session.class));
  }

  private static boolean matches(Pack pack, Session session) {
    if (!pack.type().equals(session.type())) {
      return false;
    }
    return session.type().equals("PT")
        ? Objects.equals(session.learnerId(), pack.learnerId()) && Objects.equals(session.packageId(), pack.id())
        : session.type().equals("GROUP10") && pack.trainerId() != null
            ? Objects.equals(pack.trainerId(), session.packageTrainerId()) : Objects.equals(pack.classId(), session.classId());
  }

  private void requireActiveLearner(Pack pack) {
    ServiceHttp.Profile learner = http.user(auth, pack.learnerId());
    if (learner == null || !"ACTIVE".equals(learner.status())) {
      throw Problem.conflict("ACCOUNT_LOCKED", "Tài khoản học viên đã bị khóa.");
    }
  }

  private Session session(long id) {
    return http.to(schedule, client -> client.get().uri("/internal/sessions/{id}", id)
        .retrieve().body(Session.class));
  }

  private java.util.Optional<Entry> duplicate(long learner, Long session, LocalDate date) {
    var jdbc = repository.jdbc();
    var query = session == null
        ? jdbc.sql(AttendanceRepository.ENTRIES + " WHERE c.learner_id=:learner AND c.session_id IS NULL AND c.service_date=:day")
            .param("day", date)
        : jdbc.sql(AttendanceRepository.ENTRIES + " WHERE c.learner_id=:learner AND c.session_id=:session")
            .param("session", session);
    return query.param("learner", learner).query(AttendanceRepository.ENTRY_ROW).optional();
  }

  private int counted(long session) {
    return repository.jdbc().sql("""
        SELECT count(*) FROM checkins c WHERE c.session_id=:session
          AND NOT EXISTS (SELECT FROM checkin_adjustments a WHERE a.checkin_id=c.id)
        """).param("session", session).query(Integer.class).single();
  }

  @Transactional
  public boolean exists(long session) {
    repository.lock(SESSION_LOCK, session);
    return counted(session) != 0;
  }

  List<Entry> list(Actor actor, Long learner, Long session, LocalDate date) {
    return repository.jdbc().sql(AttendanceRepository.ENTRIES + """
        WHERE (:role='ADMIN' OR (:role='LEARNER' AND c.learner_id=:viewer)
          OR (:role='TRAINER' AND c.trainer_id=:viewer))
          AND (CAST(:learner AS bigint) IS NULL OR c.learner_id=:learner)
          AND (CAST(:session AS bigint) IS NULL OR c.session_id=:session)
          AND (CAST(:day AS date) IS NULL OR c.service_date=:day)
        ORDER BY c.checked_in_at DESC LIMIT 250
        """).param("role", actor.role()).param("viewer", actor.id()).param("learner", learner)
        .param("session", session).param("day", date).query(AttendanceRepository.ENTRY_ROW).list();
  }

  Entry detail(Actor actor, long id) {
    Entry entry = repository.entry(id);
    actor.require(actor.is("ADMIN") || actor.is("TRAINER") && Objects.equals(entry.trainerId(), actor.id())
        || actor.is("LEARNER") && entry.learnerId() == actor.id());
    return entry;
  }

  List<Adjustment> adjustments(long id) {
    return repository.adjustments(id);
  }

  @Transactional
  public Adjustment adjust(Actor actor, long id, UUID key, String reason) {
    repository.lock(REQUEST_LOCK, key.hashCode());
    var replay = repository.keyedAdjustment(actor.id(), key);
    if (replay.isPresent()) {
      if (replay.get().checkinId() != id || !replay.get().reason().equals(reason)) {
        throw Problem.conflict("IDEMPOTENCY_CONFLICT", "Mã lần gửi đã gắn với nội dung khác.");
      }
      return replay.get();
    }
    Entry entry = repository.entry(id);
    repository.lock(entry.sessionId() == null ? MEMBER_LOCK : SESSION_LOCK,
        entry.sessionId() == null ? entry.learnerId() : entry.sessionId());
    Pack pack = packages.find(entry.packageId(), true);
    if (!repository.adjustments(id).isEmpty()) {
      throw Problem.conflict("ALREADY_ADJUSTED", "Lượt này đã được điều chỉnh.");
    }
    Integer before = pack.remainingSessions();
    Integer after = before == null ? null : before + 1;
    if (after != null) {
      repository.jdbc().sql("UPDATE packages SET used_sessions=used_sessions-1 WHERE id=:id")
          .param("id", pack.id()).update();
    }
    return repository.jdbc().sql("""
        INSERT INTO checkin_adjustments(checkin_id,actor_id,reason,adjusted_at,remaining_before,remaining_after,request_key)
        VALUES(:checkin,:actor,:reason,:now,:before,:after,:key) RETURNING *
        """).param("checkin", id).param("actor", actor.id()).param("reason", reason)
        .param("now", OffsetDateTime.now(PackageService.ZONE)).param("before", before).param("after", after)
        .param("key", key).query(AttendanceRepository.ADJUSTMENT).single();
  }
}
