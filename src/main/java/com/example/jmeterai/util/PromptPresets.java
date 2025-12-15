package com.example.jmeterai.util;

import com.example.jmeterai.model.LabelStat;
import com.example.jmeterai.model.SummaryMetrics;

public class PromptPresets {
  public static String jmxSystemPrompt() {
    return """
你是资深性能测试工程师。基于提供的 OpenAPI/Swagger 信息，为 Apache JMeter 生成完整可执行的测试计划。
严格要求：
1) 输出必须为 JMX XML，符合 JMeter 5.6+，不要包含任何解释或额外文本；
2) 所有 HTTP 请求采样器的 `HTTPSampler.path` 不得为空；
3) 若文档中的 `servers[0].url` 含基础路径（例如 `/api`），采样器的 `path` 必须包含该基础路径；
4) `HTTP Request Defaults` 应设置 `protocol/domain/port` 来自 `servers[0].url`，但不要依赖默认 Path 为空的行为；
5) 每个采样器使用完整路径（包含基础路径 + 接口相对路径），以确保请求目标正确。
""";
  }
  public static String understandingSystemPrompt() {
    return """
你是资深接口分析师。基于提供的 OpenAPI/Swagger 内容，输出可用于生成测试用例的中文理解摘要。
包含：基础服务器、接口总数、每端点的参数摘要（query/path）、是否存在请求体、鉴权需求与令牌端点线索。
""";
  }
  public static String understandingUserPrompt(String programName, String extra, OpenApiExtractor.OpenApiInfo info) {
    String endpoints = info.endpointsPreview == null ? "" : info.endpointsPreview;
    String raw = info.rawPreview == null ? "" : info.rawPreview;
    String base = info.baseUrl == null ? "" : info.baseUrl;
    return """
程序名称: %s
测试额外要求: %s
基础服务器: %s

接口列表:
%s

Swagger原始内容(截断):
%s

请输出结构化的中文理解摘要，服务于后续测试用例生成。
""".formatted(programName, extra, base, endpoints, raw);
  }
  public static String casesSystemPrompt() {
    return """
你是资深测试工程师。基于提供的 OpenAPI/Swagger 理解结果与接口列表，输出测试用例清单的 JSON。
每个用例包含：name, method, path, headers(对象), queryParams(对象), pathParams(对象), body(字符串), goal(字符串)。
仅输出 JSON（可为数组或包含 cases 字段的对象）。
""";
  }
  public static String casesFromDocSystemPrompt() {
    return """
你是资深接口测试工程师。将提供的业务测试用例文档与 OpenAPI/Swagger 信息结合，生成接口测试用例清单（JSON）。
要求：每个用例包含 name, method, path, headers(对象), queryParams(对象), pathParams(对象), body(字符串), goal(字符串)。
仅输出 JSON（数组或包含 cases 字段的对象）。
""";
  }
  public static String casesFromDocUserPrompt(String programName, String extra, OpenApiExtractor.OpenApiInfo info, String caseDoc) {
    String endpoints = info.endpointsPreview == null ? "" : info.endpointsPreview;
    String raw = info.rawPreview == null ? "" : info.rawPreview;
    String base = info.baseUrl == null ? "" : info.baseUrl;
    return """
程序名称: %s
测试额外要求: %s
基础服务器: %s

接口列表:
%s

Swagger原始内容(截断):
%s

测试用例文档(原文):
%s

请基于上述文档与接口信息生成接口测试用例 JSON。
""".formatted(programName, extra, base, endpoints, raw, caseDoc);
  }
  public static String jmxFixSystemPrompt() {
    return """
你是资深性能测试工程师。根据提供的现有 JMX XML、失败摘要与用例说明，修复并输出完整的 JMX XML（JMeter 5.6+）。
严格要求：
1) 输出仅为 JMX XML；
2) `HTTPSampler.path` 不得为空，需包含基础路径；
3) 修复域名/协议/端口/头/参数/断言配置；
""";
  }
  public static String jmxFixUserPrompt(String programName, String extra, OpenApiExtractor.OpenApiInfo info, String currentJmx, String casesMd, SummaryMetrics m) {
    String base = info.baseUrl == null ? "" : info.baseUrl;
    return """
程序名称: %s
基础服务器: %s
测试额外要求: %s

当前 JMX:
%s

用例说明:
%s

失败摘要:
总请求: %d, 成功: %d, 失败: %d, 错误率: %s

请修复上述 JMX 并输出完整 XML。
""".formatted(
        programName,
        base,
        extra,
        currentJmx,
        casesMd,
        m.total,
        m.success,
        m.fail,
        String.format("%.2f%%", m.errorRate * 100)
    );
  }
  public static String casesUserPrompt(String programName, String extra, OpenApiExtractor.OpenApiInfo info) {
    String endpoints = info.endpointsPreview == null ? "" : info.endpointsPreview;
    String raw = info.rawPreview == null ? "" : info.rawPreview;
    String base = info.baseUrl == null ? "" : info.baseUrl;
    return """
程序名称: %s
测试额外要求: %s
基础服务器: %s

接口列表:
%s

Swagger原始内容(截断):
%s

规则：
- method 与 path 必须准确；path 包含基础路径（如 /api）与接口相对路径；
- headers 填写必要项（如 Content-Type、Authorization 示例）；
- queryParams/pathParams 使用示例值；body 为示例 JSON（如适用）；
- goal 清晰说明验证目标（如 基本可用性 2xx、鉴权可用性）。
""".formatted(programName, extra, base, endpoints, raw);
  }
  public static String jmxUserPrompt(String programName, String extra, OpenApiExtractor.OpenApiInfo info) {
    String endpoints = info.endpointsPreview == null ? "" : info.endpointsPreview;
    String raw = info.rawPreview == null ? "" : info.rawPreview;
    String base = info.baseUrl == null ? "" : info.baseUrl;
    String prompt = """
程序名称: %s
测试额外要求: %s
基础服务器: %s

接口列表:
%s

Swagger原始内容(截断):
%s

请生成 JMeter JMX 测试计划，满足以下约束：
- 线程组与合理并发配置；
- 基于接口列表的 HTTP 采样器（含方法、完整路径、必要参数与头）；
- `HTTP Request Defaults` 的 `protocol/domain/port` 来源于 `servers[0].url`；
- 所有采样器的 `HTTPSampler.path` 必须为完整路径，包含 `servers[0].url` 中的基础路径（如 `/api`）与接口相对路径，绝不可留空；
- 若 OpenAPI 的 `paths` 仅为相对路径（不含 `/api`），请在采样器 `path` 前补全基础路径；
- 配置必要的断言与响应校验（至少添加 Response Assertion，校验响应码在 2xx）；
- 输出仅为 JMX XML 内容。
""".formatted(programName, extra, base, endpoints, raw);
    return prompt;
  }
  public static String analysisPrompt(String programName, String extra, SummaryMetrics m) {
    String header = """
程序名称: %s
测试额外要求: %s
汇总指标:
总请求: %d
成功: %d
失败: %d
错误率: %s
平均耗时(ms): %s
P95(ms): %s
P99(ms): %s
最小/最大(ms): %s

失败用例分布:
""".formatted(
        programName,
        extra,
        m.total,
        m.success,
        m.fail,
        String.format("%.2f%%", m.errorRate * 100),
        String.format("%.2f", m.avg),
        String.format("%.2f", m.p95),
        String.format("%.2f", m.p99),
        String.format("%.2f/%.2f", m.min, m.max)
    );
    StringBuilder sb = new StringBuilder(header);
    for (java.util.Map.Entry<String, LabelStat> e : m.byLabel.entrySet()) {
      LabelStat ls = e.getValue();
      if (ls.fail > 0) {
        sb.append("- ").append(ls.label).append(" 失败/总计: ").append(ls.fail).append("/").append(ls.total).append(", 响应码:");
        java.util.List<String> codes = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, Long> c : ls.codes.entrySet()) codes.add(c.getKey()+":"+c.getValue());
        sb.append(String.join(", ", codes)).append("\n");
      }
    }
    sb.append("\n成功验证项:\n");
    for (java.util.Map.Entry<String, LabelStat> e : m.byLabel.entrySet()) {
      LabelStat ls = e.getValue();
      if (ls.success > 0) {
        sb.append("- ").append(ls.label).append(" 已验证接口可用性（成功 ").append(ls.success).append(" 次）");
        java.util.List<String> codes = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, Long> c : ls.codes.entrySet()) codes.add(c.getKey()+":"+c.getValue());
        if (!codes.isEmpty()) sb.append(", 响应码分布: ").append(String.join(", ", codes));
        sb.append("\n");
      }
    }
    sb.append("""

请基于以上失败与成功结果，结合测试用例文档中的请求参数与测试目标，进行中文分析：
1) 解释失败原因可能性与改进建议；
2) 总结成功结果证明了哪些接口功能或鉴权路径是可用的；
3) 给出后续重试与优化策略。
"""
    );
    return sb.toString();
  }
}
