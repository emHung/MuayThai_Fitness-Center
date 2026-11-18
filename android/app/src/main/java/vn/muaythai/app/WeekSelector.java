package vn.muaythai.app;

import android.content.res.ColorStateList;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.function.Consumer;

/** A scrollable week strip; selecting a day delegates filtering to SchedulePane. */
final class WeekSelector {
  private final WorkspaceActivity work;
  private final Consumer<LocalDate> selected;
  private final LinearLayout days;
  private final HorizontalScrollView scroll;
  private static final String[] LABELS = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};

  WeekSelector(WorkspaceActivity work, Consumer<LocalDate> selected) {
    this.work = work;
    this.selected = selected;
    scroll = new HorizontalScrollView(work);
    scroll.setHorizontalScrollBarEnabled(false);
    days = new LinearLayout(work);
    days.setOrientation(LinearLayout.HORIZONTAL);
    scroll.addView(days);
  }

  HorizontalScrollView view() { return scroll; }

  void show(LocalDate day) {
    days.removeAllViews();
    LocalDate first = day.minusDays(day.getDayOfWeek().getValue() - 1L);
    for (int index = 0; index < 7; index++) {
      LocalDate date = first.plusDays(index);
      boolean active = date.equals(day);
      var button = new MaterialButton(work);
      button.setText(String.format(Locale.ROOT, "%s\n%d", LABELS[index], date.getDayOfMonth()));
      button.setTextSize(13);
      button.setContentDescription(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
          .withLocale(Locale.forLanguageTag("vi-VN"))));
      button.setCheckable(true);
      button.setChecked(active);
      button.setMinWidth(0);
      button.setMinimumWidth(0);
      button.setInsetTop(0);
      button.setInsetBottom(0);
      button.setCornerRadius(work.ui.dp(16));
      button.setPadding(0, 0, 0, 0);
      button.setBackgroundTintList(ColorStateList.valueOf(active ? work.ui.cards.accent()
          : ContextCompat.getColor(work, R.color.surface)));
      button.setTextColor(ContextCompat.getColor(work, active ? R.color.surface : R.color.text_primary));
      button.setSelected(active);
      button.setContentDescription(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
          .withLocale(Locale.forLanguageTag("vi-VN"))));
      button.setOnClickListener(view -> selected.accept(date));
      var size = new LinearLayout.LayoutParams(work.ui.dp(48), work.ui.dp(68));
      if (index != 0) size.leftMargin = work.ui.dp(6);
      size.height = work.ui.dp(56);
      days.addView(button, size);
    }
    scroll.post(() -> scroll.scrollTo(Math.max(0, work.ui.dp((day.getDayOfWeek().getValue() - 1) * 54) - scroll.getWidth() / 2), 0));
  }
}
