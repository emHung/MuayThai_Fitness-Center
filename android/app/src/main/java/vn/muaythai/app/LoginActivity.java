package vn.muaythai.app;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import vn.muaythai.app.databinding.ActivityLoginBinding;

public final class LoginActivity extends AppCompatActivity {
  private enum Page { LOGIN, CHANGE_PASSWORD, PROFILE }

  private ActivityLoginBinding ui;
  private Page page = Page.LOGIN;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ui = ActivityLoginBinding.inflate(getLayoutInflater());
    setContentView(ui.getRoot());
    applyInsets();
    getLifecycle().addObserver(new ConnectivityNotice(this, ui.getRoot(), null));
    getLifecycle().addObserver(new AppUpdatePrompt(this));

    LoginViewModel model = new ViewModelProvider(this).get(LoginViewModel.class);
    ui.submit.setOnClickListener(view -> model.submit(
        value(ui.email), value(ui.password), value(ui.newPassword), value(ui.repeatPassword)));
    ui.logout.setOnClickListener(view -> model.logout());
    model.state().observe(this, this::render);
  }

  private void applyInsets() {
    int space = ui.content.getPaddingTop();
    ViewCompat.setOnApplyWindowInsetsListener(ui.getRoot(), (view, window) -> {
      Insets bars = window.getInsets(WindowInsetsCompat.Type.systemBars()
          | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
      ui.content.setPadding(space + bars.left, space + bars.top, space + bars.right, space + bars.bottom);
      return window;
    });
  }

  private void render(LoginViewModel.State state) {
    if (state.session != null && !state.session.mustChangePassword && !isFinishing()) {
      WorkspaceActivity.open(this, state.session);
      return;
    }
    Page next = state.session == null ? Page.LOGIN
        : state.session.mustChangePassword ? Page.CHANGE_PASSWORD : Page.PROFILE;
    if (next != page) {
      ui.password.setText("");
      ui.newPassword.setText("");
      ui.repeatPassword.setText("");
      page = next;
    }

    ui.formCard.setVisibility(visibility(page != Page.PROFILE));
    ui.emailField.setVisibility(visibility(page == Page.LOGIN));
    ui.newPasswordField.setVisibility(visibility(page == Page.CHANGE_PASSWORD));
    ui.repeatField.setVisibility(visibility(page == Page.CHANGE_PASSWORD));
    ui.passwordField.setHint(getString(
        page == Page.CHANGE_PASSWORD ? R.string.temporary_password : R.string.password));
    ui.logout.setVisibility(visibility(page != Page.LOGIN));
    ui.submit.setEnabled(!state.busy);
    ui.logout.setEnabled(!state.busy);
    ui.progress.setVisibility(visibility(state.busy));
    ui.feedback.setVisibility(visibility(!state.message.isEmpty()));
    ui.feedback.setText(state.message);
    ui.formTitle.setText(page == Page.CHANGE_PASSWORD ? R.string.password_fields_title : R.string.account_fields_title);
    switch (page) {
      case LOGIN:
        ui.title.setText(R.string.app_name);
        ui.subtitle.setText(R.string.login_hint);
        ui.submit.setText(R.string.sign_in);
        break;
      case CHANGE_PASSWORD:
        ui.title.setText(R.string.change_title);
        ui.subtitle.setText(R.string.change_hint);
        ui.submit.setText(R.string.change_password);
        break;
      case PROFILE:
        ui.title.setText(getString(R.string.welcome, state.session.user.displayName));
        ui.subtitle.setText(roleName(state.session.user.role));
        break;
    }
  }

  private int roleName(String role) {
    switch (role) {
      case "ADMIN": return R.string.role_admin;
      case "RECEPTION": return R.string.role_reception;
      case "TRAINER": return R.string.role_trainer;
      default: return R.string.role_learner;
    }
  }

  private static int visibility(boolean visible) {
    return visible ? View.VISIBLE : View.GONE;
  }

  private static String value(EditText edit) {
    return edit.getText() == null ? "" : edit.getText().toString();
  }
}
