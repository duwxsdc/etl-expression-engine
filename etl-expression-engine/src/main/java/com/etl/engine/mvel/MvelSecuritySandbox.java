package com.etl.engine.mvel;

import org.mvel2.ParserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
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
        "java.io", "java.net", "java.nio.file",
        "Unsafe", "sun.misc", "jdk.internal"
    );

    private static final List<String> FORBIDDEN_KEYWORDS = List.of(
        "import", "package", "interface", "enum",
        "try", "catch", "finally", "throw", "throws",
        "synchronized", "volatile", "transient", "native", "strictfp"
    );
    
    private static final List<String> DANGEROUS_PATTERNS = List.of(
        "\\bclass\\s+[A-Z]",
        "while\\s*\\(\\s*true\\s*\\)",
        "for\\s*\\(\\s*;\\s*;\\s*\\)",
        "for\\s*\\(\\s*;\\s*;\\s*\\)",
        "while\\s*\\(.*\\)\\s*\\{\\s*\\}",
        "\\.wait\\s*\\(",
        "\\.notify\\s*\\(",
        "\\.notifyAll\\s*\\(",
        "Thread\\.sleep\\s*\\(\\s*[0-9]{7,}\\s*\\)",
        "System\\.gc\\s*\\(",
        "System\\.runFinalization\\s*\\(",
        "Runtime\\.getRuntime",
        "System\\.exit",
        "System\\.halt",
        "System\\.load",
        "System\\.loadLibrary",
        "exec\\s*\\(",
        "execute\\s*\\(.*\"*cmd",
        "execute\\s*\\(.*\"*sh",
        "execute\\s*\\(.*\"*bash"
    );
    
    private static final int MAX_LOOP_COMPLEXITY = 10;
    private static final int MAX_NESTING_DEPTH = 20;
    private static final int MAX_EXPRESSION_SIZE = 50000;

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
        
        if (expression.length() > MAX_EXPRESSION_SIZE) {
            logger.warn("表达式长度超过限制: {} > {}", expression.length(), MAX_EXPRESSION_SIZE);
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
        
        for (String pattern : DANGEROUS_PATTERNS) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(expression).find()) {
                logger.warn("表达式包含危险模式: {}", pattern);
                return false;
            }
        }
        
        if (!checkLoopComplexity(expression)) {
            return false;
        }
        
        if (!checkNestingDepth(expression)) {
            return false;
        }
        
        if (!checkResourceUsage(expression)) {
            return false;
        }

        return true;
    }
    
    private boolean checkLoopComplexity(String expression) {
        int whileCount = countKeyword(expression, "while");
        int forCount = countKeyword(expression, "for");
        int totalLoops = whileCount + forCount;
        
        if (totalLoops > MAX_LOOP_COMPLEXITY) {
            logger.warn("表达式循环复杂度过高: {} > {}", totalLoops, MAX_LOOP_COMPLEXITY);
            return false;
        }
        
        if (hasPotentialInfiniteLoop(expression)) {
            logger.warn("表达式可能包含无限循环");
            return false;
        }
        
        return true;
    }
    
    private int countKeyword(String expression, String keyword) {
        int count = 0;
        int index = 0;
        String lower = expression.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        
        while ((index = lower.indexOf(lowerKeyword, index)) != -1) {
            if (index > 0 && Character.isLetterOrDigit(expression.charAt(index - 1))) {
                index++;
                continue;
            }
            if (index + keyword.length() < expression.length() && 
                Character.isLetterOrDigit(expression.charAt(index + keyword.length()))) {
                index++;
                continue;
            }
            count++;
            index++;
        }
        
        return count;
    }
    
    private boolean hasPotentialInfiniteLoop(String expression) {
        String normalized = expression.replaceAll("\\s+", " ").trim();
        
        if (normalized.contains("while(true)") || normalized.contains("while (true)")) {
            String loopBody = extractLoopBody(normalized, "while");
            if (loopBody != null && !loopBody.contains("break") && !loopBody.contains("return")) {
                return true;
            }
        }
        
        if (Pattern.compile("for\\s*\\([^;]*;[^;]*;\\s*\\)").matcher(normalized).find()) {
            return true;
        }
        
        return false;
    }
    
    private String extractLoopBody(String expression, String loopType) {
        int startIndex = expression.indexOf(loopType);
        if (startIndex == -1) {
            return null;
        }
        
        int braceStart = expression.indexOf('{', startIndex);
        if (braceStart == -1) {
            return null;
        }
        
        int braceCount = 1;
        int braceEnd = braceStart + 1;
        
        while (braceEnd < expression.length() && braceCount > 0) {
            char c = expression.charAt(braceEnd);
            if (c == '{') braceCount++;
            else if (c == '}') braceCount--;
            braceEnd++;
        }
        
        return expression.substring(braceStart + 1, braceEnd - 1);
    }
    
    private boolean checkNestingDepth(String expression) {
        int maxDepth = 0;
        int currentDepth = 0;
        
        for (char c : expression.toCharArray()) {
            if (c == '{' || c == '(' || c == '[') {
                currentDepth++;
                maxDepth = Math.max(maxDepth, currentDepth);
            } else if (c == '}' || c == ')' || c == ']') {
                currentDepth--;
            }
        }
        
        if (maxDepth > MAX_NESTING_DEPTH) {
            logger.warn("表达式嵌套深度过高: {} > {}", maxDepth, MAX_NESTING_DEPTH);
            return false;
        }
        
        return true;
    }
    
    private boolean checkResourceUsage(String expression) {
        String lower = expression.toLowerCase();
        
        if (lower.contains("connection") && !lower.contains("close") && !lower.contains("try")) {
            logger.warn("表达式使用Connection但未发现关闭逻辑");
        }
        
        if (lower.contains("resultset") && !lower.contains("close") && !lower.contains("try")) {
            logger.warn("表达式使用ResultSet但未发现关闭逻辑");
        }
        
        if (lower.contains("statement") && !lower.contains("close") && !lower.contains("try")) {
            logger.warn("表达式使用Statement但未发现关闭逻辑");
        }
        
        return true;
    }
    
    public SecurityAnalysisResult analyzeExpression(String expression) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Integer> complexityMetrics = new LinkedHashMap<>();
        
        if (expression == null || expression.trim().isEmpty()) {
            errors.add("表达式为空");
            return new SecurityAnalysisResult(false, errors, warnings, complexityMetrics);
        }
        
        complexityMetrics.put("length", expression.length());
        complexityMetrics.put("whileLoops", countKeyword(expression, "while"));
        complexityMetrics.put("forLoops", countKeyword(expression, "for"));
        complexityMetrics.put("functionCalls", countFunctionCalls(expression));
        complexityMetrics.put("nestingDepth", calculateNestingDepth(expression));
        
        String upperExpression = expression.toUpperCase().trim();

        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (upperExpression.contains(keyword.toUpperCase())) {
                String regex = "\\b" + keyword.toUpperCase() + "\\b";
                if (Pattern.compile(regex).matcher(upperExpression).find()) {
                    errors.add("包含禁止的关键字: " + keyword);
                }
            }
        }

        for (String forbiddenClass : FORBIDDEN_CLASSES) {
            if (upperExpression.contains(forbiddenClass.toUpperCase())) {
                errors.add("包含禁止的类名: " + forbiddenClass);
            }
        }
        
        for (String pattern : DANGEROUS_PATTERNS) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(expression).find()) {
                errors.add("包含危险模式: " + pattern);
            }
        }
        
        if (hasPotentialInfiniteLoop(expression)) {
            warnings.add("可能包含无限循环");
        }
        
        if (complexityMetrics.get("nestingDepth") > 15) {
            warnings.add("嵌套深度较高: " + complexityMetrics.get("nestingDepth"));
        }
        
        int totalLoops = complexityMetrics.get("whileLoops") + complexityMetrics.get("forLoops");
        if (totalLoops > 5) {
            warnings.add("循环数量较多: " + totalLoops);
        }
        
        String lower = expression.toLowerCase();
        if (lower.contains("connection") && !lower.contains("close")) {
            warnings.add("使用Connection但未发现关闭逻辑");
        }
        
        boolean safe = errors.isEmpty();
        return new SecurityAnalysisResult(safe, errors, warnings, complexityMetrics);
    }
    
    private int countFunctionCalls(String expression) {
        int count = 0;
        boolean inString = false;
        char stringChar = 0;
        
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            
            if (!inString && (c == '"' || c == '\'')) {
                inString = true;
                stringChar = c;
            } else if (inString && c == stringChar) {
                inString = false;
            } else if (!inString && c == '(') {
                int j = i - 1;
                while (j >= 0 && Character.isWhitespace(expression.charAt(j))) {
                    j--;
                }
                if (j >= 0 && (Character.isLetterOrDigit(expression.charAt(j)) || expression.charAt(j) == '_')) {
                    count++;
                }
            }
        }
        
        return count;
    }
    
    private int calculateNestingDepth(String expression) {
        int maxDepth = 0;
        int currentDepth = 0;
        
        for (char c : expression.toCharArray()) {
            if (c == '{' || c == '(' || c == '[') {
                currentDepth++;
                maxDepth = Math.max(maxDepth, currentDepth);
            } else if (c == '}' || c == ')' || c == ']') {
                currentDepth--;
            }
        }
        
        return maxDepth;
    }
    
    public record SecurityAnalysisResult(
        boolean safe,
        List<String> errors,
        List<String> warnings,
        Map<String, Integer> complexityMetrics
    ) {}
}
