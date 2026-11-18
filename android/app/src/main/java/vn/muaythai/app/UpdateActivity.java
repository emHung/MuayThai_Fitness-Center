package vn.muaythai.app;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.google.android.material.button.MaterialButton;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.Executors;

/** One foreground screen observes a persistent system download; it never installs silently. */
public final class UpdateActivity extends AppCompatActivity {
  private static final String MIME = "application/vnd.android.package-archive";
  private static final long POLL_MS = 500;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private DownloadManager manager;
  private android.content.SharedPreferences settings;
  private String version, asset, digest, url;
  private long downloadId = -1;
  private File file;
  private ProgressBar progress;
  private TextView status;
  private MaterialButton action;
  private boolean foreground, verifying, verified, installerOpened, waitingPermission, failed;
  private final Runnable poll = this::refresh;

  static void open(Context context, String version, String asset, Uri url, String digest) {
    context.startActivity(new Intent(context, UpdateActivity.class).putExtra("version", version)
        .putExtra("asset", asset).putExtra("url", url.toString()).putExtra("digest", digest));
  }

  @Override protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    version = getIntent().getStringExtra("version");
    asset = getIntent().getStringExtra("asset");
    digest = getIntent().getStringExtra("digest");
    url = getIntent().getStringExtra("url");
    if (version == null || !version.matches("[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}")
        || asset == null || !asset.matches("[1-9][0-9]{0,18}") || url == null
        || !url.startsWith("https://github.com/emHung/MuayThai_Fitness-Center/releases/download/")) {
      finish(); return;
    }
    if (digest == null) digest = "";
    installerOpened = saved != null && saved.getBoolean("installerOpened");
    waitingPermission = saved != null && saved.getBoolean("waitingPermission");
    manager = getSystemService(DownloadManager.class);
    settings = getSharedPreferences("app_update", MODE_PRIVATE);
    file = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates/" + asset + ".apk");
    var ui = new WorkspaceUi(this);
    var body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(ui.dp(24), ui.dp(24), ui.dp(24), ui.dp(24));
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(body, (view, insets) -> {
      var bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
      view.setPadding(ui.dp(24) + bars.left, ui.dp(24) + bars.top, ui.dp(24) + bars.right, ui.dp(24) + bars.bottom);
      return insets;
    });
    setContentView(body);
    ui.label(body, "Cập nhật ứng dụng", 24, true);
    ui.label(body, "Phiên bản " + version, 17, false);
    progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    progress.setMax(100);
    ui.add(body, progress, 24);
    status = ui.label(body, "Đang chuẩn bị tải…", 15, false);
    status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
    action = ui.action(body, "Thử lại", () -> { if (failed) begin(true); else install(); });
    action.setVisibility(android.view.View.GONE);
    ui.action(body, "Để tải nền / quay lại", this::finish);
    ui.action(body, "Hủy tải", () -> {
      if (downloadId != -1) manager.remove(downloadId);
      settings.edit().remove(key()).apply();
      finish();
    });
    begin(false);
  }

  private String key() { return "download_" + asset; }

  private void begin(boolean retry) {
    try {
      downloadId = settings.getLong(key(), -1);
      if (retry && downloadId != -1) { manager.remove(downloadId); downloadId = -1; }
      if (downloadId == -1) {
        if (file.exists() && !file.delete()) throw new IllegalStateException("Cannot replace APK");
        var request = new DownloadManager.Request(Uri.parse(url)).setTitle(getString(R.string.app_update_download_title, version))
            .setMimeType(MIME).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "updates/" + asset + ".apk");
        downloadId = manager.enqueue(request);
        settings.edit().putLong(key(), downloadId).apply();
      }
      failed = false; verified = false;
      if (retry) { installerOpened = false; waitingPermission = false; }
      action.setVisibility(android.view.View.GONE);
      if (foreground) refresh();
    } catch (RuntimeException error) { error("Không thể bắt đầu tải. Kiểm tra dung lượng rồi thử lại.", error); }
  }

  @Override protected void onResume() {
    super.onResume();
    foreground = true;
    if (manager != null && !failed) refresh();
  }

  @Override protected void onPause() {
    foreground = false;
    handler.removeCallbacks(poll);
    super.onPause();
  }

  @Override protected void onSaveInstanceState(Bundle state) {
    state.putBoolean("installerOpened", installerOpened);
    state.putBoolean("waitingPermission", waitingPermission);
    super.onSaveInstanceState(state);
  }

  private void refresh() {
    handler.removeCallbacks(poll);
    if (!foreground || failed || verifying) return;
    try (var cursor = manager.query(new DownloadManager.Query().setFilterById(downloadId))) {
      if (cursor == null || !cursor.moveToFirst()) { error("Tệp tải không còn tồn tại. Vui lòng thử lại.", null); return; }
      int state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
      if (state == DownloadManager.STATUS_FAILED) { error("Tải thất bại. Kiểm tra mạng hoặc dung lượng rồi thử lại.", null); return; }
      if (state == DownloadManager.STATUS_SUCCESSFUL) { validate(); return; }
      long done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
      long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
      progress.setIndeterminate(total <= 0);
      int percent = total > 0 ? (int) Math.min(100, done * 100 / total) : 0;
      progress.setProgress(percent);
      status.setText(state == DownloadManager.STATUS_PAUSED ? "Đang chờ mạng, sẽ tự tiếp tục…"
          : total > 0 ? String.format(Locale.forLanguageTag("vi"), "%d%% · %.1f / %.1f MB", percent, done / 1048576d, total / 1048576d)
          : "Đang kết nối và tải bản cập nhật…");
      handler.postDelayed(poll, POLL_MS);
    } catch (RuntimeException error) { error("Không đọc được tiến độ tải. Vui lòng thử lại.", error); }
  }

  private void validate() {
    progress.setIndeterminate(false);
    progress.setProgress(100);
    if (verified) { ready(); return; }
    verifying = true;
    status.setText(R.string.update_verifying);
    var executor = Executors.newSingleThreadExecutor();
    executor.execute(() -> {
      Exception problem = null;
      try { UpdatePackage.verify(this, file, version, digest); } catch (Exception error) { problem = error; }
      Exception result = problem;
      handler.post(() -> {
        verifying = false;
        if (isDestroyed()) return;
        if (result != null) error("Tệp cập nhật không hợp lệ hoặc không cùng chữ ký. Vui lòng thử lại.", result);
        else { verified = true; if (foreground) ready(); }
      });
    });
    executor.shutdown();
  }

  private void ready() {
    status.setText(R.string.update_ready);
    action.setText(R.string.update_install);
    action.setVisibility(android.view.View.VISIBLE);
    if (!installerOpened) install();
  }

  private void install() {
    if (!verified || !foreground) return;
    try {
      if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
        status.setText(R.string.update_permission_message);
        action.setText(R.string.update_permission);
        if (!waitingPermission) {
          waitingPermission = true;
          startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
        } else {
          // Do not repeatedly reopen Settings after the user declines permission.
          installerOpened = true;
          action.setOnClickListener(view -> { waitingPermission = false; installerOpened = false; install(); });
        }
        return;
      }
      installerOpened = true;
      waitingPermission = false;
      action.setOnClickListener(view -> install());
      var uri = FileProvider.getUriForFile(this, getPackageName() + ".updates", file);
      startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, MIME)
          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
    } catch (RuntimeException error) { error("Không mở được trình cài đặt. Vui lòng thử lại.", error); }
  }

  private void error(String message, Exception problem) {
    failed = true;
    handler.removeCallbacks(poll);
    progress.setIndeterminate(false);
    status.setText(message);
    action.setText(R.string.update_retry);
    action.setOnClickListener(view -> begin(true));
    action.setVisibility(android.view.View.VISIBLE);
    if (problem != null) android.util.Log.w("AppUpdate", message, problem);
  }
}
