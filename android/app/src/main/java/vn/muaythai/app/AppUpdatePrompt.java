package vn.muaythai.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
  private boolean manual;

  static void checkNow(AppCompatActivity activity) {
    var observer = new AppUpdatePrompt(activity);
    observer.manual = true;
    activity.getLifecycle().addObserver(observer);
  }

  AppUpdatePrompt(AppCompatActivity activity) {
    this.activity = activity;
    settings = activity.getSharedPreferences("app_update", Context.MODE_PRIVATE);
  }

  @Override public void onResume(LifecycleOwner owner) {
    if (check != null || prompt != null || activity.isFinishing()) {
      return;
    }
    long now = System.currentTimeMillis();
    if (!manual) {
      String cached = settings.getString("latest_release", "");
      if (!cached.isEmpty()) {
        try {
          var release = JsonParser.parseString(cached);
          if (release.isJsonObject()) show(release.getAsJsonObject());
        } catch (com.google.gson.JsonParseException error) {
          settings.edit().remove("latest_release").apply();
          android.util.Log.w("AppUpdate", "Discarded unreadable release cache", error);
        }
      }
    }
    if (prompt != null || (!manual && settings.getLong("next_check", 0) > now)) {
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
        if (response.isSuccessful() && response.body() != null) {
          settings.edit().putLong("next_check", now + CHECK_INTERVAL_MS)
              .putString("latest_release", response.body().toString()).apply();
          show(response.body());
          if (manual && prompt == null) {
            Toast.makeText(activity, "Bạn đang dùng phiên bản mới nhất có bản cài phù hợp.", Toast.LENGTH_LONG).show();
            activity.getLifecycle().removeObserver(AppUpdatePrompt.this);
          }
        } else {
          android.util.Log.w("AppUpdate", "Release check returned HTTP " + response.code());
          failed();
        }
      }

      @Override public void onFailure(Call<JsonObject> call, Throwable error) {
        if (check == call) {
          check = null;
          android.util.Log.w("AppUpdate", "Release check failed", error);
          failed();
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
    if (manual) activity.getLifecycle().removeObserver(this);
  }

  private void failed() {
    if (manual && !activity.isFinishing() && !activity.isDestroyed()) {
      Toast.makeText(activity, "Chưa thể kiểm tra cập nhật. Vui lòng thử lại.", Toast.LENGTH_LONG).show();
      activity.getLifecycle().removeObserver(this);
    }
  }

  private void show(JsonObject release) {
    String tag = Jsons.text(release, "tag_name");
    String version = tag.startsWith("v") ? tag.substring(1) : tag;
    if (!newerThan(version, BuildConfig.VERSION_NAME)
        || (!manual && settings.getLong("snooze_" + version, 0) > System.currentTimeMillis())
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
    prompt.setOnDismissListener(dialog -> {
      prompt = null;
      if (manual) activity.getLifecycle().removeObserver(this);
    });
    prompt.show();
  }

  private void snooze(String version) {
    settings.edit().putLong("snooze_" + version, System.currentTimeMillis() + SNOOZE_MS).apply();
  }

  private void download(String version, String assetId, Uri downloadUrl) {
    String digest = "";
    try {
      var release = JsonParser.parseString(settings.getString("latest_release", "{}")).getAsJsonObject();
      for (var element : release.getAsJsonArray("assets")) {
        var asset = Jsons.object(element);
        if (assetId.equals(Jsons.text(asset, "id"))) digest = Jsons.text(asset, "digest");
      }
    } catch (RuntimeException error) {
      android.util.Log.w("AppUpdate", "Release digest unavailable", error);
    }
    UpdateActivity.open(activity, version, assetId, downloadUrl, digest);
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
