package vn.muaythai.app;

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Headers;

final class AppUpdatePrompt implements DefaultLifecycleObserver {
  private static final String APK_MIME = "application/vnd.android.package-archive";
  private static final long SNOOZE_MS = TimeUnit.HOURS.toMillis(24);
  private static final long CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(1);
  private static final String REPOSITORY = "emHung/MuayThai_Fitness-Center";
  private static final GitHubReleases RELEASES = new Retrofit.Builder()
      .baseUrl("https://api.github.com/")
      .client(new OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build())
      .addConverterFactory(GsonConverterFactory.create())
      .build().create(GitHubReleases.class);

  interface GitHubReleases {
    @Headers("Accept: application/vnd.github+json")
    @GET("repos/" + REPOSITORY + "/releases/latest")
    Call<JsonObject> latest();
  }

  private final AppCompatActivity activity;
  private final SharedPreferences settings;
  private Call<JsonObject> check;
  private AlertDialog prompt;

  AppUpdatePrompt(AppCompatActivity activity) {
    this.activity = activity;
    settings = activity.getSharedPreferences("app_update", Context.MODE_PRIVATE);
  }

  @Override public void onResume(LifecycleOwner owner) {
    if (check != null || prompt != null || activity.isFinishing()) {
      return;
    }
    long now = System.currentTimeMillis();
    if (settings.getLong("next_check", 0) > now) {
      return;
    }
    Call<JsonObject> request = RELEASES.latest();
    check = request;
    request.enqueue(new Callback<>() {
      @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
        if (response.errorBody() != null) {
          response.errorBody().close();
        }
        if (check != call) {
          return;
        }
        check = null;
        settings.edit().putLong("next_check", now + CHECK_INTERVAL_MS).apply();
        if (response.isSuccessful() && response.body() != null) {
          show(response.body());
        }
      }

      @Override public void onFailure(Call<JsonObject> call, Throwable error) {
        if (check == call) {
          check = null;
        }
      }
    });
  }

  @Override public void onPause(LifecycleOwner owner) {
    if (check != null) {
      check.cancel();
      check = null;
    }
    if (prompt != null) {
      prompt.dismiss();
    }
  }

  private void show(JsonObject release) {
    String tag = Jsons.text(release, "tag_name");
    String version = tag.startsWith("v") ? tag.substring(1) : tag;
    if (!newerThan(version, BuildConfig.VERSION_NAME)
        || settings.getLong("snooze_" + version, 0) > System.currentTimeMillis()
        || "true".equals(Jsons.text(release, "draft")) || "true".equals(Jsons.text(release, "prerelease"))) {
      return;
    }
    String assetId = releaseAssetId(release);
    if (assetId.isEmpty()) {
      return;
    }
    Uri downloadUrl = Uri.parse("https://github.com/" + REPOSITORY + "/releases/download/")
        .buildUpon().appendPath(tag).appendPath(BuildConfig.APP_UPDATE_APK_NAME).build();
    prompt = new MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.app_update_title)
        .setMessage(activity.getString(R.string.app_update_message, version))
        .setPositiveButton(R.string.app_update_now, (dialog, which) -> download(version, assetId, downloadUrl))
        .setNegativeButton(R.string.app_update_later, (dialog, which) -> snooze(version))
        .setOnCancelListener(dialog -> snooze(version))
        .create();
    prompt.setOnDismissListener(dialog -> prompt = null);
    prompt.show();
  }

  private void snooze(String version) {
    settings.edit().putLong("snooze_" + version, System.currentTimeMillis() + SNOOZE_MS).apply();
  }

  private void download(String version, String assetId, Uri downloadUrl) {
    DownloadManager manager = activity.getSystemService(DownloadManager.class);
    try {
      String key = "download_" + assetId;
      long previous = settings.getLong(key, -1);
      if (previous != -1) {
        try (var cursor = manager.query(new DownloadManager.Query().setFilterById(previous))) {
          if (cursor != null && cursor.moveToFirst()
              && cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                  != DownloadManager.STATUS_FAILED) {
            activity.startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
            return;
          }
        }
        manager.remove(previous);
      }
      var request = new DownloadManager.Request(downloadUrl)
          .setTitle(activity.getString(R.string.app_update_download_title, version))
          .setMimeType(APK_MIME)
          .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
          .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS,
              "muaythai-" + version + "-" + assetId + ".apk");
      settings.edit().putLong(key, manager.enqueue(request)).apply();
      Toast.makeText(activity, R.string.app_update_downloading, Toast.LENGTH_LONG).show();
    } catch (ActivityNotFoundException | IllegalArgumentException | IllegalStateException | SecurityException error) {
      Toast.makeText(activity, R.string.app_update_download_error, Toast.LENGTH_LONG).show();
      settings.edit().remove("next_check").apply();
    }
  }

  static String releaseAssetId(JsonObject release) {
    JsonElement assets = release.get("assets");
    if (assets == null || !assets.isJsonArray()) {
      return "";
    }
    for (var element : assets.getAsJsonArray()) {
      JsonObject asset = Jsons.object(element);
      String id = Jsons.text(asset, "id");
      if (BuildConfig.APP_UPDATE_APK_NAME.equals(Jsons.text(asset, "name"))
          && "uploaded".equals(Jsons.text(asset, "state")) && id.matches("[1-9][0-9]{0,18}")) {
        return id;
      }
    }
    return "";
  }

  static boolean newerThan(String candidate, String installed) {
    String pattern = "[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}";
    if (!candidate.matches(pattern) || !installed.matches(pattern)) {
      return false;
    }
    String[] target = candidate.split("\\.");
    String[] current = installed.split("\\.");
    for (int i = 0; i < target.length; i++) {
      int comparison = Integer.compare(Integer.parseInt(target[i]), Integer.parseInt(current[i]));
      if (comparison != 0) {
        return comparison > 0;
      }
    }
    return false;
  }
}
