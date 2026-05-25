package com.etl.engine.mvel;

import com.etl.engine.config.EngineProperties;
import com.etl.engine.context.EtlContext;
import com.etl.engine.context.EtlContextScope;
import com.etl.engine.model.ExecuteResult;
import com.etl.engine.rest.MvelRestClient;
import com.etl.engine.rest.MvelRestClientBuilder;
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
        HttpFunction.init();
        logger.info("MVEL表达式引擎初始化完成, SQL函数和HTTP函数已注册");
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
            parserContext.addImport("httpRequest", HttpFunction.class.getMethod("httpRequest", String.class));
            parserContext.addImport("http", HttpFunction.class.getMethod("http", String.class));

            parserContext.addImport("RestClient", MvelRestClient.class);
            parserContext.addInput("RestClient", MvelRestClient.class);

            parserContext.addImport("MvelRestClientBuilder", MvelRestClientBuilder.class);
            parserContext.addImport("AsyncResult", MvelRestClientBuilder.AsyncResult.class);

            parserContext.addImport("restGet", MvelRestClient.class.getMethod("get", String.class));
            parserContext.addImport("restPost", MvelRestClient.class.getMethod("post", String.class));
            parserContext.addImport("restPut", MvelRestClient.class.getMethod("put", String.class));
            parserContext.addImport("restDelete", MvelRestClient.class.getMethod("delete", String.class));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("函数注册失败", e);
        }

        Map<String, Object> contextMap = new HashMap<>(context.getAllVariables());

        try {
            logger.debug("MVEL编译表达式: 长度={}, 预览={}", 
                    expression.length(), 
                    expression.length() > 100 ? expression.substring(0, 100) + "..." : expression);
            
            Serializable compiled = MVEL.compileExpression(expression, parserContext);
            
            logger.debug("MVEL执行表达式: 编译成功, 上下文变量数={}", contextMap.size());
            
            Object result = MVEL.executeExpression(compiled, contextMap);
            
            logger.debug("MVEL执行完成: 结果类型={}, 结果={}", 
                    result != null ? result.getClass().getName() : "null",
                    result);
            
            return result;
        } catch (Exception e) {
            logger.error("MVEL执行失败: 表达式={}, 错误类型={}, 错误信息={}", 
                    expression.length() > 200 ? expression.substring(0, 200) + "..." : expression,
                    e.getClass().getName(),
                    e.getMessage());
            
            if (e.getCause() != null) {
                logger.error("MVEL执行失败-根因: 类型={}, 信息={}", 
                        e.getCause().getClass().getName(),
                        e.getCause().getMessage());
            }
            
            throw new IllegalArgumentException("表达式执行错误: " + e.getMessage(), e);
        }
    }
}
