package vn.muaythai.app;

import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.gson.JsonObject;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class PtSlotGrid {
  private static final int COLUMNS = 3;
  private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
  private final WorkspaceActivity work;
  private final LinearLayout panel;
  private int loadSequence;
  private TextView selected;

  PtSlotGrid(WorkspaceActivity work, LinearLayout parent) {
    this.work = work;
    panel = new LinearLayout(work);
    panel.setOrientation(LinearLayout.VERTICAL);
    work.ui.add(parent, panel, 8);
  }

  Runnable bindDate(EditText date, Supplier<String> query, Consumer<String> select, Runnable resetSelection) {
    Runnable refresh = () -> {
      resetSelection.run();
      load(query.get(), select);
    };
    date.addTextChangedListener(new TextWatcher() {
      private String previous = WorkspaceUi.read(date);
      @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
      @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
      @Override public void afterTextChanged(Editable text) {
        String current = WorkspaceUi.read(date);
        if (!current.equals(previous)) {
          previous = current;
          refresh.run();
        }
      }
    });
    refresh.run();
    return refresh;
  }

  void load(String query, Consumer<String> select) {
    int requested = ++loadSequence;
    panel.removeAllViews();
    selected = null;
    work.request(work.api.service.get("api/pt/slots?" + query), result -> {
      if (requested == loadSequence) show(Jsons.rows(result), select);
    }, error -> { if (requested == loadSequence) work.error(error); });
  }

  private void show(List<JsonObject> slots, Consumer<String> select) {
    if (slots.isEmpty()) {
      work.ui.cards.empty(panel, "Chưa có khung giờ phù hợp", "Thử chọn ngày khác để xem ca làm của huấn luyện viên.", R.drawable.ic_calendar);
      return;
    }
    work.ui.label(panel, "Xanh · Còn trống     Xám · Không khả dụng", 13, false);
    for (int first = 0; first < slots.size(); first += COLUMNS) {
      LinearLayout row = new LinearLayout(work);
      row.setWeightSum(COLUMNS);
      work.ui.add(panel, row, 6);
      for (int index = first; index < Math.min(first + COLUMNS, slots.size()); index++) {
        JsonObject slot = slots.get(index);
        String start = Jsons.text(slot, "startAt");
        boolean open = "true".equals(Jsons.text(slot, "available"));
        String time = OffsetDateTime.parse(start).atZoneSameInstant(WorkspaceUi.ZONE).format(HOUR);
        TextView cell = new TextView(work);
        cell.setGravity(Gravity.CENTER);
        cell.setText(time);
        cell.setTextSize(15);
        cell.setTextColor(work.getColor(open ? R.color.slot_open_text : R.color.slot_closed_text));
        cell.setContentDescription(time + (open ? ", còn trống" : ", không khả dụng"));
        GradientDrawable surface = new GradientDrawable();
        surface.setCornerRadius(work.getResources().getDimension(R.dimen.field_radius));
        surface.setColor(work.getColor(open ? R.color.slot_open_surface : R.color.slot_closed_surface));
        cell.setBackground(surface);
        if (open) {
          cell.setOnClickListener(view -> {
            if (selected != null) {
              selected.setSelected(false);
              selected.setTextColor(work.getColor(R.color.slot_open_text));
              ((GradientDrawable) selected.getBackground()).setStroke(0, 0);
            }
            selected = cell;
            cell.setSelected(true);
            cell.setTextColor(work.ui.cards.accent());
            surface.setStroke(work.ui.dp(3), work.ui.cards.accent());
            cell.announceForAccessibility("Đã chọn " + time);
            select.accept(start);
          });
          cell.setFocusable(true);
        }
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, work.ui.dp(48), 1);
        size.setMarginEnd(work.ui.dp(6));
        row.addView(cell, size);
      }
    }
  }
}
