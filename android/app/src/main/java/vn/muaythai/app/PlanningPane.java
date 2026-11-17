package vn.muaythai.app;

import android.widget.EditText;
import android.widget.LinearLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonObject;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.List;

final class PlanningPane {
  private static final String[] DAYS = {"Thứ 2","Thứ 3","Thứ 4","Thứ 5","Thứ 6","Thứ 7","Chủ nhật"};
  private final WorkspaceActivity work;

  PlanningPane(WorkspaceActivity work) {
    this.work = work;
  }

  void shifts(LinearLayout body, long trainer) {
    work.request(work.api.service.get("api/shifts/" + trainer), response -> {
      var rows = new ArrayList<>(Jsons.rows(response));
      if (rows.isEmpty()) {
        work.ui.label(body, "Chưa thiết lập ca làm.", 14, false);
      }
      for (JsonObject shift : rows) {
        int day = (int) Jsons.number(shift, "weekday");
        work.ui.row(body, DAYS[day - 1], time(shift, "startsAt") + " – " + time(shift, "endsAt"),
            work.role().equals("ADMIN") ? () -> edit(trainer, rows, shift) : null);
      }
      if (work.role().equals("ADMIN")) {
        work.ui.action(body, "Thêm ca làm", () -> edit(trainer, rows, null));
        work.ui.row(body, "Khung giờ đã đóng", "", () -> work.open("Khung giờ đã đóng", panel -> blocks(panel, trainer)));
      }
    });
  }

  private void edit(long trainer, List<JsonObject> existing, JsonObject shift) {
    work.open(shift == null ? "Thêm ca làm" : "Sửa ca làm", body -> {
      int[] day = {shift == null ? LocalDate.now(WorkspaceUi.ZONE).getDayOfWeek().getValue()
          : (int) Jsons.number(shift, "weekday")};
      var selected = work.ui.label(body, DAYS[day[0] - 1], 15, true);
      work.ui.action(body, "Chọn thứ", () -> new MaterialAlertDialogBuilder(work).setTitle("Chọn ngày trong tuần")
          .setItems(DAYS, (dialog, which) -> {
            day[0] = which + 1;
            selected.setText(DAYS[which]);
          }).show());
      EditText start = work.ui.time(body, "Bắt đầu", shift == null ? "09:00" : time(shift, "startsAt"));
      EditText end = work.ui.time(body, "Kết thúc", shift == null ? "19:00" : time(shift, "endsAt"));
      work.ui.action(body, "Lưu ca", () -> {
        var result = new ArrayList<>(existing);
        result.remove(shift);
        result.add(Jsons.body("weekday", day[0], "startsAt", WorkspaceUi.read(start), "endsAt", WorkspaceUi.read(end)));
        save(trainer, result);
      });
      if (shift != null) {
        work.ui.action(body, "Xóa ca", () -> work.confirm("Xóa ca làm", "Hệ thống sẽ kiểm lịch đã xếp.",
            "Xóa ca", () -> {
              var result = new ArrayList<>(existing);
              result.remove(shift);
              save(trainer, result);
            }));
      }
    });
  }

  private void save(long trainer, List<JsonObject> roster) {
    work.request(work.api.service.put("api/shifts/" + trainer, Jsons.body("shifts", roster)), result -> work.back());
  }

  private void blocks(LinearLayout body, long trainer) {
    String path = "api/shifts/" + trainer + "/blocks";
    work.ui.action(body, "Đóng khung", () -> work.open("Đóng khung giờ", panel -> {
      var date = work.ui.date(panel, "Ngày bắt đầu", LocalDate.now(WorkspaceUi.ZONE).toString());
      var start = work.ui.time(panel, "Giờ bắt đầu", "09:00");
      var until = work.ui.date(panel, "Ngày kết thúc", LocalDate.now(WorkspaceUi.ZONE).toString());
      var end = work.ui.time(panel, "Giờ kết thúc", "19:00");
      var reason = work.ui.field(panel, "Lý do", "");
      work.ui.action(panel, "Xác nhận đóng", () -> work.request(work.api.service.post(path, null,
          Jsons.body("startAt", SchedulePane.localStart(date, start), "endAt", SchedulePane.localStart(until, end),
              "reason", WorkspaceUi.read(reason))), result -> work.back()));
    }));
    work.request(work.api.service.get(path), response -> {
      var rows = Jsons.rows(response);
      if (rows.isEmpty()) work.ui.label(body, "Không có khung bị đóng.", 14, false);
      rows.forEach(block -> work.ui.row(body, Jsons.text(block, "reason"),
          SchedulePane.displayTime(Jsons.text(block, "startAt")) + " – "
              + SchedulePane.displayTime(Jsons.text(block, "endAt")),
          () -> work.confirm("Mở lại khung giờ", "Khung này sẽ mở theo ca làm.", "Mở lại",
              () -> work.request(work.api.service.delete(path + "/" + Jsons.id(block)), done -> work.refresh()))));
    });
  }

  void changes(LinearLayout body) {
    work.request(work.api.service.get("api/pt/changes"), response -> {
      var rows = Jsons.rows(response);
      if (rows.isEmpty()) work.ui.label(body, "Chưa có yêu cầu đổi lịch.", 14, false);
      rows.forEach(change -> work.ui.row(body, "Lịch mới " + SchedulePane.displayTime(Jsons.text(change, "proposedStart")),
          changeStatus(change), () -> work.open("Yêu cầu đổi lịch", panel -> renderChange(panel, change))));
    });
  }

  private void renderChange(LinearLayout body, JsonObject change) {
    work.ui.row(body, "Lịch mới " + SchedulePane.displayTime(Jsons.text(change, "proposedStart")),
        changeStatus(change), null);
    work.ui.action(body, "Xem buổi gốc", () -> work.schedule.detail(Jsons.number(change, "sessionId")));
    if (Jsons.text(change, "status").equals("PENDING") && Jsons.number(change, "recipientId") == work.userId()) {
      String base = "api/pt/changes/" + Jsons.id(change);
      work.ui.action(body, "Đồng ý", () -> decide(base + "/accept"));
      work.ui.action(body, "Từ chối", () -> decide(base + "/reject"));
    }
  }

  private void decide(String path) {
    work.request(work.api.service.post(path, null, Jsons.body()), response -> work.back());
  }

  void propose(LinearLayout body, long session) {
    var date = work.ui.date(body, "Ngày mới", LocalDate.now(WorkspaceUi.ZONE).plusDays(1).toString());
    var time = work.ui.time(body, "Giờ mới", "17:00");
    work.ui.label(body, "Lịch cũ được giữ trong lúc chờ người còn lại đồng ý.", 13, false);
    work.ui.action(body, "Gửi đề xuất", () -> work.request(work.api.service.post("api/pt/" + session + "/changes",
        null, Jsons.body("proposedStart", SchedulePane.localStart(date, time))), result -> {
      work.message("Đã gửi đề xuất đổi lịch.");
      work.back();
    }));
  }

  void showTrainerName(LinearLayout body, String endpoint) {
    var label = work.ui.label(body, "", 14, false);
    label.setVisibility(android.view.View.GONE);
    work.refreshQuietly(work.api.service.get(endpoint), result -> {
      String name = Jsons.text(Jsons.object(result), "displayName");
      if (!name.isEmpty()) {
        label.setText("HLV · " + name);
        label.setVisibility(android.view.View.VISIBLE);
      }
    });
  }

  void changeTrainer(LinearLayout body, String endpoint, String hint) {
    long[] trainer = {0};
    var selected = work.ui.label(body, "Chưa chọn HLV", 14, true);
    work.ui.action(body, "Chọn HLV", () -> work.directory.pick(true, "", rows -> work.choose("Chọn HLV",
        rows.stream().filter(row -> "ACTIVE".equals(Jsons.text(row, "status"))).collect(Collectors.toList()),
        "displayName", row -> {
          trainer[0] = Jsons.id(row);
          selected.setText(Jsons.text(row, "displayName"));
        })));
    work.ui.label(body, hint, 14, false);
    var reason = work.ui.field(body, "Lý do", "");
    work.ui.action(body, "Lưu thay đổi", () -> {
      String note = WorkspaceUi.read(reason);
      if (trainer[0] == 0 || note.isEmpty()) {
        work.error("Chọn HLV và nhập lý do.");
        return;
      }
      work.request(work.api.service.put(endpoint, Jsons.body("trainerId", trainer[0], "reason", note)), result -> {
        work.message("Đã cập nhật HLV.");
        work.back();
      });
    });
  }

  void direct(LinearLayout body, long learner) {
    work.request(work.api.service.get("api/packages?learnerId=" + learner), response -> {
      var packs = Jsons.rows(response).stream().filter(pack -> Jsons.text(pack, "type").equals("PT"))
          .collect(Collectors.toList());
      if (packs.isEmpty()) work.ui.label(body, "Học viên chưa có gói PT phù hợp.", 14, false);
      packs.forEach(pack -> work.ui.row(body, "PT 1–1", PackagesPane.summary(pack),
          () -> work.open("Chọn giờ PT", panel -> directForm(panel, learner, pack))));
    });
  }

  private void directForm(LinearLayout body, long learner, JsonObject pack) {
    boolean admin = work.role().equals("ADMIN");
    var date = work.ui.date(body, "Ngày bắt đầu", LocalDate.now(WorkspaceUi.ZONE).plusDays(1).toString());
    var time = work.ui.time(body, "Giờ bắt đầu", "17:00");
    var weeks = admin ? work.ui.field(body, "Số tuần lặp (1–12)", "1", android.text.InputType.TYPE_CLASS_NUMBER) : null;
    long[] trainer = {Jsons.number(pack, "trainerId")};
    if (admin) {
      PtSlotGrid grid = new PtSlotGrid(work, body);
      Runnable showHours = () -> grid.load("trainerId=" + trainer[0] + "&date=" + WorkspaceUi.read(date),
          start -> time.setText(OffsetDateTime.parse(start).toLocalTime().toString()));
      work.ui.action(body, "Cập nhật giờ trống", showHours);
      showHours.run();
      var name = work.ui.label(body, "HLV phụ trách gói", 14, false);
      work.ui.action(body, "Chọn HLV thay thế", () -> work.directory.pick(true, "",
          rows -> work.choose("Chọn HLV", rows, "displayName", selected -> {
            trainer[0] = Jsons.id(selected);
            name.setText(Jsons.text(selected, "displayName"));
            showHours.run();
          })));
    }
    work.ui.action(body, "Xác nhận lịch", () -> {
      Long count = weeks == null ? 1L : ProgressPane.positiveNumber(WorkspaceUi.read(weeks));
      if (count == null || count < 1 || count > 12) {
        work.error("Nhập số tuần từ 1 đến 12.");
        return;
      }
      work.request(work.api.service.post("api/pt/direct", null, Jsons.body("learnerId", learner,
          "packageId", Jsons.id(pack), "trainerId", trainer[0], "startAt", SchedulePane.localStart(date, time), "weeks", count)),
          done -> { work.message("Đã xác nhận lịch PT."); work.back(); });
    });
  }

  private static String changeStatus(JsonObject change) {
    return switch (Jsons.text(change, "status")) {
      case "ACCEPTED" -> "Đã đồng ý";
      case "REJECTED" -> "Đã từ chối";
      case "EXPIRED" -> "Đã hết hạn";
      default -> "Chờ phản hồi";
    };
  }

  private static String time(JsonObject shift, String key) {
    String value = Jsons.text(shift, key);
    return value.length() > 5 ? value.substring(0, 5) : value;
  }
}
