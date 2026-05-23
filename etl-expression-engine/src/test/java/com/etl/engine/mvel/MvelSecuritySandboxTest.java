package com.etl.engine.mvel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MVEL安全沙箱测试")
class MvelSecuritySandboxTest {
    
    private MvelSecuritySandbox sandbox;
    
    @BeforeEach
    void setUp() {
        sandbox = new MvelSecuritySandbox();
    }
    
    @Test
    @DisplayName("安全表达式通过检查")
    void testSafeExpression() {
        assertTrue(sandbox.isExpressionSafe("1 + 2"));
        assertTrue(sandbox.isExpressionSafe("a * b + c"));
        assertTrue(sandbox.isExpressionSafe("httpRequest('http://test.com').get().asJson()"));
    }
    
    @Test
    @DisplayName("禁止import关键字")
    void testForbiddenImport() {
        assertFalse(sandbox.isExpressionSafe("import java.util.List; 1 + 2"));
    }
    
    @Test
    @DisplayName("禁止Runtime类")
    void testForbiddenRuntime() {
        assertFalse(sandbox.isExpressionSafe("Runtime.getRuntime().exec('cmd')"));
    }
    
    @Test
    @DisplayName("禁止System.exit")
    void testForbiddenSystemExit() {
        assertFalse(sandbox.isExpressionSafe("System.exit(0)"));
    }
    
    @Test
    @DisplayName("禁止ProcessBuilder")
    void testForbiddenProcessBuilder() {
        assertFalse(sandbox.isExpressionSafe("new ProcessBuilder('cmd')"));
    }
    
    @Test
    @DisplayName("禁止无限循环while(true)")
    void testForbiddenInfiniteLoop() {
        assertFalse(sandbox.isExpressionSafe("while(true) { }"));
        assertFalse(sandbox.isExpressionSafe("while (true) { int a = 1; }"));
    }
    
    @Test
    @DisplayName("禁止空for循环")
    void testForbiddenEmptyForLoop() {
        assertFalse(sandbox.isExpressionSafe("for(;;) { }"));
    }
    
    @Test
    @DisplayName("禁止文件IO操作")
    void testForbiddenFileIO() {
        assertFalse(sandbox.isExpressionSafe("new FileInputStream('test.txt')"));
        assertFalse(sandbox.isExpressionSafe("new FileOutputStream('test.txt')"));
    }
    
    @Test
    @DisplayName("禁止Socket操作")
    void testForbiddenSocket() {
        assertFalse(sandbox.isExpressionSafe("new Socket('localhost', 8080)"));
    }
    
    @Test
    @DisplayName("空表达式返回false")
    void testEmptyExpression() {
        assertFalse(sandbox.isExpressionSafe(""));
        assertFalse(sandbox.isExpressionSafe("   "));
        assertFalse(sandbox.isExpressionSafe(null));
    }
    
    @Test
    @DisplayName("分析表达式安全性")
    void testAnalyzeExpression() {
        MvelSecuritySandbox.SecurityAnalysisResult result = sandbox.analyzeExpression("1 + 2 * 3");
        
        assertTrue(result.safe());
        assertTrue(result.errors().isEmpty());
        assertNotNull(result.complexityMetrics());
        assertTrue(result.complexityMetrics().containsKey("length"));
    }
    
    @Test
    @DisplayName("分析危险表达式")
    void testAnalyzeDangerousExpression() {
        MvelSecuritySandbox.SecurityAnalysisResult result = sandbox.analyzeExpression("Runtime.getRuntime().exec('cmd')");
        
        assertFalse(result.safe());
        assertFalse(result.errors().isEmpty());
    }
    
    @Test
    @DisplayName("分析复杂表达式")
    void testAnalyzeComplexExpression() {
        StringBuilder sb = new StringBuilder();
        sb.append("result = 0; ");
        for (int i = 0; i < 5; i++) {
            sb.append("for (int i").append(i).append(" = 0; i").append(i).append(" < 10; i").append(i).append("++) { ");
        }
        sb.append("result = result + 1; ");
        for (int i = 0; i < 5; i++) {
            sb.append("}");
        }
        
        MvelSecuritySandbox.SecurityAnalysisResult result = sandbox.analyzeExpression(sb.toString());
        
        assertNotNull(result.complexityMetrics());
        assertTrue(result.complexityMetrics().get("forLoops") >= 5);
    }
    
    @Test
    @DisplayName("检测嵌套深度")
    void testNestingDepth() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 25; i++) {
            sb.append("(");
        }
        sb.append("1");
        for (int i = 0; i < 25; i++) {
            sb.append(")");
        }
        String deepNesting = sb.toString();
        
        assertFalse(sandbox.isExpressionSafe(deepNesting));
    }
    
    @Test
    @DisplayName("检测循环复杂度")
    void testLoopComplexity() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            sb.append("while(a < 10) { ");
        }
        sb.append("a = a + 1;");
        for (int i = 0; i < 15; i++) {
            sb.append("}");
        }
        
        assertFalse(sandbox.isExpressionSafe(sb.toString()));
    }
    
    @Test
    @DisplayName("HTTP请求表达式安全")
    void testHttpExpressionSafe() {
        String expr = "http('http://api.example.com/users').header('Authorization', 'Bearer token').get().asJson()";
        assertTrue(sandbox.isExpressionSafe(expr));
    }
    
    @Test
    @DisplayName("SQL查询表达式安全")
    void testSqlExpressionSafe() {
        String expr = "sql('SELECT * FROM users WHERE id = ?')";
        assertTrue(sandbox.isExpressionSafe(expr));
    }
    
    @Test
    @DisplayName("创建安全ParserContext")
    void testCreateSafeParserContext() {
        var context = sandbox.createSafeParserContext();
        assertNotNull(context);
    }
}
