package com.example.jmeterai.util;

import com.example.jmeterai.model.ExecutionResult;
import com.example.jmeterai.model.LabelStat;
import com.example.jmeterai.model.QualityScenario;
import com.example.jmeterai.model.SummaryMetrics;
import com.example.jmeterai.model.TestCase;

public class PromptPresets {
    public static String singleInterfaceSystemPrompt() {
        return """
# Role
你是一位拥有10年经验的资深测试工程师，专注于API自动化测试。你擅长根据OpenAPI定义（Swagger）和特定的质量保障场景（Quality Scenario）设计高覆盖率、健壮的测试用例。

# Task
请阅读我提供的【OpenAPI定义】和指定的【质量场景】，生成一组符合该场景的测试用例 JSON。

# Constraints & Rules
1. **数据完整性**：必须严格遵循 OpenAPI 定义的字段类型和约束。
2. **特殊字段处理（重点）**：
   - 检查所有请求参数（Query/Body/Path）。如果参数名称包含 `phone`, `mobile`, `tel` 或语义上表示手机号的字段，**必须**生成一个随机的、符合格式的 11 位数字作为值。
   - **绝对禁止**跳过手机号字段，也不能将其设为 null 或空（除非测试场景明确要求测试空值）。
   - **必须执行**必须按照接口文档要求设置接口参数的名称！！！
3. **场景对齐**：生成的用例必须直接服务于指定的“质量场景”（例如：如果场景是“参数校验”，则应生成字段超长、类型错误、必填项缺失等用例）。
4. **数据依赖与跳过策略（重要）**：
   - 如果当前场景是 `HAPPY_PATH`（正常功能），请检查接口是否依赖预先存在的特定数据（例如：编辑/删除接口通常需要一个已存在的 ID，或者路径参数包含 `/{id}`）。
   - 如果接口需要特定 ID 才能成功，且你无法确定一个必定存在的有效值：
     - **必须跳过**生成该 `HAPPY_PATH` 用例。
     - 返回空的 `cases` 数组 `[]`。
   - 注意：对于 `ABNORMAL_INPUT` 或 `PARAM_INTEGRITY` 场景，通常不需要有效 ID（例如测试 ID 格式错误或超长），因此**不应跳过**这些场景。
5. **Body 格式**：`body` 字段必须是**字符串格式**（如果是 JSON Body，请将其序列化为字符串）。

# Output Format
仅输出纯 JSON 内容，不要包含 Markdown 代码块标记（如 ```json），也不要包含任何解释性文字。
JSON 结构必须严格符合以下 Schema：

{
  "cases": [
    {
      "name": "用例名称（简述测试目的）",
      "method": "GET/POST/PUT/DELETE等",
      "path": "请求路径（如 /api/v1/user）",
      "headers": { "Key": "Value" },
      "queryParams": { "Key": "Value" },
      "pathParams": { "Key": "Value" },
      "body": "请求体内容的字符串形式（如 '{\"name\":\"test\"}'）",
      "goal": "该用例的预期结果或断言目标"
    }
  ]
}
""";
    }
    public static String singleInterfaceUserPrompt(String programName, String method, String path, String endpointJson, QualityScenario scenario, String markdownSpec) {
        String spec = markdownSpec == null ? "" : truncateMarkdown(markdownSpec, 4000);
        String specSection = spec.isEmpty() ? "" : """

接口规范文档(节选):
%s
""".formatted(spec);
        return """
# Context Data
## Program Info
Name: %s
Target Interface: %s %s

## OpenAPI Definition
%s%s

## Quality Scenario
%s (%s)

请生成针对该场景的测试用例。
如果场景是 PARAM_INTEGRITY (参数完整性)，请生成缺少必填参数、参数为空、参数类型错误等用例。
如果场景是 ABNORMAL_INPUT (异常输入)，请生成超长字符串、特殊字符、SQL注入尝试、边界值等用例。
如果场景是 HAPPY_PATH (基本功能)，请生成正常调用的用例。
""".formatted(programName, method, path, endpointJson, specSection, scenario.name(), scenario.getDescription());
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

  public static String understandingSystemPrompt() {
    return """
你是资深接口分析师。基于提供的 OpenAPI/Swagger 内容，输出可用于生成测试用例的中文理解摘要。
包含：基础服务器、接口总数、每端点的参数摘要（query/path）、是否存在请求体、鉴权需求与令牌端点线索。
""";
  }


  public static String understandingUserPrompt(String programName, String extra, OpenApiExtractor.OpenApiInfo info, String markdownSpec) {
    String endpoints = info.endpointsPreview == null ? "" : info.endpointsPreview;
    String raw = info.rawPreview == null ? "" : info.rawPreview;
    String base = info.baseUrl == null ? "" : info.baseUrl;
    String spec = markdownSpec == null ? "" : truncateMarkdown(markdownSpec, 8000);
    String specSection = spec.isEmpty() ? "" : """

接口规范文档(截断):
%s
""".formatted(spec);
    return """
程序名称: %s
测试额外要求: %s
基础服务器: %s

接口列表:
%s

Swagger原始内容(截断):
%s%s

请输出结构化的中文理解摘要，服务于后续测试用例生成。
""".formatted(programName, extra, base, endpoints, raw, specSection);
  }

  private static String truncateMarkdown(String s, int max) {
    if (s == null) return "";
    if (s.length() <= max) return s;
    return s.substring(0, max);
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
