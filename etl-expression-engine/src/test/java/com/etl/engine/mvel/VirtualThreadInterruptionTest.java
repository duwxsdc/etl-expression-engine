package com.etl.engine.mvel;

import com.etl.engine.config.EngineProperties;
import com.etl.engine.context.EtlContext;
import com.etl.engine.model.ExecuteResult;
import com.etl.engine.sql.SqlExecuteEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 虚拟线程中断机制测试
 *
 * <p>测试三层防护机制：</p>
 * <ol>
 *   <li>安全沙箱正则预检 - 拦截明显的无限循环模式</li>
 *   <li>表达式转换 + _ic() 函数 - 在循环体中插入中断检查调用</li>
 *   <li>future.cancel(true) - 超时后设置虚拟线程中断标志</li>
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("虚拟线程中断机制测试")
class VirtualThreadInterruptionTest {

    private MvelExpressionEngine engine;
    private EngineProperties properties;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:data.sql")
                .build();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        SqlExecuteEngine sqlExecutionEngine = new SqlExecuteEngine(jdbcTemplate);

        properties = new EngineProperties();
        properties.setExpressionTimeout(3000L);  // 3秒超时，便于测试
        properties.setInterruptOnTimeout(true);
        properties.setMaxExpressionLength(10000);

        MvelSecuritySandbox sandbox = new MvelSecuritySandbox();
        engine = new MvelExpressionEngine(properties, sandbox, sqlExecutionEngine);
    }

    // ========== 第1层：安全沙箱测试 ==========

    @Test
    @DisplayName("安全沙箱 - 拦截 while(true) 无限循环")
    void testSandboxBlocksWhileTrue() {
        EtlContext context = new EtlContext();
        ExecuteResult result = engine.execute("while(true) { x = 1 }", context);
        assertFalse(result.success());
        assertTrue(result.errorMsg().contains("禁止") || result.errorMsg().contains("危险"));
    }

    @Test
    @DisplayName("安全沙箱 - 拦截 while(1>0) 无限循环")
    void testSandboxBlocksWhileComparison() {
        EtlContext context = new EtlContext();
        ExecuteResult result = engine.execute("while(1>0) { x = 1 }", context);
        assertFalse(result.success());
    }

    @Test
    @DisplayName("安全沙箱 - 拦截 for(;;) 无限循环")
    void testSandboxBlocksForEmpty() {
        EtlContext context = new EtlContext();
        ExecuteResult result = engine.execute("for(;;) { x = 1 }", context);
        assertFalse(result.success());
    }

    @Test
    @DisplayName("安全沙箱 - 拦截 for(;true;) 无限循环")
    void testSandboxBlocksForTrue() {
        EtlContext context = new EtlContext();
        ExecuteResult result = engine.execute("for(;true;) { x = 1 }", context);
        assertFalse(result.success());
    }

    @Test
    @DisplayName("安全沙箱 - 拦截 System 访问")
    void testSandboxBlocksSystemAccess() {
        EtlContext context = new EtlContext();
        ExecuteResult result = engine.execute("System.out.println('hello')", context);
        assertFalse(result.success());
    }

    @Test
    @DisplayName("安全沙箱 - 拦截 Runtime 访问")
    void testSandboxBlocksRuntimeAccess() {
        EtlContext context = new EtlContext();
        ExecuteResult result = engine.execute("Runtime.getRuntime().exec('ls')", context);
        assertFalse(result.success());
    }

    @Test
    @DisplayName("安全沙箱 - 拦截 import 关键字")
    void testSandboxBlocksImport() {
        EtlContext context = new EtlContext();
        ExecuteResult result = engine.execute("import java.io.File", context);
        assertFalse(result.success());
    }

    // ========== 第2层+第3层：表达式转换+超时中断测试 ==========

    @Test
    @DisplayName("正常表达式 - 不受中断检查影响")
    void testNormalExpressionNotAffected() {
        EtlContext context = new EtlContext();
        ExecuteResult result = engine.execute("a = 1 + 2", context);
        assertTrue(result.success());
        assertEquals(3, result.finalResult());
    }

    @Test
    @DisplayName("正常while循环 - 正确执行完成")
    void testNormalWhileLoopExecution() {
        EtlContext context = new EtlContext();
        ExecuteResult result = engine.execute("i = 0; while(i < 10) { i = i + 1 }; i", context);
        assertTrue(result.success());
        assertEquals(10, result.finalResult());
    }

    @Test
    @DisplayName("正常for循环 - 正确执行完成")
    void testNormalForLoopExecution() {
        EtlContext context = new EtlContext();
        ExecuteResult result = engine.execute("sum = 0; for(i = 1; i <= 5; i = i + 1) { sum = sum + i }; sum", context);
        assertTrue(result.success());
        assertEquals(15, result.finalResult());
    }

    @Test
    @DisplayName("链式表达式 - 多条语句正常执行")
    void testChainedExpressionsNormal() {
        EtlContext context = new EtlContext();
        ExecuteResult result = engine.execute("a = 10; b = 20; c = a + b; c * 2", context);
        assertTrue(result.success());
        assertEquals(60, result.finalResult());
    }

    @Test
    @DisplayName("超时中断 - 逻辑无限循环被超时中断")
    void testLogicalInfiniteLoopInterruptedByTimeout() {
        EtlContext context = new EtlContext();
        // i=0; while(i >= 0) { } - i永远>=0且不递增，逻辑上是无限循环
        // 沙箱不会拦截 while(i >= 0)，因为条件不是常量比较
        // 超时后 _ic() 检测到中断标志，抛出异常终止执行
        ExecuteResult result = engine.execute("i = 0; while(i >= 0) { }", context);
        assertFalse(result.success());
        assertTrue(
            result.errorMsg().contains("超时") || result.errorMsg().contains("中断"),
            "期望超时或中断，实际: " + result.errorMsg()
        );
    }

    @Test
    @DisplayName("超时中断 - interruptOnTimeout=false 时不中断线程")
    void testNoInterruptWhenConfiguredOff() {
        properties.setInterruptOnTimeout(false);
        properties.setExpressionTimeout(1000L);

        EtlContext context = new EtlContext();
        ExecuteResult result = engine.execute("i = 0; while(i >= 0) { }", context);
        assertFalse(result.success());
        assertTrue(result.errorMsg().contains("超时"));

        // 恢复配置
        properties.setInterruptOnTimeout(true);
    }

    @Test
    @DisplayName("表达式转换器 - 正确插入_ic()调用")
    void testExpressionTransformerInsertsIcCall() {
        // 验证转换器在循环体中正确插入 _ic() 调用
        String input1 = "while(i < 10) { i = i + 1 }";
        String output1 = MvelInterceptorInjector.injectInterruptChecks(input1);
        assertTrue(output1.contains("_ic();"), "应在while循环体中插入 _ic()");

        String input2 = "for(i = 0; i < 5; i = i + 1) { sum = sum + i }";
        String output2 = MvelInterceptorInjector.injectInterruptChecks(input2);
        assertTrue(output2.contains("_ic();"), "应在for循环体中插入 _ic()");

        String input3 = "do { x = x - 1 } while(x > 0)";
        String output3 = MvelInterceptorInjector.injectInterruptChecks(input3);
        assertTrue(output3.contains("_ic();"), "应在do循环体中插入 _ic()");
    }

    @Test
    @DisplayName("表达式转换器 - 不在字符串内误插入")
    void testExpressionTransformerSkipsStrings() {
        String input = "s = 'while this is a string'; while(i < 10) { i = i + 1 }";
        String output = MvelInterceptorInjector.injectInterruptChecks(input);
        // 只应在真正的while循环中插入，不应在字符串内的"while"处插入
        assertTrue(output.contains("_ic();"));
        // 字符串内容应保持不变
        assertTrue(output.contains("'while this is a string'"));
    }

    @Test
    @DisplayName("表达式转换器 - 无循环表达式不变")
    void testExpressionTransformerNoLoops() {
        String input = "a = 1 + 2; b = a * 3";
        String output = MvelInterceptorInjector.injectInterruptChecks(input);
        assertEquals(input, output, "无循环表达式不应被修改");
    }

    @Test
    @DisplayName("超时配置 - 表达式在超时时间内正常完成")
    void testExpressionCompletesWithinTimeout() {
        properties.setExpressionTimeout(5000L);
        EtlContext context = new EtlContext();
        ExecuteResult result = engine.execute("x = 100; y = x * 2; y", context);
        assertTrue(result.success());
        assertEquals(200, result.finalResult());
    }
}
