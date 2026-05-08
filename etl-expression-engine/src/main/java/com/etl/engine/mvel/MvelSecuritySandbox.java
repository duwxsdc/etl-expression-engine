package com.etl.engine.mvel;

import org.mvel2.ParserContext;
import org.mvel2.compiler.CompiledExpression;
import org.mvel2.compiler.ExpressionCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * MVEL安全沙箱
 * 增强的安全限制，禁用系统高危类、文件/进程/网络API
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
@Component
public class MvelSecuritySandbox {
    
    private static final Logger logger = LoggerFactory.getLogger(MvelSecuritySandbox.class);
    
    private static final List<String> FORBIDDEN_CLASSES = List.of(
        "Runtime", "System", "ProcessBuilder", "Process",
        "File", "FileInputStream", "FileOutputStream", "FileReader", "FileWriter",
        "URL", "URI", "HttpURLConnection", "Socket", "ServerSocket",
        "ClassLoader", "Class#forName", "execute", "exec",
        "getRuntime", "exit", "halt", "shutdown",
        "java.lang.reflect", "Method#invoke", "Constructor#newInstance",
        "Thread", "ThreadGroup", "java.util.concurrent",
        "ScriptEngine", "javax.script",
        "java.sql.DriverManager", "Connection", "Statement",
        "java.io", "java.net", "java.nio.file"
    );
    
    private static final List<String> FORBIDDEN_METHODS = List.of(
        "exec", "execute", "getRuntime", "exit", "halt", "shutdown",
        "openStream", "connect", "bind", "listen", "accept",
        "read", "write", "delete", "mkdir", "listFiles",
        "loadClass", "forName", "newInstance", "invoke"
    );
    
    private static final List<String> FORBIDDEN_KEYWORDS = List.of(
        "import", "package", "new", "class", "interface", "enum",
        "try", "catch", "finally", "throw", "throws",
        "synchronized", "volatile", "transient", "native", "strictfp"
    );
    
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "('|(\\-\\-)|(;)|(\\|\\|)|(\\*/)|(\\/\\*)|(\\bOR\\b)|(\\bAND\\b.*\\=)|(\\bUNION\\b)|(\\bINTO\\b)|(\\bOUTFILE\\b))",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * 创建安全的ParserContext
     * @return 安全的ParserContext
     */
    public ParserContext createSafeParserContext() {
        ParserContext context = new ParserContext();
        context.setStrictTypeEnforcement(true);
        context.setStrongTyping(false);
        return context;
    }
    
    /**
     * 编译表达式
     * @param expression 表达式
     * @return 编译后的表达式
     */
    public CompiledExpression compileExpression(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        
        String upperExpression = expression.toUpperCase().trim();
        
        // 检查禁止的关键字
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (upperExpression.contains(keyword.toUpperCase())) {
                String regex = "\\b" + keyword.toUpperCase() + "\\b";
                if (Pattern.compile(regex).matcher(upperExpression).find()) {
                    logger.warn("表达式包含禁止的关键字: {}", keyword);
                    throw new SecurityException("表达式包含禁止的关键字: " + keyword);
                }
            }
        }
        
        // 检查禁止的类名
        for (String forbiddenClass : FORBIDDEN_CLASSES) {
            if (upperExpression.contains(forbiddenClass.toUpperCase())) {
                logger.warn("表达式包含禁止的类名: {}", forbiddenClass);
                throw new SecurityException("表达式包含禁止的类名: " + forbiddenClass);
            }
        }
        
        // 检查SQL注入模式
        if (SQL_INJECTION_PATTERN.matcher(expression).find()) {
            logger.warn("表达式可能包含SQL注入攻击");
            throw new SecurityException("表达式可能包含SQL注入攻击");
        }
        
        try {
            ExpressionCompiler compiler = new ExpressionCompiler(expression, createSafeParserContext());
            return (CompiledExpression) compiler.compile();
        } catch (Exception e) {
            logger.error("表达式编译失败: {}", expression, e);
            throw new IllegalArgumentException("表达式编译失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 验证表达式是否安全
     * @param expression 表达式
     * @return 是否安全
     */
    public boolean isExpressionSafe(String expression) {
        try {
            compileExpression(expression);
            return true;
        } catch (SecurityException | IllegalArgumentException e) {
            logger.debug("表达式不安全: {} - {}", expression, e.getMessage());
            return false;
        }
    }
}
