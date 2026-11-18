package vn.muaythai.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Arrays;
import java.util.function.Consumer;
import retrofit2.Call;
import vn.muaythai.app.databinding.ActivityWorkspaceBinding;

public final class WorkspaceActivity extends AppCompatActivity {
  private record Screen(String title, Consumer<LinearLayout> content) {}
  private record Tab(String label, int icon, Screen screen) {}

  private static AuthApi.Session session;
  private ActivityWorkspaceBinding layout;
  private final Deque<Screen> history = new ArrayDeque<>();
  private Screen current;
  private int revision;
  private int inFlight;
  WorkspaceUi ui;
  ApiClient api;
  ImagesPane images;
  DirectoryPane directory;
  PackagesPane packages;
  SchedulePane schedule;
  AttendancePane attendance;
  ProgressPane progress;
  PlanningPane planning;
  OperationsPane operations;

  static void open(LoginActivity from, AuthApi.Session authenticated) {
    session = authenticated;
    from.startActivity(new Intent(from, WorkspaceActivity.class));
    from.finish();
  }

  @Override
  protected void onCreate(Bundle state) {
    if (session != null) setTheme(switch (session.user.role) {
      case "TRAINER" -> R.style.Theme_MuayThai_Trainer;
      case "LEARNER" -> R.style.Theme_MuayThai_Learner;
      default -> R.style.Theme_MuayThai;
    });
    super.onCreate(state);
    if (session == null) {
      login();
      return;
    }
    layout = ActivityWorkspaceBinding.inflate(getLayoutInflater());
    setContentView(layout.getRoot());
    getLifecycle().addObserver(new ConnectivityNotice(this, layout.getRoot(), layout.workspaceNav));
    ui = new WorkspaceUi(this);
    api = new ApiClient(session.authorization());
    images = new ImagesPane(this);
    getLifecycle().addObserver(new AppUpdatePrompt(this));
    directory = new DirectoryPane(this);
    packages = new PackagesPane(this);
    schedule = new SchedulePane(this);
    attendance = new AttendancePane(this);
    progress = new ProgressPane(this);
    planning = new PlanningPane(this);
    operations = new OperationsPane(this);
    attendance.prepareScanner();
    applyInsets();
    configureTabs();
    layout.accounts.setOnClickListener(view -> directory.account());
    layout.notices.setOnClickListener(view -> open("Thông báo", operations::notices));
    layout.back.setOnClickListener(view -> back());
    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
      @Override public void handleOnBackPressed() {
        if (!history.isEmpty()) {
          back();
        } else {
          finish();
        }
      }
    });
  }

  private void applyInsets() {
    ViewCompat.setOnApplyWindowInsetsListener(layout.getRoot(), (view, window) -> {
      Insets bars = window.getInsets(WindowInsetsCompat.Type.systemBars()
          | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
      view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
      return window;
    });
  }

  private void configureTabs() {
    List<Tab> tabs = switch (role()) {
      case "ADMIN" -> Arrays.asList(
          tab("Quét", R.drawable.ic_scan, "Quét QR check-in", attendance::scanner),
          tab("Lịch", R.drawable.ic_calendar, "Lịch toàn trung tâm", schedule::calendar),
          tab("Học viên", R.drawable.ic_group, "Học viên", body -> directory.list(body, false)),
          tab("Quản lý", R.drawable.ic_grid, "Quản lý", this::manage));
      case "RECEPTION" -> Arrays.asList(
          tab("Quét", R.drawable.ic_scan, "Quét QR check-in", attendance::scanner),
          tab("Lịch", R.drawable.ic_calendar, "Lịch toàn trung tâm", schedule::calendar),
          tab("Học viên", R.drawable.ic_group, "Học viên", body -> directory.list(body, false)));
      case "TRAINER" -> Arrays.asList(
          tab("Lịch dạy", R.drawable.ic_calendar, "Lịch dạy của tôi", schedule::calendar),
          tab("Yêu cầu", R.drawable.ic_inbox, "Chờ xác nhận", schedule::requests),
          tab("Học viên PT", R.drawable.ic_group, "Học viên PT", body -> directory.list(body, false)));
      default -> Arrays.asList(
          tab("Lịch", R.drawable.ic_calendar, "Lịch của tôi", schedule::calendar),
          tab("Đặt lịch", R.drawable.ic_book, "Đặt lịch", schedule::bookingHub),
          tab("Thẻ QR", R.drawable.ic_qr, "Thẻ tập của tôi", packages::mine),
          tab("Hành trình", R.drawable.ic_trend, "Hành trình", body -> progress.journey(body, null)));
    };
    int accent = ContextCompat.getColor(this, switch (role()) {
      case "ADMIN", "RECEPTION" -> R.color.admin;
      case "TRAINER" -> R.color.trainer;
      default -> R.color.learner;
    });
    var colors = new ColorStateList(new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
        new int[] {accent, ContextCompat.getColor(this, R.color.text_secondary)});
    layout.workspaceNav.setItemIconTintList(colors);
    layout.workspaceNav.setItemTextColor(colors);
    layout.workspaceNav.setItemActiveIndicatorColor(ColorStateList.valueOf(
        androidx.core.graphics.ColorUtils.setAlphaComponent(accent, 24)));
    for (int index = 0; index < tabs.size(); index++) {
      Tab tab = tabs.get(index);
      layout.workspaceNav.getMenu().add(0, index + 1, index, tab.label()).setIcon(tab.icon());
    }
    layout.workspaceNav.setOnItemSelectedListener(item -> {
      history.clear();
      render(tabs.get(item.getItemId() - 1).screen());
      return true;
    });
    render(tabs.get(0).screen());
  }

  private static Tab tab(String label, int icon, String title, Consumer<LinearLayout> content) {
    return new Tab(label, icon, new Screen(title, content));
  }

  private void manage(LinearLayout body) {
    var grid = ui.cards.grid(body);
    ui.cards.tile(grid, "Lễ tân", "Tài khoản tại quầy", R.drawable.ic_user, () -> open("Lễ tân", directory::receptionists));
    ui.cards.tile(grid, "Huấn luyện viên", "Tài khoản và chuyên môn", R.drawable.ic_book,
        () -> open("Huấn luyện viên", content -> directory.list(content, true)));
    ui.cards.tile(grid, "Lớp nhóm", "Lớp 30, lớp 10 và lịch lặp", R.drawable.ic_group, () -> open("Lớp nhóm", schedule::classes));
    ui.cards.tile(grid, "Chăm sóc học viên", "Gói sắp hết và học viên lâu chưa tập", R.drawable.ic_trend,
        () -> open("Chăm sóc học viên", operations::care));
    ui.cards.menu(body, "Lịch sử điểm danh", "Lượt đã ghi và điều chỉnh", R.drawable.ic_calendar,
        () -> open("Lịch sử điểm danh", content -> attendance.history(content, null, null)));
  }

  String role() {
    return session.user.role;
  }

  boolean canAdjust() {
    return session.user.canAdjustCheckins;
  }

  long userId() {
    return session.user.id;
  }

  void open(String title, Consumer<LinearLayout> content) {
    if (current != null) {
      history.push(current);
    }
    render(new Screen(title, content));
  }

  void refresh() {
    render(current);
  }

  void back() {
    if (!history.isEmpty()) {
      render(history.pop());
    }
  }

  private void render(Screen screen) {
    current = screen;
    revision++;
    inFlight = 0;
    layout.workspaceTitle.setText(screen.title());
    layout.workspaceBody.removeAllViews();
    layout.workspaceScroll.scrollTo(0, 0);
    layout.back.setVisibility(history.isEmpty() ? View.GONE : View.VISIBLE);
    layout.workspaceNav.setVisibility(history.isEmpty() ? View.VISIBLE : View.GONE);
    layout.loading.setVisibility(View.GONE);
    layout.screenError.setVisibility(View.GONE);
    var presentation = ScreenHeader.of(screen.title());
    layout.workspaceSubtitle.setText(presentation.subtitle());
    layout.screenIcon.setVisibility(history.isEmpty() ? View.VISIBLE : View.GONE);
    layout.screenIcon.setImageResource(presentation.icon());
    layout.screenIcon.setImageTintList(ColorStateList.valueOf(ui.cards.accent()));
    layout.screenIcon.setBackground(ui.cards.background(
        androidx.core.graphics.ColorUtils.setAlphaComponent(ui.cards.accent(), 20), 14));
    for (var button : List.of(layout.back, layout.notices, layout.accounts)) {
      button.setBackground(ui.cards.background(ContextCompat.getColor(this, R.color.surface), 14));
    }
    if (history.isEmpty() && !screen.title().equals("Quét QR check-in") && !presentation.heroTitle().isEmpty()) {
      ui.cards.hero(layout.workspaceBody, presentation.heroTitle(), presentation.description(), presentation.icon());
    }
    screen.content().accept(layout.workspaceBody);
  }

  void request(Call<JsonElement> call, Consumer<JsonElement> success) {
    request(call, success, this::error);
  }

  void request(Call<JsonElement> call, Consumer<JsonElement> success, Consumer<String> failure) {
    request(call, success, failure, true);
  }

  void refreshQuietly(Call<JsonElement> call, Consumer<JsonElement> success) {
    request(call, success, message -> {}, false);
  }

  private void request(Call<JsonElement> call, Consumer<JsonElement> success, Consumer<String> failure,
      boolean showLoading) {
    int started = revision;
    if (showLoading) {
      inFlight++;
      layout.loading.setVisibility(View.VISIBLE);
      layout.screenError.setVisibility(View.GONE);
    }
    api.send(call, new ApiClient.Result() {
      @Override public void success(JsonElement json) {
        if (finishRequest(started, showLoading)) {
          success.accept(json);
        }
      }

      @Override public void failure(String code, String message, JsonObject details) {
        if (!finishRequest(started, showLoading)) {
          return;
        }
        if (code.equals("SESSION_EXPIRED") || code.equals("PASSWORD_CHANGE_REQUIRED")) {
          Toast.makeText(WorkspaceActivity.this, message, Toast.LENGTH_LONG).show();
          login();
        } else {
          failure.accept(message);
          var conflicts = Jsons.rows(details.get("sessions"));
          if (!conflicts.isEmpty()) {
            conflicts.forEach(row -> row.addProperty("choiceLabel",
                Jsons.text(row, "title") + " · " + SchedulePane.times(row)));
            choose("Buổi tập cần xử lý", conflicts, "choiceLabel", item -> schedule.detail(Jsons.id(item)));
          }
        }
      }
    });
  }

  private boolean finishRequest(int started, boolean showLoading) {
    if (isFinishing() || isDestroyed() || revision != started) {
      return false;
    }
    if (showLoading && --inFlight == 0) {
      layout.loading.setVisibility(View.GONE);
    }
    return true;
  }

  void error(String message) {
    layout.screenError.setText(message);
    layout.screenError.setVisibility(View.VISIBLE);
  }

  void confirm(String title, String message, String action, Runnable run) {
    new MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message)
        .setNegativeButton("Đóng", null).setPositiveButton(action, (dialog, choice) -> run.run()).show();
  }

  void choose(String title, List<JsonObject> rows, String name, Consumer<JsonObject> action) {
    if (rows.isEmpty()) {
      error("Không có mục phù hợp.");
      return;
    }
    String[] labels = rows.stream().map(row -> Jsons.text(row, name, "Mục " + Jsons.id(row))).toArray(String[]::new);
    new MaterialAlertDialogBuilder(this).setTitle(title)
        .setItems(labels, (dialog, selected) -> action.accept(rows.get(selected))).show();
  }

  void message(String text) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
  }

  void logout() {
    AuthApi.Session current = session;
    new AuthRepository().logout(current, new AuthRepository.Result<>() {
      @Override public void success(Void ignored) { login(); }
      @Override public void failure(String code, String message) {
        if ("SESSION_EXPIRED".equals(code) || "ACCOUNT_LOCKED".equals(code)) {
          login();
        } else {
          error(message);
        }
      }
    });
  }

  void restartLogin() {
    login();
  }

  private void login() {
    session = null;
    startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
    finish();
  }
}
