package vn.muaythai.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.net.Uri;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.function.Consumer;

final class DirectoryPane {
  private final WorkspaceActivity work;

  DirectoryPane(WorkspaceActivity work) {
    this.work = work;
  }

  void receptionists(LinearLayout body) {
    work.ui.action(body, "Thêm lễ tân", () -> work.open("Thêm lễ tân", panel -> {
      EditText name = work.ui.field(panel, "Họ và tên", "");
      EditText email = work.ui.field(panel, "Email đăng nhập", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
      work.ui.action(panel, "Tạo tài khoản", () -> work.request(work.api.service.post("api/receptionists", null,
          Jsons.body("displayName", WorkspaceUi.read(name), "username", WorkspaceUi.read(email))), created -> {
            var result = Jsons.object(created);
            work.back();
            showTemporaryPassword(WorkspaceUi.read(email), Jsons.text(result, "initialPassword"));
          }));
    }));
    work.request(work.api.service.get("api/receptionists"), value -> Jsons.rows(value).forEach(profile ->
        work.ui.row(body, Jsons.text(profile, "displayName"), info(profile), () -> work.open(
            Jsons.text(profile, "displayName"), panel -> accountActions(panel, profile)))));
  }

  private void accountActions(LinearLayout body, JsonObject profile) {
    long id = Jsons.id(profile);
    boolean locked = Jsons.text(profile, "status").equals("LOCKED");
    String action = locked ? "Mở khóa tài khoản" : "Khóa tài khoản";
    work.ui.action(body, action, () -> work.open(action, panel -> {
      EditText reason = work.ui.field(panel, "Lý do", "");
      work.ui.action(panel, action, () -> work.request(work.api.service.patch("api/users/" + id + "/status",
          Jsons.body("status", locked ? "ACTIVE" : "LOCKED", "reason", WorkspaceUi.read(reason))), done -> {
            work.back();
            work.back();
          }));
    }));
    work.ui.action(body, "Cấp lại mật khẩu", () -> work.open("Cấp lại mật khẩu", panel -> {
      EditText reason = work.ui.field(panel, "Lý do", "");
      work.ui.action(panel, "Cấp mật khẩu tạm", () -> work.request(work.api.service.post(
          "api/users/" + id + "/password-reset", null, Jsons.body("reason", WorkspaceUi.read(reason))), value -> {
            work.back();
            showTemporaryPassword(Jsons.text(profile, "username"), Jsons.text(Jsons.object(value), "password"));
          }));
    }));
  }

  private void showTemporaryPassword(String email, String password) {
    work.confirm("Mật khẩu tạm", "Email " + email + "\nMật khẩu " + password
        + "\nGửi riêng cho người được cấp. Các phiên cũ đã hết hiệu lực.", "Đóng", () -> {});
  }

  void list(LinearLayout body, boolean trainers) {
    String label = trainers ? "HLV" : "học viên";
    EditText query = work.ui.field(body, "Tìm " + label, "");
    if (work.role().equals("ADMIN")) {
      work.ui.action(body, "Thêm " + label, () -> work.open("Thêm " + label, container -> form(container, trainers, null)));
    }
    LinearLayout results = new LinearLayout(work);
    results.setOrientation(LinearLayout.VERTICAL);
    work.ui.iconAction(work.ui.actions(body), "Tìm kiếm", R.drawable.ic_search,
        () -> load(results, trainers, WorkspaceUi.read(query)));
    work.ui.add(body, results, 5);
    load(results, trainers, "");
  }

  private void load(LinearLayout body, boolean trainers, String term) {
    pick(trainers, term, rows -> {
      body.removeAllViews();
      if (rows.isEmpty()) {
        work.ui.cards.empty(body, "Không tìm thấy hồ sơ", "Thử tên hoặc mã khác để tìm kiếm.", R.drawable.ic_group);
      }
      rows.forEach(profile -> work.ui.row(body, Jsons.text(profile, "displayName"),
          info(profile), () -> detail(trainers, profile)));
    });
  }

  void pick(boolean trainers, String q, Consumer<List<JsonObject>> results) {
    work.request(work.api.service.get(directory(trainers) + "?size=100&q=" + Uri.encode(q)),
        answer -> results.accept(Jsons.rows(answer)));
  }

  private void detail(boolean trainers, JsonObject profile) {
    long id = Jsons.id(profile);
    String name = Jsons.text(profile, "displayName");
    work.open(name, body -> work.request(work.api.service.get(directory(trainers) + "/" + id),
        result -> renderProfile(body, trainers, Jsons.object(result))));
  }

  private void renderProfile(LinearLayout body, boolean trainers, JsonObject profile) {
    long id = Jsons.id(profile);
    work.images.show(body, "api/users/" + id + "/image", true, work.role().equals("ADMIN"));
    work.ui.row(body, Jsons.text(profile, "displayName"), info(profile), null);
    showField(body, "Điện thoại", Jsons.text(profile, "phone"));
    if (trainers) {
      showField(body, "Chuyên môn", Jsons.text(profile, "specialty"));
      if (work.role().equals("ADMIN")) {
        work.ui.row(body, "Ca làm việc", "Lịch theo tuần và khung giờ đóng",
            () -> work.open("Ca làm việc", panel -> work.planning.shifts(panel, id)));
      }
    }
    if (work.role().equals("ADMIN")) {
      work.ui.action(body, "Sửa hồ sơ", () -> work.open("Sửa hồ sơ", content -> form(content, trainers, profile)));
      if (trainers) {
        work.ui.action(body, "Xóa hồ sơ HLV", () -> work.confirm("Xóa hồ sơ HLV",
            "Chỉ xóa được HLV chưa phát sinh dữ liệu nghiệp vụ.", "Xóa hồ sơ",
            () -> work.request(work.api.service.delete("api/trainers/" + id), done -> {
              work.message("Đã xóa hồ sơ HLV.");
              work.back();
            })));
      }
      accountActions(body, profile);
    }
    if (!trainers) {
      if (work.role().equals("RECEPTION")) {
        work.ui.row(body, "Lịch sử điểm danh", "Các lượt do bạn ghi", () -> work.open("Lịch sử điểm danh",
            list -> work.attendance.history(list, id, null)));
        return;
      }
      work.ui.row(body, "Gói tập", "Hạn và số lượt còn lại", () -> work.open("Gói tập", list -> work.packages.forLearner(list, id)));
      work.ui.row(body, "Lịch sử điểm danh", "", () -> work.open("Lịch sử điểm danh", list -> work.attendance.history(list, id, null)));
      work.ui.row(body, "Xếp lịch PT", work.role().equals("TRAINER") ? "Buổi đã thỏa thuận ngoài ca" : "Tạo lịch đã xác nhận",
          () -> work.open("Xếp lịch PT", panel -> work.planning.direct(panel, id)));
      work.ui.row(body, "Hành trình tập", "", () -> work.open("Hành trình tập", list -> work.progress.journey(list, id)));
    }
  }

  private void form(LinearLayout body, boolean trainers, JsonObject profile) {
    boolean editing = profile != null;
    JsonObject initial = editing ? profile : new JsonObject();
    EditText name = work.ui.field(body, "Họ và tên", Jsons.text(initial, "displayName"));
    EditText email = editing ? null : work.ui.field(body, "Email đăng nhập", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    EditText phone = work.ui.field(body, "Điện thoại", Jsons.text(initial, "phone"), InputType.TYPE_CLASS_PHONE);
    EditText specialty = trainers ? work.ui.field(body, "Chuyên môn", Jsons.text(initial, "specialty")) : null;
    EditText temporary = trainers && !editing ? work.ui.field(body, "Mật khẩu tạm (bỏ trống để tự sinh)", "",
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD) : null;
    work.ui.action(body, editing ? "Lưu thay đổi" : "Tạo tài khoản", () -> {
      JsonObject values = Jsons.body("displayName", WorkspaceUi.read(name), "phone", WorkspaceUi.read(phone),
          "specialty", specialty == null ? null : WorkspaceUi.read(specialty));
      if (editing) {
        work.request(work.api.service.put(directory(trainers) + "/" + Jsons.id(initial), values), saved -> work.back());
      } else {
        values.addProperty("username", WorkspaceUi.read(email));
        if (temporary != null && temporary.length() > 0) {
          values.addProperty("temporaryPassword", temporary.getText().toString());
        }
        work.request(work.api.service.post(directory(trainers), null, values), created -> {
          JsonObject result = Jsons.object(created);
          JsonObject newProfile = Jsons.object(result.get("profile"));
          String user = Jsons.text(newProfile, "username");
          String password = Jsons.text(result, "initialPassword");
          work.back();
          work.confirm("Tài khoản đã tạo", "Email " + user + "\nMật khẩu ban đầu " + password,
              "Sao chép", () -> {
                ClipboardManager clipboard = work.getSystemService(ClipboardManager.class);
                if (clipboard != null) {
                  clipboard.setPrimaryClip(ClipData.newPlainText("Tài khoản mới", user + "\n" + password));
                  work.message("Đã sao chép tài khoản.");
                }
              });
        });
      }
    });
  }

  void account() {
    work.open("Tài khoản của tôi", body -> work.request(work.api.service.get("api/users/me/profile"), value -> {
      JsonObject profile = Jsons.object(value);
      var identity = work.ui.card(body);
      ((com.google.android.material.card.MaterialCardView) identity.getParent()).setCardBackgroundColor(work.ui.cards.accent());
      identity.setOrientation(LinearLayout.HORIZONTAL);
      identity.setGravity(android.view.Gravity.CENTER_VERTICAL);
      var avatar = new LinearLayout(work);
      avatar.setOrientation(LinearLayout.VERTICAL);
      identity.addView(avatar, new LinearLayout.LayoutParams(work.ui.dp(96), -2));
      work.images.show(avatar, "api/users/me/image", true, false);
      var details = new LinearLayout(work);
      details.setOrientation(LinearLayout.VERTICAL);
      var detailSize = new LinearLayout.LayoutParams(0, -2, 1);
      detailSize.leftMargin = work.ui.dp(14);
      identity.addView(details, detailSize);
      work.ui.label(details, Jsons.text(profile, "displayName"), 22, true)
          .setTextColor(androidx.core.content.ContextCompat.getColor(work, R.color.surface));
      work.ui.label(details, info(profile), 13, false)
          .setTextColor(androidx.core.content.ContextCompat.getColor(work, R.color.surface));
      work.images.editActions(details, "api/users/me/image", true);
      var information = work.ui.cards.section(body, "Thông tin cá nhân", R.drawable.ic_user);
      EditText name = work.ui.field(information, "Họ và tên", Jsons.text(profile, "displayName"));
      EditText phone = work.ui.field(information, "Điện thoại", Jsons.text(profile, "phone"), InputType.TYPE_CLASS_PHONE);
      EditText specialty = work.role().equals("TRAINER")
          ? work.ui.field(information, "Chuyên môn", Jsons.text(profile, "specialty")) : null;
      work.ui.action(information, "Lưu thông tin", () -> work.request(work.api.service.put("api/users/me",
          Jsons.body("displayName", WorkspaceUi.read(name), "phone", WorkspaceUi.read(phone),
              "specialty", specialty == null ? null : WorkspaceUi.read(specialty))),
          done -> { work.message("Đã lưu thông tin."); work.refresh(); }));
      var actions = work.ui.cards.grid(body, 3);
      work.ui.cards.tile(actions, "Đổi mật khẩu", "Bảo mật tài khoản", R.drawable.ic_key, () -> work.open("Đổi mật khẩu", this::password));
      work.ui.cards.tile(actions, "Cập nhật ứng dụng", "Kiểm tra phiên bản mới", R.drawable.ic_refresh, () -> AppUpdatePrompt.checkNow(work));
      work.ui.cards.tile(actions, "Đăng xuất", "Thoát khỏi tài khoản", R.drawable.ic_logout, work::logout);
      work.ui.cards.menu(body, "Thông báo", "Xem cập nhật và yêu cầu", R.drawable.ic_inbox,
          () -> work.open("Thông báo", work.operations::notices));
      work.ui.cards.menu(body, "Thông tin ứng dụng", "Phiên bản và cập nhật", R.drawable.ic_book,
          () -> work.open("Thông tin ứng dụng", panel -> {
            var appInformation = work.ui.cards.section(panel, "MuayThai & Fitness Center", R.drawable.ic_grid);
            work.ui.label(appInformation, work.getString(R.string.app_version_label, BuildConfig.VERSION_NAME), 17, true);
            work.ui.action(appInformation, "Kiểm tra cập nhật", () -> AppUpdatePrompt.checkNow(work));
          }));
    }));
  }

  private void password(LinearLayout body) {
    int fieldType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
    EditText old = work.ui.field(body, "Mật khẩu hiện tại", "", fieldType);
    EditText next = work.ui.field(body, "Mật khẩu mới", "", fieldType);
    EditText again = work.ui.field(body, "Nhập lại mật khẩu mới", "", fieldType);
    work.ui.action(body, "Lưu mật khẩu", () -> {
      String password = WorkspaceUi.read(next);
      if (!password.equals(WorkspaceUi.read(again)) || password.length() < 8) {
        work.error("Mật khẩu mới phải từ 8 ký tự và trùng với ô xác nhận.");
        return;
      }
      work.request(work.api.service.post("api/auth/change-password", null,
          Jsons.body("currentPassword", WorkspaceUi.read(old), "newPassword", password)), response -> {
        work.message("Đã đổi mật khẩu. Đăng nhập lại bằng mật khẩu mới.");
        work.restartLogin();
      });
    });
  }

  private void showField(LinearLayout body, String label, String value) {
    if (!value.trim().isEmpty()) {
      work.ui.label(body, label + " · " + value, 13, false);
    }
  }

  private static String info(JsonObject profile) {
    String code = Jsons.text(profile, "memberCode");
    return (code.isEmpty() ? "" : code + " · ") + Jsons.text(profile, "username") + " · "
        + (Jsons.text(profile, "status").equals("LOCKED") ? "Đã khóa" : "Hoạt động");
  }

  private static String directory(boolean trainers) {
    return trainers ? "api/trainers" : "api/learners";
  }
}
