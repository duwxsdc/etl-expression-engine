package com.etl.engine.sql;

import com.etl.engine.model.SqlQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SQL执行引擎
 * 只读SQL执行引擎，黑名单拦截高危语句
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
@Component
public class SqlExecuteEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(SqlExecuteEngine.class);
    
    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
        "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE", 
        "TRUNCATE", "REPLACE", "MERGE", "GRANT", "REVOKE",
        "EXEC", "EXECUTE", "CALL", "INTO", "SET"
    );
    
    private static final Pattern SQL_INJECTION_PATTERNS = Pattern.compile(
        "('|(\\-\\-)|(;)|(\\|\\|)|(\\*/)|(\\/\\*)|(\\bOR\\b)|(\\bAND\\b.*\\=)|(\\bUNION\\b)|(\\bINTO\\b)|(\\bOUTFILE\\b))",
        Pattern.CASE_INSENSITIVE
    );
    
    private final JdbcTemplate jdbcTemplate;
    
    public SqlExecuteEngine(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * 执行SQL查询
     * @param sql SQL语句
     * @return 查询结果
     */
    public Object executeQuery(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL语句不能为空");
        }
        
        String trimmedSql = sql.trim();
        
        if (!validateSql(trimmedSql)) {
            throw new SecurityException("SQL语句验证失败: 包含禁止的操作");
        }
        
        try {
            logger.info("执行SQL查询: {}", trimmedSql);
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(trimmedSql);
            
            logger.info("SQL查询完成, 返回 {} 行数据", results.size());
            return results;
        } catch (Exception e) {
            logger.error("SQL执行错误: {}", e.getMessage(), e);
            throw new RuntimeException("SQL执行错误: " + e.getMessage(), e);
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
}
