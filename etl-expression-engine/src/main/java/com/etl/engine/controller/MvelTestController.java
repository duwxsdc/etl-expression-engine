package com.etl.engine.controller;

import com.etl.engine.model.TestCaseResult;
import com.etl.engine.model.TestSuiteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/etl/expression/test")
public class MvelTestController {
    
    private static final Logger logger = LoggerFactory.getLogger(MvelTestController.class);
    
    @GetMapping("/api1")
    public ResponseEntity<Map<String, Object>> testApi1Get(
            @RequestParam(required = false, defaultValue = "default") String param,
            @RequestHeader(value = "X-Custom-Header", required = false) String customHeader) {
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", "GET");
        result.put("endpoint", "/etl/expression/test/api1");
        result.put("param", param);
        result.put("customHeader", customHeader);
        result.put("timestamp", System.currentTimeMillis());
        result.put("status", "success");
        
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/api1")
    public ResponseEntity<Map<String, Object>> testApi1Post(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", "POST");
        result.put("endpoint", "/etl/expression/test/api1");
        result.put("body", body);
        result.put("authenticated", auth != null && auth.startsWith("Bearer "));
        result.put("timestamp", System.currentTimeMillis());
        result.put("status", "success");
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/api2/{category}/{id}")
    public ResponseEntity<Map<String, Object>> testApi2WithPathVars(
            @PathVariable String category,
            @PathVariable Long id,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", "GET");
        result.put("endpoint", "/etl/expression/test/api2/{category}/{id}");
        result.put("pathVariables", Map.of("category", category, "id", id));
        result.put("queryParams", Map.of("sort", sort, "pageSize", pageSize));
        result.put("timestamp", System.currentTimeMillis());
        result.put("status", "success");
        
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/api3")
    public ResponseEntity<Map<String, Object>> testApi3WithValidation(
            @RequestBody Map<String, Object> data,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey) {
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", "POST");
        result.put("endpoint", "/etl/expression/test/api3");
        result.put("receivedData", data);
        result.put("headers", Map.of(
            "X-Request-Id", requestId != null ? requestId : "not-provided",
            "X-Api-Key", apiKey != null ? "***" + apiKey.substring(Math.max(0, apiKey.length() - 4)) : "not-provided"
        ));
        result.put("dataSize", data != null ? data.size() : 0);
        result.put("timestamp", System.currentTimeMillis());
        result.put("status", "success");
        
        return ResponseEntity.ok(result);
    }
    
    @PutMapping("/api4/{resourceId}")
    public ResponseEntity<Map<String, Object>> testApi4Put(
            @PathVariable String resourceId,
            @RequestBody Map<String, Object> updates,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", "PUT");
        result.put("endpoint", "/etl/expression/test/api4/{resourceId}");
        result.put("resourceId", resourceId);
        result.put("updates", updates);
        result.put("optimisticLock", ifMatch);
        result.put("timestamp", System.currentTimeMillis());
        result.put("status", "success");
        
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/api5/{resourceId}")
    public ResponseEntity<Map<String, Object>> testApi5Delete(
            @PathVariable String resourceId,
            @RequestHeader(value = "X-Confirm-Delete", required = false, defaultValue = "false") boolean confirm) {
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", "DELETE");
        result.put("endpoint", "/etl/expression/test/api5/{resourceId}");
        result.put("resourceId", resourceId);
        result.put("confirmed", confirm);
        result.put("deleted", confirm);
        result.put("timestamp", System.currentTimeMillis());
        
        if (!confirm) {
            result.put("status", "cancelled");
            result.put("message", "Deletion requires X-Confirm-Delete header");
            return ResponseEntity.badRequest().body(result);
        }
        
        result.put("status", "success");
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/api6/search")
    public ResponseEntity<Map<String, Object>> testApi6Search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) String[] fields,
            @RequestParam(required = false, defaultValue = "false") Boolean includeInactive) {
        
        List<Map<String, Object>> mockResults = new ArrayList<>();
        for (int i = 1; i <= Math.min(size, 5); i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("name", "Item " + i);
            item.put("active", true);
            mockResults.add(item);
        }
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", "GET");
        result.put("endpoint", "/etl/expression/test/api6/search");
        result.put("query", query);
        result.put("pagination", Map.of("page", page, "size", size, "total", 100));
        result.put("fields", fields != null ? Arrays.asList(fields) : Collections.emptyList());
        result.put("includeInactive", includeInactive);
        result.put("results", mockResults);
        result.put("resultCount", mockResults.size());
        result.put("timestamp", System.currentTimeMillis());
        result.put("status", "success");
        
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/api7/batch")
    public ResponseEntity<Map<String, Object>> testApi7Batch(
            @RequestBody List<Map<String, Object>> items,
            @RequestHeader(value = "X-Batch-Size", required = false) Integer batchSize) {
        
        List<Map<String, Object>> processedItems = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            if (item.containsKey("error")) {
                errors.add("Item " + i + ": simulated error");
            } else {
                Map<String, Object> processed = new LinkedHashMap<>(item);
                processed.put("processed", true);
                processed.put("index", i);
                processedItems.add(processed);
            }
        }
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", "POST");
        result.put("endpoint", "/etl/expression/test/api7/batch");
        result.put("inputCount", items.size());
        result.put("processedCount", processedItems.size());
        result.put("errorCount", errors.size());
        result.put("processedItems", processedItems);
        result.put("errors", errors);
        result.put("batchSize", batchSize != null ? batchSize : items.size());
        result.put("timestamp", System.currentTimeMillis());
        result.put("status", errors.isEmpty() ? "success" : "partial");
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/api8/timeout")
    public ResponseEntity<Map<String, Object>> testApi8Timeout(
            @RequestParam(required = false, defaultValue = "0") Integer delayMs) {
        
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", "GET");
        result.put("endpoint", "/etl/expression/test/api8/timeout");
        result.put("requestedDelayMs", delayMs);
        result.put("actualDelayMs", delayMs);
        result.put("timestamp", System.currentTimeMillis());
        result.put("status", "success");
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/api9/error")
    public ResponseEntity<Map<String, Object>> testApi9Error(
            @RequestParam(required = false, defaultValue = "400") Integer code) {
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("endpoint", "/etl/expression/test/api9/error");
        result.put("requestedCode", code);
        result.put("timestamp", System.currentTimeMillis());
        
        return switch (code) {
            case 400 -> {
                result.put("status", "error");
                result.put("error", "Bad Request");
                result.put("message", "Invalid parameters provided");
                yield ResponseEntity.badRequest().body(result);
            }
            case 401 -> {
                result.put("status", "error");
                result.put("error", "Unauthorized");
                result.put("message", "Authentication required");
                yield ResponseEntity.status(401).body(result);
            }
            case 403 -> {
                result.put("status", "error");
                result.put("error", "Forbidden");
                result.put("message", "Access denied");
                yield ResponseEntity.status(403).body(result);
            }
            case 404 -> {
                result.put("status", "error");
                result.put("error", "Not Found");
                result.put("message", "Resource not found");
                yield ResponseEntity.status(404).body(result);
            }
            case 500 -> {
                result.put("status", "error");
                result.put("error", "Internal Server Error");
                result.put("message", "An unexpected error occurred");
                yield ResponseEntity.status(500).body(result);
            }
            default -> {
                result.put("status", "success");
                result.put("message", "No error simulated");
                yield ResponseEntity.ok(result);
            }
        };
    }
    
    @GetMapping("/api10/headers")
    public ResponseEntity<Map<String, Object>> testApi10Headers(
            @RequestHeader Map<String, String> headers) {
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", "GET");
        result.put("endpoint", "/etl/expression/test/api10/headers");
        result.put("receivedHeaders", headers);
        result.put("headerCount", headers.size());
        result.put("timestamp", System.currentTimeMillis());
        result.put("status", "success");
        
        return ResponseEntity.ok(result);
    }
}
