package vn.muaythai.app;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okhttp3.MultipartBody;
import retrofit2.http.Multipart;
import retrofit2.http.Part;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Url;

final class ApiClient {
  interface Service {
    @Multipart @PUT Call<JsonElement> image(@Url String path, @Part MultipartBody.Part file);
    @GET Call<JsonElement> get(@Url String path);
    @POST Call<JsonElement> post(@Url String path, @Header("Idempotency-Key") String key, @Body JsonObject body);
    @PUT Call<JsonElement> put(@Url String path, @Body JsonObject body);
    @PATCH Call<JsonElement> patch(@Url String path, @Body JsonObject body);
    @DELETE Call<JsonElement> delete(@Url String path);
  }

  interface Result {
    void success(JsonElement json);
    void failure(String code, String message, JsonObject details);
  }

  private final Gson gson = new Gson();
  private final EventSource.Factory calendarEvents;
  final Service service;
  final String authorization;

  ApiClient(String authorization) {
    this.authorization = authorization;
    OkHttpClient client = new OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
            .header("Authorization", authorization).build()))
        .build();
    calendarEvents = EventSources.createFactory(client.newBuilder()
        .callTimeout(0, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build());
    service = new Retrofit.Builder().baseUrl(BuildConfig.AUTH_BASE_URL).client(client)
        .addConverterFactory(GsonConverterFactory.create(gson)).build().create(Service.class);
  }

  EventSource calendarStream(EventSourceListener listener) {
    return calendarEvents.newEventSource(new Request.Builder()
        .url(BuildConfig.AUTH_BASE_URL + "api/sessions/events").build(), listener);
  }

  void send(Call<JsonElement> call, Result result) {
    call.enqueue(new Callback<>() {
      @Override
      public void onResponse(Call<JsonElement> request, Response<JsonElement> response) {
        if (response.isSuccessful()) {
          result.success(response.body());
          return;
        }
        JsonObject error = new JsonObject();
        try (ResponseBody body = response.errorBody()) {
          JsonObject parsed = body == null ? null : gson.fromJson(body.string(), JsonObject.class);
          if (parsed != null) {
            error = parsed;
          }
        } catch (IOException | IllegalStateException | com.google.gson.JsonParseException ignored) {
          // A proxy can produce a non-JSON response.
        }
        String fallback = response.code() == 401 ? "SESSION_EXPIRED" : "HTTP_ERROR";
        result.failure(Jsons.text(error, "code", fallback),
            Jsons.text(error, "message", "Máy chủ chưa xử lý được yêu cầu."),
            Jsons.object(error.get("details")));
      }

      @Override
      public void onFailure(Call<JsonElement> request, Throwable error) {
        result.failure("NETWORK_ERROR", "Không kết nối được máy chủ. Vui lòng thử lại.", new JsonObject());
      }
    });
  }
}
