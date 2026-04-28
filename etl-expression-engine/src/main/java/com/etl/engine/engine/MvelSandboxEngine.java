package com.etl.engine.engine;

import com.etl.engine.config.EngineProperties;
import com.etl.engine.context.ContextHolder;
import com.etl.engine.context.SessionContext;
import com.etl.engine.model.ExpressionResult;
import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.mvel2.CompileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * MVEL安全沙箱引擎
 * 提供安全的表达式执行环境
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
@Component
public class MvelSandboxEngine {

    private static final Logger logger = LoggerFactory.getLogger(MvelSandboxEngine.class);

    private static final Pattern ASSIGNMENT_PATTERN = Pattern.compile(
        "^\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*(.+)$"
    );

    private static final Pattern VARIABLE_NAME_PATTERN = Pattern.compile(
        "^[a-zA-Z_][a-zA-Z0-9_]*$"
    );

    private static final List<String> FORBIDDEN_PATTERNS = List.of(
        "Runtime", "System", "ProcessBuilder", "Process",
        "File", "FileInputStream", "FileOutputStream", "FileReader", "FileWriter",
        "URL", "URI", "HttpURLConnection", "Socket", "ServerSocket",
        "ClassLoader", "Class#forName", "execute", "exec",
        "getRuntime", "exit", "halt", "shutdown",
        "java.lang.reflect", "Method#invoke", "Constructor#newInstance",
        "Thread", "ThreadGroup", "java.util.concurrent",
        "ScriptEngine", "javax.script",
        "java.sql.DriverManager", "Connection", "Statement"
    );

    private static final List<String> FORBIDDEN_KEYWORDS = List.of(
        "import", "package", "new", "class", "interface", "enum",
        "try", "catch", "finally", "throw", "throws",
        "synchronized", "volatile", "transient", "native", "strictfp"
    );

    private final EngineProperties properties;
    private final ExecutorService executorService;
    private final SqlExecutionEngine sqlExecutionEngine;

    public MvelSandboxEngine(EngineProperties properties, SqlExecutionEngine sqlExecutionEngine) {
        this.properties = properties;
        this.sqlExecutionEngine = sqlExecutionEngine;
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "mvel-executor");
            t.setDaemon(true);
            return t;
        });
    }

    public ExpressionResult execute(String expression, SessionContext sessionContext) {
        if (expression == null || expression.isBlank()) {
            return ExpressionResult.failure(
                sessionContext.getSessionId(),
                expression,
                "表达式不能为空"
            );
        }

        String trimmedExpression = expression.trim();
        if (trimmedExpression.length() > properties.getMaxExpressionLength()) {
            return ExpressionResult.failure(
                sessionContext.getSessionId(),
                expression,
                "表达式长度超过限制: " + properties.getMaxExpressionLength()
            );
        }

        if (!validateExpression(trimmedExpression)) {
            return ExpressionResult.failure(
                sessionContext.getSessionId(),
                expression,
                "表达式包含禁止的内容"
            );
        }

        try {
            return executeWithTimeout(trimmedExpression, sessionContext);
        } catch (Exception e) {
            logger.error("表达式执行异常: sessionId={}, expression={}", 
                    sessionContext.getSessionId(), expression, e);
            return ExpressionResult.failure(
                sessionContext.getSessionId(),
                expression,
                "执行异常: " + e.getMessage()
            );
        }
    }

    private ExpressionResult executeWithTimeout(String expression, SessionContext sessionContext) {
        try {
            Future<ExpressionResult> future = executorService.submit(() -> {
                try {
                    ContextHolder.setContext(sessionContext);
                    return executeInternal(expression, sessionContext);
                } finally {
                    ContextHolder.clearContext();
                }
            });

            return future.get(properties.getExpressionTimeout(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logger.warn("表达式执行超时: sessionId={}, expression={}", 
                    sessionContext.getSessionId(), expression);
            return ExpressionResult.failure(
                sessionContext.getSessionId(),
                expression,
                "表达式执行超时, 超过 " + properties.getExpressionTimeout() + "ms"
            );
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String errorMsg = cause != null ? cause.getMessage() : e.getMessage();
            logger.error("表达式执行错误: sessionId={}, error={}", 
                    sessionContext.getSessionId(), errorMsg);
            return ExpressionResult.failure(
                sessionContext.getSessionId(),
                expression,
                "执行错误: " + errorMsg
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ExpressionResult.failure(
                sessionContext.getSessionId(),
                expression,
                "执行被中断"
            );
        }
    }

    private ExpressionResult executeInternal(String expression, SessionContext sessionContext) {
        String[] lines = expression.split(";\\s*");
        Object lastResult = null;
        Map<String, Object> assignedVariables = new HashMap<>();

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) {
                continue;
            }

            Object result = executeSingleExpression(trimmedLine, sessionContext);
            lastResult = result;

            Matcher matcher = ASSIGNMENT_PATTERN.matcher(trimmedLine);
            if (matcher.matches()) {
                String varName = matcher.group(1);
                assignedVariables.put(varName, result);
            }
        }

        return ExpressionResult.success(
            sessionContext.getSessionId(),
            expression,
            lastResult,
            assignedVariables
        );
    }

    private Object executeSingleExpression(String expression, SessionContext sessionContext) {
        Matcher assignmentMatcher = ASSIGNMENT_PATTERN.matcher(expression);
        
        if (assignmentMatcher.matches()) {
            String varName = assignmentMatcher.group(1);
            String valueExpression = assignmentMatcher.group(2);
            
            if (!VARIABLE_NAME_PATTERN.matcher(varName).matches()) {
                throw new IllegalArgumentException("无效的变量名: " + varName);
            }

            Object value = evaluateExpression(valueExpression, sessionContext);
            sessionContext.setVariable(varName, value);
            return value;
        }

        return evaluateExpression(expression, sessionContext);
    }

    private Object evaluateExpression(String expression, SessionContext sessionContext) {
        if (isSqlQuery(expression)) {
            return executeSqlQuery(expression);
        }

        ParserContext parserContext = createSafeParserContext();
        Map<String, Object> context = new HashMap<>(sessionContext.getAllVariables());

        try {
            Serializable compiled = MVEL.compileExpression(expression, parserContext);
            return MVEL.executeExpression(compiled, context);
        } catch (CompileException e) {
            throw new IllegalArgumentException("表达式编译错误: " + e.getMessage());
        }
    }

    private boolean isSqlQuery(String expression) {
        String upper = expression.trim().toUpperCase();
        return upper.startsWith("SQL:") || upper.startsWith("SELECT ");
    }

    private Object executeSqlQuery(String expression) {
        if (!properties.isEnableSqlExecution()) {
            throw new SecurityException("SQL执行功能已禁用");
        }

        String sql = expression.trim();
        if (sql.toUpperCase().startsWith("SQL:")) {
            sql = sql.substring(4).trim();
        }

        return sqlExecutionEngine.executeQuery(sql);
    }

    private ParserContext createSafeParserContext() {
        ParserContext context = new ParserContext();
        context.setStrictTypeEnforcement(true);
        context.setStrongTyping(false);
        return context;
    }

    private boolean validateExpression(String expression) {
        String upperExpression = expression.toUpperCase();

        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (upperExpression.contains(keyword.toUpperCase())) {
                String regex = "\\b" + keyword.toUpperCase() + "\\b";
                if (Pattern.compile(regex).matcher(upperExpression).find()) {
                    logger.warn("表达式包含禁止的关键字: {}", keyword);
                    return false;
                }
            }
        }

        for (String pattern : FORBIDDEN_PATTERNS) {
            if (upperExpression.contains(pattern.toUpperCase())) {
                logger.warn("表达式包含禁止的模式: {}", pattern);
                return false;
            }
        }

        return true;
    }

    public void shutdown() {
        executorService.shutdown();
        logger.info("MVEL沙箱引擎已关闭");
    }
}
