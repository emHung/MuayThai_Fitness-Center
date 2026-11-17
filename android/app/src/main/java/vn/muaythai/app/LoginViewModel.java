package vn.muaythai.app;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import retrofit2.Call;

public final class LoginViewModel extends ViewModel {
  static final class State {
    final AuthApi.Session session;
    final boolean busy;
    final String message;

    State(AuthApi.Session session, boolean busy, String message) {
      this.session = session;
      this.busy = busy;
      this.message = message;
    }
  }

  private final AuthRepository repository = new AuthRepository();
  private final MutableLiveData<State> state = new MutableLiveData<>(new State(null, false, ""));
  private Call<?> pending;

  LiveData<State> state() {
    return state;
  }

  void submit(String email, String password, String newPassword, String repeatedPassword) {
    State current = state.getValue();
    if (current.busy) {
      return;
    }
    if (current.session == null) {
      if (email.trim().isEmpty() || password.isEmpty()) {
        show(null, false, "Nhập email và mật khẩu.");
        return;
      }
      show(null, true, "");
      pending = repository.login(email.trim(), password, sessionResult(null));
      return;
    }
    if (!current.session.mustChangePassword) {
      return;
    }
    if (password.isEmpty() || newPassword.length() < 8 || !newPassword.equals(repeatedPassword)) {
      show(current.session, false, "Nhập mật khẩu tạm và xác nhận mật khẩu mới từ 8 ký tự.");
      return;
    }
    show(current.session, true, "");
    pending = repository.changePassword(current.session, password, newPassword, sessionResult(current.session));
  }

  void logout() {
    State current = state.getValue();
    if (current.busy || current.session == null) {
      return;
    }
    show(current.session, true, "");
    pending = repository.logout(current.session, new AuthRepository.Result<>() {
      @Override
      public void success(Void ignored) {
        show(null, false, "");
      }

      @Override
      public void failure(String code, String message) {
        handleFailure(current.session, code, message);
      }
    });
  }

  private AuthRepository.Result<AuthApi.Session> sessionResult(AuthApi.Session previous) {
    return new AuthRepository.Result<>() {
      @Override
      public void success(AuthApi.Session session) {
        if (session == null || !session.valid()) {
          show(previous, false, "Máy chủ trả về thông tin đăng nhập không hợp lệ.");
          return;
        }
        show(session, false, "");
      }

      @Override
      public void failure(String code, String message) {
        handleFailure(previous, code, message);
      }
    };
  }

  private void handleFailure(AuthApi.Session previous, String code, String message) {
    boolean invalid = "SESSION_EXPIRED".equals(code) || "ACCOUNT_LOCKED".equals(code);
    show(invalid ? null : previous, false, message);
  }

  private void show(AuthApi.Session session, boolean busy, String message) {
    state.setValue(new State(session, busy, message));
  }

  @Override
  protected void onCleared() {
    if (pending != null) {
      pending.cancel();
    }
  }
}
