package com.example.jmeterai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiClient {
  private final OkHttpClient http = new OkHttpClient.Builder()
      .connectTimeout(java.time.Duration.ofSeconds(60))
      .readTimeout(java.time.Duration.ofSeconds(300))
      .writeTimeout(java.time.Duration.ofSeconds(300))
      .build();
  private final ObjectMapper mapper = new ObjectMapper();
  private final String apiKey;
  private final String baseUrl;
  private final String model;

  public GeminiClient(String apiKey, String baseUrl, String model) {
    this.apiKey = apiKey;
    this.baseUrl = baseUrl;
    this.model = model;
  }

  public String chat(String system, String user) throws Exception {
    String url = baseUrl.endsWith("/") ? baseUrl + "v1beta/models/" + model + ":generateContent" : baseUrl + "/v1beta/models/" + model + ":generateContent";
    String payload = buildPayload(system + "\n" + user);
    Request req = new Request.Builder()
        .url(url)
        .addHeader("x-goog-api-key", apiKey)
        .addHeader("Content-Type", "application/json")
        .post(RequestBody.create(payload, MediaType.parse("application/json")))
        .build();
    try (Response resp = http.newCall(req).execute()) {
      String body = resp.body() == null ? "" : resp.body().string();
      if (!resp.isSuccessful()) throw new RuntimeException("LLM调用失败: " + resp.code() + " " + body);
      JsonNode root = mapper.readTree(body);
      JsonNode candidates = root.path("candidates");
      if (!candidates.isArray() || candidates.size() == 0) throw new RuntimeException("无结果");
      JsonNode content = candidates.get(0).path("content");
      JsonNode parts = content.path("parts");
      if (!parts.isArray() || parts.size() == 0) throw new RuntimeException("无结果");
      return parts.get(0).path("text").asText("");
    }
  }

  private String buildPayload(String text) throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    sb.append("\"contents\":[{");
    sb.append("\"parts\":[{");
    sb.append("\"text\":").append(jsonEscape(text));
    sb.append("}]");
    sb.append("}]");
    sb.append("}");
    return sb.toString();
  }

  private String jsonEscape(String s) throws Exception {
    return mapper.writeValueAsString(s);
  }
}
