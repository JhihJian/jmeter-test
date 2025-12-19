package com.example.jmeterai.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.*;

public class OpenApiExtractor {
  public static class Endpoint {
    public final String method;
    public final String path;
    public final List<String> tags;
    public Endpoint(String method, String path, List<String> tags) { 
        this.method = method; 
        this.path = path; 
        this.tags = tags;
    }
  }
  public static class OpenApiInfo {
    public final String rawPreview;
    public final String endpointsPreview;
    public final String baseUrl;
    public final List<Endpoint> endpoints;
    public final JsonNode root;
    public OpenApiInfo(String rawPreview, String endpointsPreview, String baseUrl, List<Endpoint> endpoints, JsonNode root) {
      this.rawPreview = rawPreview;
      this.endpointsPreview = endpointsPreview;
      this.baseUrl = baseUrl;
      this.endpoints = endpoints;
      this.root = root;
    }
  }
  private final OkHttpClient http = new OkHttpClient();
  private final ObjectMapper mapper = new ObjectMapper();

  public OpenApiInfo load(String url) throws Exception {
    Request req = new Request.Builder().url(url).get().build();
    try (Response resp = http.newCall(req).execute()) {
      if (resp.body() == null) throw new RuntimeException("接口文档下载失败: 无响应体");
      String raw = resp.body() == null ? "" : resp.body().string();
      String rawPreview = truncate(raw, 15000);
      if (!resp.isSuccessful()) {
        throw new RuntimeException("接口文档下载失败: HTTP " + resp.code());
      }
      ParseResult pr = parse(raw, url);
      if (pr.root == null || !pr.root.isObject()) throw new RuntimeException("接口文档不是JSON或无法解析");
      return new OpenApiInfo(rawPreview, pr.preview, pr.baseUrl, pr.endpoints, pr.root);
    }
  }

  private static class ParseResult {
    String preview;
    String baseUrl;
    List<Endpoint> endpoints;
    JsonNode root;
  }

  private ParseResult parse(String raw, String swaggerUrl) {
    ParseResult res = new ParseResult();
    res.preview = "";
    res.baseUrl = deriveBase(swaggerUrl);
    res.endpoints = new ArrayList<>();
    try {
      JsonNode root = mapper.readTree(raw);
      res.root = root;
      // servers[0].url
      JsonNode servers = root.path("servers");
      if (servers.isArray() && servers.size() > 0) {
        String s0 = servers.get(0).path("url").asText("");
        if (!s0.isEmpty()) res.baseUrl = s0;
      }
      JsonNode paths = root.path("paths");
      if (paths.isObject()) {
        StringBuilder sb = new StringBuilder();
        paths.fieldNames().forEachRemaining(p -> {
          JsonNode item = paths.path(p);
          item.fieldNames().forEachRemaining(m -> {
            JsonNode op = item.path(m);
            String summary = op.path("summary").asText("");
            
            List<String> tags = new ArrayList<>();
            JsonNode tagsNode = op.path("tags");
            if (tagsNode.isArray()) {
                tagsNode.forEach(t -> tags.add(t.asText()));
            }

            sb.append(m.toUpperCase()).append(" ").append(p);
            if (!summary.isEmpty()) sb.append(" - ").append(summary);
            sb.append("\n");
            res.endpoints.add(new Endpoint(m.toUpperCase(), p, tags));
          });
        });
        res.preview = sb.toString();
      }
    } catch (Exception e) {
      throw new RuntimeException("接口文档解析失败: " + e.getMessage(), e);
    }
    return res;
  }

  private String deriveBase(String swaggerUrl) {
    URI u = URI.create(swaggerUrl);
    String scheme = u.getScheme();
    String host = u.getHost();
    int port = u.getPort();
    if (scheme == null || host == null) throw new RuntimeException("无法从文档地址推断基础URL: " + swaggerUrl);
    StringBuilder sb = new StringBuilder();
    sb.append(scheme).append("://").append(host);
    if (port > 0) sb.append(":").append(port);
    return sb.toString();
  }

  public String getEndpointJson(JsonNode root, String method, String path) {
      if (root == null) return "";
      try {
          JsonNode pathNode = root.path("paths").path(path);
          if (pathNode.isMissingNode()) return "";
          JsonNode methodNode = pathNode.path(method.toLowerCase());
          if (methodNode.isMissingNode()) return "";
          
          // Collect refs
          Set<String> refKeys = new HashSet<>();
          collectRefs(methodNode, refKeys);
          
          // Resolve refs from components/schemas (OpenAPI 3) or definitions (Swagger 2)
          Map<String, JsonNode> definitions = new HashMap<>();
          Queue<String> toProcess = new LinkedList<>(refKeys);
          Set<String> processed = new HashSet<>();
          
          while(!toProcess.isEmpty()) {
              String ref = toProcess.poll();
              if (processed.contains(ref)) continue;
              processed.add(ref);
              
              JsonNode schemaNode = null;
              String schemaName = null;
              
              if (ref.startsWith("#/components/schemas/")) {
                  schemaName = ref.substring("#/components/schemas/".length());
                  schemaNode = root.path("components").path("schemas").path(schemaName);
              } else if (ref.startsWith("#/definitions/")) {
                  schemaName = ref.substring("#/definitions/".length());
                  schemaNode = root.path("definitions").path(schemaName);
              }
              
              if (schemaNode != null && !schemaNode.isMissingNode()) {
                  definitions.put(schemaName, schemaNode);
                  // Find refs inside this schema
                  Set<String> newRefs = new HashSet<>();
                  collectRefs(schemaNode, newRefs);
                  toProcess.addAll(newRefs);
              }
          }
          
          // Construct result
          ObjectNode result = mapper.createObjectNode();
          result.set("operation", methodNode);
          if (!definitions.isEmpty()) {
              ObjectNode defsNode = mapper.createObjectNode();
              definitions.forEach(defsNode::set);
              result.set("components_schemas", defsNode);
          }
          
          return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
      } catch (Exception e) {
          return "";
      }
  }

  private void collectRefs(JsonNode node, Set<String> refs) {
    if (node.isObject()) {
      if (node.has("$ref")) {
        refs.add(node.get("$ref").asText());
      }
      node.forEach(child -> collectRefs(child, refs));
    } else if (node.isArray()) {
      node.forEach(child -> collectRefs(child, refs));
    }
  }

  private String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() <= max ? s : s.substring(0, max);
  }
}
