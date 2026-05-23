package com.etl.engine.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/etl/expression/test")
public class ResponseHandlerTestController {
    
    private static final Logger logger = LoggerFactory.getLogger(ResponseHandlerTestController.class);
    
    @GetMapping("/response/simple")
    public ResponseEntity<Map<String, Object>> testSimpleResponse() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Hello World");
        result.put("code", 200);
        result.put("success", true);
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/response/nested")
    public ResponseEntity<Map<String, Object>> testNestedResponse() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", Map.of(
            "id", 1,
            "name", "张三",
            "profile", Map.of(
                "age", 30,
                "department", "研发部",
                "level", "高级"
            )
        ));
        data.put("metadata", Map.of(
            "version", "1.0",
            "created", "2024-01-01"
        ));
        data.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(data);
    }
    
    @GetMapping("/response/array")
    public ResponseEntity<Map<String, Object>> testArrayResponse() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", Arrays.asList(
            Map.of("id", 1, "name", "A", "price", 10.5),
            Map.of("id", 2, "name", "B", "price", 20.0),
            Map.of("id", 3, "name", "C", "price", 15.8)
        ));
        result.put("tags", Arrays.asList("tag1", "tag2", "tag3"));
        result.put("scores", Arrays.asList(85, 90, 78, 92, 88));
        result.put("matrix", Arrays.asList(
            Arrays.asList(1, 2, 3),
            Arrays.asList(4, 5, 6)
        ));
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/response/transform")
    public ResponseEntity<Map<String, Object>> testTransformResponse(
            @RequestBody Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("original", input);
        result.put("transformed", Map.of(
            "upperName", input.getOrDefault("name", "").toString().toUpperCase(),
            "doubledValue", ((Number) input.getOrDefault("value", 0)).intValue() * 2,
            "processed", true
        ));
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/response/error")
    public ResponseEntity<Map<String, Object>> testErrorResponse(
            @RequestParam(defaultValue = "false") boolean triggerError) {
        
        if (triggerError) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", true);
            error.put("code", 400);
            error.put("message", "模拟的错误响应");
            error.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.badRequest().body(error);
        }
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("error", false);
        result.put("code", 200);
        result.put("message", "正常响应");
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/response/complex")
    public ResponseEntity<Map<String, Object>> testComplexResponse() {
        List<Map<String, Object>> orders = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> order = new LinkedHashMap<>();
            order.put("orderId", "ORD-" + i);
            order.put("status", i % 2 == 0 ? "completed" : "pending");
            order.put("items", Arrays.asList(
                Map.of("productId", "P" + i + "1", "qty", i, "price", 10.0 * i),
                Map.of("productId", "P" + i + "2", "qty", i + 1, "price", 15.0 * i)
            ));
            order.put("totalAmount", 10.0 * i + 15.0 * i);
            orders.add(order);
        }
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orders", orders);
        result.put("summary", Map.of(
            "totalOrders", orders.size(),
            "completedOrders", orders.stream().filter(o -> "completed".equals(o.get("status"))).count(),
            "pendingOrders", orders.stream().filter(o -> "pending".equals(o.get("status"))).count()
        ));
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }
}
