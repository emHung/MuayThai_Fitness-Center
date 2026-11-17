package vn.muaythai.app;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

interface AuthApi {
  @POST("api/auth/login")
  Call<Session> login(@Body Login request);

  @POST("api/auth/change-password")
  Call<Session> changePassword(@Header("Authorization") String authorization, @Body PasswordChange request);

  @POST("api/auth/logout")
  Call<Void> logout(@Header("Authorization") String authorization);

  final class Login {
    final String username;
    final String password;

    Login(String username, String password) {
      this.username = username;
      this.password = password;
    }
  }

  final class PasswordChange {
    final String currentPassword;
    final String newPassword;

    PasswordChange(String currentPassword, String newPassword) {
      this.currentPassword = currentPassword;
      this.newPassword = newPassword;
    }
  }

  final class Session {
    String token;
    User user;
    boolean mustChangePassword;

    String authorization() {
      return "Bearer " + token;
    }

    boolean valid() {
      return token != null && !token.isEmpty() && user != null && user.role != null
          && user.displayName != null;
    }
  }

  final class User {
    long id;
    boolean canAdjustCheckins;
    String displayName;
    String role;
  }
}
