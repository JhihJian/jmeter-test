package com.example.jmeterai.service;

import com.example.jmeterai.model.ExecutionResult;
import com.example.jmeterai.model.TestCase;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CurlExecutorService {

    private final OkHttpClient client;

    public CurlExecutorService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public List<ExecutionResult> executeAll(List<TestCase> cases, String baseUrl) {
        List<ExecutionResult> results = new ArrayList<>();
        for (TestCase tc : cases) {
            ExecutionResult r = executeOne(tc, baseUrl);
            results.add(r);
            // log.info("测试结果:{}", r);
        }
        return results;
    }

    public ExecutionResult executeOne(TestCase tc, String baseUrl) {
        ExecutionResult res = new ExecutionResult();
        res.caseName = tc.name;
        res.method = tc.method;
        
        String url = buildUrl(baseUrl, tc.path, tc.queryParams, tc.pathParams);
        res.url = url;

        String curl = generateCurl(tc.method, url, tc.headers, tc.body);
        res.curlCommand = curl;

        Request.Builder rb = new Request.Builder().url(url);
        
        // Headers
        if (tc.headers != null) {
            for (Map.Entry<String, String> entry : tc.headers.entrySet()) {
                rb.addHeader(entry.getKey(), entry.getValue());
            }
        }

        // Body
        RequestBody body = null;
        if (tc.body != null && !tc.body.isEmpty() && requiresBody(tc.method)) {
            MediaType mediaType = MediaType.parse(tc.headers.getOrDefault("Content-Type", "application/json"));
            body = RequestBody.create(tc.body, mediaType);
        } else if (requiresBody(tc.method)) {
            body = RequestBody.create("", null);
        }

        rb.method(tc.method, body);

        long start = System.currentTimeMillis();
        try (Response response = client.newCall(rb.build()).execute()) {
            res.durationMs = System.currentTimeMillis() - start;
            res.statusCode = response.code();
            res.responseBody = response.body() != null ? response.body().string() : "";
            res.success = response.isSuccessful(); // 2xx range
        } catch (IOException e) {
            res.durationMs = System.currentTimeMillis() - start;
            res.success = false;
            res.errorMessage = e.getMessage();
            res.statusCode = -1;
        }

        return res;
    }

    private boolean requiresBody(String method) {
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method);
    }

    private String buildUrl(String baseUrl, String path, Map<String, String> query, Map<String, String> pathParams) {
        String finalPath = path;
        // Path params
        if (pathParams != null) {
            for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                finalPath = finalPath.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        
        String fullUrl = (baseUrl.endsWith("/") && finalPath.startsWith("/")) ? 
                baseUrl + finalPath.substring(1) : 
                (!baseUrl.endsWith("/") && !finalPath.startsWith("/")) ? baseUrl + "/" + finalPath : baseUrl + finalPath;

        // Query params
        if (query != null && !query.isEmpty()) {
            HttpUrl.Builder urlBuilder = HttpUrl.parse(fullUrl).newBuilder();
            for (Map.Entry<String, String> entry : query.entrySet()) {
                urlBuilder.addQueryParameter(entry.getKey(), entry.getValue());
            }
            return urlBuilder.build().toString();
        }
        return fullUrl;
    }

    private String generateCurl(String method, String url, Map<String, String> headers, String body) {
        StringBuilder sb = new StringBuilder("curl -X ").append(method).append(" '").append(url).append("'");
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                sb.append(" -H '").append(entry.getKey()).append(": ").append(entry.getValue()).append("'");
            }
        }
        if (body != null && !body.isEmpty()) {
            sb.append(" -d '").append(body.replace("'", "'\\''")).append("'");
        }
        return sb.toString();
    }
}
