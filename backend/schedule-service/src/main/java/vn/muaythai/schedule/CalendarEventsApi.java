package vn.muaythai.schedule;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.muaythai.shared.Actor;

@RestController
class CalendarEventsApi {
  private final CalendarEvents events;
  private final ScheduleRepository repository;

  CalendarEventsApi(CalendarEvents events, ScheduleRepository repository) {
    this.events = events;
    this.repository = repository;
  }

  @GetMapping(path = "/api/sessions/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  ResponseEntity<SseEmitter> subscribe(Actor actor) {
    actor.require(actor.is("ADMIN") || actor.is("TRAINER") || actor.is("LEARNER"));
    return ResponseEntity.ok().header("Cache-Control", "no-store").body(events.subscribe());
  }

  @PostMapping("/internal/calendar/notify")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void notifyChange() {
    repository.jdbc().sql("SELECT pg_notify('muaythai_calendar', '')").query((rs, row) -> true).single();
  }
}
