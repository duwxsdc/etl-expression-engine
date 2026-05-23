package com.etl.engine.mvel;

import com.etl.engine.sql.SqlExecuteEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class SqlFunction {

    private static final Logger logger = LoggerFactory.getLogger(SqlFunction.class);

    private static SqlExecuteEngine sqlExecuteEngine;

    private SqlFunction() {
    }

    public static void init(SqlExecuteEngine engine) {
        sqlExecuteEngine = engine;
    }

    public static List<Map<String, Object>> sql(String sqlExpression) {
        if (sqlExecuteEngine == null) {
            throw new IllegalStateException("SQL执行引擎未初始化");
        }
        logger.debug("MVEL SQL函数调用: {}", sqlExpression);
        Object result = sqlExecuteEngine.executeQuery(sqlExpression);
        if (result instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) result;
            return list;
        }
        throw new RuntimeException("SQL查询返回了非预期类型: " + (result != null ? result.getClass().getName() : "null"));
    }

    public static Object sqlValue(String sqlExpression) {
        if (sqlExecuteEngine == null) {
            throw new IllegalStateException("SQL执行引擎未初始化");
        }
        logger.debug("MVEL SQL单值函数调用: {}", sqlExpression);
        Object result = sqlExecuteEngine.executeQuery(sqlExpression);
        if (result instanceof List<?> list) {
            if (list.isEmpty()) {
                return null;
            }
            Map<String, Object> firstRow = (Map<String, Object>) list.getFirst();
            if (firstRow.isEmpty()) {
                return null;
            }
            return firstRow.values().iterator().next();
        }
        return result;
    }
}
