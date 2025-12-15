package com.example.jmeterai.service;

import com.example.jmeterai.model.*;
import com.example.jmeterai.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PipelineService {

    @Autowired
    private LlmService llmService;

    @Autowired
    private TestCaseGenerator testCaseGenerator;

    @Autowired
    private CurlExecutorService curlExecutorService;

    public ProjectResult runPipeline(String swaggerUrl, String programName, String extra) throws Exception {
        ProjectResult result = new ProjectResult();
        Logger log = Logger.get(PipelineService.class);

        // 1. API Understanding
        log.info("Downloading Swagger: " + swaggerUrl);
        OpenApiExtractor extractor = new OpenApiExtractor();
        OpenApiExtractor.OpenApiInfo info = extractor.load(swaggerUrl);

        log.info("Analyzing API...");
        String understandingText = ModelUtils.stripCodeFences(llmService.callLlm(PromptPresets.understandingSystemPrompt(),
                PromptPresets.understandingUserPrompt(programName, extra, info)));
        result.apiUnderstanding = understandingText;

        ApiUnderstandingResult ar = new ApiUnderstandingResult();
        ar.summaryText = understandingText;

        // 2. Test Case Generation
        log.info("Generating Test Cases...");
        String casesText = llmService.callLlm(PromptPresets.casesSystemPrompt(),
                PromptPresets.casesUserPrompt(programName, extra, info));
        List<TestCase> cases = testCaseGenerator.parseLlmCases(casesText);
        result.testCases = cases;

        // 3. Execution (Curl)
        log.info("Executing Test Cases...");
        String baseUrl = info.baseUrl;
        if (baseUrl == null || baseUrl.isEmpty()) {
             log.warn("Base URL not found in Swagger, execution might fail if paths are relative.");
             baseUrl = "http://localhost:8080";
        }
        
        List<ExecutionResult> execResults = curlExecutorService.executeAll(cases, baseUrl);
        result.executionResults = execResults;

        // 4. Summary
        log.info("Generating Summary...");
        SummaryMetrics metrics = calculateMetrics(execResults);
        
        String analysisPrompt = PromptPresets.analysisPrompt(programName, 
            testCaseGenerator.describe(cases, ar), 
            metrics);
            
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
