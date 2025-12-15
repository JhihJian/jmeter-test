package com.example.jmeterai.model;

import com.example.jmeterai.util.OpenApiExtractor;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ApiUnderstanding {
  public ApiUnderstandingResult analyze(OpenApiExtractor.OpenApiInfo info, String extra) {
    ApiUnderstandingResult r = new ApiUnderstandingResult();
    r.isJson = info.root != null && info.root.isObject();
    if (!r.isJson) throw new RuntimeException("接口文档不是JSON或无法解析");
    JsonNode root = info.root;
    String baseUrl = info.baseUrl == null ? "" : info.baseUrl;
    Set<String> tags = new HashSet<>();
    JsonNode paths = root.path("paths");
    if (paths.isObject()) {
      Iterator<String> it = paths.fieldNames();
      while (it.hasNext()) {
        String p = it.next();
        JsonNode item = paths.path(p);
        Iterator<String> mit = item.fieldNames();
        while (mit.hasNext()) {
          String m = mit.next();
          JsonNode op = item.path(m);
          JsonNode t = op.path("tags");
          if (t.isArray()) for (JsonNode tn : t) tags.add(tn.asText(""));
          JsonNode params = op.path("parameters");
          if (params.isArray()) {
            for (JsonNode pn : params) {
              String name = pn.path("name").asText("");
              if (!name.isEmpty()) r.commonParams.add(name);
            }
          }
          String lowerPath = p.toLowerCase();
          String lowerSum = op.path("summary").asText("").toLowerCase();
          boolean authHint = lowerPath.contains("auth") || lowerPath.contains("token") || lowerSum.contains("auth") || lowerSum.contains("token");
          if (authHint && r.tokenEndpoint == null) r.tokenEndpoint = p;
        }
      }
    }
    r.commonParams = dedup(r.commonParams);
    r.tags = new ArrayList<>(tags);
    List<String> schemas = new ArrayList<>();
    JsonNode comps = root.path("components").path("schemas");
    if (comps.isObject()) comps.fieldNames().forEachRemaining(schemas::add);
    r.schemas = schemas;
    boolean hasSecurity = root.path("security").isArray() && root.path("security").size() > 0;
    boolean hasSchemes = root.path("components").path("securitySchemes").isObject();
    r.authNeeded = hasSecurity || hasSchemes || r.tokenEndpoint != null;
    r.summaryText = buildSummaryText(r, baseUrl, paths);
    return r;
  }

  private List<String> dedup(List<String> list) {
    List<String> out = new ArrayList<>();
    Set<String> s = new HashSet<>();
    for (String v : list) if (s.add(v)) out.add(v);
    return out;
  }

  private String buildSummaryText(ApiUnderstandingResult r, String baseUrl, JsonNode paths) {
    StringBuilder sb = new StringBuilder();
    sb.append("接口文档理解结果\n");
    sb.append("文档格式: ").append(r.isJson ? "JSON" : "非JSON").append("\n");
    sb.append("基础服务器: ").append(baseUrl).append("\n");
    sb.append("接口总数: ").append(paths != null && paths.isObject() ? paths.size() : 0).append("\n");
    sb.append("标签分类: ").append(r.tags.isEmpty() ? "无" : String.join(", ", r.tags)).append("\n");
    sb.append("模型定义: ").append(r.schemas.isEmpty() ? "无" : String.join(", ", r.schemas)).append("\n");
    sb.append("共性参数: ").append(r.commonParams.isEmpty() ? "无" : String.join(", ", r.commonParams)).append("\n");
    sb.append("认证需求: ").append(r.authNeeded ? "可能需要认证" : "未知或不需要").append("\n");
    if (r.tokenEndpoint != null) sb.append("令牌接口: ").append(r.tokenEndpoint).append("\n");
    if (paths != null && paths.isObject()) {
      sb.append("\n接口参数摘要:\n");
      java.util.Iterator<String> it = paths.fieldNames();
      while (it.hasNext()) {
        String p = it.next();
        JsonNode item = paths.path(p);
        java.util.Iterator<String> mit = item.fieldNames();
        while (mit.hasNext()) {
          String m = mit.next();
          JsonNode op = item.path(m);
          java.util.List<String> plist = new java.util.ArrayList<>();
          JsonNode params = op.path("parameters");
          if (params.isArray()) {
            for (JsonNode pn : params) {
              String name = pn.path("name").asText("");
              String in = pn.path("in").asText("");
              if (!name.isEmpty()) plist.add(in+":"+name);
            }
          }
          boolean hasBody = op.path("requestBody").isObject();
          sb.append("- ").append(m.toUpperCase()).append(" ").append(p).append(" | 参数: ")
            .append(plist.isEmpty() ? "无" : String.join(", ", plist))
            .append(" | 请求体: ").append(hasBody ? "有" : "无").append("\n");
        }
      }
    }
    return sb.toString();
  }
}
