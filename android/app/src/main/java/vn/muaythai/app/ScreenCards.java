package vn.muaythai.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

/** Shared screen composition. Business callbacks and data stay in the owning Pane. */
final class ScreenCards {
  private final WorkspaceUi ui;
  private final Context context;

  ScreenCards(WorkspaceUi ui, Context context) {
    this.ui = ui;
    this.context = context;
  }

  int accent() {
    String role = context instanceof WorkspaceActivity work ? work.role() : "ADMIN";
    return ContextCompat.getColor(context, switch (role) {
      case "TRAINER" -> R.color.trainer;
      case "LEARNER" -> R.color.learner;
      default -> R.color.admin;
    });
  }

  GradientDrawable background(int color, int radius) {
    var shape = new GradientDrawable();
    shape.setColor(color);
    shape.setCornerRadius(ui.dp(radius));
    return shape;
  }

  ImageView icon(int resource, int size, boolean filled) {
    var image = new ImageView(context);
    image.setImageResource(resource);
    image.setImageTintList(ColorStateList.valueOf(filled
        ? ContextCompat.getColor(context, R.color.surface) : accent()));
    image.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10));
    image.setBackground(background(filled ? accent() : ColorUtils.setAlphaComponent(accent(), 18), 16));
    image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    image.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(size), ui.dp(size)));
    return image;
  }

  LinearLayout hero(LinearLayout parent, String title, String description, int resource) {
    var panel = ui.card(parent);
    var card = (com.google.android.material.card.MaterialCardView) panel.getParent();
    card.setCardBackgroundColor(accent());
    card.setStrokeWidth(0);
    panel.setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(20));
    panel.setOrientation(LinearLayout.HORIZONTAL);
    panel.setGravity(Gravity.CENTER_VERTICAL);
    var text = new LinearLayout(context);
    text.setOrientation(LinearLayout.VERTICAL);
    panel.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
    TextView heading = ui.label(text, title, 22, true);
    heading.setTextColor(ContextCompat.getColor(context, R.color.surface));
    if (!description.isEmpty()) {
      var body = ui.label(text, description, 14, false);
      body.setTextColor(ContextCompat.getColor(context, R.color.surface));
      body.setLineSpacing(ui.dp(2), 1);
      body.setAlpha(.9f);
    }
    var image = icon(resource, 64, true);
    image.setAlpha(.65f);
    var size = new LinearLayout.LayoutParams(ui.dp(64), ui.dp(64));
    size.leftMargin = ui.dp(12);
    panel.addView(image, size);
    return text;
  }

  LinearLayout section(LinearLayout parent, String title, int resource) {
    var panel = ui.card(parent);
    var header = new LinearLayout(context);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.addView(icon(resource, 36, false));
    var label = ui.label(header, title, 17, true);
    var size = new LinearLayout.LayoutParams(0, -2, 1);
    size.leftMargin = ui.dp(10);
    label.setLayoutParams(size);
    ui.add(panel, header, 0);
    return panel;
  }

  LinearLayout grid(LinearLayout parent) {
    return grid(parent, 2);
  }

  LinearLayout grid(LinearLayout parent, int columns) {
    var grid = new LinearLayout(context);
    grid.setOrientation(LinearLayout.VERTICAL);
    grid.setTag(columns);
    ui.add(parent, grid, 0);
    return grid;
  }

  void pill(LinearLayout row, String title, int resource, Runnable action) {
    var button = ui.action(row, title, action);
    button.setTextSize(12);
    button.setIconResource(resource);
    button.setIconSize(ui.dp(18));
    button.setIconTint(ColorStateList.valueOf(accent()));
    button.setTextColor(accent());
    button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.surface)));
    button.setStrokeWidth(ui.dp(1));
    button.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.border)));
    button.setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(4));
    var size = new LinearLayout.LayoutParams(0, -2, 1);
    if (row.getChildCount() > 1) size.leftMargin = ui.dp(8);
    button.setLayoutParams(size);
  }

  void tile(LinearLayout grid, String title, String subtitle, int resource, Runnable action) {
    var configuration = context.getResources().getConfiguration();
    int columns = configuration.screenWidthDp >= 360 && configuration.fontScale < 1.2f
        ? (Integer) grid.getTag() : 1;
    LinearLayout row = grid.getChildCount() == 0 ? null
        : (LinearLayout) grid.getChildAt(grid.getChildCount() - 1);
    if (row == null || row.getChildCount() >= columns) {
      row = new LinearLayout(context);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setBaselineAligned(false);
      ui.add(grid, row, 8);
    }
    var card = new com.google.android.material.card.MaterialCardView(context);
    card.setRadius(ui.dp(22));
    card.setCardElevation(0);
    card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface));
    card.setStrokeColor(ContextCompat.getColor(context, R.color.border));
    card.setStrokeWidth(ui.dp(1));
    var size = new LinearLayout.LayoutParams(0, -1, 1);
    if (row.getChildCount() != 0) size.leftMargin = ui.dp(10);
    row.addView(card, size);
    var content = new LinearLayout(context);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14));
    content.setMinimumHeight(ui.dp(116));
    content.addView(icon(resource, 40, false));
    ui.label(content, title, 15, true);
    if (!subtitle.isEmpty()) ui.label(content, subtitle, 12, false);
    card.addView(content);
    card.setContentDescription(title + (subtitle.isEmpty() ? "" : ". " + subtitle));
    card.setFocusable(true);
    card.setClickable(true);
    card.setOnClickListener(view -> action.run());
  }

  void menu(LinearLayout parent, String title, String subtitle, int resource, Runnable action) {
    var panel = ui.card(parent);
    panel.setOrientation(LinearLayout.HORIZONTAL);
    panel.setGravity(Gravity.CENTER_VERTICAL);
    panel.setMinimumHeight(ui.dp(68));
    panel.addView(icon(resource, 44, false));
    var text = new LinearLayout(context);
    text.setOrientation(LinearLayout.VERTICAL);
    var size = new LinearLayout.LayoutParams(0, -2, 1);
    size.leftMargin = ui.dp(12);
    panel.addView(text, size);
    ui.label(text, title, 16, true);
    if (!subtitle.isEmpty()) ui.label(text, subtitle, 13, false);
    var arrow = new ImageView(context);
    arrow.setImageResource(R.drawable.ic_chevron);
    arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    panel.addView(arrow, new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)));
    panel.setFocusable(true);
    panel.setOnClickListener(view -> action.run());
    android.util.TypedValue ripple = new android.util.TypedValue();
    context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
    panel.setForeground(ContextCompat.getDrawable(context, ripple.resourceId));
  }

  void empty(LinearLayout parent, String title, String description, int resource) {
    var panel = new LinearLayout(context);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setGravity(Gravity.CENTER_HORIZONTAL);
    panel.setPadding(ui.dp(16), ui.dp(32), ui.dp(16), ui.dp(28));
    panel.addView(icon(resource, 64, false));
    var heading = ui.label(panel, title, 17, true);
    heading.setGravity(Gravity.CENTER);
    if (!description.isEmpty()) {
      var body = ui.label(panel, description, 14, false);
      body.setGravity(Gravity.CENTER);
    }
    ui.add(parent, panel, 12);
  }
}
