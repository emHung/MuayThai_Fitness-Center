package vn.muaythai.app;

import android.os.Handler;
import android.os.Looper;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;

final class CalendarUpdates {
  private static final int MAX_RECONNECT_DELAY_MS = 15000;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ApiClient api;
  private final Runnable changed;
  private final Runnable reconnect = this::connect;
  private EventSource stream;
  private boolean active;
  private int failures;

  CalendarUpdates(ApiClient api, Runnable changed) {
    this.api = api;
    this.changed = changed;
  }

  void start() {
    if (!active) {
      active = true;
      connect();
    }
  }

  void stop() {
    active = false;
    failures = 0;
    if (stream != null) {
      stream.cancel();
      stream = null;
    }
    main.removeCallbacks(reconnect);
    main.removeCallbacks(changed);
  }

  private void connect() {
    if (!active || stream != null) return;
    stream = api.calendarStream(new EventSourceListener() {
      @Override public void onEvent(EventSource source, String id, String type, String data) {
        if ("calendar".equals(type) || "ready".equals(type)) {
          main.post(() -> {
            if (!active || stream != source) return;
            failures = 0;
            main.removeCallbacks(changed);
            main.postDelayed(changed, 150);
          });
        }
      }

      @Override public void onClosed(EventSource source) {
        main.post(() -> retry(source));
      }

      @Override public void onFailure(EventSource source, Throwable error, Response response) {
        main.post(() -> retry(source));
      }
    });
  }

  private void retry(EventSource source) {
    if (!active || stream != source) return;
    stream = null;
    int delay = Math.min(MAX_RECONNECT_DELAY_MS, 1000 << Math.min(failures++, 4));
    main.postDelayed(reconnect, delay);
  }
}
