package vn.muaythai.app;

import android.net.Uri;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

final class ClassPane {
  private static final String[] DAYS = {"Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"};
  private final WorkspaceActivity work;

  ClassPane(WorkspaceActivity work) {
    this.work = work;
  }

  void show(LinearLayout body) {
    work.ui.action(body, "Tạo lớp 30", () -> work.open("Tạo lớp 30", panel -> form(panel, "GROUP30", null)));
    work.ui.action(body, "Tạo lớp 10", () -> work.open("Tạo lớp 10", panel -> form(panel, "GROUP10", null)));
    var query = work.ui.field(body, "Tên lớp cần tìm", "");
    LinearLayout results = new LinearLayout(work);
    results.setOrientation(LinearLayout.VERTICAL);
    Runnable search = () -> {
      results.removeAllViews();
      String filter = "?q=" + Uri.encode(WorkspaceUi.read(query));
      load(results, "GROUP30", "Lớp 30", filter);
      load(results, "GROUP10", "Lớp 10", filter);
    };
    work.ui.action(body, "Tìm lớp", search);
    work.ui.add(body, results, 8);
    search.run();
  }

  private void load(LinearLayout parent, String type, String label, String filter) {
    LinearLayout list = new LinearLayout(work);
    list.setOrientation(LinearLayout.VERTICAL);
    work.ui.label(list, label, 15, true);
    work.ui.add(parent, list, 8);
    work.request(work.api.service.get(path(type) + filter), response -> {
      var rows = Jsons.rows(response);
      if (rows.isEmpty()) {
        work.ui.label(list, "Chưa có lớp.", 13, false);
      }
      rows.forEach(item -> work.ui.row(list, Jsons.text(item, "title"), label(item),
          () -> work.open(Jsons.text(item, "title"), panel -> detail(panel, type, Jsons.id(item)))));
    });
  }

  private void detail(LinearLayout body, String type, long id) {
    String base = path(type) + "/" + id;
    work.request(work.api.service.get(base), response -> {
      JsonObject info = Jsons.object(response);
      work.images.show(body, base + "/image", "api/packages/types/" + type + "/image", false,
          work.role().equals("ADMIN"));
      String status = Jsons.text(info, "status");
      work.ui.row(body, Jsons.text(info, "title"), label(info), null);
      if (status.equals("PENDING")) {
        change(body, base, "approve", "Duyệt lớp");
        change(body, base, "reject", "Từ chối");
      }
      if (status.equals("OPEN")) {
        work.ui.action(body, "Sửa lịch lặp", () -> work.open("Sửa lịch lớp", panel -> form(panel, type, info)));
        change(body, base, "stop", "Dừng lớp");
      }
    });
  }

  private void change(LinearLayout body, String base, String action, String label) {
    work.ui.action(body, label, () -> work.confirm(label, "Xác nhận cập nhật trạng thái lớp.", label,
        () -> work.request(work.api.service.post(base + "/" + action, null, Jsons.body()), result -> work.refresh())));
  }

  void form(LinearLayout body, String type, JsonObject current) {
    boolean editing = current != null;
    JsonObject source = editing ? current : new JsonObject();
    EditText title = work.ui.field(body, "Tên lớp", Jsons.text(source, "title"));
    EditText start = work.ui.time(body, "Giờ bắt đầu", hhmm(Jsons.text(source, "startsAt", "18:00")));
    EditText end = work.ui.time(body, "Giờ kết thúc", hhmm(Jsons.text(source, "endsAt", "19:00")));
    Long[] trainer = {editing ? Jsons.number(source, "trainerId") : null};
    if (work.role().equals("ADMIN")) {
      var trainerText = work.ui.label(body, trainer[0] == null ? "Chưa chọn HLV" : "HLV đã gán", 13, false);
      work.ui.action(body, "Chọn HLV", () -> work.directory.pick(true, "",
          rows -> work.choose("Chọn huấn luyện viên", rows, "displayName", row -> {
            trainer[0] = Jsons.id(row);
            trainerText.setText(Jsons.text(row, "displayName"));
          })));
    }
    boolean[] checked = days(source);
    var selected = work.ui.label(body, daySummary(checked), 13, false);
    work.ui.action(body, "Chọn ngày lặp", () -> new MaterialAlertDialogBuilder(work).setTitle("Ngày tập trong tuần")
        .setMultiChoiceItems(DAYS, checked, (dialog, which, setting) -> {
          checked[which] = setting;
          selected.setText(daySummary(checked));
        }).setPositiveButton("Xong", null).show());
    work.ui.action(body, editing ? "Lưu thay đổi" : "Tạo lịch lớp", () -> {
      List<Integer> weekdays = new ArrayList<>();
      for (int i = 0; i < checked.length; i++) {
        if (checked[i]) {
          weekdays.add(i + 1);
        }
      }
      if (weekdays.isEmpty() || trainer[0] == null && work.role().equals("ADMIN")) {
        work.error("Chọn ngày lặp và HLV cho lớp.");
        return;
      }
      var payload = Jsons.body("title", WorkspaceUi.read(title), "trainerId", trainer[0], "weekdays", weekdays,
          "startsAt", WorkspaceUi.read(start), "endsAt", WorkspaceUi.read(end));
      var call = editing ? work.api.service.put(path(type) + "/" + Jsons.id(source), payload)
          : work.api.service.post(path(type), null, payload);
      work.request(call, saved -> { work.message("Đã lưu lớp."); work.back(); });
    });
  }

  private static boolean[] days(JsonObject source) {
    boolean[] checked = new boolean[DAYS.length];
    if (source.has("weekdays") && source.get("weekdays").isJsonArray()) {
      source.getAsJsonArray("weekdays").forEach(day -> checked[day.getAsInt() - 1] = true);
    }
    return checked;
  }

  private static String daySummary(boolean[] checked) {
    List<String> labels = new ArrayList<>();
    for (int i = 0; i < checked.length; i++) {
      if (checked[i]) {
        labels.add(DAYS[i]);
      }
    }
    return labels.isEmpty() ? "Chưa chọn ngày lặp" : String.join(", ", labels);
  }

  private static String path(String type) {
    return type.equals("GROUP10") ? "api/classes10" : "api/classes30";
  }

  private static String label(JsonObject info) {
    String state = switch (Jsons.text(info, "status")) {
      case "OPEN" -> "Đang mở";
      case "PENDING" -> "Chờ duyệt";
      case "STOPPED" -> "Đã dừng";
      default -> "Đã từ chối";
    };
    return hhmm(Jsons.text(info, "startsAt")) + " – " + hhmm(Jsons.text(info, "endsAt")) + " · " + state;
  }

  private static String hhmm(String time) {
    return time.length() > 5 ? time.substring(0, 5) : time;
  }
}
