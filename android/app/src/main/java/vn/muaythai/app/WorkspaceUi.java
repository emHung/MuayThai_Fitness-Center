package vn.muaythai.app;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;

final class WorkspaceUi {
  static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private final Context context;

  WorkspaceUi(Context context) {
    this.context = context;
  }

  TextView label(LinearLayout parent, String text, int size, boolean strong) {
    TextView view = new TextView(context);
    view.setText(text);
    view.setTextSize(size);
    view.setTextColor(ContextCompat.getColor(context, strong ? R.color.text_primary : R.color.text_secondary));
    if (strong) {
      view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
    }
    add(parent, view, 4);
    return view;
  }

  void action(LinearLayout parent, String title, Runnable run) {
    MaterialButton button = new MaterialButton(context);
    button.setText(title);
    button.setAllCaps(false);
    button.setMinHeight(dp(46));
    button.setCornerRadius(dp(12));
    button.setOnClickListener(view -> run.run());
    add(parent, button, 7);
  }

  void row(LinearLayout parent, String title, String subtitle, Runnable action) {
    LinearLayout column = card(parent);
    label(column, title, 15, true);
    if (!subtitle.trim().isEmpty()) {
      label(column, subtitle, 13, false);
    }
    if (action != null) {
      column.setFocusable(true);
      column.setOnClickListener(view -> action.run());
      android.util.TypedValue ripple = new android.util.TypedValue();
      context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
      column.setForeground(ContextCompat.getDrawable(context, ripple.resourceId));
    }
  }

  LinearLayout card(LinearLayout parent) {
    MaterialCardView card = new MaterialCardView(context);
    card.setRadius(dp(16));
    card.setCardElevation(0);
    card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface));
    card.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.border)));
    card.setStrokeWidth(dp(1));
    LinearLayout column = new LinearLayout(context);
    column.setOrientation(LinearLayout.VERTICAL);
    column.setPadding(dp(14), dp(9), dp(14), dp(11));
    card.addView(column);
    add(parent, card, 8);
    return column;
  }

  EditText field(LinearLayout parent, String title, String content, int type) {
    TextInputLayout layout = new TextInputLayout(context);
    layout.setHint(title);
    layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
    TextInputEditText edit = new TextInputEditText(layout.getContext());
    edit.setInputType(type);
    edit.setText(content);
    edit.setMinHeight(dp(48));
    layout.addView(edit, new LinearLayout.LayoutParams(-1, -2));
    add(parent, layout, 7);
    return edit;
  }

  EditText field(LinearLayout parent, String title, String content) {
    return field(parent, title, content, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
  }

  EditText date(LinearLayout parent, String title, String initial) {
    EditText input = field(parent, title, initial);
    input.setFocusable(false);
    input.setOnClickListener(view -> {
      LocalDate date = read(input).isEmpty() ? LocalDate.now(ZONE) : LocalDate.parse(read(input));
      new DatePickerDialog(context,
          (picker, year, month, day) -> input.setText(LocalDate.of(year, month + 1, day).toString()),
          date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth()).show();
    });
    return input;
  }

  EditText time(LinearLayout parent, String title, String initial) {
    EditText input = field(parent, title, initial);
    input.setFocusable(false);
    input.setOnClickListener(view -> {
      LocalTime time = LocalTime.parse(read(input));
      new TimePickerDialog(context,
          (picker, hour, minute) -> input.setText(String.format(Locale.ROOT, "%02d:%02d", hour, minute)),
          time.getHour(), time.getMinute(), true).show();
    });
    return input;
  }

  void add(LinearLayout parent, View view, int margin) {
    LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
    size.topMargin = dp(margin);
    parent.addView(view, size);
  }

  static String read(EditText edit) {
    return edit.getText() == null ? "" : edit.getText().toString().trim();
  }

  int dp(int value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }
}
