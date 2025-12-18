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

    public ProjectResult runPipeline(String swaggerUrl, String programName, String extra, List<String> tags, String authorization) throws Exception {
        ProjectResult result = new ProjectResult();

        // 1. API Understanding
        log.info("Downloading Swagger: {}", swaggerUrl);
        OpenApiExtractor extractor = new OpenApiExtractor();
        OpenApiExtractor.OpenApiInfo info = extractor.load(swaggerUrl);

        // Filter endpoints by tags if provided
        List<OpenApiExtractor.Endpoint> endpointsToTest = info.endpoints;
        if (tags != null && !tags.isEmpty()) {
            log.info("Filtering endpoints by tags: {}", tags);
            endpointsToTest = info.endpoints.stream()
                .filter(ep -> ep.tags != null && ep.tags.stream().anyMatch(tags::contains))
                .collect(java.util.stream.Collectors.toList());
            log.info("Filtered endpoints count: {} / {}", endpointsToTest.size(), info.endpoints.size());
        }

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
        result.baseUrl = baseUrl;
        
        int endpointIndex = 0;
        int totalEndpoints = endpointsToTest.size();

        // Iterate endpoints
        for (OpenApiExtractor.Endpoint endpoint : endpointsToTest) {
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
                        if (authorization != null && !authorization.isEmpty()) {
                            if (tc.headers == null) tc.headers = new java.util.LinkedHashMap<>();
                            tc.headers.put("Authorization", authorization);
                        }
                        tc.tags = endpoint.tags;
                        allCases.add(tc);
                        log.info("      Executing Case: {} (Goal: {})", tc.name, tc.goal);
                        ExecutionResult execResult = curlExecutorService.executeOne(tc, baseUrl);
                        execResult.scenario = scenario;
                        execResult.tags = endpoint.tags;

                        log.info("      Decision and assertions for case: {}", tc.name);
                        int retryCount = 0;
                        int maxRetries = 3;
                        boolean decided = false;
                        while (retryCount < maxRetries && !decided) {
                            try {
                                String decisionJson = llmService.callLlm(
                                    PromptPresets.caseDecisionSystemPrompt(),
                                    PromptPresets.caseDecisionUserPrompt(tc, execResult, endpointJson)
                                );
                                String cleaned = ModelUtils.stripCodeFences(decisionJson);
                                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                                com.fasterxml.jackson.databind.JsonNode root = om.readTree(cleaned);
                                boolean conforms = root.path("conforms").asBoolean(false);
                                String reason = root.path("reason").asText("");
                                if (conforms) {
                                    List<Assertion> assertions = testCaseGenerator.parseAssertions(root.path("assertions").toString());
                                    if (assertions != null && !assertions.isEmpty()) {
                                        tc.assertions = assertions;
                                        execResult.assertions = assertions;
                                        execResult.assertionReason = reason;
                                        execResult.verificationReason = reason;
                                        decided = true;
                                    } else {
                                        retryCount++;
                                    }
                                } else {
                                    String action = root.path("action").asText("");
                                    if ("adjust_case".equals(action)) {
                                        com.fasterxml.jackson.databind.JsonNode ac = root.path("adjustedCase");
                                        TestCase adjusted = testCaseGenerator.parseSingleCase(ac);
                                        if (authorization != null && !authorization.isEmpty()) {
                                            if (adjusted.headers == null) adjusted.headers = new java.util.LinkedHashMap<>();
                                            adjusted.headers.put("Authorization", authorization);
                                        }
                                        adjusted.tags = endpoint.tags;
                                        allCases.add(adjusted);
                                        ExecutionResult adjustedResult = curlExecutorService.executeOne(adjusted, baseUrl);
                                        adjustedResult.scenario = scenario;
                                        adjustedResult.tags = endpoint.tags;
                                        String decision2Json = llmService.callLlm(
                                            PromptPresets.caseDecisionSystemPrompt(),
                                            PromptPresets.caseDecisionUserPrompt(adjusted, adjustedResult, endpointJson)
                                        );
                                        String cleaned2 = ModelUtils.stripCodeFences(decision2Json);
                                        com.fasterxml.jackson.databind.JsonNode root2 = om.readTree(cleaned2);
                                        boolean conforms2 = root2.path("conforms").asBoolean(false);
                                        String reason2 = root2.path("reason").asText("");
                                        if (conforms2) {
                                            List<Assertion> assertions2 = testCaseGenerator.parseAssertions(root2.path("assertions").toString());
                                            adjusted.assertions = assertions2;
                                            adjustedResult.assertions = assertions2;
                                            adjustedResult.assertionReason = reason2;
                                            adjustedResult.verificationReason = reason2;
                                        } else {
                                            String abnormalDesc2 = root2.path("abnormalDescription").asText(reason2);
                                            adjustedResult.interfaceAbnormal = true;
                                            adjustedResult.abnormalDescription = abnormalDesc2;
                                            adjustedResult.verificationReason = abnormalDesc2;
                                        }
                                        verifyLocally(adjusted, adjustedResult);
                                        allResults.add(adjustedResult);
                                        execResult.caseAdjusted = true;
                                        execResult.adjustmentNote = reason;
                                        execResult.success = false;
                                        execResult.verificationPassed = false;
                                        if (execResult.verificationReason == null || execResult.verificationReason.isEmpty()) {
                                            execResult.verificationReason = reason;
                                        }
                                        decided = true;
                                    } else if ("mark_abnormal".equals(action)) {
                                        String abnormalDesc = root.path("abnormalDescription").asText(reason);
                                        execResult.interfaceAbnormal = true;
                                        execResult.abnormalDescription = abnormalDesc;
                                        execResult.verificationReason = abnormalDesc;
                                        decided = true;
                                    } else {
                                        retryCount++;
                                    }
                                }
                            } catch (Exception e) {
                                retryCount++;
                            }
                        }
                        if (!decided) {
                            try {
                                String assertionsJson = llmService.callLlm(
                                    PromptPresets.assertionGenerationSystemPrompt(),
                                    PromptPresets.assertionGenerationUserPrompt(tc, execResult, endpointJson)
                                );
                                List<Assertion> assertions = testCaseGenerator.parseAssertions(assertionsJson);
                                if (assertions != null && !assertions.isEmpty()) {
                                    tc.assertions = assertions;
                                    execResult.assertions = assertions;
                                }
                            } catch (Exception e) {}
                        }
                        verifyLocally(tc, execResult);
                        
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

    public SummaryMetrics calculateMetrics(List<ExecutionResult> results) {
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

    public List<ExecutionResult> reRunTestCases(List<TestCase> cases, String baseUrl) {
        List<ExecutionResult> results = new java.util.ArrayList<>();
        for (TestCase tc : cases) {
            log.info("Re-running Case: {}", tc.name);
            ExecutionResult execResult = curlExecutorService.executeOne(tc, baseUrl);
            execResult.tags = tc.tags;
            
            // Verify locally
            verifyLocally(tc, execResult);
            
            results.add(execResult);
        }
        return results;
    }

    private void verifyLocally(TestCase tc, ExecutionResult result) {
        if (result.interfaceAbnormal) {
            result.success = false;
            result.verificationPassed = false;
            if (result.verificationReason == null || result.verificationReason.isEmpty()) {
                result.verificationReason = result.abnormalDescription == null ? "接口异常" : result.abnormalDescription;
            }
            return;
        }
        if (result.caseAdjusted) {
            result.success = false;
            result.verificationPassed = false;
            if (result.verificationReason == null || result.verificationReason.isEmpty()) {
                result.verificationReason = result.adjustmentNote == null ? "用例已调整，原结果不符合预期" : result.adjustmentNote;
            }
            return;
        }
        if (tc.assertions == null || tc.assertions.isEmpty()) {
            // Default verification if no assertions
             if (result.statusCode >= 200 && result.statusCode < 300) {
                 result.success = true;
                 result.verificationPassed = true;
                 result.verificationReason = "No assertions generated, assumed success (2xx)";
             } else {
                 result.success = false;
                 result.verificationPassed = false;
                 result.verificationReason = "No assertions generated, status code " + result.statusCode;
             }
             return;
        }

        boolean allPassed = true;
        StringBuilder reasons = new StringBuilder();
        
        for (Assertion a : tc.assertions) {
            boolean passed = false;
            String actual = "";
            try {
                if ("statusCode".equals(a.type)) {
                    int expected = Integer.parseInt(a.expected);
                    passed = (result.statusCode == expected);
                    actual = String.valueOf(result.statusCode);
                } else if ("bodyContains".equals(a.type)) {
                    passed = result.responseBody != null && result.responseBody.contains(a.expected);
                    actual = "Body content";
                } else if ("responseTime".equals(a.type)) {
                    long expected = Long.parseLong(a.expected);
                    passed = result.durationMs < expected;
                    actual = result.durationMs + "ms";
                } else if ("jsonPath".equals(a.type)) {
                     com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                     com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(result.responseBody);
                     passed = checkJsonPath(root, a.expression, a.operator, a.expected);
                     actual = "JsonPath result";
                }
            } catch (Exception e) {
                passed = false;
                actual = "Error: " + e.getMessage();
            }
            
            if (!passed) {
                allPassed = false;
                reasons.append("[").append(a.failureMessage).append(" (Expected: ").append(a.expected).append(", Actual: ").append(actual).append(")] ");
            }
        }
        
        result.verificationPassed = allPassed;
        result.success = allPassed;
        result.verificationReason = allPassed ? "All assertions passed" : reasons.toString();
    }

    private boolean checkJsonPath(com.fasterxml.jackson.databind.JsonNode root, String expression, String operator, String expected) {
        if (expression == null) return false;
        if (expression.startsWith("$.")) expression = expression.substring(2);
        
        String[] parts = expression.split("\\.");
        com.fasterxml.jackson.databind.JsonNode current = root;
        for (String part : parts) {
            if (current == null) return false;
            if (part.contains("[")) {
                String name = part.substring(0, part.indexOf("["));
                String indexStr = part.substring(part.indexOf("[")+1, part.indexOf("]"));
                int index = Integer.parseInt(indexStr);
                if (!name.isEmpty()) current = current.path(name);
                current = current.path(index);
            } else {
                current = current.path(part);
            }
        }
        
        if (current.isMissingNode()) return false;
        
        String val = current.asText();
        if ("equals".equals(operator)) return val.equals(expected);
        if ("contains".equals(operator)) return val.contains(expected);
        if ("notContains".equals(operator)) return !val.contains(expected);
        if ("greaterThan".equals(operator)) {
            try { return Double.parseDouble(val) > Double.parseDouble(expected); } catch (Exception e) { return false; }
        }
        if ("lessThan".equals(operator)) {
            try { return Double.parseDouble(val) < Double.parseDouble(expected); } catch (Exception e) { return false; }
        }
        return true;
    }
}
