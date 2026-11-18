package vn.muaythai.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.LinearLayout;

/** Personal list preferences; business records stay on the server. */
final class HiddenItems {
  private final WorkspaceActivity work;
  private final SharedPreferences preferences;

  HiddenItems(WorkspaceActivity work) {
    this.work = work;
    preferences = work.getSharedPreferences("hidden-items-" + work.userId(), Context.MODE_PRIVATE);
  }

  boolean hidden(String scope, long id) {
    return preferences.getBoolean(scope + ":" + id, false);
  }

  void attach(LinearLayout row, String scope, long id, boolean eligible) {
    if (!eligible) return;
    row.setOnLongClickListener(view -> {
      work.confirm("Ẩn khỏi danh sách", "Chỉ ẩn trên tài khoản này tại thiết bị này. Lịch sử vẫn được giữ để đối soát.",
          "Ẩn", () -> {
            preferences.edit().putBoolean(scope + ":" + id, true).apply();
            ((android.view.View) row.getParent()).setVisibility(android.view.View.GONE);
            com.google.android.material.snackbar.Snackbar.make(work.findViewById(android.R.id.content), "Đã ẩn khỏi danh sách", 6000)
                .setAction("Hoàn tác", undo -> {
                  preferences.edit().remove(scope + ":" + id).apply();
                  ((android.view.View) row.getParent()).setVisibility(android.view.View.VISIBLE);
                }).show();
          });
      return true;
    });
  }

  void restoreAction(LinearLayout body, String scope) {
    if (preferences.getAll().keySet().stream().noneMatch(key -> key.startsWith(scope + ":"))) return;
    work.ui.iconAction(work.ui.actions(body), "Hiện lại các mục đã ẩn", R.drawable.ic_refresh,
        () -> work.confirm("Hiện lại các mục đã ẩn", "Khôi phục các mục đã ẩn trong danh sách này.", "Hiện lại", () -> {
          var edit = preferences.edit();
          preferences.getAll().keySet().stream().filter(key -> key.startsWith(scope + ":")).forEach(edit::remove);
          edit.apply();
          work.refresh();
        }));
  }
}
