package com.etl.engine.engine;

import com.etl.engine.model.SqlQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * SQL执行引擎
 * 提供安全的只读SQL查询功能
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
@Component
public class SqlExecutionEngine {

    private static final Logger logger = LoggerFactory.getLogger(SqlExecutionEngine.class);

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
        "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE", 
        "TRUNCATE", "REPLACE", "MERGE", "GRANT", "REVOKE",
        "EXEC", "EXECUTE", "CALL", "INTO"
    );

    private static final Pattern SQL_INJECTION_PATTERNS = Pattern.compile(
        "('|(\\-\\-)|(;)|(\\|\\|)|(\\*/)|(\\/\\*)|(\\bOR\\b)|(\\bAND\\b.*\\=)|(\\bUNION\\b)|(\\bINTO\\b)|(\\bOUTFILE\\b))",
        Pattern.CASE_INSENSITIVE
    );

    private final JdbcTemplate jdbcTemplate;

    public SqlExecutionEngine(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SqlQueryResult executeQuery(String sql) {
        if (sql == null || sql.isBlank()) {
            return SqlQueryResult.failure("SQL语句不能为空");
        }

        String trimmedSql = sql.trim();

        if (!validateSql(trimmedSql)) {
            return SqlQueryResult.failure("SQL语句验证失败: 包含禁止的操作");
        }

        try {
            logger.info("执行SQL查询: {}", trimmedSql);
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(trimmedSql);
            
            logger.info("SQL查询完成, 返回 {} 行数据", results.size());
            return SqlQueryResult.success(results, results.size());
            
        } catch (Exception e) {
            logger.error("SQL执行错误: {}", e.getMessage(), e);
            return SqlQueryResult.failure("SQL执行错误: " + e.getMessage());
        }
    }

    private boolean validateSql(String sql) {
        String upperSql = sql.toUpperCase().trim();

        if (!upperSql.startsWith("SELECT")) {
            logger.warn("SQL验证失败: 只允许SELECT查询语句");
            return false;
        }

        for (String keyword : FORBIDDEN_KEYWORDS) {
            String regex = "\\b" + keyword + "\\b";
            if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(sql).find()) {
                logger.warn("SQL验证失败: 包含禁止的关键字 {}", keyword);
                return false;
            }
        }

        if (SQL_INJECTION_PATTERNS.matcher(sql).find()) {
            logger.warn("SQL验证失败: 可能包含SQL注入攻击");
            return false;
        }

        return true;
    }

    public boolean testConnection() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            logger.error("数据库连接测试失败", e);
            return false;
        }
    }
}
