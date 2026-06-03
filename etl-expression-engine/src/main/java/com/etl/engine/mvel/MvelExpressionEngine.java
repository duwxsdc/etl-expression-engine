package com.etl.engine.mvel;

import com.etl.engine.config.EngineProperties;
import com.etl.engine.context.EtlContext;
import com.etl.engine.context.EtlContextScope;
import com.etl.engine.context.GlobalContext;
import com.etl.engine.model.ExecuteResult;
import com.etl.engine.model.ExtendedInfo;
import com.etl.engine.rest.ext.ExtRestClient;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        logger.info("MVEL表达式引擎初始化完成, SQL函数已注册");
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
        // Capture GlobalContext requestId for virtual thread propagation
        GlobalContext mainCtx = GlobalContext.current();
        String capturedRequestId = mainCtx != null ? mainCtx.getRequestId() : null;
        
        try {
            Future<ExecuteResult> future = Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
                try {
                    // Set GlobalContext in virtual thread with same requestId
                    if (capturedRequestId != null) {
                        GlobalContext.init(capturedRequestId);
                    }
                    
                    return ScopedValue.where(EtlContextScope.CURRENT_CONTEXT, context)
                                      .call(() -> executeInternal(expression, context));
                } catch (Exception e) {
                    logger.error("虚拟线程执行异常: sessionId={}, expression={}",
                            context.getSessionId(), expression, e);
                    throw new RuntimeException(e);
                } finally {
                    // Clean up virtual thread's GlobalContext
                    GlobalContext.clear();
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
        List<String> lines = splitExpression(expression);
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

            parserContext.addImport("RestClient", ExtRestClient.class);
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

    /**
     * 智能分割表达式，正确处理字符串字面量、单引号、双引号、注释等边界
     *
     * @param expression 原始表达式
     * @return 分割后的表达式列表
     */
    private static List<String> splitExpression(String expression) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;      // 单引号字符串内
        boolean inDoubleQuote = false;      // 双引号字符串内
        boolean inLineComment = false;      // 行注释内 //
        boolean inBlockComment = false;     // 块注释内 /* */
        char prevChar = '\0';
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            // 处理块注释结束
            if (inBlockComment) {
                if (prevChar == '*' && c == '/') {
                    inBlockComment = false;
                    prevChar = '\0';
                    continue;
                }
                prevChar = c;
                continue;
            }
            // 处理行注释结束（遇到换行）
            if (inLineComment) {
                if (c == '\n' || c == '\r') {
                    inLineComment = false;
                }
                continue;
            }
            // 处理转义字符
            if (prevChar == '\\') {
                current.append(c);
                prevChar = '\0';
                continue;
            }
            // 检查注释开始（只有不在字符串内才处理）
            if (!inSingleQuote && !inDoubleQuote) {
                if (prevChar == '/' && c == '/') {
                    if (current.length() > 0) {
                        current.deleteCharAt(current.length() - 1);
                    }
                    inLineComment = true;
                    prevChar = '\0';
                    continue;
                }
                if (prevChar == '/' && c == '*') {
                    if (current.length() > 0) {
                        current.deleteCharAt(current.length() - 1);
                    }
                    inBlockComment = true;
                    prevChar = '\0';
                    continue;
                }
            }
            // 处理引号切换
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                current.append(c);
                prevChar = c;
                continue;
            }
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                current.append(c);
                prevChar = c;
                continue;
            }
            // 只有不在字符串内时才按分号分割
            if (c == ';' && !inSingleQuote && !inDoubleQuote) {
                String segment = current.toString().trim();
                if (!segment.isEmpty()) {
                    result.add(segment);
                }
                current.setLength(0);
                prevChar = '\0';
                continue;
            }
            current.append(c);
            prevChar = c;
        }
        // 添加最后一段
        String segment = current.toString().trim();
        if (!segment.isEmpty()) {
            result.add(segment);
        }
        return result;
    }
}
