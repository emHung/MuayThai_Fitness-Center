package vn.muaythai.app;

import android.net.Uri;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;

final class SchedulePane {
  private static final int CALENDAR_REFRESH_MS = 15000;
  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm", Locale.ROOT);
  private final WorkspaceActivity work;
  private final ClassPane classes;

  SchedulePane(WorkspaceActivity work) {
    this.work = work;
    this.classes = new ClassPane(work);
  }

  void calendar(LinearLayout body) {
    LocalDate today = LocalDate.now(WorkspaceUi.ZONE);
    EditText date = work.ui.date(body, "Ngày xem lịch", today.toString());
    work.ui.row(body, "Yêu cầu đổi lịch", "", () -> work.open("Đổi lịch PT", work.planning::changes));
    if (work.role().equals("ADMIN")) {
      work.ui.action(body, "Quản lý lớp nhóm", () -> work.open("Lớp nhóm", this::classes));
    } else if (work.role().equals("TRAINER")) {
      work.operations.reviewReminder(body);
      work.ui.row(body, "Ca làm của tôi", "", () -> work.open("Ca làm việc", panel -> work.planning.shifts(panel, work.userId())));
      work.ui.action(body, "Đề xuất lớp 10", () -> work.open("Đề xuất lớp 10", panel -> classes.form(panel, "GROUP10", null)));
    }
    EditText query = work.ui.field(body, "Tên buổi cần tìm", "");
    CalendarFeed list = new CalendarFeed();
    work.ui.action(body, "Xem theo ngày", () -> list.show(LocalDate.parse(WorkspaceUi.read(date)), 1, WorkspaceUi.read(query)));
    work.ui.action(body, "Xem theo tuần", () -> list.show(LocalDate.parse(WorkspaceUi.read(date)), 7, WorkspaceUi.read(query)));
    work.ui.add(body, list, 6);
    list.show(today, 1, "");
  }

  private final class CalendarFeed extends LinearLayout {
    private String path;
    private int loadSequence;
    private JsonElement previous;
    private final CalendarUpdates updates = new CalendarUpdates(work.api, () -> load(false));
    private final Runnable tick = new Runnable() {
      @Override public void run() {
        if (hasWindowFocus() && path != null) load(false);
        postDelayed(this, CALENDAR_REFRESH_MS);
      }
    };

    CalendarFeed() {
      super(work);
      setOrientation(VERTICAL);
    }

    @Override protected void onAttachedToWindow() {
      super.onAttachedToWindow();
      postDelayed(tick, CALENDAR_REFRESH_MS);
      if (getWindowVisibility() == VISIBLE) updates.start();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
      super.onWindowVisibilityChanged(visibility);
      if (updates == null) return;
      if (visibility == VISIBLE && isAttachedToWindow()) updates.start();
      else updates.stop();
    }

    @Override protected void onDetachedFromWindow() {
      removeCallbacks(tick);
      updates.stop();
      super.onDetachedFromWindow();
    }

    void show(LocalDate first, int days, String query) {
      String from = Uri.encode(first.atStartOfDay(WorkspaceUi.ZONE).toOffsetDateTime().toString());
      String to = Uri.encode(first.plusDays(days).atStartOfDay(WorkspaceUi.ZONE).toOffsetDateTime().toString());
      path = "api/sessions?from=" + from + "&to=" + to + "&q=" + Uri.encode(query);
      load(true);
    }

    private void load(boolean interactive) {
      if (path == null) return;
      int requested = ++loadSequence;
      var call = work.api.service.get(path);
      Consumer<JsonElement> loaded = result -> {
        if (loadSequence != requested || java.util.Objects.equals(result, previous)) return;
        previous = result;
        removeAllViews();
        showSessions(this, Jsons.rows(result));
      };
      if (interactive) work.request(call, loaded);
      else work.refreshQuietly(call, loaded);
    }
  }

  void requests(LinearLayout body) {
    work.request(work.api.service.get("api/pt/requests"), result -> showSessions(body, Jsons.rows(result)));
  }

  private void showSessions(LinearLayout body, List<JsonObject> sessions) {
    if (sessions.isEmpty()) {
      work.ui.label(body, "Chưa có buổi tập trong khoảng thời gian này.", 14, false);
    }
    sessions.forEach(session -> work.ui.row(body, Jsons.text(session, "title"),
        times(session) + " · " + status(session), () -> detail(Jsons.id(session))));
  }

  void detail(long id) {
    work.open("Chi tiết buổi tập", body -> work.request(work.api.service.get("api/sessions/" + id),
        result -> showDetail(body, Jsons.object(result))));
  }

  private void showDetail(LinearLayout body, JsonObject session) {
    long id = Jsons.id(session);
    String state = Jsons.text(session, "status");
    String type = Jsons.text(session, "type");
    boolean admin = work.role().equals("ADMIN");
    boolean trainer = work.role().equals("TRAINER");
    boolean learner = work.role().equals("LEARNER");
    boolean pending = state.equals("PENDING_TRAINER");
    boolean confirmed = state.equals("CONFIRMED");
    work.ui.row(body, Jsons.text(session, "title"), times(session) + " · " + status(session), null);
    work.planning.showTrainerName(body, "api/sessions/" + id + "/trainer");
    if (admin && (pending || confirmed)
        && OffsetDateTime.parse(Jsons.text(session, "startAt")).isAfter(OffsetDateTime.now(WorkspaceUi.ZONE))) {
      work.ui.action(body, "Chọn HLV thay thế", () -> work.open("Chọn HLV thay thế", panel ->
          work.planning.changeTrainer(panel, "api/sessions/" + id + "/trainer",
              "HLV được chọn phải còn trống trong ca. Buổi tập giữ nguyên ngày, giờ và học viên.")));
    }
    if (trainer && pending) {
      work.ui.action(body, "Duyệt yêu cầu", () -> work.request(work.api.service.post(
          "api/pt/" + id + "/confirm", null, Jsons.body()), done -> work.refresh()));
      work.ui.action(body, "Từ chối", () -> reasonAction("Từ chối yêu cầu", "api/pt/" + id + "/reject"));
    }
    if ((pending || confirmed) && (admin || trainer && !type.equals("GROUP30") || learner && type.equals("PT"))) {
      work.ui.action(body, "Hủy buổi tập", () -> reasonAction("Hủy buổi tập", "api/sessions/" + id + "/cancel"));
    }
    if (confirmed && type.equals("PT")) {
      work.ui.action(body, "Đề xuất đổi giờ", () -> work.open("Đổi lịch PT", panel -> work.planning.propose(panel, id)));
    }
    if (confirmed && learner && type.equals("GROUP10")) {
      work.ui.action(body, "Đăng ký buổi", () -> work.packages.selectMine("GROUP10",
          pack -> work.request(work.api.service.put("api/sessions/" + id + "/signup",
              Jsons.body("packageId", Jsons.id(pack))), result -> work.message("Đã giữ chỗ."))));
      work.ui.action(body, "Đổi sang buổi khác", () -> moveGroup(session));
      work.ui.action(body, "Hủy đăng ký lớp", () -> work.request(work.api.service.delete(
          "api/sessions/" + id + "/signup"), result -> work.message("Đã xử lý hủy đăng ký.")));
    }
    if (confirmed && (admin || trainer)) {
      work.ui.action(body, "Điểm danh thủ công", () -> work.open("Điểm danh thủ công",
          panel -> work.attendance.manual(panel, session)));
    }
    if (admin || trainer) {
      work.ui.row(body, "Danh sách điểm danh", "", () -> work.open("Điểm danh buổi tập",
          content -> work.attendance.history(content, null, id)));
      work.ui.row(body, "Danh sách học viên của buổi", "Đã đến, chưa đến và vắng",
          () -> work.open("Danh sách học viên", panel -> work.attendance.roster(panel, session)));
    }
    if (confirmed && (trainer || admin || type.equals("PT"))) {
      work.ui.row(body, trainer ? "Ghi nhận xét và đánh giá" : "Xem đánh giá", "",
          () -> work.open("Đánh giá buổi tập", content -> work.progress.note(content, session)));
    }
  }

  private void moveGroup(JsonObject old) {
    work.packages.selectMine("GROUP10", pack -> work.request(work.api.service.get("api/sessions"), response -> {
      long trainer = Jsons.number(pack, "trainerId");
      long classId = Jsons.number(pack, "classId");
      var choices = Jsons.rows(response).stream().filter(row -> Jsons.text(row, "type").equals("GROUP10")
          && (trainer != 0 ? packageTrainer(row) == trainer : Jsons.number(row, "classId") == classId)
          && Jsons.id(row) != Jsons.id(old)
          && Jsons.text(row, "status").equals("CONFIRMED")).collect(Collectors.toList());
      choices.forEach(row -> row.addProperty("choiceLabel", Jsons.text(row, "title") + " · " + times(row)));
      work.choose("Chọn buổi mới", choices, "choiceLabel", next ->
          work.request(work.api.service.post("api/sessions/" + Jsons.id(old) + "/move-signup", null,
              Jsons.body("targetSessionId", Jsons.id(next), "packageId", Jsons.id(pack))), saved -> {
                work.message("Đã đổi buổi lớp 10.");
                detail(Jsons.id(Jsons.object(saved)));
              }));
    }));
  }

  private static long packageTrainer(JsonObject session) {
    long assigned = Jsons.number(session, "assignedTrainerId");
    return assigned == 0 ? Jsons.number(session, "trainerId") : assigned;
  }

  private void reasonAction(String title, String path) {
    work.open(title, body -> {
      EditText reason = work.ui.field(body, "Lý do", "");
      work.ui.action(body, title, () -> work.request(work.api.service.post(path, null,
          Jsons.body("reason", WorkspaceUi.read(reason))), done -> work.back()));
    });
  }

  void classes(LinearLayout body) {
    classes.show(body);
  }

  void bookingHub(LinearLayout body) {
    work.ui.row(body, "Đặt PT với HLV phụ trách", "Chọn gói, ngày và giờ",
        () -> work.packages.selectMine("PT", pack -> work.open("Đặt buổi PT", panel -> book(panel, pack))));
    work.ui.row(body, "Lớp nhóm 10", "Xem buổi có thể đăng ký", () -> work.open("Lịch lớp 10", panel -> {
      work.request(work.api.service.get("api/sessions"), result -> showSessions(panel,
          Jsons.rows(result).stream().filter(session -> Jsons.text(session, "type").equals("GROUP10"))
              .collect(Collectors.toList())));
    }));
    work.ui.label(body, "Với lớp 30, xem lịch và điểm danh theo buổi đã được xếp.", 13, false);
  }

  private void book(LinearLayout body, JsonObject pack) {
    long packageId = Jsons.id(pack);
    work.ui.row(body, PackagesPane.title(pack), PackagesPane.summary(pack), null);
    EditText date = work.ui.date(body, "Ngày tập", LocalDate.now(WorkspaceUi.ZONE).plusDays(1).toString());
    PtSlotGrid grid = new PtSlotGrid(work, body);
    Runnable showHours = () -> grid.load("packageId=" + packageId + "&date=" + WorkspaceUi.read(date),
        start -> work.confirm("Gửi yêu cầu đặt PT", displayTime(start), "Gửi yêu cầu",
            () -> work.request(work.api.service.post("api/pt/bookings", null,
                Jsons.body("packageId", packageId, "startAt", start)), saved -> {
              work.message("Đã gửi yêu cầu. Chờ HLV xác nhận.");
              detail(Jsons.id(Jsons.object(saved)));
            })));
    work.ui.action(body, "Cập nhật giờ trống", showHours);
    showHours.run();
  }

  static String localStart(EditText date, EditText time) {
    return LocalDate.parse(WorkspaceUi.read(date)).atTime(LocalTime.parse(WorkspaceUi.read(time)))
        .atZone(WorkspaceUi.ZONE).toOffsetDateTime().toString();
  }

  static String times(JsonObject session) {
    return displayTime(Jsons.text(session, "startAt"));
  }

  static String displayTime(String value) {
    return value.isEmpty() ? "" : OffsetDateTime.parse(value).atZoneSameInstant(WorkspaceUi.ZONE).format(DATE_TIME);
  }

  private static String status(JsonObject session) {
    return switch (Jsons.text(session, "status")) {
      case "CONFIRMED" -> "Đã xác nhận";
      case "PENDING_TRAINER" -> "Chờ HLV";
      case "CANCELLED" -> "Đã hủy";
      case "REJECTED" -> "Từ chối";
      default -> "";
    };
  }
}
