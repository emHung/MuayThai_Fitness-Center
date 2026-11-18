package vn.muaythai.app;

import android.net.Uri;
import android.widget.EditText;
import android.widget.LinearLayout;

final class OperationsPane {
  private final WorkspaceActivity work;

  OperationsPane(WorkspaceActivity work) {
    this.work = work;
  }

  void reviewReminder(LinearLayout body) {
    LinearLayout panel = new LinearLayout(work);
    panel.setOrientation(LinearLayout.VERTICAL);
    work.ui.add(body, panel, 0);
    work.refreshQuietly(work.api.service.get("api/notifications"), response -> {
      long count = Jsons.rows(response).stream().map(item -> Jsons.object(item.get("notice")))
          .filter(item -> "REVIEW".equals(Jsons.text(item, "kind"))).count();
      if (count != 0) {
        work.ui.row(panel, count + " buổi PT cần đánh giá", "Mở thông báo để xem các buổi",
            () -> work.open("Thông báo", this::notices));
      }
    });
  }

  void notices(LinearLayout body) {
    if (work.role().equals("ADMIN")) {
      work.ui.action(body, "Gửi thông báo chung", () -> work.open("Thông báo chung", this::broadcast));
    }
    work.request(work.api.service.get("api/notifications"), response -> {
      var rows = Jsons.rows(response);
      if (rows.isEmpty()) work.ui.cards.empty(body, "Chưa có thông báo", "Các yêu cầu và cập nhật mới sẽ xuất hiện tại đây.", R.drawable.ic_inbox);
      rows.forEach(item -> {
        var notice = Jsons.object(item.get("notice"));
        String title = Jsons.text(notice, "title");
        boolean unread = !Jsons.text(item, "read").equals("true");
        String details = Jsons.text(notice, "message") + " · " + SchedulePane.displayTime(Jsons.text(notice,"at"));
        work.ui.row(body, title + (unread ? " · Mới" : ""), details,
            () -> work.open(title, content -> {
              work.ui.label(content, details, 15, false);
              if (unread) {
                work.request(work.api.service.put("api/notifications/read?id="
                    + Uri.encode(Jsons.text(notice, "id")), Jsons.body()), ignored -> {});
              }
              long session = Jsons.number(notice, "sessionId");
              if (session > 0) {
                work.ui.action(content, "Xem buổi tập", () -> work.schedule.detail(session));
              }
              if (Jsons.text(notice, "kind").equals("CHANGE")) {
                work.ui.action(content, "Xem yêu cầu đổi lịch", () -> work.open("Đổi lịch PT", work.planning::changes));
              }
            }));
      });
    });
  }

  private void broadcast(LinearLayout body) {
    EditText title = work.ui.field(body, "Tiêu đề", "");
    EditText message = work.ui.field(body, "Nội dung", "");
    work.ui.action(body, "Gửi tới học viên", () -> work.request(work.api.service.post("api/notifications", null,
        Jsons.body("title", WorkspaceUi.read(title), "message", WorkspaceUi.read(message))),
        done -> { work.message("Đã gửi thông báo."); work.back(); }));
  }

  void care(LinearLayout body) {
    work.ui.cards.menu(body, "Gói sắp hết", "Số lượt hoặc thời hạn", R.drawable.ic_qr, () -> work.open("Gói sắp hết",
        panel -> listCare(panel, "renewals")));
    work.ui.cards.menu(body, "Lâu chưa tập", "Từ 14 ngày", R.drawable.ic_trend, () -> work.open("Học viên lâu chưa tập",
        panel -> listCare(panel, "inactive")));
  }

  private void listCare(LinearLayout body, String kind) {
    work.request(work.api.service.get("api/care/" + kind), response -> {
      var rows = Jsons.rows(response);
      if (rows.isEmpty()) work.ui.cards.empty(body, "Không có học viên phù hợp", "Danh sách được cập nhật theo dữ liệu gói và lịch tập.", R.drawable.ic_group);
      rows.forEach(row -> work.ui.row(body, Jsons.text(row, "learnerName"),
          Jsons.text(row, "memberCode") + " · " + Jsons.text(row, "detail"),
          () -> work.open(Jsons.text(row, "learnerName"),
              panel -> work.packages.forLearner(panel, Jsons.number(row, "learnerId")))));
    });
  }
}
