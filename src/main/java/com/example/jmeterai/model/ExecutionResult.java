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
    
    // Grouping info
    public java.util.List<String> tags;
    
    public boolean interfaceAbnormal;
    public String abnormalDescription;
    public boolean caseAdjusted;
    public String adjustmentNote;
    public java.util.List<Assertion> assertions;
    public String assertionReason;
}
