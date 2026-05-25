package com.etl.engine.controller;

import com.etl.engine.context.EtlContext;
import com.etl.engine.mvel.MvelExpressionEngine;
import com.etl.engine.model.ExecuteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/test")
public class MvelTestRunnerController {
    
    private static final Logger logger = LoggerFactory.getLogger(MvelTestRunnerController.class);
    
    private final MvelExpressionEngine mvelExpressionEngine;
    
    public MvelTestRunnerController(MvelExpressionEngine mvelExpressionEngine) {
        this.mvelExpressionEngine = mvelExpressionEngine;
    }
    
    @PostMapping("/run-mvel")
    public ResponseEntity<?> runMvel(@RequestBody MvelTestRequest request) {
        logger.info("执行MVEL脚本: {}", request.expression());
        
        long startTime = System.currentTimeMillis();
        
        try {
            EtlContext context = new EtlContext();
            if (request.variables() != null) {
                request.variables().forEach(context::setVariable);
            }
            
            ExecuteResult result = mvelExpressionEngine.execute(request.expression(), context);
            
            long duration = System.currentTimeMillis() - startTime;
            
            logger.info("MVEL执行完成: success={}, duration={}ms", result.success(), duration);
            
            return ResponseEntity.ok(Map.of(
                "success", result.success(),
                "result", result.finalResult() != null ? result.finalResult() : "null",
                "error", result.errorMsg() != null ? result.errorMsg() : "",
                "duration", duration,
                "timestamp", Instant.now()
            ));
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("MVEL执行异常: {}", e.getMessage(), e);
            
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage(),
                "duration", duration,
                "timestamp", Instant.now()
            ));
        }
    }
    
    @PostMapping("/run-mvel/simple")
    public ResponseEntity<?> runMvelSimple(@RequestBody String expression) {
        logger.info("执行简单MVEL脚本: {}", expression);
        
        long startTime = System.currentTimeMillis();
        
        try {
            EtlContext context = new EtlContext();
            ExecuteResult result = mvelExpressionEngine.execute(expression, context);
            
            long duration = System.currentTimeMillis() - startTime;
            
            return ResponseEntity.ok(Map.of(
                "success", result.success(),
                "result", result.finalResult() != null ? result.finalResult() : "null",
                "error", result.errorMsg() != null ? result.errorMsg() : "",
                "duration", duration
            ));
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "Unknown error",
                "duration", duration
            ));
        }
    }
    
    @PostMapping("/run-mvel/restclient")
    public ResponseEntity<?> testRestClient(@RequestBody RestClientTestRequest request) {
        logger.info("测试RestClient异步回调: url={}, timeout={}ms", request.url(), request.timeoutMs());
        
        String expression = String.format(
            "RestClient.post(\"%s\")" +
            ".header(\"Content-Type\", \"application/json\")" +
            ".bodyJson(payload)" +
            ".bindCallback(%d)" +
            ".execute()" +
            ".waitCallback()",
            request.url(), request.timeoutMs()
        );
        
        try {
            EtlContext context = new EtlContext();
            context.setVariable("payload", request.payload());
            
            ExecuteResult result = mvelExpressionEngine.execute(expression, context);
            
            return ResponseEntity.ok(Map.of(
                "success", result.success(),
                "result", result.finalResult() != null ? result.finalResult() : "null",
                "error", result.errorMsg() != null ? result.errorMsg() : "",
                "expression", expression
            ));
            
        } catch (Exception e) {
            logger.error("RestClient测试失败: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "Unknown error",
                "expression", expression
            ));
        }
    }
    
    @GetMapping("/restclient/status")
    public ResponseEntity<?> getRestClientStatus() {
        int pendingCount = com.etl.engine.rest.MvelRestClient.getPendingEventCount();
        com.etl.engine.callback.LocalNodeInfo nodeInfo = com.etl.engine.rest.MvelRestClient.getNodeInfo();
        
        return ResponseEntity.ok(Map.of(
            "pendingEvents", pendingCount,
            "nodeIp", nodeInfo.getNodeIp(),
            "nodePort", nodeInfo.getServerPort(),
            "nodeId", nodeInfo.getNodeId()
        ));
    }
    
    public record MvelTestRequest(
        String expression,
        Map<String, Object> variables
    ) {}
    
    public record RestClientTestRequest(
        String url,
        int timeoutMs,
        Map<String, Object> payload
    ) {}
}
