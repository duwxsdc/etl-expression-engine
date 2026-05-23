package com.etl.engine;

import com.etl.engine.config.EngineProperties;
import com.etl.engine.context.SessionContext;
import com.etl.engine.engine.MvelSandboxEngine;
import com.etl.engine.sql.SqlExecuteEngine;
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
@DisplayName("SQL函数注册单元测试")
class SqlFunctionTest {

    private MvelSandboxEngine engine;
    private SessionContext testContext;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:data.sql")
                .build();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        SqlExecuteEngine sqlEngine = new SqlExecuteEngine(jdbcTemplate);

        EngineProperties properties = new EngineProperties(5000L, 10000, true, true);
        engine = new MvelSandboxEngine(properties, sqlEngine);

        testContext = new SessionContext("SQL-FUNC-TEST");
    }

    @Test
    @DisplayName("测试sql()函数基本查询")
    void testSqlFunctionBasicQuery() {
        ExpressionResult result = engine.execute("sql(\"SELECT 1 AS test_value\")", testContext);
        assertTrue(result.success());
        assertNotNull(result.result());
    }

    @Test
    @DisplayName("测试sql()函数查询数据表")
    void testSqlFunctionTableQuery() {
        ExpressionResult result = engine.execute("sql(\"SELECT * FROM etl_config\")", testContext);
        assertTrue(result.success());
        assertNotNull(result.result());
    }

    @Test
    @DisplayName("测试sql()函数结果赋值给变量")
    void testSqlFunctionAssignment() {
        ExpressionResult result = engine.execute("configData = sql(\"SELECT * FROM etl_config\")", testContext);
        assertTrue(result.success());
        assertTrue(testContext.hasVariable("configData"));
        assertNotNull(testContext.getVariable("configData"));
    }

    @Test
    @DisplayName("测试sqlValue()函数获取单值")
    void testSqlValueFunction() {
        ExpressionResult result = engine.execute("sqlValue(\"SELECT count(*) FROM etl_config\")", testContext);
        assertTrue(result.success());
        assertNotNull(result.result());
    }

    @Test
    @DisplayName("测试sqlValue()函数结果赋值")
    void testSqlValueFunctionAssignment() {
        ExpressionResult result = engine.execute("total = sqlValue(\"SELECT count(*) FROM etl_config\")", testContext);
        assertTrue(result.success());
        assertTrue(testContext.hasVariable("total"));
    }

    @Test
    @DisplayName("测试sql()函数与表达式组合使用")
    void testSqlFunctionWithExpression() {
        ExpressionResult result = engine.execute("x=10; data=sql(\"SELECT 1 AS val\"); x+1", testContext);
        assertTrue(result.success());
        assertEquals(11, result.result());
        assertTrue(testContext.hasVariable("x"));
        assertTrue(testContext.hasVariable("data"));
    }

    @Test
    @DisplayName("测试sql()函数拒绝非SELECT语句")
    void testSqlFunctionRejectsDml() {
        ExpressionResult result = engine.execute("sql(\"INSERT INTO etl_config VALUES (99, 'k', 'v', 'd')\")", testContext);
        assertFalse(result.success());
    }

    @Test
    @DisplayName("测试sql()函数拒绝DROP语句")
    void testSqlFunctionRejectsDrop() {
        ExpressionResult result = engine.execute("sql(\"DROP TABLE etl_config\")", testContext);
        assertFalse(result.success());
    }

    @Test
    @DisplayName("测试sql()函数带WHERE条件查询")
    void testSqlFunctionWithWhere() {
        ExpressionResult result = engine.execute("sql(\"SELECT config_key FROM etl_config WHERE id > 0\")", testContext);
        assertTrue(result.success());
    }

    @Test
    @DisplayName("测试sqlValue()函数与算术运算组合")
    void testSqlValueWithArithmetic() {
        ExpressionResult result = engine.execute("cnt = sqlValue(\"SELECT count(*) FROM etl_config\"); cnt * 2", testContext);
        assertTrue(result.success());
        assertTrue(testContext.hasVariable("cnt"));
    }
}
