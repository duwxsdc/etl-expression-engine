package com.etl.engine;

import com.etl.engine.config.EngineProperties;
import com.etl.engine.context.ContextHolder;
import com.etl.engine.context.SessionContext;
import com.etl.engine.engine.MvelSandboxEngine;
import com.etl.engine.engine.SqlExecutionEngine;
import com.etl.engine.model.ExpressionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MVEL沙箱引擎单元测试
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MvelSandboxEngineTest {

    private MvelSandboxEngine engine;
    private SessionContext testContext;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        SqlExecutionEngine sqlEngine = new SqlExecutionEngine(jdbcTemplate);
        
        EngineProperties properties = new EngineProperties(5000L, 10000, true, true);
        engine = new MvelSandboxEngine(properties, sqlEngine);
        
        testContext = new SessionContext("TEST-SESSION");
    }

    @Test
    @DisplayName("测试变量赋值")
    void testVariableAssignment() {
        ExpressionResult result = engine.execute("a=100", testContext);

        assertTrue(result.success());
        assertEquals(100, result.result());
        assertEquals(100, testContext.getVariable("a"));
        assertTrue(result.assignedVariables().containsKey("a"));
    }

    @Test
    @DisplayName("测试字符串变量赋值")
    void testStringVariableAssignment() {
        ExpressionResult result = engine.execute("name=\"etl\"", testContext);

        assertTrue(result.success());
        assertEquals("etl", result.result());
        assertEquals("etl", testContext.getVariable("name"));
    }

    @Test
    @DisplayName("测试布尔变量赋值")
    void testBooleanVariableAssignment() {
        ExpressionResult result = engine.execute("flag=true", testContext);

        assertTrue(result.success());
        assertEquals(true, result.result());
        assertEquals(true, testContext.getVariable("flag"));
    }

    @Test
    @DisplayName("测试表达式运算")
    void testArithmeticExpression() {
        testContext.setVariable("a", 10);
        testContext.setVariable("b", 20);

        ExpressionResult result = engine.execute("a+b", testContext);

        assertTrue(result.success());
        assertEquals(30, result.result());
    }

    @Test
    @DisplayName("测试三元运算")
    void testTernaryExpression() {
        testContext.setVariable("score", 85);

        ExpressionResult result = engine.execute("score >= 60 ? \"pass\" : \"fail\"", testContext);

        assertTrue(result.success());
        assertEquals("pass", result.result());
    }

    @Test
    @DisplayName("测试多行表达式执行")
    void testMultiLineExpression() {
        String expression = "a=10; b=20; c=a+b; c*2";

        ExpressionResult result = engine.execute(expression, testContext);

        assertTrue(result.success());
        assertEquals(60, result.result());
        assertEquals(10, testContext.getVariable("a"));
        assertEquals(20, testContext.getVariable("b"));
        assertEquals(30, testContext.getVariable("c"));
    }

    @Test
    @DisplayName("测试变量跨行引用")
    void testVariableCrossLineReference() {
        engine.execute("x=100", testContext);
        engine.execute("y=x*2", testContext);
        ExpressionResult result = engine.execute("x+y", testContext);

        assertTrue(result.success());
        assertEquals(300, result.result());
    }

    @Test
    @DisplayName("测试对象取值")
    void testObjectPropertyAccess() {
        Map<String, Object> user = new HashMap<>();
        user.put("name", "张三");
        user.put("age", 30);
        testContext.setVariable("user", user);

        ExpressionResult result = engine.execute("user['name']", testContext);

        assertTrue(result.success());
        assertEquals("张三", result.result());
    }

    @Test
    @DisplayName("测试逻辑判断")
    void testLogicalExpression() {
        testContext.setVariable("a", true);
        testContext.setVariable("b", false);

        ExpressionResult result = engine.execute("a && !b", testContext);

        assertTrue(result.success());
        assertEquals(true, result.result());
    }

    @Test
    @DisplayName("测试空表达式")
    void testEmptyExpression() {
        ExpressionResult result = engine.execute("", testContext);

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    @DisplayName("测试禁止的关键字")
    void testForbiddenKeyword() {
        ExpressionResult result = engine.execute("import java.io.File", testContext);

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("禁止"));
    }

    @Test
    @DisplayName("测试禁止的危险类")
    void testForbiddenClass() {
        ExpressionResult result = engine.execute("Runtime.getRuntime()", testContext);

        assertFalse(result.success());
    }

    @Test
    @DisplayName("测试超时限制")
    void testTimeout() {
        EngineProperties shortTimeoutProps = new EngineProperties(100L, 10000, true, true);
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        SqlExecutionEngine sqlEngine = new SqlExecutionEngine(jdbcTemplate);
        MvelSandboxEngine shortTimeoutEngine = new MvelSandboxEngine(shortTimeoutProps, sqlEngine);
        SessionContext timeoutContext = new SessionContext("TIMEOUT-TEST");

        ExpressionResult result = shortTimeoutEngine.execute("a=1; while(a<100000000) { a=a+1 }; a", timeoutContext);

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("超时"));
    }

    @Test
    @DisplayName("测试语法错误")
    void testSyntaxError() {
        ExpressionResult result = engine.execute("a+++b", testContext);

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    @DisplayName("测试除零-返回Infinity")
    void testDivisionByZero() {
        ExpressionResult result = engine.execute("10/0.0", testContext);

        assertTrue(result.success());
        assertEquals(Double.POSITIVE_INFINITY, result.result());
    }

    @Test
    @DisplayName("测试复杂表达式")
    void testComplexExpression() {
        String expression = "a = 10; b = 20; c = a > b ? a : b; d = c * 2 + 5; d";
        
        ExpressionResult result = engine.execute(expression, testContext);

        assertTrue(result.success());
        assertEquals(45, result.result());
    }
}
