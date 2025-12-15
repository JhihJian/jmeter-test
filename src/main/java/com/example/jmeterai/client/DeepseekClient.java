package com.example.jmeterai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DeepseekClient {
  private final OkHttpClient http = new OkHttpClient.Builder()
      .connectTimeout(java.time.Duration.ofSeconds(60))
      .readTimeout(java.time.Duration.ofSeconds(300))
      .writeTimeout(java.time.Duration.ofSeconds(300))
      .build();
  private final ObjectMapper mapper = new ObjectMapper();
  private final String apiKey;
  private final String baseUrl;
  private final String model;

  public DeepseekClient(String apiKey, String baseUrl, String model) {
    this.apiKey = apiKey;
    this.baseUrl = baseUrl;
    this.model = model;
  }

  public String chat(String system, String user) throws Exception {
    String url = baseUrl.endsWith("/") ? baseUrl + "v1/chat/completions" : baseUrl + "/v1/chat/completions";
    String payload = buildPayload(system, user);
    Request req = new Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer " + apiKey)
        .addHeader("Content-Type", "application/json")
        .post(RequestBody.create(payload, MediaType.parse("application/json")))
        .build();
    try (Response resp = http.newCall(req).execute()) {
      String body = resp.body() == null ? "" : resp.body().string();
      if (!resp.isSuccessful()) throw new RuntimeException("LLM调用失败: " + resp.code() + " " + body);
      JsonNode root = mapper.readTree(body);
      JsonNode choices = root.path("choices");
      if (!choices.isArray() || choices.size() == 0) throw new RuntimeException("无结果");
      JsonNode msg = choices.get(0).path("message");
      return msg.path("content").asText();
    }
  }

  private String buildPayload(String system, String user) throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    sb.append("\"model\":\"").append(model).append("\",");
    sb.append("\"temperature\":0.2,");
    sb.append("\"messages\":[");
    sb.append("{\"role\":\"system\",\"content\":").append(jsonEscape(system)).append("},");
    sb.append("{\"role\":\"user\",\"content\":").append(jsonEscape(user)).append("}");
    sb.append("]}");
    return sb.toString();
  }

  private String jsonEscape(String s) throws Exception {
    return mapper.writeValueAsString(s);
  }
}
