package vn.muaythai.progress;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import vn.muaythai.shared.Actor;
import vn.muaythai.shared.Problem;
import vn.muaythai.shared.ServiceHttp;
import vn.muaythai.shared.ServiceHttp.Notice;

@RestController
class NotificationsApi {
  record Broadcast(@NotBlank @Size(max=120) String title, @NotBlank @Size(max=600) String message) {}
  record Item(Notice notice, boolean read) {}

  private final JdbcClient db;
  private final ServiceHttp http;
  private final String schedule;
  private final String checkin;

  NotificationsApi(JdbcClient db, ServiceHttp http, @Value("${app.schedule-uri}") String schedule,
      @Value("${app.checkin-uri}") String checkin) {
    this.db = db;
    this.http = http;
    this.schedule = schedule;
    this.checkin = checkin;
  }

  @PostMapping("/api/notifications")
  @ResponseStatus(HttpStatus.CREATED)
  void publish(Actor actor, @Valid @RequestBody Broadcast form) {
    actor.require("ADMIN");
    db.sql("INSERT INTO broadcasts(title,message,actor_id) VALUES(:title,:message,:actor)")
        .param("title", form.title().strip()).param("message", form.message().strip()).param("actor", actor.id()).update();
  }

  @GetMapping("/api/notifications")
  List<Item> inbox(Actor actor) {
    List<Notice> notices = new ArrayList<>();
    if (actor.is("LEARNER") || actor.is("ADMIN")) {
      notices.addAll(db.sql("SELECT * FROM broadcasts ORDER BY id DESC LIMIT 100").query((rs,row) -> new Notice(
          "center-" + rs.getLong("id"), rs.getString("title"), rs.getString("message"),
          rs.getObject("created_at", OffsetDateTime.class).toString(), null, "CENTER")).list());
    }
    if (!actor.is("ADMIN")) {
      Notice[] scheduleItems = http.to(schedule, client -> client.get()
          .uri("/internal/notices?userId={id}&role={role}", actor.id(), actor.role()).retrieve().body(Notice[].class));
      if (scheduleItems != null) notices.addAll(List.of(scheduleItems));
    }
    if (actor.is("LEARNER")) {
      Notice[] packages = http.to(checkin, client -> client.get().uri("/internal/notices?userId={id}", actor.id())
          .retrieve().body(Notice[].class));
      if (packages != null) notices.addAll(List.of(packages));
    }
    if (actor.is("TRAINER")) {
      var reviewed = new HashSet<>(db.sql("SELECT session_id FROM session_notes WHERE trainer_id=:id")
          .param("id", actor.id()).query(Long.class).list());
      notices.removeIf(item -> item.kind().equals("REVIEW") && reviewed.contains(item.sessionId()));
    }
    var reads = new HashSet<>(db.sql("SELECT notice_key FROM notice_reads WHERE user_id=:id")
        .param("id", actor.id()).query(String.class).list());
    return notices.stream().sorted(Comparator.comparing((Notice n) -> OffsetDateTime.parse(n.at())).reversed())
        .limit(150).map(n -> new Item(n, reads.contains(n.id()))).toList();
  }

  @PutMapping("/api/notifications/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void markRead(Actor actor, @RequestParam String id) {
    if (!inbox(actor).stream().anyMatch(item -> item.notice().id().equals(id))) {
      throw Problem.missing("Không tìm thấy thông báo.");
    }
    db.sql("INSERT INTO notice_reads(user_id,notice_key) VALUES(:user,:id) ON CONFLICT DO NOTHING")
        .param("user", actor.id()).param("id", id).update();
  }
}
