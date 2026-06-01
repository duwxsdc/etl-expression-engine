package com.etl.engine.util;

import com.etl.engine.context.GlobalContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 链式调用日志器
 * 记录HTTP调用、SQL执行等关键节点信息，调用链信息仅打印在日志中
 */
@Slf4j
public final class ChainCallLogger {

    private ChainCallLogger() {
    }

    /**
     * 记录HTTP调用
     *
     * @param url        请求URL
     * @param statusCode HTTP响应状态码
     * @param durationMs 耗时（毫秒）
     */
    public static void logHttpCall(String url, int statusCode, long durationMs) {
        GlobalContext ctx = GlobalContext.current();
        if (ctx != null) {
            ctx.setThirdPartyStatusCode(statusCode);
        }

        log.info("[ChainCallLogger] HTTP调用: url={}, status={}, duration={}ms",
                url, statusCode, durationMs);
    }

    /**
     * 记录SQL执行
     *
     * @param sql          SQL语句
     * @param affectedRows 影响行数
     * @param durationMs   耗时（毫秒）
     */
    public static void logSqlExecution(String sql, int affectedRows, long durationMs) {
        GlobalContext ctx = GlobalContext.current();
        if (ctx != null) {
            ctx.setSqlAffectedRows(affectedRows);
        }

        log.info("[ChainCallLogger] SQL执行: sql={}, affectedRows={}, duration={}ms",
                truncate(sql, 50), affectedRows, durationMs);
    }

    /**
     * 记录表达式执行
     *
     * @param expression 表达式
     * @param durationMs 耗时（毫秒）
     * @param success    是否成功
     * @param error      错误信息
     */
    public static void logExpressionEval(String expression, long durationMs,
                                         boolean success, String error) {
        log.info("[ChainCallLogger] 表达式执行: expression={}, duration={}ms, status={}",
                truncate(expression, 50), durationMs, success ? "SUCCESS" : "FAILURE");

        if (!success && error != null) {
            log.error("[ChainCallLogger] 表达式执行失败: {}", error);
        }
    }

    private static String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }
}
