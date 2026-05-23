package com.etl.engine.mvel;

import com.etl.engine.config.EngineProperties;
import com.etl.engine.context.EtlContext;
import com.etl.engine.context.EtlContextScope;
import com.etl.engine.model.ExecuteResult;
import com.etl.engine.sql.SqlExecuteEngine;
import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.mvel2.compiler.CompiledExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.lang.ScopedValue;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MvelExpressionEngine {

    private static final Logger logger = LoggerFactory.getLogger(MvelExpressionEngine.class);

    private static final Pattern ASSIGNMENT_PATTERN = Pattern.compile(
        "^\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*(.+)$"
    );

    private static final Pattern VARIABLE_NAME_PATTERN = Pattern.compile(
        "^[a-zA-Z_][a-zA-Z0-9_]*$"
    );

    private final EngineProperties properties;
    private final MvelSecuritySandbox mvelSecuritySandbox;
    private final SqlExecuteEngine sqlExecuteEngine;

    public MvelExpressionEngine(EngineProperties properties,
                               MvelSecuritySandbox mvelSecuritySandbox,
                               SqlExecuteEngine sqlExecuteEngine) {
        this.properties = properties;
        this.mvelSecuritySandbox = mvelSecuritySandbox;
        this.sqlExecuteEngine = sqlExecuteEngine;
        SqlFunction.init(sqlExecuteEngine);
    }

    public ExecuteResult execute(String expression, EtlContext context) {
        if (expression == null || expression.isBlank()) {
            return ExecuteResult.failure(
                context.getSessionId(),
                expression,
                "表达式不能为空",
                context.getAllVariables()
            );
        }

        String trimmedExpression = expression.trim();
        if (trimmedExpression.length() > properties.getMaxExpressionLength()) {
            return ExecuteResult.failure(
                context.getSessionId(),
                expression,
                "表达式长度超过限制: " + properties.getMaxExpressionLength(),
                context.getAllVariables()
            );
        }

        if (!mvelSecuritySandbox.isExpressionSafe(trimmedExpression)) {
            return ExecuteResult.failure(
                context.getSessionId(),
                expression,
                "表达式包含禁止的内容",
                context.getAllVariables()
            );
        }

        try {
            return executeWithTimeout(trimmedExpression, context);
        } catch (Exception e) {
            logger.error("表达式执行异常: sessionId={}, expression={}",
                    context.getSessionId(), expression, e);
            return ExecuteResult.failure(
                context.getSessionId(),
                expression,
                "执行异常: " + e.getMessage(),
                context.getAllVariables()
            );
        }
    }

    private ExecuteResult executeWithTimeout(String expression, EtlContext context) {
        try {
            Future<ExecuteResult> future = Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
                try {
                    return ScopedValue.where(EtlContextScope.CURRENT_CONTEXT, context)
                                      .call(() -> executeInternal(expression, context));
                } catch (Exception e) {
                    logger.error("虚拟线程执行异常: sessionId={}, expression={}",
                            context.getSessionId(), expression, e);
                    throw new RuntimeException(e);
                }
            });

            return future.get(properties.getExpressionTimeout(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logger.warn("表达式执行超时: sessionId={}, expression={}",
                    context.getSessionId(), expression);
            return ExecuteResult.failure(
                context.getSessionId(),
                expression,
                "表达式执行超时, 超过 " + properties.getExpressionTimeout() + "ms",
                context.getAllVariables()
            );
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String errorMsg = cause != null ? cause.getMessage() : e.getMessage();
            logger.error("表达式执行错误: sessionId={}, error={}",
                    context.getSessionId(), errorMsg);
            return ExecuteResult.failure(
                context.getSessionId(),
                expression,
                "执行错误: " + errorMsg,
                context.getAllVariables()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ExecuteResult.failure(
                context.getSessionId(),
                expression,
                "执行被中断",
                context.getAllVariables()
            );
        }
    }

    private ExecuteResult executeInternal(String expression, EtlContext context) {
        String[] lines = expression.split(";\\s*");
        Object lastResult = null;
        Map<String, Object> assignedVariables = new HashMap<>();

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) {
                continue;
            }

            Object result = executeSingleExpression(trimmedLine, context);
            lastResult = result;

            Matcher matcher = ASSIGNMENT_PATTERN.matcher(trimmedLine);
            if (matcher.matches()) {
                String varName = matcher.group(1);
                assignedVariables.put(varName, result);
            }
        }

        return ExecuteResult.success(
            context.getSessionId(),
            expression,
            lastResult,
            context.getAllVariables()
        );
    }

    private Object executeSingleExpression(String expression, EtlContext context) {
        Matcher assignmentMatcher = ASSIGNMENT_PATTERN.matcher(expression);

        if (assignmentMatcher.matches()) {
            String varName = assignmentMatcher.group(1);
            String valueExpression = assignmentMatcher.group(2);

            if (!VARIABLE_NAME_PATTERN.matcher(varName).matches()) {
                throw new IllegalArgumentException("无效的变量名: " + varName);
            }

            Object value = evaluateExpression(valueExpression, context);
            context.setVariable(varName, value);
            return value;
        }

        return evaluateExpression(expression, context);
    }

    private Object evaluateExpression(String expression, EtlContext context) {
        ParserContext parserContext = mvelSecuritySandbox.createSafeParserContext();

        try {
            parserContext.addImport("sql", SqlFunction.class.getMethod("sql", String.class));
            parserContext.addImport("sqlValue", SqlFunction.class.getMethod("sqlValue", String.class));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("SQL函数注册失败", e);
        }

        Map<String, Object> contextMap = new HashMap<>(context.getAllVariables());

        try {
            Serializable compiled = MVEL.compileExpression(expression, parserContext);
            return MVEL.executeExpression(compiled, contextMap);
        } catch (Exception e) {
            throw new IllegalArgumentException("表达式执行错误: " + e.getMessage(), e);
        }
    }
}
