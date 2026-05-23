package com.etl.engine.mvel;

import org.mvel2.ParserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class MvelSecuritySandbox {

    private static final Logger logger = LoggerFactory.getLogger(MvelSecuritySandbox.class);

    private static final List<String> FORBIDDEN_CLASSES = List.of(
        "Runtime", "System", "ProcessBuilder", "Process",
        "FileInputStream", "FileOutputStream", "FileReader", "FileWriter",
        "HttpURLConnection", "Socket", "ServerSocket",
        "ClassLoader", "getRuntime", "exit", "halt",
        "java.lang.reflect", "Method#invoke", "Constructor#newInstance",
        "ScriptEngine", "javax.script",
        "java.sql.DriverManager",
        "java.io", "java.net", "java.nio.file"
    );

    private static final List<String> FORBIDDEN_KEYWORDS = List.of(
        "import", "package", "class", "interface", "enum",
        "try", "catch", "finally", "throw", "throws",
        "synchronized", "volatile", "transient", "native", "strictfp"
    );

    public ParserContext createSafeParserContext() {
        ParserContext context = new ParserContext();
        context.setStrictTypeEnforcement(true);
        context.setStrongTyping(false);
        return context;
    }

    public boolean isExpressionSafe(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return false;
        }

        String upperExpression = expression.toUpperCase().trim();

        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (upperExpression.contains(keyword.toUpperCase())) {
                String regex = "\\b" + keyword.toUpperCase() + "\\b";
                if (Pattern.compile(regex).matcher(upperExpression).find()) {
                    logger.warn("表达式包含禁止的关键字: {}", keyword);
                    return false;
                }
            }
        }

        for (String forbiddenClass : FORBIDDEN_CLASSES) {
            if (upperExpression.contains(forbiddenClass.toUpperCase())) {
                logger.warn("表达式包含禁止的类名: {}", forbiddenClass);
                return false;
            }
        }

        return true;
    }
}
