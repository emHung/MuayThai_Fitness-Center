package vn.muaythai.app;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

final class AuthRepository {
  interface Result<T> {
    void success(T value);
    void failure(String code, String message);
  }

  private static final long REQUEST_TIMEOUT_SECONDS = 15;
  private final Gson gson = new Gson();
  private final AuthApi api = new Retrofit.Builder()
      .baseUrl(BuildConfig.AUTH_BASE_URL)
      .client(new OkHttpClient.Builder().callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).build())
      .addConverterFactory(GsonConverterFactory.create(gson))
      .build().create(AuthApi.class);

  Call<AuthApi.Session> login(String username, String password, Result<AuthApi.Session> result) {
    return submit(api.login(new AuthApi.Login(username, password)), result);
  }

  Call<AuthApi.Session> changePassword(
      AuthApi.Session session, String current, String next, Result<AuthApi.Session> result) {
    return submit(api.changePassword(session.authorization(), new AuthApi.PasswordChange(current, next)), result);
  }

  Call<Void> logout(AuthApi.Session session, Result<Void> result) {
    return submit(api.logout(session.authorization()), result);
  }

  private <T> Call<T> submit(Call<T> call, Result<T> result) {
    call.enqueue(new Callback<>() {
      @Override
      public void onResponse(Call<T> request, Response<T> response) {
        if (response.isSuccessful()) {
          result.success(response.body());
          return;
        }
        try (ResponseBody body = response.errorBody()) {
          ApiError error = body == null ? null : gson.fromJson(body.string(), ApiError.class);
          if (error != null && error.code != null && error.message != null) {
            result.failure(error.code, error.message);
            return;
          }
        } catch (IOException | JsonParseException ignored) {
          // Fall back to the HTTP response when an upstream service returns a non-JSON error.
        }
        if (response.code() == 401) {
          result.failure("SESSION_EXPIRED", "Phiên đăng nhập đã hết hiệu lực.");
        } else {
          result.failure("HTTP_ERROR", "Hệ thống chưa xử lý được yêu cầu.");
        }
      }

      @Override
      public void onFailure(Call<T> request, Throwable error) {
        if (!request.isCanceled()) {
          result.failure("NETWORK_ERROR", "Không kết nối được máy chủ. Vui lòng thử lại.");
        }
      }
    });
    return call;
  }

  private static final class ApiError {
    String code;
    String message;
  }
}
