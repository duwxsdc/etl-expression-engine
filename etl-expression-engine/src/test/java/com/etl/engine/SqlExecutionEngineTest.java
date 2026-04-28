package com.etl.engine;

import com.etl.engine.engine.SqlExecutionEngine;
import com.etl.engine.model.SqlQueryResult;
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
 * SQL执行引擎单元测试
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqlExecutionEngineTest {

    private SqlExecutionEngine engine;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        engine = new SqlExecutionEngine(jdbcTemplate);
    }

    @Test
    @DisplayName("测试合法SELECT查询")
    void testValidSelectQuery() {
        SqlQueryResult result = engine.executeQuery("SELECT 1 AS test_value");

        assertTrue(result.success());
        assertEquals(1, result.rowCount());
    }

    @Test
    @DisplayName("测试空SQL")
    void testEmptySql() {
        SqlQueryResult result = engine.executeQuery("");

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("不能为空"));
    }

    @Test
    @DisplayName("测试INSERT语句被拦截")
    void testInsertBlocked() {
        SqlQueryResult result = engine.executeQuery("INSERT INTO test VALUES (1)");

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("验证失败"));
    }

    @Test
    @DisplayName("测试UPDATE语句被拦截")
    void testUpdateBlocked() {
        SqlQueryResult result = engine.executeQuery("UPDATE test SET a=1");

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("验证失败"));
    }

    @Test
    @DisplayName("测试DELETE语句被拦截")
    void testDeleteBlocked() {
        SqlQueryResult result = engine.executeQuery("DELETE FROM test");

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("验证失败"));
    }

    @Test
    @DisplayName("测试DROP语句被拦截")
    void testDropBlocked() {
        SqlQueryResult result = engine.executeQuery("DROP TABLE test");

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("验证失败"));
    }

    @Test
    @DisplayName("测试非SELECT语句被拦截")
    void testNonSelectBlocked() {
        SqlQueryResult result = engine.executeQuery("CREATE TABLE test_table (id INT)");

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("SELECT") || result.errorMessage().contains("验证失败"));
    }

    @Test
    @DisplayName("测试SQL注入攻击被拦截")
    void testSqlInjectionBlocked() {
        SqlQueryResult result = engine.executeQuery("SELECT * FROM test WHERE id=1 OR 1=1");

        assertFalse(result.success());
    }

    @Test
    @DisplayName("测试数据库连接")
    void testConnection() {
        boolean connected = engine.testConnection();
        assertTrue(connected);
    }
}
