package com.example.jmeterai;

import java.util.List;

public class SimpleJmxBuilder {
  public String build(OpenApiExtractor.OpenApiInfo info, String extra) {
    return build(info, extra, new TestCaseGenerator().generate(info, extra));
  }

  public String build(OpenApiExtractor.OpenApiInfo info, String extra, List<TestCase> cases) {
    String base = info.baseUrl == null ? "" : info.baseUrl;
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<jmeterTestPlan version=\"1.2\" properties=\"5.0\" jmeter=\"5.6.3\">\n");
    xml.append("  <hashTree>\n");
    xml.append("    <TestPlan guiclass=\"TestPlanGui\" testclass=\"TestPlan\" testname=\"Auto Test Plan\" enabled=\"true\">\n");
    xml.append("      <stringProp name=\"TestPlan.comments\"></stringProp>\n");
    xml.append("      <boolProp name=\"TestPlan.functional_mode\">false</boolProp>\n");
    xml.append("      <boolProp name=\"TestPlan.tearDown_on_shutdown\">true</boolProp>\n");
    xml.append("      <boolProp name=\"TestPlan.serialize_threadgroups\">false</boolProp>\n");
    xml.append("      <elementProp name=\"TestPlan.user_defined_variables\" elementType=\"Arguments\" guiclass=\"ArgumentsPanel\" testclass=\"Arguments\" testname=\"User Defined Variables\" enabled=\"true\">\n");
    xml.append("        <collectionProp name=\"Arguments.arguments\"/>\n");
    xml.append("      </elementProp>\n");
    xml.append("      <stringProp name=\"TestPlan.user_define_classpath\"></stringProp>\n");
    xml.append("    </TestPlan>\n");
    xml.append("    <hashTree>\n");
    String protocol = ""; String domain = ""; String port = ""; String basePath = "";
    try {
      java.net.URI u = java.net.URI.create(base);
      protocol = u.getScheme();
      domain = u.getHost();
      int pt = u.getPort();
      port = pt > 0 ? String.valueOf(pt) : "";
      basePath = u.getPath() == null ? "" : u.getPath();
      if (protocol == null || domain == null) {
        throw new RuntimeException("基础URL无效，无法解析协议或域名: " + base);
      }
    } catch (Exception e) {
      throw new RuntimeException("基础URL解析失败: " + e.getMessage(), e);
    }
    xml.append("      <ConfigTestElement guiclass=\"HttpDefaultsGui\" testclass=\"ConfigTestElement\" testname=\"HTTP Request Defaults\" enabled=\"true\">\n");
    xml.append("        <stringProp name=\"HTTPSampler.domain\">").append(domain).append("</stringProp>\n");
    xml.append("        <stringProp name=\"HTTPSampler.port\">").append(port).append("</stringProp>\n");
    xml.append("        <stringProp name=\"HTTPSampler.protocol\">").append(protocol).append("</stringProp>\n");
    xml.append("        <stringProp name=\"HTTPSampler.path\"></stringProp>\n");
    xml.append("        <stringProp name=\"HTTPSampler.contentEncoding\"></stringProp>\n");
    xml.append("        <stringProp name=\"HTTPSampler.concurrentPool\">6</stringProp>\n");
    xml.append("      </ConfigTestElement>\n");
    xml.append("      <hashTree/>\n");
    java.util.List<java.util.AbstractMap.SimpleEntry<String,String>> globalHeaders = parseHeaders(extra);
    if (!globalHeaders.isEmpty()) {
      xml.append("      <HeaderManager guiclass=\"HeaderPanel\" testclass=\"HeaderManager\" testname=\"HTTP Header Manager\" enabled=\"true\">\n");
      xml.append("        <collectionProp name=\"HeaderManager.headers\">\n");
      for (java.util.AbstractMap.SimpleEntry<String,String> h : globalHeaders) {
        xml.append("          <elementProp name=\"\" elementType=\"Header\">\n");
        xml.append("            <stringProp name=\"Header.name\">").append(xml(h.getKey())).append("</stringProp>\n");
        xml.append("            <stringProp name=\"Header.value\">").append(xml(h.getValue())).append("</stringProp>\n");
        xml.append("          </elementProp>\n");
      }
      xml.append("        </collectionProp>\n");
      xml.append("      </HeaderManager>\n");
      xml.append("      <hashTree/>\n");
    }
    xml.append("      <ThreadGroup guiclass=\"ThreadGroupGui\" testclass=\"ThreadGroup\" testname=\"Thread Group\" enabled=\"true\">\n");
    xml.append("        <stringProp name=\"ThreadGroup.on_sample_error\">continue</stringProp>\n");
    xml.append("        <elementProp name=\"ThreadGroup.main_controller\" elementType=\"LoopController\" guiclass=\"LoopControlPanel\" testclass=\"LoopController\" testname=\"Loop Controller\" enabled=\"true\">\n");
    xml.append("          <boolProp name=\"LoopController.continue_forever\">false</boolProp>\n");
    xml.append("          <stringProp name=\"LoopController.loops\">1</stringProp>\n");
    xml.append("        </elementProp>\n");
    xml.append("        <stringProp name=\"ThreadGroup.num_threads\">1</stringProp>\n");
    xml.append("        <stringProp name=\"ThreadGroup.ramp_time\">1</stringProp>\n");
    xml.append("        <boolProp name=\"ThreadGroup.scheduler\">false</boolProp>\n");
    xml.append("        <stringProp name=\"ThreadGroup.duration\"></stringProp>\n");
    xml.append("        <stringProp name=\"ThreadGroup.delay\"></stringProp>\n");
    xml.append("      </ThreadGroup>\n");
    xml.append("      <hashTree>\n");
    if (cases != null) {
      for (TestCase tc : cases) {
        String method = tc.method;
        String path = joinPaths(basePath, tc.path);
        xml.append("        <HTTPSamplerProxy guiclass=\"HttpTestSampleGui\" testclass=\"HTTPSamplerProxy\" testname=\"")
           .append(xml(tc.name))
           .append("\" enabled=\"true\">\n");
        xml.append("          <elementProp name=\"HTTPsampler.Arguments\" elementType=\"Arguments\" guiclass=\"HTTPArgumentsPanel\" testclass=\"Arguments\" testname=\"User Defined Variables\" enabled=\"true\">\n");
        xml.append("            <collectionProp name=\"Arguments.arguments\">\n");
        boolean bodyMethod = method.equals("POST") || method.equals("PUT") || method.equals("PATCH");
        if (bodyMethod && tc.body != null && !tc.body.isEmpty()) {
          xml.append("              <elementProp name=\"\" elementType=\"HTTPArgument\">\n");
          xml.append("                <boolProp name=\"HTTPArgument.always_encode\">false</boolProp>\n");
          xml.append("                <stringProp name=\"Argument.value\">").append(xml(tc.body)).append("</stringProp>\n");
          xml.append("                <stringProp name=\"Argument.metadata\">=</stringProp>\n");
          xml.append("                <boolProp name=\"HTTPArgument.use_equals\">true</boolProp>\n");
          xml.append("                <stringProp name=\"Argument.name\"></stringProp>\n");
          xml.append("              </elementProp>\n");
        }
        if (!bodyMethod && tc.queryParams != null && !tc.queryParams.isEmpty()) {
          for (java.util.Map.Entry<String,String> q : tc.queryParams.entrySet()) {
            xml.append("              <elementProp name=\"\" elementType=\"HTTPArgument\">\n");
            xml.append("                <boolProp name=\"HTTPArgument.always_encode\">false</boolProp>\n");
            xml.append("                <stringProp name=\"Argument.value\">").append(xml(q.getValue())).append("</stringProp>\n");
            xml.append("                <stringProp name=\"Argument.metadata\">=</stringProp>\n");
            xml.append("                <boolProp name=\"HTTPArgument.use_equals\">true</boolProp>\n");
            xml.append("                <stringProp name=\"Argument.name\">").append(xml(q.getKey())).append("</stringProp>\n");
            xml.append("              </elementProp>\n");
          }
        }
        xml.append("            </collectionProp>\n");
        xml.append("          </elementProp>\n");
        xml.append("          <boolProp name=\"HTTPSampler.postBodyRaw\">").append(bodyMethod && tc.body != null && !tc.body.isEmpty() ? "true" : "false").append("</boolProp>\n");
        xml.append("          <stringProp name=\"HTTPSampler.domain\"></stringProp>\n");
        xml.append("          <stringProp name=\"HTTPSampler.port\"></stringProp>\n");
        xml.append("          <stringProp name=\"HTTPSampler.protocol\"></stringProp>\n");
        String replacedPath = applyPathParams(path, tc.pathParams);
        xml.append("          <stringProp name=\"HTTPSampler.path\">").append(xml(replacedPath)).append("</stringProp>\n");
        xml.append("          <stringProp name=\"HTTPSampler.method\">").append(method).append("</stringProp>\n");
        xml.append("          <boolProp name=\"HTTPSampler.follow_redirects\">true</boolProp>\n");
        xml.append("          <boolProp name=\"HTTPSampler.auto_redirects\">false</boolProp>\n");
        xml.append("          <boolProp name=\"HTTPSampler.use_keepalive\">true</boolProp>\n");
        xml.append("          <boolProp name=\"HTTPSampler.DO_MULTIPART_POST\">false</boolProp>\n");
        xml.append("          <stringProp name=\"HTTPSampler.embedded_url_re\"></stringProp>\n");
        xml.append("          <stringProp name=\"HTTPSampler.connect_timeout\">10000</stringProp>\n");
        xml.append("          <stringProp name=\"HTTPSampler.response_timeout\">30000</stringProp>\n");
        xml.append("        </HTTPSamplerProxy>\n");
        xml.append("        <hashTree>\n");
        if (tc.headers != null && !tc.headers.isEmpty()) {
          xml.append("          <HeaderManager guiclass=\"HeaderPanel\" testclass=\"HeaderManager\" testname=\"HTTP Header Manager\" enabled=\"true\">\n");
          xml.append("            <collectionProp name=\"HeaderManager.headers\">\n");
          for (java.util.Map.Entry<String,String> h : tc.headers.entrySet()) {
            xml.append("              <elementProp name=\"\" elementType=\"Header\">\n");
            xml.append("                <stringProp name=\"Header.name\">").append(xml(h.getKey())).append("</stringProp>\n");
            xml.append("                <stringProp name=\"Header.value\">").append(xml(h.getValue())).append("</stringProp>\n");
            xml.append("              </elementProp>\n");
          }
          xml.append("            </collectionProp>\n");
          xml.append("          </HeaderManager>\n");
          xml.append("          <hashTree/>\n");
        }
        xml.append("        </hashTree>\n");
      }
    }
    xml.append("      </hashTree>\n");
    xml.append("    </hashTree>\n");
    xml.append("  </hashTree>\n");
    xml.append("</jmeterTestPlan>\n");
    return xml.toString();
  }

  private String joinPaths(String basePath, String p) {
    String a = basePath == null ? "" : basePath.trim();
    String b = p == null ? "" : p.trim();
    if (a.isEmpty() && b.isEmpty()) return "/";
    if (b.isEmpty()) return a.isEmpty() ? "/" : a;
    if (a.isEmpty()) return b;
    boolean aEnds = a.endsWith("/");
    boolean bStarts = b.startsWith("/");
    if (aEnds && bStarts) return a + b.substring(1);
    if (!aEnds && !bStarts) return a + "/" + b;
    return a + b;
  }

  private String applyPathParams(String p, java.util.Map<String,String> vars) {
    if (p == null || p.isEmpty() || vars == null || vars.isEmpty()) return p;
    String out = p;
    for (java.util.Map.Entry<String,String> e : vars.entrySet()) {
      String key = e.getKey();
      String val = e.getValue();
      out = out.replace("{"+key+"}", val);
    }
    return out;
  }

  private java.util.List<java.util.AbstractMap.SimpleEntry<String,String>> parseHeaders(String extra) {
    java.util.List<java.util.AbstractMap.SimpleEntry<String,String>> list = new java.util.ArrayList<>();
    if (extra == null) return list;
    String[] parts = extra.split("[\\n;]+");
    for (String p : parts) {
      String s = p.trim();
      if (s.isEmpty()) continue;
      // 仅解析以 "header:" 前缀的键值对，例如：header: Authorization=Bearer xxx
      String lower = s.toLowerCase();
      if (!lower.startsWith("header:")) continue;
      String kv = s.substring("header:".length()).trim();
      int idx = kv.indexOf('=');
      if (idx > 0) {
        String k = kv.substring(0, idx).trim();
        String v = kv.substring(idx + 1).trim();
        if (!k.isEmpty() && !v.isEmpty()) {
          list.add(new java.util.AbstractMap.SimpleEntry<>(k, v));
        }
      }
    }
    return list;
  }

  private String xml(String s) {
    if (s == null) return "";
    return s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }
}
