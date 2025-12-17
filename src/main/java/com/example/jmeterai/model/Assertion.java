package com.example.jmeterai.model;

public class Assertion {
    public String type; // "statusCode", "bodyContains", "jsonPath", "responseTime"
    public String expression; // For jsonPath, the path. For others, maybe null.
    public String operator; // "equals", "contains", "greaterThan", "lessThan"
    public String expected;
    public String successMessage; // Meaning if passed
    public String failureMessage; // Meaning if failed
}
