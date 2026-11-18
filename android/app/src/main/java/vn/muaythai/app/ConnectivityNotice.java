package vn.muaythai.app;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.provider.Settings;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import com.google.android.material.snackbar.Snackbar;

/** Observes device Internet connectivity without retrying business operations. */
final class ConnectivityNotice implements DefaultLifecycleObserver {
  private final AppCompatActivity activity;
  private final View root;
  private final View anchor;
  private final ConnectivityManager manager;
  private ConnectivityManager.NetworkCallback callback;
  private Network current;
  private Boolean online;
  private Snackbar notice;
  private int generation;

  ConnectivityNotice(AppCompatActivity activity, View root, View anchor) {
    this.activity = activity;
    this.root = root;
    this.anchor = anchor;
    manager = activity.getSystemService(ConnectivityManager.class);
  }

  @Override public void onResume(LifecycleOwner owner) {
    int started = ++generation;
    callback = new ConnectivityManager.NetworkCallback() {
      private void update(Runnable action) {
        activity.runOnUiThread(() -> {
          if (generation == started && !activity.isDestroyed()) action.run();
        });
      }

      @Override public void onAvailable(Network network) {
        update(() -> current = network);
      }

      @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
        update(() -> {
          if (network.equals(current)) render(connected(capabilities));
        });
      }

      @Override public void onLost(Network network) {
        update(() -> {
          if (network.equals(current)) {
            current = null;
            render(false);
          }
        });
      }
    };
    manager.registerDefaultNetworkCallback(callback);
    current = manager.getActiveNetwork();
    render(connected(current == null ? null : manager.getNetworkCapabilities(current)));
  }

  @Override public void onPause(LifecycleOwner owner) {
    generation++;
    if (callback != null) {
      manager.unregisterNetworkCallback(callback);
      callback = null;
    }
    if (notice != null) notice.dismiss();
    notice = null;
  }

  private static boolean connected(NetworkCapabilities capabilities) {
    return capabilities != null
        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
  }

  private void render(boolean connected) {
    Boolean previous = online;
    online = connected;
    if (connected && Boolean.TRUE.equals(previous)) return;
    if (connected) {
      if (notice != null) notice.dismiss();
      notice = null;
      if (Boolean.FALSE.equals(previous)) {
        notice = bar(R.string.network_restored, Snackbar.LENGTH_LONG);
        notice.show();
      }
    } else if (!Boolean.FALSE.equals(previous) || notice == null || !notice.isShownOrQueued()) {
      if (notice != null) notice.dismiss();
      notice = bar(R.string.network_offline, Snackbar.LENGTH_INDEFINITE)
          .setAction(R.string.network_settings, view -> activity.startActivity(
              new Intent(Settings.ACTION_WIRELESS_SETTINGS)));
      notice.show();
    }
  }

  private Snackbar bar(int message, int duration) {
    Snackbar result = Snackbar.make(root, message, duration).setTextMaxLines(3);
    if (anchor != null) result.setAnchorView(anchor);
    return result;
  }
}
