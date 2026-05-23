package com.etl.engine.model;

import java.util.List;
import java.util.Map;

public record TestSuiteResult(
    String suiteName,
    int totalTests,
    int passedCount,
    int failedCount,
    long totalTimeMs,
    List<TestCaseResult> results,
    Map<String, Object> summary
) {
    public double getPassRate() {
        return totalTests > 0 ? (double) passedCount / totalTests * 100 : 0;
    }
    
    public static TestSuiteResult of(String suiteName, List<TestCaseResult> results) {
        int total = results.size();
        int passed = (int) results.stream().filter(TestCaseResult::passed).count();
        int failed = total - passed;
        long totalTime = results.stream().mapToLong(TestCaseResult::executionTimeMs).sum();
        
        Map<String, Object> summary = Map.of(
            "passRate", total > 0 ? (double) passed / total * 100 : 0,
            "avgTimeMs", total > 0 ? totalTime / total : 0
        );
        
        return new TestSuiteResult(suiteName, total, passed, failed, totalTime, results, summary);
    }
}
