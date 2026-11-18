package vn.muaythai.app;

import android.text.InputType;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.google.gson.JsonObject;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.function.Consumer;

final class PackagesPane {
  private record Kind(String id, String label) {}

  private static final List<Kind> TYPES = Arrays.asList(
      new Kind("PT", "PT 1–1"), new Kind("GROUP30", "Lớp 30"),
      new Kind("GROUP10", "Lớp 10"), new Kind("MEMBERSHIP", "Tập tự do"));

  private final WorkspaceActivity work;

  PackagesPane(WorkspaceActivity work) {
    this.work = work;
  }

  void mine(LinearLayout body) {
    work.request(work.api.service.get("api/packages/mine"), response -> {
      var rows = Jsons.rows(response);
      if (rows.isEmpty()) {
        work.ui.label(body, "Bạn chưa được gán gói tập.", 14, false);
      }
      rows.forEach(pack -> work.ui.row(body, title(pack), summary(pack), () -> showQr(Jsons.id(pack))));
    });
  }

  void forLearner(LinearLayout body, long learner) {
    if (work.role().equals("ADMIN")) {
      work.ui.action(body, "Gán gói", () -> work.open("Chọn loại gói", panel -> kindChooser(panel, learner)));
    }
    work.request(work.api.service.get("api/packages?learnerId=" + learner), result -> {
      var rows = Jsons.rows(result);
      if (rows.isEmpty()) {
        work.ui.label(body, "Học viên chưa có gói tập.", 14, false);
      }
      rows.forEach(pack -> work.ui.row(body, title(pack), summary(pack),
          work.role().equals("ADMIN") ? () -> work.open(title(pack), panel -> work.request(
              work.api.service.get("api/packages/" + Jsons.id(pack)),
              response -> adminPack(panel, learner, Jsons.object(response)))) : null));
    });
  }

  private void adminPack(LinearLayout panel, long learner, JsonObject pack) {
    qrContent(panel, pack);
    String type = Jsons.text(pack, "type");
    if (type.equals("PT") || type.equals("GROUP10")) {
      long currentTrainer = Jsons.number(pack, "trainerId");
      if (currentTrainer > 0) {
        work.planning.showTrainerName(panel, "api/trainers/" + currentTrainer);
      }
      work.ui.action(panel, "Đổi HLV phụ trách", () -> work.open("Đổi HLV phụ trách", form ->
          work.planning.changeTrainer(form, "api/packages/" + Jsons.id(pack) + "/trainer",
              "HLV mới áp dụng cho lịch đặt sau khi đổi. Các buổi đã đặt vẫn theo lịch hiện tại.")));
    }
    if (type.equals("GROUP10")) {
      work.ui.action(panel, "Gán lịch lớp cố định", () -> assignFixedGroup(learner, pack));
    }
  }

  private void assignFixedGroup(long learner, JsonObject pack) {
    work.request(work.api.service.get("api/classes10"), response -> {
      var classes = Jsons.rows(response).stream().filter(row -> Jsons.text(row, "status").equals("OPEN")
          && Jsons.number(row, "trainerId") == Jsons.number(pack, "trainerId")).collect(Collectors.toList());
      work.choose("Chọn lớp cố định", classes, "title", chosen -> work.request(work.api.service.put(
          "api/classes10/" + Jsons.id(chosen) + "/fixed/" + learner, Jsons.body("packageId", Jsons.id(pack))),
          saved -> work.message("Đã gán " + Jsons.rows(saved).size() + " buổi của lớp.")));
    });
  }

  void selectMine(String type, Consumer<JsonObject> choice) {
    work.request(work.api.service.get("api/packages/mine"), result -> {
      List<JsonObject> rows = Jsons.rows(result).stream().filter(pack -> type.equals(Jsons.text(pack, "type")))
          .collect(Collectors.toList());
      rows.forEach(pack -> pack.addProperty("choiceLabel", title(pack) + " · " + summary(pack)));
      work.choose("Chọn gói", rows, "choiceLabel", choice);
    });
  }

  private void kindChooser(LinearLayout body, long learner) {
    TYPES.forEach(kind -> {
      work.ui.row(body, kind.label(), "",
          () -> work.open("Gán gói " + kind.label(), content -> assign(content, learner, kind)));
    });
  }

  private void assign(LinearLayout body, long learner, Kind kind) {
    boolean membership = kind.id().equals("MEMBERSHIP");
    boolean pt = kind.id().equals("PT") || kind.id().equals("GROUP10");
    LocalDate today = LocalDate.now(WorkspaceUi.ZONE);
    EditText start = work.ui.date(body, "Hiệu lực từ", today.toString());
    EditText end = work.ui.date(body, "Hết hạn", today.plusDays(30).toString());
    EditText count = membership ? null : work.ui.field(body, "Số buổi", "10", InputType.TYPE_CLASS_NUMBER);
    Long[] target = {null};
    if (!membership) {
      var selected = work.ui.label(body, pt ? "Chưa chọn HLV" : "Chưa chọn lớp", 13, false);
      Consumer<JsonObject> chosen = row -> {
        target[0] = Jsons.id(row);
        selected.setText(Jsons.text(row, pt ? "displayName" : "title"));
      };
      if (pt) {
        work.ui.action(body, "Chọn HLV", () -> work.directory.pick(true, "",
            rows -> work.choose("Chọn HLV", rows, "displayName", chosen)));
      } else {
        String path = kind.id().equals("GROUP10") ? "api/classes10" : "api/classes30";
        work.ui.action(body, "Chọn lớp", () -> work.request(work.api.service.get(path),
            result -> work.choose("Chọn lớp", Jsons.rows(result).stream()
                .filter(row -> Jsons.text(row, "status").equals("OPEN")).collect(Collectors.toList()), "title", chosen)));
      }
    }
    work.ui.action(body, "Lưu gói", () -> {
      if (!membership && (target[0] == null || WorkspaceUi.read(count).isEmpty())) {
        work.error("Chọn HLV hoặc lớp và nhập số buổi.");
        return;
      }
      Long total = membership ? null : ProgressPane.positiveNumber(WorkspaceUi.read(count));
      if (!membership && (total == null || total < 1 || total > 500)) {
        work.error("Nhập số buổi từ 1 đến 500.");
        return;
      }
      JsonObject payload = Jsons.body("learnerId", learner, "type", kind.id(), "startDate", WorkspaceUi.read(start),
          "endDate", WorkspaceUi.read(end), "totalSessions", total, "trainerId", pt ? target[0] : null,
          "classId", !membership && !pt ? target[0] : null);
      work.request(work.api.service.post("api/packages", null, payload), result -> {
        work.message("Đã gán gói tập.");
        work.back();
        work.back();
      });
    });
  }

  private void showQr(long id) {
    work.open("Mã QR của gói", body -> work.request(work.api.service.get("api/packages/mine/" + id),
        response -> qrContent(body, Jsons.object(response))));
  }

  private void qrContent(LinearLayout body, JsonObject pack) {
    var card = work.ui.card(body);
    var image = new ImageView(work);
    image.setContentDescription("Mã QR của gói " + title(pack));
    image.setAdjustViewBounds(true);
    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    image.setMaxHeight(work.ui.dp(230));
    try {
      image.setImageBitmap(new BarcodeEncoder().encodeBitmap(Jsons.text(pack, "code"), BarcodeFormat.QR_CODE, 500, 500));
      work.ui.add(card, image, 4);
    } catch (WriterException | IllegalArgumentException error) {
      work.error("Không thể hiển thị mã QR của gói.");
    }
    work.ui.label(card, Jsons.text(pack, "learnerName"), 17, true);
    work.ui.label(card, title(pack) + " · " + summary(pack), 13, false);
    work.ui.label(card, "Mã gói " + Jsons.text(pack, "code"), 12, false);
  }

  static String title(JsonObject pack) {
    String type = Jsons.text(pack, "type");
    return TYPES.stream().filter(kind -> kind.id().equals(type)).findFirst()
        .map(Kind::label).orElse("Gói tập");
  }

  static String summary(JsonObject pack) {
    String remaining = Jsons.text(pack, "remainingSessions", "Tập tự do");
    return remaining + (remaining.equals("Tập tự do") ? "" : " buổi") + " · Hạn "
        + Jsons.text(pack, "endDate", "chưa xác định");
  }
}
