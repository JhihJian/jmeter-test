package com.example.jmeterai.service;

import com.example.jmeterai.model.*;
import com.example.jmeterai.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PipelineService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PipelineService.class);

    @Autowired
    private LlmService llmService;

    @Autowired
    private TestCaseGenerator testCaseGenerator;

    @Autowired
    private CurlExecutorService curlExecutorService;

    public ProjectResult runPipeline(String swaggerUrl, String programName, String extra) throws Exception {
        ProjectResult result = new ProjectResult();

        // 1. API Understanding
        log.info("Downloading Swagger: {}", swaggerUrl);
        OpenApiExtractor extractor = new OpenApiExtractor();
        OpenApiExtractor.OpenApiInfo info = extractor.load(swaggerUrl);

        log.info("Analyzing API...");
        String understandingText = ModelUtils.stripCodeFences(llmService.callLlm(PromptPresets.understandingSystemPrompt(),
                PromptPresets.understandingUserPrompt(programName, extra, info)));
        result.apiUnderstanding = understandingText;

        ApiUnderstandingResult ar = new ApiUnderstandingResult();
        ar.summaryText = understandingText;
        log.info("总结api结果: {}", ar.summaryText);
        // 2. Test Case Generation & Execution (Iterative by Interface & Scenario)
        log.info("Starting Interface-by-Interface Testing...");
        List<TestCase> allCases = new java.util.ArrayList<>();
        List<ExecutionResult> allResults = new java.util.ArrayList<>();

        String baseUrl = info.baseUrl;
        if (baseUrl == null || baseUrl.isEmpty()) {
             log.warn("Base URL not found in Swagger, execution might fail if paths are relative.");
             baseUrl = "http://localhost:8080";
        }
        
        int endpointIndex = 0;
        int totalEndpoints = info.endpoints.size();

        // Iterate endpoints
        for (OpenApiExtractor.Endpoint endpoint : info.endpoints) {
            endpointIndex++;
            String endpointJson = extractor.getEndpointJson(info.root, endpoint.method, endpoint.path);
            if (endpointJson.isEmpty()) {
                log.warn("Skipping endpoint {} {} (JSON extraction failed)", endpoint.method, endpoint.path);
                continue;
            }

            log.info("Testing Endpoint [{}/{}]: {} {}", endpointIndex, totalEndpoints, endpoint.method, endpoint.path);

            // Iterate Quality Scenarios
            for (QualityScenario scenario : QualityScenario.values()) {
                log.info("  Scenario: {}", scenario.name());
                
                // a. Generate Cases for this scenario
                try {
                    log.info("    Generating cases for scenario: {}", scenario.name());
                    String casesText = llmService.callLlm(
                        PromptPresets.singleInterfaceSystemPrompt(),
                        PromptPresets.singleInterfaceUserPrompt(programName, endpoint.method, endpoint.path, endpointJson, scenario)
                    );
                    if (log.isDebugEnabled()) {
                        log.debug("    LLM Generated Cases Response: {}", casesText);
                    }
                    
                    List<TestCase> scenarioCases = testCaseGenerator.parseLlmCases(casesText);
                    log.info("    Parsed {} cases from LLM response", scenarioCases.size());
                    
                    // b. Execute Cases immediately
                    for (TestCase tc : scenarioCases) {
                        allCases.add(tc);
                        log.info("      Executing Case: {} (Goal: {})", tc.name, tc.goal);
                        ExecutionResult execResult = curlExecutorService.executeOne(tc, baseUrl);
                        execResult.scenario = scenario;

                        // c. Verification (LLM Analysis)
                        log.info("      Verifying result for case: {}", tc.name);
                        String verifyJson = llmService.callLlm(
                            PromptPresets.verificationSystemPrompt(),
                            PromptPresets.verificationUserPrompt(tc, execResult, endpointJson)
                        );
                        if (log.isDebugEnabled()) {
                            log.debug("      Verification Response: {}", verifyJson);
                        }
                        
                        // Parse verification result
                        try {
                             com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                             com.fasterxml.jackson.databind.JsonNode verifyNode = mapper.readTree(ModelUtils.stripCodeFences(verifyJson));
                             execResult.verificationPassed = verifyNode.path("passed").asBoolean(false);
                             execResult.verificationReason = verifyNode.path("reason").asText("No reason provided");
                             
                             // Override success flag based on verification
                             execResult.success = execResult.verificationPassed;
                        } catch (Exception e) {
                             log.warn("Verification parsing failed: " + e.getMessage());
                             execResult.verificationReason = "Verification parsing failed";
                        }
                        
                        allResults.add(execResult);
                        log.info("      Result: {} - Reason: {}", (execResult.success ? "PASS" : "FAIL"), execResult.verificationReason);
                    }
                } catch (Exception e) {
                    log.error("Error testing " + endpoint.path + " scenario " + scenario.name() + ": " + e.getMessage(), e);
                }
            }
        }

        result.testCases = allCases;
        result.executionResults = allResults;

        // 4. Summary
        log.info("Generating Summary...");
        SummaryMetrics metrics = calculateMetrics(allResults);
        
        String analysisPrompt = PromptPresets.analysisPrompt(programName, 
            testCaseGenerator.describe(allCases, ar), 
            metrics);
        log.info("analysis prompt:"+analysisPrompt);
        String summary = ModelUtils.stripCodeFences(llmService.callLlm("你是资深测试分析师，输出中文总结，不要附加无关内容。", analysisPrompt));
        result.summary = summary;

        return result;
    }

    private SummaryMetrics calculateMetrics(List<ExecutionResult> results) {
        SummaryMetrics m = new SummaryMetrics();
        m.total = results.size();
        m.success = results.stream().filter(r -> r.success).count();
        m.fail = m.total - m.success;
        m.errorRate = m.total == 0 ? 0 : (double) m.fail / m.total;
        
        if (m.total > 0) {
            double sum = results.stream().mapToDouble(r -> r.durationMs).sum();
            m.avg = sum / m.total;
            m.min = results.stream().mapToDouble(r -> r.durationMs).min().orElse(0);
            m.max = results.stream().mapToDouble(r -> r.durationMs).max().orElse(0);
            
             List<Double> sorted = results.stream().map(r -> (double)r.durationMs).sorted().toList();
             m.p95 = percentile(sorted, 0.95);
             m.p99 = percentile(sorted, 0.99);
        }
        
        for (ExecutionResult r : results) {
            String name = r.caseName == null ? "Unknown" : r.caseName;
            LabelStat ls = m.byLabel.computeIfAbsent(name, k -> new LabelStat());
            ls.label = name;
            ls.total++;
            if (r.success) ls.success++; else ls.fail++;
            String code = String.valueOf(r.statusCode);
            ls.codes.put(code, ls.codes.getOrDefault(code, 0L) + 1);
        }
        return m;
    }

    private double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }
}
