package com.etl.engine.rest.enhanced;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/demo/enhanced")
public class EnhancedRestClientDemoController {
    
    @GetMapping("/run")
    public ResponseEntity<Map<String, Object>> runDemo() {
        StringBuilder output = new StringBuilder();
        
        output.append("\n").append("=".repeat(60)).append("\n");
        output.append("EnhancedRestClient 链式调用演示\n");
        output.append("=".repeat(60)).append("\n\n");
        
        EnhancedRestClient client = EnhancedRestClient.create(RestClient.create());
        ExtensionRegistry registry = client.getExtensionRegistry();
        
        registry.register(DefaultTokenExtension.withBearerToken("demo-access-token-12345"));
        registry.register(new CallbackExtension("127.0.0.1", 8080));
        
        output.append("已注册扩展:\n");
        registry.getExtensions().forEach(ext -> 
            output.append("  - ").append(ext.name()).append(" (order: ").append(ext.order()).append(")\n")
        );
        output.append("\n");
        
        String result1 = example1(client, output);
        String result2 = example2(client, output);
        String result3 = example3(client, registry, output);
        
        output.append("\n").append("=".repeat(60)).append("\n");
        output.append("演示完成!\n");
        output.append("=".repeat(60)).append("\n");
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "output", output.toString(),
            "example1", result1,
            "example2", result2,
            "example3", result3,
            "pendingCallbacks", CallbackManager.getInstance().getPendingCount()
        ));
    }
    
    private String example1(EnhancedRestClient client, StringBuilder output) {
        output.append("-".repeat(60)).append("\n");
        output.append("示例1: 带动态Token的POST请求\n");
        output.append("-".repeat(60)).append("\n");
        
        try {
            String result = client.post()
                .uri("http://localhost:8080/demo/enhanced/echo")
                .defaultToken()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message", "Hello from EnhancedRestClient!", "timestamp", System.currentTimeMillis()))
                .retrieve()
                .body(String.class);
            
            output.append("响应: ").append(result).append("\n");
            return result;
        } catch (Exception e) {
            output.append("错误: ").append(e.getMessage()).append("\n");
            return "error: " + e.getMessage();
        }
    }
    
    private String example2(EnhancedRestClient client, StringBuilder output) {
        output.append("\n").append("-".repeat(60)).append("\n");
        output.append("示例2: 异步回调等待\n");
        output.append("-".repeat(60)).append("\n");
        
        String eventId = CallbackManager.getInstance().generateEventId();
        output.append("生成 eventId: ").append(eventId).append("\n");
        
        Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
            try {
                Thread.sleep(2000);
                output.append("\n[模拟第三方] 2秒后发送回调...\n");
                
                CallbackManager.getInstance().complete(eventId, Map.of(
                    "status", "SUCCESS",
                    "eventId", eventId,
                    "data", Map.of(
                        "processed", true,
                        "result", "数据处理完成",
                        "processedAt", System.currentTimeMillis()
                    )
                ));
                output.append("[模拟第三方] 回调已发送!\n");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        try {
            output.append("发送异步请求，等待回调 (超时: 10秒)...\n");
            
            Object callbackResult = client.post()
                .uri("http://localhost:8080/demo/enhanced/async")
                .defaultToken()
                .callback(eventId, 10000)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("taskId", eventId, "action", "process"))
                .retrieve()
                .body(Object.class);
            
            output.append("\n回调结果: ").append(callbackResult).append("\n");
            return String.valueOf(callbackResult);
        } catch (CallbackTimeoutException e) {
            output.append("回调超时: ").append(e.getMessage()).append("\n");
            return "timeout: " + e.getMessage();
        } catch (Exception e) {
            output.append("错误: ").append(e.getMessage()).append("\n");
            return "error: " + e.getMessage();
        }
    }
    
    private String example3(EnhancedRestClient client, ExtensionRegistry registry, StringBuilder output) {
        output.append("\n").append("-".repeat(60)).append("\n");
        output.append("示例3: 多扩展组合使用\n");
        output.append("-".repeat(60)).append("\n");
        
        registry.register(new TracingExtension());
        registry.register(new LoggingExtension());
        
        try {
            String result = client.post()
                .uri("http://localhost:8080/demo/enhanced/echo")
                .enable("tracing", "logging")
                .defaultToken()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("demo", "multi-extension", "extensions", "tracing,logging,defaultToken"))
                .retrieve()
                .body(String.class);
            
            output.append("响应: ").append(result).append("\n");
            return result;
        } catch (Exception e) {
            output.append("错误: ").append(e.getMessage()).append("\n");
            return "error: " + e.getMessage();
        }
    }
    
    @PostMapping("/echo")
    public ResponseEntity<Map<String, Object>> echo(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "X-Span-Id", required = false) String spanId) {
        
        System.out.println("\n[服务端] 收到请求:");
        System.out.println("  Authorization: " + auth);
        if (traceId != null) System.out.println("  X-Trace-Id: " + traceId);
        if (spanId != null) System.out.println("  X-Span-Id: " + spanId);
        System.out.println("  Body: " + body);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "received", body,
            "auth", auth != null ? "已注入" : "未注入",
            "traceId", traceId,
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    @PostMapping("/async")
    public ResponseEntity<Map<String, Object>> asyncEndpoint(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestHeader(value = "X-Callback-EventId", required = false) String eventId) {
        
        System.out.println("\n[服务端] 收到异步请求:");
        System.out.println("  Authorization: " + auth);
        System.out.println("  X-Callback-EventId: " + eventId);
        System.out.println("  Body: " + body);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "请求已接收，等待回调",
            "eventId", eventId,
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
            "status", "running",
            "pendingCallbacks", CallbackManager.getInstance().getPendingCount()
        ));
    }
}
