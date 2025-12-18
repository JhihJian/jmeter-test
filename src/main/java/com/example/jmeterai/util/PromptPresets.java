package com.example.jmeterai.util;

import com.example.jmeterai.model.ExecutionResult;
import com.example.jmeterai.model.LabelStat;
import com.example.jmeterai.model.QualityScenario;
import com.example.jmeterai.model.SummaryMetrics;
import com.example.jmeterai.model.TestCase;

public class PromptPresets {
    public static String singleInterfaceSystemPrompt() {
        return """
你是资深测试工程师。基于提供的单个接口 OpenAPI 定义，针对指定的“质量场景”生成测试用例 JSON。
注意，如果接口参数中包含phone入参，你应该生成11位随机的手机号进行测试
每个用例包含：name, method, path, headers(对象), queryParams(对象), pathParams(对象), body(字符串), goal(字符串)。
仅输出 JSON（数组或包含 cases 字段的对象）。
""";
    }

    public static String singleInterfaceUserPrompt(String programName, String method, String path, String endpointJson, QualityScenario scenario) {
        return """
程序名称: %s
目标接口: %s %s
测试场景: %s (%s)

接口定义 (JSON):
%s

请生成针对该场景的测试用例。
如果场景是 PARAM_INTEGRITY (参数完整性)，请生成缺少必填参数、参数为空、参数类型错误等用例。
如果场景是 ABNORMAL_INPUT (异常输入)，请生成超长字符串、特殊字符、SQL注入尝试、边界值等用例。
如果场景是 HAPPY_PATH (基本功能)，请生成正常调用的用例。
""".formatted(programName, method, path, scenario.name(), scenario.getDescription(), endpointJson);
    }

    public static String assertionGenerationSystemPrompt() {
        return """
你是测试断言生成专家。根据请求和实际响应结果，生成一组自动化断言。
断言类型(type)支持: "statusCode", "bodyContains", "jsonPath", "responseTime".
操作符(operator)支持: "equals", "contains", "notContains", "greaterThan", "lessThan".
输出 JSON 数组，每个元素包含: type, expression(仅jsonPath需要), operator, expected, successMessage, failureMessage.

重要规则：
1. 即使 HTTP 状态码为 200，也必须检查响应体中的业务状态码（如 code, status, errCode 等）是否表示成功。
2. 如果业务状态码表示错误（例如 code != 0 或 code != 200），必须生成断言来捕获该错误（预期失败用例除外）。
3. 对于预期成功的用例，如果返回了业务错误信息（如 msg, message, error 等），应生成断言确保这些字段不包含错误关键词，或直接校验业务状态码。
4. **必须输出有效的纯 JSON 格式**，不要包含 Markdown 代码块标记（如 ```json ... ```）。

示例:
[
  {
    "type": "statusCode",
    "operator": "equals",
    "expected": "200",
    "successMessage": "请求成功",
    "failureMessage": "状态码非200"
  },
  {
    "type": "jsonPath",
    "expression": "$.code",
    "operator": "equals",
    "expected": "200",
    "successMessage": "业务码正常",
    "failureMessage": "业务码错误"
  },
  {
    "type": "bodyContains",
    "operator": "notContains",
    "expected": "手机号不能为空",
    "successMessage": "未包含错误信息",
    "failureMessage": "响应包含错误信息"
  }
]
""";
    }

    public static String assertionGenerationUserPrompt(TestCase tc, ExecutionResult result, String endpointJson) {
        return """
接口定义:
%s

测试用例:
名称: %s
目标: %s
Method: %s
URL: %s
Request Body: %s

实际执行结果:
Status Code: %d
Response Body: %s

请根据实际响应生成合理的断言列表。
1. 如果响应包含动态字段（如时间戳、ID），请不要对具体值做严格相等断言，而是检查格式或存在性（如 jsonPath 检查非空）。
2. 如果响应是错误（4xx/5xx），且用例预期是失败，请生成断言检查错误码或错误信息。
3. **特别注意**：很多系统在业务异常时仍返回 HTTP 200，但会在 Body 中包含错误码（如 "code": "6002"）或错误信息（如 "msg": "..."）。
   - 如果用例预期成功，请务必增加对 Body 中业务成功标识（如 code=200/0/success）的断言，防止假阳性通过。
   - 如果用例预期失败（负面测试），请增加对 Body 中特定错误码或错误信息的断言。
""".formatted(endpointJson, tc.name, tc.goal, tc.method, result.url, tc.body, result.statusCode, result.responseBody);
    }

  public static String verificationSystemPrompt() {
    return """
你是智能测试验证专家。基于接口定义、测试用例和实际执行结果（状态码、响应体），判断测试是否通过。
对于负面测试（如异常输入），如果接口返回 4xx 并包含错误提示，应视为“通过”。
对于正面测试，如果接口返回 2xx 且数据符合预期，视为“通过”。
仅输出 JSON，格式：{"passed": true/false, "reason": "分析原因..."}
""";
  }

  public static String verificationUserPrompt(TestCase tc, ExecutionResult result, String endpointJson) {
    return """
接口定义:
%s

测试用例:
名称: %s
目标: %s
Method: %s
URL: %s
Request Body: %s

执行结果:
Status Code: %d
Response Body: %s
Error Message: %s

请判断该测试是否符合预期（passed）。注意：
1. 如果是用例是预期失败（如 goal 包含 '失败'、'异常'、'校验'），且服务器返回了 400/401/403/422 等错误码，或者返回 200 但 Body 中包含明确的业务错误码/信息，应判定为 passed=true。
2. 如果是用例是预期成功，但返回 4xx/5xx，判定为 passed=false。
3. 如果返回 200，必须检查 Body 内容。如果 Body 包含业务错误码（如 code!="200"且code!="0"）或错误信息（如 "msg":"xxx不能为空"），判定为 passed=false。
""".formatted(endpointJson, tc.name, tc.goal, tc.method, result.url, tc.body, result.statusCode, result.responseBody, result.errorMessage);
  }

  public static String caseDecisionSystemPrompt() {
    return """
你是接口测试裁判与断言生成器。基于接口定义、测试用例与实际响应，先判断响应是否符合该用例设计；若符合，直接生成断言；若不符合，则在“调整用例”与“标记接口异常”两者中选择更合理的一项并输出结构化决定。
仅输出一个 JSON 对象（不包含代码块）。
字段：
- conforms: true/false
- reason: string
- action: "none" | "adjust_case" | "mark_abnormal"
- assertions: 当 conforms=true 且 action="none" 时的断言数组，每项包含 type, expression(可选), operator, expected, successMessage, failureMessage
- adjustedCase: 当 action="adjust_case" 时提供单个用例对象，包含 name, method, path, headers(对象), queryParams(对象), pathParams(对象), body(字符串), goal(字符串)
- abnormalDescription: 当 action="mark_abnormal" 时给出中文描述，说明不符合预期的原因

断言生成规则与前述一致：即使 HTTP 200 也需校验业务码/成功标识；负面用例要针对错误码/错误信息生成断言；避免对动态字段做严格相等。
""";
  }

  public static String caseDecisionUserPrompt(TestCase tc, ExecutionResult result, String endpointJson) {
    return """
接口定义:
%s

测试用例:
名称: %s
目标: %s
Method: %s
Path: %s
Headers: %s
QueryParams: %s
PathParams: %s
Request Body: %s

实际执行结果:
Status Code: %d
Response Body: %s
耗时(ms): %d

请先判断该响应是否符合该用例的设计目标（conforms）。如果符合，直接输出断言数组（assertions），并将 action 设为 "none"。
如果不符合，请在两种策略中选择其一：
1) 调整用例（adjust_case）：给出 adjustedCase（完整用例对象）与 reason，说明如何调整以更符合接口实际行为。
2) 标记接口异常（mark_abnormal）：给出 abnormalDescription（中文原因），说明该接口可能返回异常或文档/实现不一致。

输出为上述结构的 JSON 对象。
""".formatted(
      endpointJson,
      tc.name,
      tc.goal,
      tc.method,
      tc.path,
      tc.headers == null ? "{}" : new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(tc.headers).toString(),
      tc.queryParams == null ? "{}" : new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(tc.queryParams).toString(),
      tc.pathParams == null ? "{}" : new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(tc.pathParams).toString(),
      tc.body == null ? "" : tc.body,
      result.statusCode,
      result.responseBody == null ? "" : result.responseBody,
      result.durationMs
    );
  }

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
