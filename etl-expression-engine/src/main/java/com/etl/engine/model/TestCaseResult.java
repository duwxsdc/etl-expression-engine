package com.etl.engine.model;

import java.util.List;
import java.util.Map;

public record TestCaseResult(
    String testId,
    String testName,
    String category,
    boolean passed,
    String expression,
    Object expectedValue,
    Object actualValue,
    String errorMessage,
    long executionTimeMs,
    Map<String, Object> metadata
) {
    public static TestCaseResult success(String testId, String testName, String category,
                                         String expression, Object expected, Object actual, 
                                         long timeMs, Map<String, Object> metadata) {
        return new TestCaseResult(testId, testName, category, true, expression, 
                expected, actual, null, timeMs, metadata);
    }
    
    public static TestCaseResult failure(String testId, String testName, String category,
                                          String expression, Object expected, Object actual,
                                          String error, long timeMs, Map<String, Object> metadata) {
        return new TestCaseResult(testId, testName, category, false, expression,
                expected, actual, error, timeMs, metadata);
    }
}
