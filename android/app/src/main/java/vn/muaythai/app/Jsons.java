package vn.muaythai.app;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

final class Jsons {
  private static final Gson GSON = new Gson();

  private Jsons() {}

  static JsonObject body(Object... pairs) {
    var json = new JsonObject();
    for (int i = 0; i < pairs.length; i += 2) {
      Object value = pairs[i + 1];
      if (value != null) {
        json.add((String) pairs[i], GSON.toJsonTree(value));
      }
    }
    return json;
  }

  static JsonObject object(JsonElement json) {
    return json != null && json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
  }

  static List<JsonObject> rows(JsonElement json) {
    JsonElement array = json != null && json.isJsonObject() ? json.getAsJsonObject().get("items") : json;
    List<JsonObject> rows = new ArrayList<>();
    if (array != null && array.isJsonArray()) {
      for (JsonElement child : array.getAsJsonArray()) {
        if (child.isJsonObject()) {
          rows.add(child.getAsJsonObject());
        }
      }
    }
    return rows;
  }

  static String text(JsonObject json, String key) {
    return text(json, key, "");
  }

  static String text(JsonObject json, String key, String fallback) {
    JsonElement value = json.get(key);
    return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? fallback : value.getAsString();
  }

  static long id(JsonObject json) {
    return number(json, "id");
  }

  static long number(JsonObject json, String key) {
    String value = text(json, key);
    return value.isEmpty() ? 0 : Long.parseLong(value);
  }
}
