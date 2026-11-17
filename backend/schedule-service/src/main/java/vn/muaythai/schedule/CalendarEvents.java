package vn.muaythai.schedule;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
class CalendarEvents {
  private static final Logger log = LoggerFactory.getLogger(CalendarEvents.class);
  private final DataSource dataSource;
  private final Duration lifetime;
  private final Duration heartbeat;
  private final Set<SseEmitter> subscribers = ConcurrentHashMap.newKeySet();
  private Connection connection;
  private Instant lastSignal = Instant.EPOCH;
  private Instant reconnectAfter = Instant.EPOCH;

  CalendarEvents(DataSource dataSource, @Value("${app.calendar-stream-duration:PT30S}") Duration lifetime,
      @Value("${app.calendar-stream-heartbeat:PT8S}") Duration heartbeat) {
    this.dataSource = dataSource;
    this.lifetime = lifetime;
    this.heartbeat = heartbeat;
  }

  synchronized SseEmitter subscribe() {
    var emitter = new SseEmitter(lifetime.toMillis());
    emitter.onCompletion(() -> subscribers.remove(emitter));
    emitter.onError(error -> subscribers.remove(emitter));
    emitter.onTimeout(() -> {
      subscribers.remove(emitter);
      emitter.complete();
    });
    subscribers.add(emitter);
    send(emitter, connection == null ? null : "ready");
    return emitter;
  }

  @Scheduled(fixedDelayString = "${app.calendar-listen-delay:PT0.5S}")
  synchronized void distribute() {
    if (subscribers.isEmpty()) {
      disconnect();
      return;
    }
    Instant now = Instant.now();
    if (now.isBefore(reconnectAfter)) {
      return;
    }
    try {
      if (connection == null) {
        connection = dataSource.getConnection();
        try (var statement = connection.createStatement()) {
          statement.execute("LISTEN muaythai_calendar");
        }
        // Refresh again once LISTEN is active, covering changes made during connection setup.
        broadcast("ready");
      }
      var notifications = connection.unwrap(PGConnection.class).getNotifications(100);
      if (notifications != null && notifications.length > 0) {
        broadcast("calendar");
      } else if (lastSignal.plus(heartbeat).isBefore(now)) {
        broadcast(null);
      }
    } catch (SQLException failure) {
      disconnect();
      reconnectAfter = now.plusSeconds(2);
      log.warn("Kết nối PostgreSQL theo dõi lịch bị gián đoạn.", failure);
    }
  }

  private void broadcast(String event) {
    subscribers.forEach(emitter -> send(emitter, event));
    lastSignal = Instant.now();
  }

  private void send(SseEmitter emitter, String event) {
    try {
      emitter.send(event != null ? SseEmitter.event().name(event).data("{}")
          : SseEmitter.event().comment("heartbeat"));
    } catch (IOException | IllegalStateException closed) {
      subscribers.remove(emitter);
      emitter.complete();
    }
  }

  @PreDestroy
  synchronized void disconnect() {
    if (connection == null) {
      return;
    }
    try (Connection listening = connection; var statement = listening.createStatement()) {
      statement.execute("UNLISTEN muaythai_calendar");
    } catch (SQLException failure) {
      log.debug("Không thể đóng listener PostgreSQL.", failure);
    } finally {
      connection = null;
    }
  }
}
