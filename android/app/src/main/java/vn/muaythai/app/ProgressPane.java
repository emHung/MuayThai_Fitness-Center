package vn.muaythai.app;

import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.google.gson.JsonObject;
import java.time.YearMonth;
import java.util.Locale;

final class ProgressPane {
  private final WorkspaceActivity work;

  ProgressPane(WorkspaceActivity work) {
    this.work = work;
  }

  void journey(LinearLayout body, Long learnerId) {
    String filter = learnerId == null ? "" : "?learnerId=" + learnerId;
    LinearLayout goals = panel(body, "Mục tiêu tháng");
    LinearLayout notes = panel(body, "Đánh giá của HLV");
    work.request(work.api.service.get("api/goals" + filter), response -> {
      var rows = Jsons.rows(response);
      if (rows.isEmpty()) {
        work.ui.label(goals, "Chưa có mục tiêu.", 13, false);
      }
      rows.forEach(goal -> work.ui.row(goals, Jsons.text(goal, "title"),
          Jsons.text(goal, "month") + " · " + Jsons.text(goal, "progress") + "% · " + Jsons.text(goal, "description"),
          work.role().equals("TRAINER") && learnerId != null
              ? () -> work.open("Cập nhật mục tiêu", form -> goal(form, learnerId, goal)) : null));
      if (work.role().equals("TRAINER") && learnerId != null) {
        work.ui.action(goals, "Thêm mục tiêu", () -> work.open("Mục tiêu tháng", form -> goal(form, learnerId, new JsonObject())));
      }
    });
    work.request(work.api.service.get("api/notes" + filter), response -> {
      var rows = Jsons.rows(response);
      if (rows.isEmpty()) {
        work.ui.label(notes, "Chưa có đánh giá.", 13, false);
      }
      rows.forEach(note -> {
        String rating = Jsons.text(note, "rating");
        work.ui.row(notes, Jsons.text(note, "sessionTitle") + (rating.isEmpty() ? "" : " · " + rating + "/5"),
            SchedulePane.displayTime(Jsons.text(note, "sessionAt")) + " · " + Jsons.text(note, "trainerName")
                + "\n" + Jsons.text(note, "content") + "\n" + Jsons.text(note, "comment"), null);
      });
    });
    if (work.role().equals("LEARNER")) {
      work.ui.action(body, "Lịch sử điểm danh", () -> work.open("Lịch sử điểm danh",
          panel -> work.attendance.history(panel, null, null)));
    }
  }

  private LinearLayout panel(LinearLayout parent, String title) {
    LinearLayout body = new LinearLayout(work);
    body.setOrientation(LinearLayout.VERTICAL);
    work.ui.label(body, title, 16, true);
    work.ui.add(parent, body, 7);
    return body;
  }

  void note(LinearLayout body, JsonObject session) {
    String path = "api/notes/session/" + Jsons.id(session);
    boolean edit = work.role().equals("TRAINER");
    work.request(work.api.service.get(path), result -> {
      var note = Jsons.object(result);
      if (edit) {
        editNote(body, session, note, path);
      } else {
        work.ui.row(body, Jsons.text(note, "content"), Jsons.text(note, "comment"), null);
        if (!Jsons.text(note, "rating").isEmpty()) {
          work.ui.label(body, Jsons.text(note, "rating") + "/5", 17, true);
        }
      }
    }, error -> {
      if (edit && error.equals("Buổi này chưa có ghi chú.")) {
        editNote(body, session, new JsonObject(), path);
      } else {
        work.error(error);
      }
    });
  }

  private void editNote(LinearLayout body, JsonObject session, JsonObject note, String path) {
    EditText content = work.ui.field(body, "Nội dung buổi tập", Jsons.text(note, "content"));
    EditText comment = work.ui.field(body, "Nhận xét", Jsons.text(note, "comment"));
    EditText rating = Jsons.text(session, "type").equals("PT")
        ? work.ui.field(body, "Điểm 1–5", Jsons.text(note, "rating"), InputType.TYPE_CLASS_NUMBER) : null;
    work.ui.action(body, "Lưu đánh giá", () -> {
      Long score = rating == null ? null : positiveNumber(WorkspaceUi.read(rating));
      if (rating != null && (score == null || score < 1 || score > 5)) {
        work.error("Nhập điểm đánh giá từ 1 đến 5.");
        return;
      }
      work.request(work.api.service.put(path, Jsons.body("content", WorkspaceUi.read(content),
          "comment", WorkspaceUi.read(comment), "rating", score)), saved -> { work.message("Đã lưu đánh giá."); work.back(); });
    });
  }

  private void goal(LinearLayout body, long learner, JsonObject goal) {
    EditText month = work.ui.field(body, "Tháng (YYYY-MM)",
        Jsons.text(goal, "month", YearMonth.now(WorkspaceUi.ZONE).toString()));
    EditText title = work.ui.field(body, "Mục tiêu", Jsons.text(goal, "title"));
    EditText description = work.ui.field(body, "Mô tả", Jsons.text(goal, "description"));
    EditText value = work.ui.field(body, "Tiến độ (%)", Jsons.text(goal, "progress", "0"), InputType.TYPE_CLASS_NUMBER);
    work.ui.action(body, "Lưu mục tiêu", () -> {
      Long score = positiveNumber(WorkspaceUi.read(value));
      if (score == null || score < 0 || score > 100) {
        work.error("Nhập tiến độ từ 0 đến 100.");
        return;
      }
      work.request(work.api.service.put("api/goals/" + learner, Jsons.body("month", WorkspaceUi.read(month),
          "title", WorkspaceUi.read(title), "description", WorkspaceUi.read(description), "progress", score)),
          saved -> { work.message("Đã lưu mục tiêu."); work.back(); });
    });
  }

  static Long positiveNumber(String value) {
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException invalid) {
      return null;
    }
  }
}
