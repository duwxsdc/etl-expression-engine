package com.etl.engine.mock;

import com.etl.engine.context.GlobalContext;
import com.etl.engine.model.ExecuteResult;
import com.etl.engine.model.ExtendedInfo;
import com.etl.engine.util.ChainCallLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * ExtendedInfo 验证测试控制器
 * 用于验证扩展信息字段、向后兼容性、链式调用日志等功能
 */
@RestController
@RequestMapping("/mock/test")
@Slf4j
public class ExtendedInfoTestController {

    /**
     * 测试1：验证ExtendedInfo字段完整性
     * 模拟第三方接口调用和SQL执行后的响应
     */
    @GetMapping("/extended-info/success")
    public ResponseEntity<ExecuteResult> testExtendedInfoSuccess() {
        // 初始化全局上下文
        GlobalContext.init("REQ-TEST-001");

        try {
            // 模拟第三方调用
            simulateThirdPartyCall();

            // 模拟SQL执行
            simulateSqlExecution();

            // 构建扩展信息
            ExtendedInfo extendedInfo = GlobalContext.current().buildExtendedInfo();

            // 构建成功结果
            ExecuteResult result = ExecuteResult.success(
                    "ETL-SESSION-001",
                    "result = http(\"http://api.example.com/data\"); users = sql(\"SELECT * FROM users\")",
                    Map.of("status", "ok", "count", 10),
                    new HashMap<>() {{
                        put("result", Map.of("status", "ok"));
                        put("users", new String[]{"user1", "user2"});
                    }},
                    extendedInfo
            );

            return ResponseEntity.ok(result);
        } finally {
            GlobalContext.clear();
        }
    }

    /**
     * 测试2：验证失败场景的ExtendedInfo
     */
    @GetMapping("/extended-info/failure")
    public ResponseEntity<ExecuteResult> testExtendedInfoFailure() {
        GlobalContext.init("REQ-TEST-002");

        try {
            ExtendedInfo extendedInfo = GlobalContext.current().buildExtendedInfo("数据库连接失败");

            ExecuteResult result = ExecuteResult.failure(
                    "ETL-SESSION-002",
                    "result = sql(\"SELECT * FROM invalid_table\")",
                    "表不存在: invalid_table",
                    new HashMap<>(),
                    extendedInfo
            );

            return ResponseEntity.ok(result);
        } finally {
            GlobalContext.clear();
        }
    }

    /**
     * 测试3：验证向后兼容性 - 旧代码调用（不带extended参数）
     */
    @GetMapping("/backward-compat/success")
    public ResponseEntity<ExecuteResult> testBackwardCompatSuccess() {
        // 使用旧版工厂方法（不带extended参数）
        ExecuteResult result = ExecuteResult.success(
                "ETL-SESSION-OLD",
                "result = 1 + 1",
                2,
                Map.of("result", 2)
        );

        // 验证extended为null
        Map<String, Object> response = new HashMap<>();
        response.put("executeResult", result);
        response.put("extendedIsNull", result.extended() == null);
        response.put("sessionId", result.sessionId());
        response.put("success", result.success());
        response.put("finalResult", result.finalResult());

        log.info("向后兼容测试(成功): extended={}, success={}", result.extended(), result.success());
        return ResponseEntity.ok(result);
    }

    /**
     * 测试4：验证向后兼容性 - 旧代码失败场景
     */
    @GetMapping("/backward-compat/failure")
    public ResponseEntity<ExecuteResult> testBackwardCompatFailure() {
        // 使用旧版工厂方法（不带extended参数）
        ExecuteResult result = ExecuteResult.failure(
                "ETL-SESSION-OLD",
                "result = invalid_method()",
                "方法不存在: invalid_method",
                Map.of()
        );

        // 验证extended为null
        Map<String, Object> response = new HashMap<>();
        response.put("executeResult", result);
        response.put("extendedIsNull", result.extended() == null);
        response.put("sessionId", result.sessionId());
        response.put("success", result.success());
        response.put("errorMsg", result.errorMsg());

        log.info("向后兼容测试(失败): extended={}, errorMsg={}", result.extended(), result.errorMsg());
        return ResponseEntity.ok(result);
    }

    /**
     * 测试5：验证新版工厂方法（带extended参数）
     */
    @GetMapping("/new-api/success")
    public ResponseEntity<ExecuteResult> testNewApiSuccess() {
        GlobalContext.init("REQ-TEST-003");

        try {
            ExtendedInfo extendedInfo = ExtendedInfo.builder()
                    .requestId("REQ-TEST-003")
                    .apiStatusCode(200)
                    .executionTimeMs(150L)
                    .thirdPartyStatusCode(200)
                    .sqlAffectedRows(5)
                    .build();

            ExecuteResult result = ExecuteResult.success(
                    "ETL-SESSION-NEW",
                    "result = http(\"http://api.example.com/data\")",
                    Map.of("data", "test"),
                    Map.of("result", Map.of("data", "test")),
                    extendedInfo
            );

            return ResponseEntity.ok(result);
        } finally {
            GlobalContext.clear();
        }
    }

    /**
     * 测试6：验证ChainCallLogger日志记录
     */
    @GetMapping("/chain-logger")
    public ResponseEntity<Map<String, Object>> testChainCallLogger() {
        GlobalContext.init("REQ-TEST-004");

        try {
            // 记录HTTP调用日志
            ChainCallLogger.logHttpCall("http://api.example.com/users", 200, 45);
            ChainCallLogger.logHttpCall("http://api.example.com/posts", 404, 30);

            // 记录SQL执行日志
            ChainCallLogger.logSqlExecution("SELECT * FROM users WHERE id = 1", 1, 20);
            ChainCallLogger.logSqlExecution("UPDATE users SET name = 'test'", 5, 35);

            // 记录表达式执行日志
            ChainCallLogger.logExpressionEval("result = http(...)", 150, true, null);

            // 获取GlobalContext中的数据
            ExtendedInfo extendedInfo = GlobalContext.current().buildExtendedInfo();

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "日志已记录到ChainCallLogger，请查看控制台日志",
                    "extendedInfo", extendedInfo,
                    "note", "thirdPartyStatusCode应为最后一次HTTP调用的状态码(404)",
                    "sqlAffectedRows", "应为最后一次SQL的影响行数(5)"
            );

            return ResponseEntity.ok(response);
        } finally {
            GlobalContext.clear();
        }
    }

    /**
     * 测试7：混合场景 - 第三方调用成功 + SQL执行成功
     */
    @GetMapping("/mixed/success")
    public ResponseEntity<ExecuteResult> testMixedSuccess() {
        GlobalContext.init("REQ-TEST-005");

        try {
            // 模拟完整的ETL流程
            simulateThirdPartyCall();
            simulateSqlExecution();

            ExtendedInfo extendedInfo = GlobalContext.current().buildExtendedInfo();

            ExecuteResult result = ExecuteResult.success(
                    "ETL-SESSION-MIXED",
                    "users = http(\"http://api.example.com/users\"); " +
                            "filtered = sql(\"SELECT * FROM users WHERE active = true\"); " +
                            "count = filtered.size()",
                    100,
                    Map.of(
                            "users", Map.of("total", 100),
                            "filtered", Map.of("total", 80)
                    ),
                    extendedInfo
            );

            return ResponseEntity.ok(result);
        } finally {
            GlobalContext.clear();
        }
    }

    /**
     * 测试8：验证部分字段为null的场景
     */
    @GetMapping("/partial/nulls")
    public ResponseEntity<ExecuteResult> testPartialNulls() {
        GlobalContext.init("REQ-TEST-006");

        try {
            // 只执行SQL，不执行HTTP调用
            simulateSqlExecution();

            ExtendedInfo extendedInfo = GlobalContext.current().buildExtendedInfo();

            ExecuteResult result = ExecuteResult.success(
                    "ETL-SESSION-PARTIAL",
                    "users = sql(\"SELECT * FROM users\")",
                    new String[]{"user1", "user2"},
                    Map.of("users", new String[]{"user1", "user2"}),
                    extendedInfo
            );

            return ResponseEntity.ok(result);
        } finally {
            GlobalContext.clear();
        }
    }

    /**
     * 辅助方法：模拟第三方调用
     */
    private void simulateThirdPartyCall() {
        // 模拟HTTP调用成功
        ChainCallLogger.logHttpCall(
                "http://api.example.com/data",
                200,
                45L
        );
    }

    /**
     * 辅助方法：模拟SQL执行
     */
    private void simulateSqlExecution() {
        // 模拟SQL执行成功
        ChainCallLogger.logSqlExecution(
                "SELECT * FROM users WHERE active = true",
                5,
                30L
        );
    }
}
