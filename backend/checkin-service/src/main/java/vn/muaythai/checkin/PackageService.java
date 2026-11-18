package vn.muaythai.checkin;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.muaythai.shared.Actor;
import vn.muaythai.shared.Problem;
import vn.muaythai.shared.ServiceHttp;

@Service
class PackageService {
  private static final Logger log = LoggerFactory.getLogger(PackageService.class);
  private record TrainerChanged(long packageId) {}
  static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final RowMapper<PackagesApi.Pack> ROW = (rs, row) -> {
    Integer total = rs.getObject("total_sessions", Integer.class);
    Integer remaining = total == null ? null : total - rs.getInt("used_sessions");
    return new PackagesApi.Pack(rs.getLong("id"), rs.getObject("code", UUID.class), rs.getLong("learner_id"),
        rs.getString("learner_name"), rs.getString("member_code"), rs.getString("type"),
        rs.getObject("trainer_id", Long.class), rs.getObject("class_id", Long.class),
        rs.getString("start_date"), rs.getString("end_date"), total, remaining);
  };

  private final JdbcClient db;
  private final ServiceHttp http;
  private final String auth;
  private final String schedule;
  private final ApplicationEventPublisher events;

  PackageService(JdbcClient db, ServiceHttp http, ApplicationEventPublisher events, @Value("${app.auth-uri}") String auth,
      @Value("${app.schedule-uri}") String schedule) {
    this.db = db;
    this.http = http;
    this.auth = auth;
    this.schedule = schedule;
    this.events = events;
  }

  PackagesApi.Pack create(PackagesApi.Form form) {
    ServiceHttp.Profile learner = http.user(auth, form.learnerId());
    if (!"LEARNER".equals(learner.role()) || !"ACTIVE".equals(learner.status())) {
      throw Problem.invalid("Chọn học viên đang hoạt động.");
    }
    Long trainer = validate(form);
    if (trainer != null) {
      http.referenceTrainer(auth, trainer);
    }
    return db.sql("""
        INSERT INTO packages(code,learner_id,learner_name,member_code,type,trainer_id,class_id,
          start_date,end_date,total_sessions)
        VALUES(:code,:learner,:name,:member,:type,:trainer,:class,:start,:end,:total) RETURNING *
        """).param("code", UUID.randomUUID()).param("learner", learner.id()).param("name", learner.displayName())
        .param("member", learner.memberCode()).param("type", form.type().name()).param("trainer", trainer)
        .param("class", form.classId()).param("start", form.startDate()).param("end", form.endDate())
        .param("total", form.totalSessions()).query(ROW).single();
  }

  private Long validate(PackagesApi.Form form) {
    if (form.endDate().isBefore(form.startDate())
        || (form.type() == PackagesApi.Type.MEMBERSHIP) != (form.totalSessions() == null)) {
      throw Problem.invalid("Kiểm tra hạn hiệu lực và số buổi của gói.");
    }
    Long trainerId = form.trainerId();
    switch (form.type()) {
      case PT -> {
        if (form.trainerId() == null || form.classId() != null) {
          throw Problem.invalid("Gói PT phải gắn với một huấn luyện viên.");
        }
        ServiceHttp.Profile trainer = http.user(auth, form.trainerId());
        if (!"TRAINER".equals(trainer.role()) || !"ACTIVE".equals(trainer.status())) {
          throw Problem.invalid("Huấn luyện viên đã chọn không khả dụng.");
        }
      }
      case GROUP10 -> {
        if (form.classId() != null) {
          Map<?, ?> info = openClass(form);
          if (!(info.get("trainerId") instanceof Number owner)) {
            throw new Problem(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "DEPENDENCY_UNAVAILABLE", "Chưa xác định được HLV của lớp.", Map.of());
          }
          trainerId = owner.longValue();
          if (form.trainerId() != null && !form.trainerId().equals(trainerId)) {
            throw Problem.invalid("HLV phải là người phụ trách lớp đã chọn.");
          }
        }
        if (trainerId == null) throw Problem.invalid("Chọn lớp 10 đang mở.");
        var trainer = http.user(auth, trainerId);
        if (!"TRAINER".equals(trainer.role()) || !"ACTIVE".equals(trainer.status())) {
          throw Problem.invalid("HLV của lớp không khả dụng.");
        }
      }
      case GROUP30 -> {
        if (form.classId() == null || form.trainerId() != null) {
          throw Problem.invalid("Chọn lớp ứng với gói.");
        }
        openClass(form);
      }
      case MEMBERSHIP -> {
        if (form.classId() != null || form.trainerId() != null) {
          throw Problem.invalid("Gói tập tự do không gắn với lớp hoặc HLV.");
        }
      }
    }
    return trainerId;
  }

  private Map<?, ?> openClass(PackagesApi.Form form) {
    Map<?, ?> info = http.to(schedule, client -> client.get().uri("/internal/classes/{id}", form.classId())
        .retrieve().body(Map.class));
    if (info == null || !form.type().name().equals(info.get("type")) || !"OPEN".equals(info.get("status"))) {
      throw Problem.invalid("Lớp đã chọn chưa mở hoặc khác loại gói.");
    }
    return info;
  }

  boolean hasTrainerHistory(long id) {
    return db.sql("""
        SELECT EXISTS (SELECT FROM packages WHERE trainer_id=:id)
            OR EXISTS (SELECT FROM checkins WHERE trainer_id=:id)
            OR EXISTS (SELECT FROM package_trainer_changes WHERE previous_trainer_id=:id OR new_trainer_id=:id)
        """).param("id", id).query(Boolean.class).single();
  }

  @Transactional
  public PackagesApi.Pack changeTrainer(long actor, long id, PackagesApi.TrainerChange form) {
    var current = find(id, true);
    if (current.type().equals("GROUP10") && current.classId() != null) {
      throw Problem.invalid("Gói lớp 10 giữ HLV của lớp đã chọn. Liên hệ quản trị để xử lý lịch lớp.");
    }
    if (!List.of("PT", "GROUP10").contains(current.type())) {
      throw Problem.invalid("Chỉ gói PT và lớp 10 có HLV phụ trách.");
    }
    if (Objects.equals(current.trainerId(), form.trainerId())) {
      return current;
    }
    var trainer = http.user(auth, form.trainerId());
    if (!"TRAINER".equals(trainer.role()) || !"ACTIVE".equals(trainer.status())) {
      throw Problem.invalid("Chọn HLV đang hoạt động.");
    }
    http.referenceTrainer(auth, trainer.id());
    var updated = db.sql("UPDATE packages SET trainer_id=:trainer,class_id=NULL WHERE id=:id RETURNING *")
        .param("id", id).param("trainer", trainer.id()).query(ROW).single();
    db.sql("""
        INSERT INTO package_trainer_changes(package_id,previous_trainer_id,new_trainer_id,actor_id,reason)
        VALUES(:id,:previous,:trainer,:actor,:reason)
        """).param("id", id).param("previous", current.trainerId()).param("trainer", trainer.id())
        .param("actor", actor).param("reason", form.reason().strip()).update();
    events.publishEvent(new TrainerChanged(id));
    return updated;
  }

  @TransactionalEventListener
  void notifyCalendar(TrainerChanged event) {
    try {
      http.to(schedule, client -> client.post().uri("/internal/calendar/notify").retrieve().toBodilessEntity());
    } catch (Problem unavailable) {
      log.warn("Chưa gửi tín hiệu cập nhật lịch sau khi đổi HLV cho gói {} ({}).", event.packageId(), unavailable.code);
    }
  }

  List<PackagesApi.Pack> list(long learner) {
    return db.sql("SELECT * FROM packages WHERE learner_id=:learner ORDER BY id DESC LIMIT 200")
        .param("learner", learner).query(ROW).list();
  }

  List<PackagesApi.Pack> visible(Actor actor, long learner) {
    actor.require(actor.is("ADMIN") || actor.is("LEARNER") && actor.id() == learner
        || actor.is("TRAINER") && assignments(actor.id()).contains(learner));
    return actor.is("TRAINER") ? list(learner).stream()
        .filter(pack -> java.util.Objects.equals(pack.trainerId(), actor.id())).toList() : list(learner);
  }

  List<PackagesApi.Pack> forSession(Actor actor, long id) {
    actor.require(actor.is("ADMIN") || actor.is("TRAINER"));
    ServiceHttp.Session session = http.to(schedule, client -> client.get().uri("/internal/sessions/{id}", id)
        .retrieve().body(ServiceHttp.Session.class));
    actor.require(actor.is("ADMIN") || actor.id() == session.trainerId());
    if (session.packageId() != null) {
      return List.of(find(session.packageId(), false));
    }
    Long[] reserved = session.type().equals("GROUP10") ? http.to(schedule, client -> client.get()
        .uri("/internal/sessions/{id}/registered-packages", id).retrieve().body(Long[].class)) : new Long[0];
    List<Long> registered = new java.util.ArrayList<>(Arrays.asList(reserved == null ? new Long[0] : reserved));
    registered.add(-1L);
    return db.sql("""
        SELECT * FROM packages WHERE (class_id=:class OR trainer_id=:trainer OR id IN (:registered))
          AND type=:type AND start_date<=:today AND end_date>=:today
          AND used_sessions<total_sessions ORDER BY learner_name,id LIMIT 200
        """).param("class", session.classId()).param("trainer", session.packageTrainerId())
        .param("registered", registered).param("type", session.type()).param("today", LocalDate.now(ZONE))
        .query(ROW).list();
  }

  List<Long> assignments(long trainer) {
    return db.sql("SELECT DISTINCT learner_id FROM packages WHERE type='PT' AND trainer_id=:trainer AND end_date >= :today")
        .param("trainer", trainer).param("today", LocalDate.now(ZONE)).query(Long.class).list();
  }

  PackagesApi.Pack find(long id, boolean lock) {
    return db.sql("SELECT * FROM packages WHERE id=:id" + (lock ? " FOR UPDATE" : ""))
        .param("id", id).query(ROW).optional().orElseThrow(() -> Problem.missing("Không tìm thấy gói."));
  }

  PackagesApi.Pack scan(String code) {
    UUID id;
    try {
      id = UUID.fromString(code.strip());
    } catch (IllegalArgumentException error) {
      throw Problem.missing("Mã gói không hợp lệ.");
    }
    return db.sql("SELECT * FROM packages WHERE code=:code").param("code", id)
        .query(ROW).optional().orElseThrow(() -> Problem.missing("Không tìm thấy mã gói."));
  }

  long lookupMember(String code) {
    ServiceHttp.Profile profile = http.to(auth, client -> client.get()
        .uri("/internal/users/by-member-code?code={code}", code).retrieve().body(ServiceHttp.Profile.class));
    if (profile == null || !"LEARNER".equals(profile.role()) || !"ACTIVE".equals(profile.status())) {
      throw Problem.missing("Không tìm thấy học viên đang hoạt động.");
    }
    return profile.id();
  }

  void requireUsable(PackagesApi.Pack pack, LocalDate day) {
    if (day.isBefore(LocalDate.parse(pack.startDate())) || day.isAfter(LocalDate.parse(pack.endDate()))) {
      throw Problem.conflict("PACKAGE_EXPIRED", "Gói chưa đến ngày hiệu lực hoặc đã hết hạn.");
    }
    if (pack.remainingSessions() != null && pack.remainingSessions() < 1) {
      throw Problem.conflict("PACKAGE_EXHAUSTED", "Gói không còn buổi tập.");
    }
  }
}
