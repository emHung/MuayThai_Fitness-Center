package vn.muaythai.app;

import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.Executors;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

final class ImagesPane {
  private final WorkspaceActivity work;
  private final ActivityResultLauncher<PickVisualMediaRequest> picker;
  private String path;
  private String title;
  private String previewUri;

  ImagesPane(WorkspaceActivity work) {
    this.work = work;
    Bundle saved = work.getSavedStateRegistry().consumeRestoredStateForKey("image_picker");
    if (saved != null) {
      path = saved.getString("path");
      title = saved.getString("title");
      previewUri = saved.getString("uri");
    }
    work.getSavedStateRegistry().registerSavedStateProvider("image_picker", () -> {
      Bundle state = new Bundle();
      state.putString("path", path);
      state.putString("title", title);
      state.putString("uri", previewUri);
      return state;
    });
    picker = work.registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
      if (uri != null && path != null) preview(uri, path, title);
    });
    if (previewUri != null && path != null) {
      work.getWindow().getDecorView().post(() -> preview(Uri.parse(previewUri), path, title));
    }
  }

  void show(LinearLayout parent, String path, boolean avatar, boolean editable) {
    var view = image(parent, avatar);
    var request = Glide.with(work).load(remote(path)).diskCacheStrategy(DiskCacheStrategy.NONE)
        .skipMemoryCache(true).placeholder(avatar ? R.drawable.ic_user : R.drawable.ic_book)
        .error(avatar ? R.drawable.ic_user : R.drawable.ic_book);
    if (avatar) request.circleCrop().into(view);
    else request.centerCrop().into(view);
    if (editable) editActions(parent, path, avatar);
  }

  void editActions(LinearLayout parent, String path, boolean avatar) {
      var actions = work.ui.actions(parent);
      actions.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
      work.ui.iconAction(actions, work.getString(R.string.choose_image), R.drawable.ic_image_edit, () -> {
        this.path = path;
        title = work.getString(avatar ? R.string.profile_image : R.string.course_image);
        picker.launch(new PickVisualMediaRequest.Builder()
            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
      });
      var gap = new android.view.View(work);
      gap.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      actions.addView(gap, new LinearLayout.LayoutParams(
          work.getResources().getDimensionPixelSize(R.dimen.image_action_gap), 1));
      work.ui.iconAction(actions, work.getString(R.string.delete_image), R.drawable.ic_delete_bin, () -> work.confirm(
          work.getString(R.string.delete_image), work.getString(R.string.delete_image_prompt),
          work.getString(R.string.delete_image),
          () -> work.request(work.api.service.delete(path), done -> work.refresh())));
  }

  private GlideUrl remote(String path) {
    return new GlideUrl(BuildConfig.AUTH_BASE_URL + path, new LazyHeaders.Builder()
        .addHeader("Authorization", work.api.authorization).build());
  }

  private ImageView image(LinearLayout parent, boolean avatar) {
    var view = new ImageView(work);
    view.setContentDescription(work.getString(avatar ? R.string.profile_image : R.string.course_image));
    view.setScaleType(ImageView.ScaleType.CENTER_CROP);
    if (avatar) view.setBackground(work.ui.cards.background(
        androidx.core.content.ContextCompat.getColor(work, R.color.surface), 20));
    var size = new LinearLayout.LayoutParams(avatar ? work.ui.dp(80) : -1, work.ui.dp(avatar ? 80 : 176));
    size.gravity = android.view.Gravity.CENTER_HORIZONTAL;
    size.bottomMargin = work.ui.dp(8);
    parent.addView(view, size);
    return view;
  }

  private void preview(Uri uri, String path, String title) {
    previewUri = uri.toString();
    var panel = new LinearLayout(work);
    panel.setOrientation(LinearLayout.VERTICAL);
    int padding = work.ui.dp(20);
    panel.setPadding(padding, padding, padding, padding);
    Glide.with(work).load(uri).fitCenter().into(image(panel, false));
    AlertDialog dialog = new MaterialAlertDialogBuilder(work).setTitle(title).setView(panel)
        .setNegativeButton(R.string.cancel_image, null).setPositiveButton(R.string.save_image, null).create();
    dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        .setOnClickListener(button -> upload(dialog, uri, path)));
    dialog.setOnDismissListener(dismissed -> previewUri = null);
    dialog.show();
  }

  private void enabled(AlertDialog dialog, boolean value) {
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(value);
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(value);
    dialog.setCancelable(value);
  }

  private void upload(AlertDialog dialog, Uri uri, String path) {
    enabled(dialog, false);
    var executor = Executors.newSingleThreadExecutor();
    executor.execute(() -> {
      com.bumptech.glide.request.FutureTarget<android.graphics.Bitmap> target = null;
      try (var input = work.getContentResolver().openInputStream(uri);
          var bytes = new ByteArrayOutputStream()) {
        if (input == null) throw new IOException("Image unavailable");
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
          if (bytes.size() + read > 5 * 1024 * 1024) throw new IOException("Image too large");
          bytes.write(buffer, 0, read);
        }
        target = Glide.with(work.getApplicationContext()).asBitmap()
            .load(bytes.toByteArray()).submit(1280, 1280);
        bytes.reset();
        if (!target.get().compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, bytes)) {
          throw new IOException("Image encoding failed");
        }
        byte[] content = bytes.toByteArray();
        work.runOnUiThread(() -> {
          if (work.isFinishing() || work.isDestroyed()) return;
          var part = MultipartBody.Part.createFormData("file", "image.jpg",
              RequestBody.create(content, MediaType.get("image/jpeg")));
          work.api.send(work.api.service.image(path, part), new ApiClient.Result() {
            @Override public void success(JsonElement json) {
              if (work.isFinishing() || work.isDestroyed()) return;
              dialog.dismiss();
              work.message(work.getString(R.string.image_saved));
              work.refresh();
            }
            @Override public void failure(String code, String message, JsonObject details) {
              if (work.isFinishing() || work.isDestroyed()) return;
              enabled(dialog, true);
              work.error(message);
            }
          });
        });
      } catch (Exception error) {
        if (error instanceof InterruptedException) Thread.currentThread().interrupt();
        android.util.Log.w("Images", "Unable to prepare selected image", error);
        work.runOnUiThread(() -> {
          if (work.isFinishing() || work.isDestroyed()) return;
          enabled(dialog, true);
          work.error(work.getString(R.string.image_failed));
        });
      } finally {
        if (target != null) Glide.with(work.getApplicationContext()).clear(target);
        executor.shutdown();
      }
    });
  }

}
