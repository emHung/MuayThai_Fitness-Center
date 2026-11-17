package vn.muaythai.schedule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.muaythai.shared.Actor;

@RestController
class ShiftsApi {
  record Shift(@Min(1) @Max(7) int weekday, @NotNull LocalTime startsAt, @NotNull LocalTime endsAt) {}
  record Shifts(@NotNull @Size(max = 42) List<@Valid Shift> shifts) {}
  record Block(@NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt,
      @NotBlank @Size(max = 240) String reason) {}
  record BlockView(long id, String startAt, String endAt, String reason) {}
  record Slot(String startAt, boolean available) {}

  private final ShiftService shifts;
  private final ScheduleService schedule;

  ShiftsApi(ShiftService shifts, ScheduleService schedule) {
    this.shifts = shifts;
    this.schedule = schedule;
  }

  @GetMapping("/api/shifts/{trainer}")
  List<Shift> list(Actor actor, @PathVariable long trainer) {
    actor.require(actor.is("ADMIN") || actor.is("TRAINER") && actor.id() == trainer);
    return shifts.week(trainer);
  }

  @PutMapping("/api/shifts/{trainer}")
  List<Shift> save(Actor actor, @PathVariable long trainer, @Valid @RequestBody Shifts input) {
    actor.require("ADMIN");
    return shifts.replace(trainer, input.shifts());
  }

  @GetMapping("/api/shifts/{trainer}/blocks")
  List<BlockView> blocks(Actor actor, @PathVariable long trainer) {
    actor.require("ADMIN");
    return shifts.blocks(trainer);
  }

  @PostMapping("/api/shifts/{trainer}/blocks")
  @ResponseStatus(HttpStatus.CREATED)
  BlockView block(Actor actor, @PathVariable long trainer, @Valid @RequestBody Block input) {
    actor.require("ADMIN");
    return shifts.close(trainer, input);
  }

  @DeleteMapping("/api/shifts/{trainer}/blocks/{block}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void reopen(Actor actor, @PathVariable long trainer, @PathVariable long block) {
    actor.require("ADMIN");
    shifts.reopen(trainer, block);
  }

  @GetMapping("/api/pt/slots")
  List<Slot> slots(Actor actor, @RequestParam LocalDate date, @RequestParam(required = false) Long trainerId,
      @RequestParam(required = false) Long packageId) {
    return schedule.slots(actor, date, trainerId, packageId);
  }
}
