package com.example.jmeterai.model;

public class ExecutionResult {
    public String caseName;
    public String method;
    public String url;
    public String curlCommand;
    public int statusCode;
    public String responseBody;
    public long durationMs;
    public boolean success;
    public String errorMessage;
    
    // Verification fields
    public boolean verificationPassed;
    public String verificationReason;
    public QualityScenario scenario;
}
