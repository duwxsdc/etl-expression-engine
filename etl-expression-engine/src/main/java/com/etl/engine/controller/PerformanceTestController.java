package com.etl.engine.controller;

import com.etl.engine.context.EtlContext;
import com.etl.engine.context.EtlContextManager;
import com.etl.engine.mvel.MvelExpressionEngine;
import com.etl.engine.model.ExecuteResult;
import com.etl.engine.util.RequestLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/etl/performance")
public class PerformanceTestController {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceTestController.class);

    private final EtlContextManager etlContextManager;
    private final MvelExpressionEngine mvelExpressionEngine;

    @Autowired
    public PerformanceTestController(EtlContextManager etlContextManager,
                                     MvelExpressionEngine mvelExpressionEngine) {
        this.etlContextManager = etlContextManager;
        this.mvelExpressionEngine = mvelExpressionEngine;
    }

    @PostMapping("/complex-test")
    public ResponseEntity<Map<String, Object>> runComplexTest() {
        String requestId = RequestLogger.generateRequestId();
        long totalStartTime = System.currentTimeMillis();
        
        RequestLogger.logRequest(logger, requestId, "/etl/performance/complex-test", "POST", Map.of());
        
        Map<String, Object> results = new LinkedHashMap<>();
        results.put("requestId", requestId);
        results.put("startTime", new Date());
        
        List<Map<String, Object>> testResults = new ArrayList<>();
        
        testResults.add(testComplexNestedLogic(requestId));
        testResults.add(testConcurrentHttpRequests(requestId));
        testResults.add(testLargeDataProcessing(requestId));
        testResults.add(testErrorHandling(requestId));
        testResults.add(testSessionPersistence(requestId));
        
        long totalDuration = System.currentTimeMillis() - totalStartTime;
        
        results.put("tests", testResults);
        results.put("totalDurationMs", totalDuration);
        results.put("endTime", new Date());
        results.put("status", "COMPLETED");
        
        RequestLogger.logSuccess(logger, requestId, "/etl/performance/complex-test", totalDuration,
                Map.of("testCount", testResults.size()));
        
        return ResponseEntity.ok(results);
    }

    private Map<String, Object> testComplexNestedLogic(String requestId) {
        String testName = "ComplexNestedLogic";
        long startTime = System.currentTimeMillis();
        
        RequestLogger.logStage(logger, requestId, "TEST_START", testName, null);
        
        EtlContext context = etlContextManager.createSession();
        
        String expression = """
            result = new java.util.HashMap();
            
            data = [
                {'id': 1, 'name': 'Task1', 'priority': 3, 'status': 'PENDING'},
                {'id': 2, 'name': 'Task2', 'priority': 1, 'status': 'RUNNING'},
                {'id': 3, 'name': 'Task3', 'priority': 2, 'status': 'COMPLETED'},
                {'id': 4, 'name': 'Task4', 'priority': 1, 'status': 'PENDING'},
                {'id': 5, 'name': 'Task5', 'priority': 3, 'status': 'FAILED'}
            ];
            
            highPriority = new java.util.ArrayList();
            lowPriority = new java.util.ArrayList();
            completed = new java.util.ArrayList();
            pending = new java.util.ArrayList();
            
            for (item : data) {
                if (item.priority >= 3) {
                    highPriority.add(item);
                } else {
                    lowPriority.add(item);
                }
                
                if (item.status == 'COMPLETED') {
                    completed.add(item);
                } else if (item.status == 'PENDING') {
                    pending.add(item);
                }
            }
            
            result.put('highPriority', highPriority);
            result.put('lowPriority', lowPriority);
            result.put('completed', completed);
            result.put('pending', pending);
            result.put('stats', {
                'total': data.size(),
                'highPriorityCount': highPriority.size(),
                'pendingCount': pending.size()
            });
            
            finalScore = (highPriority.size() * 10) + (pending.size() * 5) - completed.size();
            result.put('finalScore', finalScore);
            
            result
            """;
        
        ExecuteResult execResult = mvelExpressionEngine.execute(expression, context);
        
        long duration = System.currentTimeMillis() - startTime;
        
        etlContextManager.destroySession(context.getSessionId());
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("testName", testName);
        result.put("durationMs", duration);
        result.put("success", execResult.success());
        result.put("resultPreview", execResult.finalResult() != null ? 
            execResult.finalResult().toString().substring(0, Math.min(200, execResult.finalResult().toString().length())) : null);
        
        RequestLogger.logStage(logger, requestId, "TEST_COMPLETE", testName, 
                Map.of("durationMs", duration, "success", execResult.success()));
        
        return result;
    }

    private Map<String, Object> testConcurrentHttpRequests(String requestId) {
        String testName = "ConcurrentHttpRequests";
        long startTime = System.currentTimeMillis();
        
        RequestLogger.logStage(logger, requestId, "TEST_START", testName, null);
        
        EtlContext context = etlContextManager.createSession();
        
        String expression = """
            responses = new java.util.ArrayList();
            errors = new java.util.ArrayList();
            
            async1 = http('https://httpbin.org/get').queryVariable('req', '1').asyncGet();
            async2 = http('https://httpbin.org/get').queryVariable('req', '2').asyncGet();
            async3 = http('https://httpbin.org/get').queryVariable('req', '3').asyncGet();
            
            try {
                r1 = async1.get(5000);
                if (r1.statusCode() == 200) {
                    responses.add({'req': 1, 'status': r1.statusCode()});
                }
            } catch (e) {
                errors.add({'req': 1, 'error': e.message});
            }
            
            try {
                r2 = async2.get(5000);
                if (r2.statusCode() == 200) {
                    responses.add({'req': 2, 'status': r2.statusCode()});
                }
            } catch (e) {
                errors.add({'req': 2, 'error': e.message});
            }
            
            try {
                r3 = async3.get(5000);
                if (r3.statusCode() == 200) {
                    responses.add({'req': 3, 'status': r3.statusCode()});
                }
            } catch (e) {
                errors.add({'req': 3, 'error': e.message});
            }
            
            {
                'totalRequests': 3,
                'successCount': responses.size(),
                'errorCount': errors.size(),
                'responses': responses,
                'errors': errors
            }
            """;
        
        ExecuteResult execResult = mvelExpressionEngine.execute(expression, context);
        
        long duration = System.currentTimeMillis() - startTime;
        
        etlContextManager.destroySession(context.getSessionId());
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("testName", testName);
        result.put("durationMs", duration);
        result.put("success", execResult.success());
        result.put("result", execResult.finalResult());
        
        RequestLogger.logStage(logger, requestId, "TEST_COMPLETE", testName,
                Map.of("durationMs", duration, "success", execResult.success()));
        
        return result;
    }

    private Map<String, Object> testLargeDataProcessing(String requestId) {
        String testName = "LargeDataProcessing";
        long startTime = System.currentTimeMillis();
        
        RequestLogger.logStage(logger, requestId, "TEST_START", testName, null);
        
        EtlContext context = etlContextManager.createSession();
        
        String expression = """
            import java.util.ArrayList;
            import java.util.HashMap;
            
            largeList = new ArrayList();
            for (i : {0..99}) {
                item = new HashMap();
                item.put('id', i);
                item.put('value', i * i);
                item.put('category', i % 5);
                item.put('active', i % 2 == 0);
                largeList.add(item);
            }
            
            categories = new HashMap();
            for (i : {0..4}) {
                categories.put(i, new ArrayList());
            }
            
            for (item : largeList) {
                cat = item.get('category');
                list = categories.get(cat);
                list.add(item);
            }
            
            sumByCategory = new HashMap();
            for (entry : categories.entrySet()) {
                sum = 0;
                for (item : entry.getValue()) {
                    sum = sum + item.get('value');
                }
                sumByCategory.put(entry.getKey(), sum);
            }
            
            totalSum = 0;
            for (val : sumByCategory.values()) {
                totalSum = totalSum + val;
            }
            
            {
                'itemCount': largeList.size(),
                'categoryCount': categories.size(),
                'sumByCategory': sumByCategory,
                'totalSum': totalSum
            }
            """;
        
        ExecuteResult execResult = mvelExpressionEngine.execute(expression, context);
        
        long duration = System.currentTimeMillis() - startTime;
        
        etlContextManager.destroySession(context.getSessionId());
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("testName", testName);
        result.put("durationMs", duration);
        result.put("success", execResult.success());
        result.put("result", execResult.finalResult());
        
        RequestLogger.logStage(logger, requestId, "TEST_COMPLETE", testName,
                Map.of("durationMs", duration, "success", execResult.success()));
        
        return result;
    }

    private Map<String, Object> testErrorHandling(String requestId) {
        String testName = "ErrorHandling";
        long startTime = System.currentTimeMillis();
        
        RequestLogger.logStage(logger, requestId, "TEST_START", testName, null);
        
        List<Map<String, Object>> errorTests = new ArrayList<>();
        
        String[] errorExpressions = {
            "invalidSyntax[",
            "undefinedVariable = nonexistent.method()",
            "http('invalid-url-format').get()",
            "1 / 0",
            "sql('INVALID SQL')"
        };
        
        for (String expr : errorExpressions) {
            EtlContext context = etlContextManager.createSession();
            ExecuteResult execResult = mvelExpressionEngine.execute(expr, context);
            
            Map<String, Object> testResult = new LinkedHashMap<>();
            testResult.put("expression", expr.length() > 30 ? expr.substring(0, 30) + "..." : expr);
            testResult.put("handled", !execResult.success());
            testResult.put("errorMessage", execResult.errorMsg());
            errorTests.add(testResult);
            
            etlContextManager.destroySession(context.getSessionId());
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("testName", testName);
        result.put("durationMs", duration);
        result.put("success", true);
        result.put("errorTests", errorTests);
        result.put("allErrorsHandled", errorTests.stream().allMatch(t -> (boolean) t.get("handled")));
        
        RequestLogger.logStage(logger, requestId, "TEST_COMPLETE", testName,
                Map.of("durationMs", duration));
        
        return result;
    }

    private Map<String, Object> testSessionPersistence(String requestId) {
        String testName = "SessionPersistence";
        long startTime = System.currentTimeMillis();
        
        RequestLogger.logStage(logger, requestId, "TEST_START", testName, null);
        
        EtlContext context = etlContextManager.createSession();
        String sessionId = context.getSessionId();
        
        List<Map<String, Object>> steps = new ArrayList<>();
        
        ExecuteResult r1 = mvelExpressionEngine.execute("a = 100", context);
        steps.add(Map.of("step", 1, "expr", "a = 100", "success", r1.success()));
        
        ExecuteResult r2 = mvelExpressionEngine.execute("b = a + 50", context);
        steps.add(Map.of("step", 2, "expr", "b = a + 50", "success", r2.success()));
        
        ExecuteResult r3 = mvelExpressionEngine.execute("c = a + b", context);
        steps.add(Map.of("step", 3, "expr", "c = a + b", "success", r3.success(), "result", r3.finalResult()));
        
        etlContextManager.updateSession(context);
        
        EtlContext restoredContext = etlContextManager.getSession(sessionId);
        boolean sessionRestored = restoredContext != null;
        
        ExecuteResult r4 = mvelExpressionEngine.execute("c * 2", restoredContext);
        steps.add(Map.of("step", 4, "expr", "c * 2", "success", r4.success(), "result", r4.finalResult()));
        
        etlContextManager.destroySession(sessionId);
        
        long duration = System.currentTimeMillis() - startTime;
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("testName", testName);
        result.put("durationMs", duration);
        result.put("success", r4.success() && r4.finalResult() != null && r4.finalResult().equals(300));
        result.put("steps", steps);
        result.put("sessionRestored", sessionRestored);
        
        RequestLogger.logStage(logger, requestId, "TEST_COMPLETE", testName,
                Map.of("durationMs", duration, "success", r4.success()));
        
        return result;
    }

    @PostMapping("/stress-test")
    public ResponseEntity<Map<String, Object>> runStressTest(
            @RequestParam(defaultValue = "10") int concurrentUsers,
            @RequestParam(defaultValue = "5") int requestsPerUser) {
        
        String requestId = RequestLogger.generateRequestId();
        long totalStartTime = System.currentTimeMillis();
        
        RequestLogger.logRequest(logger, requestId, "/etl/performance/stress-test", "POST",
                Map.of("concurrentUsers", concurrentUsers, "requestsPerUser", requestsPerUser));
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(concurrentUsers * requestsPerUser);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        Map<String, Long> responseTimes = new ConcurrentHashMap<>();
        
        String testExpression = "result = 1 + 2 + 3; result * 2";
        
        long testStartTime = System.currentTimeMillis();
        
        for (int user = 0; user < concurrentUsers; user++) {
            final int userId = user;
            for (int req = 0; req < requestsPerUser; req++) {
                final int reqId = req;
                executor.submit(() -> {
                    long startTime = System.currentTimeMillis();
                    try {
                        EtlContext context = etlContextManager.createSession();
                        ExecuteResult result = mvelExpressionEngine.execute(testExpression, context);
                        etlContextManager.destroySession(context.getSessionId());
                        
                        if (result.success()) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        logger.error("Stress test request failed: user={}, req={}", userId, reqId, e);
                    } finally {
                        long duration = System.currentTimeMillis() - startTime;
                        responseTimes.put("user" + userId + "_req" + reqId, duration);
                        latch.countDown();
                    }
                });
            }
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executor.shutdown();
        
        long totalDuration = System.currentTimeMillis() - totalStartTime;
        long testDuration = System.currentTimeMillis() - testStartTime;
        
        long avgResponseTime = responseTimes.values().stream()
                .mapToLong(Long::longValue)
                .sum() / responseTimes.size();
        
        long maxResponseTime = responseTimes.values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0);
        
        long minResponseTime = responseTimes.values().stream()
                .mapToLong(Long::longValue)
                .min()
                .orElse(0);
        
        int totalRequests = concurrentUsers * requestsPerUser;
        double throughput = (double) totalRequests / (testDuration / 1000.0);
        
        Map<String, Object> results = new LinkedHashMap<>();
        results.put("requestId", requestId);
        results.put("configuration", Map.of(
                "concurrentUsers", concurrentUsers,
                "requestsPerUser", requestsPerUser,
                "totalRequests", totalRequests
        ));
        results.put("results", Map.of(
                "successCount", successCount.get(),
                "failureCount", failureCount.get(),
                "successRate", String.format("%.2f%%", (double) successCount.get() / totalRequests * 100)
        ));
        results.put("timing", Map.of(
                "totalDurationMs", totalDuration,
                "testDurationMs", testDuration,
                "avgResponseTimeMs", avgResponseTime,
                "maxResponseTimeMs", maxResponseTime,
                "minResponseTimeMs", minResponseTime
        ));
        results.put("throughput", Map.of(
                "requestsPerSecond", String.format("%.2f", throughput),
                "avgLatencyMs", avgResponseTime
        ));
        results.put("status", "COMPLETED");
        
        RequestLogger.logSuccess(logger, requestId, "/etl/performance/stress-test", totalDuration,
                Map.of("successCount", successCount.get(), "failureCount", failureCount.get()));
        
        return ResponseEntity.ok(results);
    }

    @GetMapping("/http-stress")
    public ResponseEntity<Map<String, Object>> httpStressTest(
            @RequestParam(defaultValue = "5") int concurrentRequests,
            @RequestParam(defaultValue = "https://httpbin.org/get") String targetUrl) {
        
        String requestId = RequestLogger.generateRequestId();
        long startTime = System.currentTimeMillis();
        
        RequestLogger.logRequest(logger, requestId, "/etl/performance/http-stress", "GET",
                Map.of("concurrentRequests", concurrentRequests, "targetUrl", targetUrl));
        
        EtlContext context = etlContextManager.createSession();
        
        StringBuilder expressionBuilder = new StringBuilder();
        expressionBuilder.append("results = new java.util.ArrayList();\n");
        
        for (int i = 0; i < concurrentRequests; i++) {
            expressionBuilder.append(String.format(
                "async%d = http('%s').queryVariable('req', '%d').asyncGet();\n",
                i, targetUrl, i
            ));
        }
        
        for (int i = 0; i < concurrentRequests; i++) {
            expressionBuilder.append(String.format(
                "try { r%d = async%d.get(10000); results.add({'req': %d, 'status': r%d.statusCode()}); } " +
                "catch (e) { results.add({'req': %d, 'error': e.message}); }\n",
                i, i, i, i, i
            ));
        }
        
        expressionBuilder.append("results");
        
        ExecuteResult result = mvelExpressionEngine.execute(expressionBuilder.toString(), context);
        
        long duration = System.currentTimeMillis() - startTime;
        
        etlContextManager.destroySession(context.getSessionId());
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("requestId", requestId);
        response.put("concurrentRequests", concurrentRequests);
        response.put("durationMs", duration);
        response.put("success", result.success());
        response.put("results", result.finalResult());
        
        RequestLogger.logSuccess(logger, requestId, "/etl/performance/http-stress", duration,
                Map.of("concurrentRequests", concurrentRequests, "success", result.success()));
        
        return ResponseEntity.ok(response);
    }
}
