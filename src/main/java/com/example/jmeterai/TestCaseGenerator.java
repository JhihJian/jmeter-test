package com.example.jmeterai;

import java.util.ArrayList;
import java.util.List;

public class TestCaseGenerator {
  public List<TestCase> generate(OpenApiExtractor.OpenApiInfo info, String extra) {
    return generate(info, extra, null);
  }

  public List<TestCase> generate(OpenApiExtractor.OpenApiInfo info, String extra, ApiUnderstandingResult ar) {
    List<TestCase> out = new ArrayList<>();
    if (info.endpoints != null && !info.endpoints.isEmpty()) {
      com.fasterxml.jackson.databind.JsonNode root = info.root;
      com.fasterxml.jackson.databind.JsonNode paths = root == null ? null : root.path("paths");
      for (OpenApiExtractor.Endpoint ep : info.endpoints) {
        TestCase tc = new TestCase();
        tc.method = ep.method;
        tc.path = ep.path;
        tc.name = "用例: " + ep.method + " " + ep.path + " - 基本可用性";
        if (paths != null && paths.isObject()) {
          com.fasterxml.jackson.databind.JsonNode item = paths.path(ep.path);
          com.fasterxml.jackson.databind.JsonNode op = item.path(ep.method.toLowerCase());
          extractParams(op, tc);
          extractBody(op, tc);
        }
        if (("POST".equals(tc.method) || "PUT".equals(tc.method) || "PATCH".equals(tc.method)) && tc.body == null) {
          tc.body = "{}";
          tc.headers.put("Content-Type", "application/json");
        }
        if (ar != null && ar.authNeeded) tc.headers.put("Authorization", "Bearer ${token}");
        out.add(tc);
      }
    } else {
      out.add(caseOf("GET", "/api/api-docs", "用例: GET /api/api-docs - Swagger可用性"));
      out.add(caseOf("GET", "/", "用例: GET / - 基本连通性"));
      out.add(caseOf("GET", "/actuator/health", "用例: GET /actuator/health - 健康检查"));
    }
    if (ar != null && ar.tokenEndpoint != null) out.add(0, caseOf("POST", ar.tokenEndpoint, "用例: 获取令牌 - " + ar.tokenEndpoint));
    return out;
  }

  private TestCase caseOf(String method, String path, String name) {
    TestCase tc = new TestCase();
    tc.method = method;
    tc.path = path;
    tc.name = name;
    return tc;
  }

  public String describe(List<TestCase> cases, ApiUnderstandingResult ar) {
    StringBuilder sb = new StringBuilder();
    if (ar != null && ar.summaryText != null) {
      sb.append(ar.summaryText).append("\n");
    }
    sb.append("测试用例说明\n\n");
    for (TestCase tc : cases) {
      sb.append("- ").append(tc.name).append("\n");
      sb.append("  方法/路径: ").append(tc.method).append(" ").append(tc.path).append("\n");
      if (!tc.headers.isEmpty()) {
        sb.append("  请求头: ");
        java.util.List<String> hs = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String,String> e : tc.headers.entrySet()) hs.add(e.getKey()+": "+e.getValue());
        sb.append(String.join(", ", hs)).append("\n");
      }
      if (!tc.queryParams.isEmpty()) {
        sb.append("  查询参数: ");
        java.util.List<String> qp = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String,String> e : tc.queryParams.entrySet()) qp.add(e.getKey()+"="+e.getValue());
        sb.append(String.join(", ", qp)).append("\n");
      }
      if (!tc.pathParams.isEmpty()) {
        sb.append("  路径参数: ");
        java.util.List<String> pp = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String,String> e : tc.pathParams.entrySet()) pp.add(e.getKey()+"="+e.getValue());
        sb.append(String.join(", ", pp)).append("\n");
      }
      if (tc.body != null && !tc.body.isEmpty()) {
        sb.append("  请求体: ").append(tc.body).append("\n");
      }
      boolean auth = tc.headers.containsKey("Authorization");
      sb.append("  测试目标: 基本可用性（响应码2xx）");
      if (auth) sb.append("，鉴权可用性");
      sb.append("\n");
    }
    return sb.toString();
  }

  private void extractParams(com.fasterxml.jackson.databind.JsonNode op, TestCase tc) {
    if (op == null || op.isMissingNode()) return;
    com.fasterxml.jackson.databind.JsonNode params = op.path("parameters");
    if (params != null && params.isArray()) {
      for (com.fasterxml.jackson.databind.JsonNode pn : params) {
        String name = pn.path("name").asText("");
        String in = pn.path("in").asText("");
        String val = sampleValue(pn);
        if (!name.isEmpty()) {
          if ("query".equals(in)) tc.queryParams.put(name, val);
          if ("path".equals(in)) tc.pathParams.put(name, val);
        }
      }
    }
  }

  private void extractBody(com.fasterxml.jackson.databind.JsonNode op, TestCase tc) {
    com.fasterxml.jackson.databind.JsonNode rb = op.path("requestBody").path("content").path("application/json").path("schema");
    if (rb != null && rb.isObject()) {
      String s = buildJsonFromSchema(rb);
      if (s != null && !s.isEmpty()) {
        tc.body = s;
        tc.headers.put("Content-Type", "application/json");
      }
    }
  }

  private String sampleValue(com.fasterxml.jackson.databind.JsonNode param) {
    String ex = param.path("example").asText("");
    if (!ex.isEmpty()) return ex;
    String def = param.path("schema").path("default").asText("");
    if (!def.isEmpty()) return def;
    String type = param.path("schema").path("type").asText("");
    if ("integer".equals(type) || "number".equals(type)) return "1";
    if ("boolean".equals(type)) return "true";
    return "sample";
  }

  private String buildJsonFromSchema(com.fasterxml.jackson.databind.JsonNode schema) {
    java.util.Map<String,Object> obj = new java.util.LinkedHashMap<>();
    if ("object".equals(schema.path("type").asText("object"))) {
      com.fasterxml.jackson.databind.JsonNode props = schema.path("properties");
      if (props.isObject()) {
        java.util.Iterator<String> it = props.fieldNames();
        while (it.hasNext()) {
          String k = it.next();
          com.fasterxml.jackson.databind.JsonNode p = props.path(k);
          String ex = p.path("example").asText("");
          String def = p.path("default").asText("");
          String type = p.path("type").asText("");
          Object v;
          if (!ex.isEmpty()) v = ex;
          else if (!def.isEmpty()) v = def;
          else if ("integer".equals(type) || "number".equals(type)) v = 1;
          else if ("boolean".equals(type)) v = true;
          else v = "sample";
          obj.put(k, v);
        }
      }
    }
    try {
      com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
      return m.writeValueAsString(obj);
    } catch (Exception e) {
      return "{}";
    }
  }

  public List<TestCase> parseLlmCases(String content) {
    String s = ModelUtils.stripCodeFences(content);
    java.util.List<TestCase> out = new java.util.ArrayList<>();
    try {
      com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
      com.fasterxml.jackson.databind.JsonNode root = m.readTree(s);
      com.fasterxml.jackson.databind.JsonNode arr = root.isArray() ? root : root.path("cases");
      if (arr != null && arr.isArray()) {
        for (com.fasterxml.jackson.databind.JsonNode c : arr) {
          TestCase tc = new TestCase();
          tc.name = c.path("name").asText("");
          tc.method = c.path("method").asText("");
          tc.path = c.path("path").asText("");
          tc.body = c.path("body").asText("");
          com.fasterxml.jackson.databind.JsonNode headers = c.path("headers");
          if (headers.isObject()) {
            java.util.Iterator<String> it = headers.fieldNames();
            while (it.hasNext()) {
              String k = it.next();
              tc.headers.put(k, headers.path(k).asText(""));
            }
          }
          com.fasterxml.jackson.databind.JsonNode qps = c.path("queryParams");
          if (qps.isObject()) {
            java.util.Iterator<String> it = qps.fieldNames();
            while (it.hasNext()) {
              String k = it.next();
              tc.queryParams.put(k, qps.path(k).asText(""));
            }
          }
          com.fasterxml.jackson.databind.JsonNode pps = c.path("pathParams");
          if (pps.isObject()) {
            java.util.Iterator<String> it = pps.fieldNames();
            while (it.hasNext()) {
              String k = it.next();
              tc.pathParams.put(k, pps.path(k).asText(""));
            }
          }
          if (tc.name == null || tc.name.isEmpty()) tc.name = "用例: " + tc.method + " " + tc.path + " - 基本可用性";
          out.add(tc);
        }
      }
    } catch (Exception e) {
    }
    return out;
  }
}
