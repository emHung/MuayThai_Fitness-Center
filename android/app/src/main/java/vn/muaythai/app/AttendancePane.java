package vn.muaythai.app;

import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.activity.result.ActivityResultLauncher;
import com.google.gson.JsonObject;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import java.util.List;
import java.util.UUID;

final class AttendancePane {
  private final WorkspaceActivity work;
  private ActivityResultLauncher<ScanOptions> scanner;

  AttendancePane(WorkspaceActivity work) {
    this.work = work;
  }

  void prepareScanner() {
    scanner = work.registerForActivityResult(new ScanContract(), response -> {
      if (response.getContents() != null) {
        openPreview(response.getContents(), "QR", null);
      }
    });
  }

  void scanner(LinearLayout body) {
    var scannerCard = work.ui.cards.hero(body, "Quét QR của gói tập",
        "Đưa mã QR vào khung, kiểm tra gói và buổi tập trước khi xác nhận.", R.drawable.ic_qr);
    var camera = work.ui.action(scannerCard, "Mở camera", () -> {
      ScanOptions options = new ScanOptions()
          .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
          .setPrompt("Đưa mã QR vào khung")
          .setCaptureActivity(PortraitScanner.class)
          .setBeepEnabled(false);
      scanner.launch(options);
    });
    camera.setIconResource(R.drawable.ic_camera);
    camera.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
        androidx.core.content.ContextCompat.getColor(work, R.color.surface)));
    camera.setTextColor(work.ui.cards.accent());
    camera.setIconTint(android.content.res.ColorStateList.valueOf(work.ui.cards.accent()));
    var shortcuts = work.ui.cards.grid(body);
    work.ui.cards.tile(shortcuts, "Nhập mã QR", "Nhập mã thủ công", R.drawable.ic_qr,
        () -> work.open("Nhập mã", this::lookup));
    work.ui.cards.tile(shortcuts, "Tra học viên", "Tìm kiếm hồ sơ", R.drawable.ic_group,
        () -> work.open("Học viên", panel -> work.directory.list(panel, false)));
    work.ui.cards.menu(body, "Lịch sử điểm danh", "Xem và tra cứu lượt đã ghi", R.drawable.ic_calendar,
        () -> work.open("Lịch sử điểm danh", panel -> history(panel, null, null)));
    var recent = work.ui.cards.section(body, "Điểm danh gần đây", R.drawable.ic_inbox);
    work.request(work.api.service.get("api/checkins"), response -> {
      var rows = Jsons.rows(response);
      if (rows.isEmpty()) work.ui.cards.empty(recent, "Chưa có lượt điểm danh", "Các lượt đã ghi sẽ xuất hiện tại đây.", R.drawable.ic_scan);
      rows.stream().limit(3).forEach(entry -> work.ui.row(recent, Jsons.text(entry, "learnerName"),
          Jsons.text(entry, "sessionTitle") + " · " + SchedulePane.displayTime(Jsons.text(entry, "checkedInAt")),
          () -> detail(Jsons.id(entry))));
    }, message -> work.ui.label(recent, message, 13, false));
  }

  private void lookup(LinearLayout body) {
    EditText code = work.ui.field(body, "Mã gói hoặc mã hội viên", "");
    var actions = work.ui.actions(body);
    work.ui.iconAction(actions, "Tra mã gói", R.drawable.ic_qr, () -> openPreview(WorkspaceUi.read(code), "PACKAGE_CODE", null));
    work.ui.iconAction(actions, "Tra mã hội viên", R.drawable.ic_user, () -> openPreview(WorkspaceUi.read(code), "MEMBER_CODE", null));
  }

  private void openPreview(String code, String kind, Long selected) {
    if (code.isEmpty()) {
      work.error("Nhập hoặc quét mã trước.");
      return;
    }
    UUID requestKey = UUID.randomUUID();
    work.open("Xác nhận điểm danh", body -> work.request(work.api.service.post("api/checkins/preview", null,
        Jsons.body("code", code, "codeKind", kind, "packageId", selected)), response -> {
      var preview = Jsons.object(response);
      List<JsonObject> choices = Jsons.rows(preview.get("choices"));
      if (!choices.isEmpty()) {
        work.ui.label(body, "Chọn gói để điểm danh", 15, true);
        choices.forEach(pack -> work.ui.row(body, PackagesPane.title(pack), PackagesPane.summary(pack),
            () -> openPreview(code, kind, Jsons.id(pack))));
        return;
      }
      var pack = Jsons.object(preview.get("pack"));
      var session = Jsons.object(preview.get("session"));
      if (pack.size() == 0) {
        work.ui.label(body, "Học viên chưa có gói để điểm danh.", 14, false);
        return;
      }
      work.ui.row(body, Jsons.text(pack, "learnerName"), Jsons.text(pack, "memberCode"), null);
      work.ui.row(body, PackagesPane.title(pack), PackagesPane.summary(pack), null);
      work.ui.row(body, Jsons.text(session, "title", "Tập tự do"), SchedulePane.times(session), null);
      work.ui.action(body, "Xác nhận điểm danh", () -> record(
          "api/checkins/confirm", requestKey,
          Jsons.body("sessionId", session.size() == 0 ? null : Jsons.id(session), "packageId", Jsons.id(pack),
              "method", kind.equals("QR") ? "QR" : "CODE_ENTRY")));
    }));
  }

  void manual(LinearLayout body, JsonObject session) {
    long sessionId = Jsons.id(session);
    long[] packageId = {0};
    UUID requestKey = UUID.randomUUID();
    work.ui.row(body, Jsons.text(session, "title"), SchedulePane.times(session), null);
    var chosen = work.ui.label(body, "Chưa chọn học viên", 14, false);
    work.ui.action(body, "Chọn học viên và gói", () -> work.request(work.api.service.get(
        "api/packages/for-session?sessionId=" + sessionId), response -> {
      var rows = Jsons.rows(response);
      rows.forEach(pack -> pack.addProperty("choiceLabel", Jsons.text(pack, "learnerName")
          + " · " + PackagesPane.summary(pack)));
      work.choose("Chọn học viên", rows, "choiceLabel", pack -> {
        packageId[0] = Jsons.id(pack);
        chosen.setText(Jsons.text(pack, "learnerName") + " · " + PackagesPane.title(pack));
      });
    }));
    EditText reason = work.ui.field(body, "Lý do ghi thủ công", "");
    work.ui.action(body, "Ghi điểm danh", () -> {
      if (packageId[0] == 0 || WorkspaceUi.read(reason).isEmpty()) {
        work.error("Chọn gói và nhập lý do điểm danh thủ công.");
        return;
      }
      record("api/checkins/manual", requestKey, Jsons.body("sessionId", sessionId, "packageId", packageId[0],
          "reason", WorkspaceUi.read(reason)));
    });
  }

  private void record(String path, UUID key, JsonObject body) {
    work.request(work.api.service.post(path, key.toString(), body),
        result -> detail(Jsons.id(Jsons.object(result))));
  }

  void roster(LinearLayout body, JsonObject session) {
    long id = Jsons.id(session);
    boolean ended = java.time.OffsetDateTime.parse(Jsons.text(session, "endAt")).isBefore(java.time.OffsetDateTime.now());
    work.request(work.api.service.get("api/checkins/roster?sessionId=" + id), response -> {
      var rows = Jsons.rows(response);
      work.ui.label(body, rows.stream().filter(row -> Jsons.text(row, "status").equals("RECORDED")).count()
          + " người đã điểm danh", 18, true);
      if (rows.isEmpty()) work.ui.label(body, "Chưa có học viên trong danh sách.", 14, false);
      rows.forEach(row -> {
        String state = Jsons.text(row, "status");
        String label = switch (state) {
          case "RECORDED" -> "Đã đến";
          case "ABSENT" -> "Vắng";
          case "ADJUSTED" -> "Đã điều chỉnh";
          default -> "Chưa đến";
        };
        work.ui.row(body, Jsons.text(row, "learnerName"), Jsons.text(row, "memberCode") + " · " + label
                + " · Còn " + Jsons.text(row, "remainingSessions", "không tính lượt"),
            ended && state.equals("WAITING") ? () -> work.confirm("Ghi nhận vắng mặt",
                Jsons.text(row, "learnerName"), "Đánh dấu vắng",
                () -> work.request(work.api.service.put("api/checkins/roster/" + id + "/absent/"
                    + Jsons.number(row, "learnerId"), Jsons.body()), saved -> work.refresh())) : null);
      });
    });
  }

  void history(LinearLayout body, Long learnerId, Long sessionId) {
    String path = "api/checkins";
    if (learnerId != null) {
      path += "?learnerId=" + learnerId;
    } else if (sessionId != null) {
      path += "?sessionId=" + sessionId;
    }
    work.request(work.api.service.get(path), response -> {
      var rows = Jsons.rows(response);
      if (rows.isEmpty()) {
        work.ui.label(body, "Chưa có lượt điểm danh.", 14, false);
      }
      rows.forEach(entry -> work.ui.row(body, Jsons.text(entry, "learnerName"),
          Jsons.text(entry, "sessionTitle") + " · " + SchedulePane.displayTime(Jsons.text(entry, "checkedInAt"))
              + (Jsons.text(entry, "status").equals("ADJUSTED") ? " · Đã điều chỉnh" : ""),
          () -> detail(Jsons.id(entry))));
    });
  }

  private void detail(long id) {
    work.open("Kết quả điểm danh", body -> work.request(work.api.service.get("api/checkins/" + id), result -> {
      JsonObject entry = Jsons.object(result);
      boolean adjusted = Jsons.text(entry, "status").equals("ADJUSTED");
      var card = work.ui.card(body);
      work.ui.label(card, adjusted ? "Đã điều chỉnh" : "Điểm danh thành công", 22, true);
      work.ui.label(card, Jsons.text(entry, "learnerName") + " · " + Jsons.text(entry, "sessionTitle"), 14, false);
      work.ui.label(card, SchedulePane.displayTime(Jsons.text(entry, "checkedInAt")), 14, false);
      String remaining = Jsons.text(entry, "remainingAfterEvent");
      if (!remaining.isEmpty()) {
        work.ui.label(card, remaining + " buổi còn lại tại thời điểm ghi", 14, true);
      }
      if (adjusted) {
        work.request(work.api.service.get("api/checkins/" + id + "/adjustments"), history ->
            Jsons.rows(history).forEach(change -> work.ui.row(body, "Lý do điều chỉnh",
                Jsons.text(change, "reason") + " · Còn " + Jsons.text(change, "remainingAfter", "không tính lượt"), null)));
      } else if (work.canAdjust()) {
        work.ui.action(body, "Điều chỉnh", () -> work.open("Điều chỉnh điểm danh", form -> adjust(form, id)));
      }
    }));
  }

  private void adjust(LinearLayout body, long id) {
    UUID key = UUID.randomUUID();
    EditText reason = work.ui.field(body, "Lý do điều chỉnh", "");
    work.ui.action(body, "Xác nhận hoàn lượt", () -> work.request(work.api.service.post(
        "api/checkins/" + id + "/adjustments", key.toString(), Jsons.body("reason", WorkspaceUi.read(reason))),
        saved -> { work.message("Đã lưu điều chỉnh."); work.back(); }));
  }
}
