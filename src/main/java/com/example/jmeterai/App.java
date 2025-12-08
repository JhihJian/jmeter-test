package com.example.jmeterai;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class App {
  public static void main(String[] args) {
    try {
      if (args.length < 2) {
        Logger.get(App.class).info("用法: java -jar jmeter-ai.jar <swagger_api_doc_url> <program_name> [extra_requirements]");
        System.exit(1);
      }
      String swaggerUrl = args[0];
      String programName = args[1];
      String extra = args.length >= 3 ? args[2] : "";
//      String apiKey = System.getenv().getOrDefault("DEEPSEEK_API_KEY", "");
      String apiKey = "";
      String baseUrl = System.getenv().getOrDefault("DEEPSEEK_BASE_URL", "https://api.deepseek.com");
      String model = System.getenv().getOrDefault("DEEPSEEK_MODEL", "deepseek-chat");
      String gKey = System.getenv().getOrDefault("GEMINI_API_KEY", "");
      String gBase = System.getenv().getOrDefault("GEMINI_BASE_URL", "https://generativelanguage.googleapis.com");
      String gModel = System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3-pro-preview");
      String dKey = System.getenv().getOrDefault("DASHSCOPE_API_KEY", "");
      String dBase = System.getenv().getOrDefault("DASHSCOPE_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1");
      String dModel = System.getenv().getOrDefault("DASHSCOPE_MODEL", "qwen3-max");
      String provider = System.getenv().getOrDefault("LLM_PROVIDER", "").toLowerCase();
      boolean hasDeepseek = !apiKey.isEmpty();
      boolean hasGemini = !gKey.isEmpty();
      boolean hasDashscope = !dKey.isEmpty();
      if (provider.isEmpty() && !hasDeepseek && !hasGemini && !hasDashscope) {
        throw new RuntimeException("未配置LLM环境变量: 需要 DEEPSEEK_API_KEY 或 GEMINI_API_KEY 或 DASHSCOPE_API_KEY");
      }

      Logger log = Logger.get(App.class);
      log.info("正在下载接口文档: " + swaggerUrl);
      OpenApiExtractor extractor = new OpenApiExtractor();
      OpenApiExtractor.OpenApiInfo info = extractor.load(swaggerUrl);

      log.info("正在分析接口文档(LLM)...");
      String usys = PromptPresets.understandingSystemPrompt();
      String uusr = PromptPresets.understandingUserPrompt(programName, extra, info);
      String llmText;
      if (provider.equals("deepseek") || (provider.isEmpty() && hasDeepseek)) {
        DeepseekClient client = new DeepseekClient(apiKey, baseUrl, model);
        llmText = client.chat(usys, uusr);
      } else if (provider.equals("gemini") || (provider.isEmpty() && hasGemini)) {
        GeminiClient client = new GeminiClient(gKey, gBase, gModel);
        llmText = client.chat(usys, uusr);
      } else if (provider.equals("dashscope") || (provider.isEmpty() && hasDashscope)) {
        DashscopeClient client = new DashscopeClient(dKey, dBase, dModel);
        llmText = client.chat(usys, uusr);
      } else {
        throw new RuntimeException("LLM_PROVIDER 未识别或未配置对应密钥");
      }
      if (llmText == null || llmText.isEmpty()) throw new RuntimeException("LLM理解结果为空");
      String understandingText = ModelUtils.stripCodeFences(llmText);
      Files.writeString(Paths.get("api_understanding.md"), understandingText, StandardCharsets.UTF_8);
      ApiUnderstandingResult ar = new ApiUnderstandingResult();
      ar.summaryText = understandingText;

      log.info("正在生成测试用例(LLM)...");
      TestCaseGenerator tcg = new TestCaseGenerator();
      String csys = PromptPresets.casesSystemPrompt();
      String cusr = PromptPresets.casesUserPrompt(programName, extra, info);
      String casesText;
      if (provider.equals("deepseek") || (provider.isEmpty() && hasDeepseek)) {
        DeepseekClient client = new DeepseekClient(apiKey, baseUrl, model);
        casesText = client.chat(csys, cusr);
      } else if (provider.equals("gemini") || (provider.isEmpty() && hasGemini)) {
        GeminiClient client = new GeminiClient(gKey, gBase, gModel);
        casesText = client.chat(csys, cusr);
      } else if (provider.equals("dashscope") || (provider.isEmpty() && hasDashscope)) {
        DashscopeClient client = new DashscopeClient(dKey, dBase, dModel);
        casesText = client.chat(csys, cusr);
      } else {
        throw new RuntimeException("LLM_PROVIDER 未识别或未配置对应密钥");
      }
      if (casesText == null || casesText.isEmpty()) throw new RuntimeException("LLM用例生成结果为空");
      java.util.List<TestCase> cases = tcg.parseLlmCases(casesText);
      if (cases == null || cases.isEmpty()) throw new RuntimeException("LLM用例生成结果不可解析或为空");
      Files.writeString(Paths.get("cases.md"), tcg.describe(cases, ar), StandardCharsets.UTF_8);
      log.info("测试用例："+tcg.describe(cases, ar));
      log.info("正在生成JMX测试计划...");
      SimpleJmxBuilder builder = new SimpleJmxBuilder();
      String jmx = builder.build(info, extra, cases);

      Path jmxPath = Paths.get(programName.replaceAll("\\s+", "_") + "_test.jmx");
      Files.writeString(jmxPath, jmx, StandardCharsets.UTF_8);
      Path jtlPath = Paths.get("result.jtl");
      Path reportDir = Paths.get("report");

      log.info("正在执行JMeter（非GUI）...");
      JMeterRunner runner = new JMeterRunner();
      runner.run(jmxPath, jtlPath, reportDir);

      log.info("正在解析JTL并生成总结...");
      ReportParser parser = new ReportParser();
      SummaryMetrics metrics = parser.parseJtl(jtlPath);
      String analysisPrompt = PromptPresets.analysisPrompt(programName, extra, metrics);
      String summary;
      if (provider.equals("deepseek") || (provider.isEmpty() && hasDeepseek)) {
        DeepseekClient client = new DeepseekClient(apiKey, baseUrl, model);
        String s = client.chat("你是资深测试分析师，输出中文总结，不要附加无关内容。", analysisPrompt);
        summary = ModelUtils.stripCodeFences(s);
      } else if (provider.equals("gemini") || (provider.isEmpty() && hasGemini)) {
        GeminiClient client = new GeminiClient(gKey, gBase, gModel);
        String s = client.chat("你是资深测试分析师，输出中文总结，不要附加无关内容。", analysisPrompt);
        summary = ModelUtils.stripCodeFences(s);
      } else if (provider.equals("dashscope") || (provider.isEmpty() && hasDashscope)) {
        DashscopeClient client = new DashscopeClient(dKey, dBase, dModel);
        String s = client.chat("你是资深测试分析师，输出中文总结，不要附加无关内容。", analysisPrompt);
        summary = ModelUtils.stripCodeFences(s);
      } else {
        throw new RuntimeException("LLM_PROVIDER 未识别或未配置对应密钥");
      }
      Files.writeString(Paths.get("summary.txt"), summary, StandardCharsets.UTF_8);
      log.info(summary);
    } catch (Exception e) {
      Logger.get(App.class).error("执行失败: " + e.getMessage(), e);
      System.exit(1);
    }
  }
}
