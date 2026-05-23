package com.etl.engine;

import com.etl.engine.config.EngineProperties;
import com.etl.engine.context.EtlContext;
import com.etl.engine.context.EtlContextManager;
import com.etl.engine.context.SessionContext;
import com.etl.engine.context.SessionContextManager;
import com.etl.engine.engine.MvelSandboxEngine;
import com.etl.engine.model.ExpressionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("MVEL表达式引擎集成测试")
class MvelExpressionEngineIntegrationTest {

    private MvelSandboxEngine engine;
    private SessionContextManager sessionManager;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:data.sql")
                .build();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        com.etl.engine.sql.SqlExecuteEngine sqlExecutionEngine =
                new com.etl.engine.sql.SqlExecuteEngine(jdbcTemplate);

        EngineProperties properties = new EngineProperties(5000L, 10000, true, true);
        engine = new MvelSandboxEngine(properties, sqlExecutionEngine);

        sessionManager = new SessionContextManager();
    }

    @Test
    @DisplayName("集成测试 - 完整会话生命周期")
    void testFullSessionLifecycle() {
        SessionContext context = sessionManager.createSession();
        assertNotNull(context);
        assertNotNull(context.getSessionId());

        ExpressionResult result1 = engine.execute("a=100", context);
        assertTrue(result1.success());
        assertEquals(100, result1.result());

        ExpressionResult result2 = engine.execute("b=a+50", context);
        assertTrue(result2.success());
        assertEquals(150, result2.result());

        ExpressionResult result3 = engine.execute("a+b", context);
        assertTrue(result3.success());
        assertEquals(250, result3.result());

        sessionManager.destroySession(context.getSessionId());
        assertNull(sessionManager.getSession(context.getSessionId()));
    }

    @Test
    @DisplayName("集成测试 - 多会话隔离")
    void testMultiSessionIsolation() {
        SessionContext session1 = sessionManager.createSession();
        SessionContext session2 = sessionManager.createSession();

        engine.execute("x=100", session1);
        engine.execute("x=200", session2);

        ExpressionResult result1 = engine.execute("x", session1);
        ExpressionResult result2 = engine.execute("x", session2);

        assertEquals(100, result1.result());
        assertEquals(200, result2.result());

        sessionManager.destroySession(session1.getSessionId());
        sessionManager.destroySession(session2.getSessionId());
    }

    @Test
    @DisplayName("集成测试 - SQL函数与表达式混合执行")
    void testSqlFunctionWithExpressions() {
        SessionContext context = sessionManager.createSession();

        ExpressionResult result = engine.execute("threshold=2; cnt=sqlValue(\"SELECT count(*) FROM etl_config\"); cnt", context);
        assertTrue(result.success());

        sessionManager.destroySession(context.getSessionId());
    }

    @Test
    @DisplayName("集成测试 - 复杂表达式链式执行")
    void testChainedExpressionExecution() {
        SessionContext context = sessionManager.createSession();

        String complexExpr = "a=10; b=20; c=a+b; d=c*2; flag=d>50 ? \"big\" : \"small\"; flag";
        ExpressionResult result = engine.execute(complexExpr, context);

        assertTrue(result.success());
        assertEquals("big", result.result());

        sessionManager.destroySession(context.getSessionId());
    }

    @Test
    @DisplayName("集成测试 - 错误恢复：表达式错误后上下文仍可用")
    void testErrorRecovery() {
        SessionContext context = sessionManager.createSession();

        engine.execute("a=100", context);

        ExpressionResult badResult = engine.execute("a+++b", context);
        assertFalse(badResult.success());

        ExpressionResult goodResult = engine.execute("a*2", context);
        assertTrue(goodResult.success());
        assertEquals(200, goodResult.result());

        sessionManager.destroySession(context.getSessionId());
    }

    @Test
    @DisplayName("集成测试 - 安全沙箱拦截")
    void testSecuritySandboxIntegration() {
        SessionContext context = sessionManager.createSession();

        ExpressionResult runtimeResult = engine.execute("Runtime.getRuntime()", context);
        assertFalse(runtimeResult.success());

        ExpressionResult importResult = engine.execute("import java.io.File", context);
        assertFalse(importResult.success());

        ExpressionResult normalResult = engine.execute("1+1", context);
        assertTrue(normalResult.success());

        sessionManager.destroySession(context.getSessionId());
    }
}
