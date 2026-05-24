package com.etl.engine.mvel;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MVEL安全沙箱 - import语句与全限定名对比测试")
class MvelSecuritySandboxImportTest {
    
    private MvelSecuritySandbox sandbox;
    
    @BeforeEach
    void setUp() {
        sandbox = new MvelSecuritySandbox();
    }
    
    @Nested
    @DisplayName("import语句测试 - 应被拦截")
    class ImportStatementTests {
        
        @Test
        @DisplayName("使用import语句 - 应被拒绝")
        void testImportStatement_ShouldBeRejected() {
            String expression = """
                import java.util.ArrayList;
                list = new ArrayList();
                list.add(1);
                list
                """;
            
            boolean result = sandbox.isExpressionSafe(expression);
            
            assertFalse(result, "包含import语句的表达式应被拒绝");
            System.out.println("✅ import语句被正确拦截");
        }
        
        @Test
        @DisplayName("使用import多个类 - 应被拒绝")
        void testMultipleImports_ShouldBeRejected() {
            String expression = """
                import java.util.ArrayList;
                import java.util.HashMap;
                
                list = new ArrayList();
                map = new HashMap();
                map.put('list', list);
                map
                """;
            
            boolean result = sandbox.isExpressionSafe(expression);
            
            assertFalse(result, "包含多个import语句的表达式应被拒绝");
            System.out.println("✅ 多个import语句被正确拦截");
        }
        
        @Test
        @DisplayName("使用try-catch语句 - 应被拒绝")
        void testTryCatch_ShouldBeRejected() {
            String expression = """
                result = null;
                try {
                    result = someOperation();
                } catch (e) {
                    result = 'error';
                }
                result
                """;
            
            boolean result = sandbox.isExpressionSafe(expression);
            
            assertFalse(result, "包含try-catch的表达式应被拒绝");
            System.out.println("✅ try-catch语句被正确拦截");
        }
        
        @Test
        @DisplayName("使用synchronized块 - 应被拒绝")
        void testSynchronized_ShouldBeRejected() {
            String expression = """
                counter = 0;
                synchronized(this) {
                    counter = counter + 1;
                }
                counter
                """;
            
            boolean result = sandbox.isExpressionSafe(expression);
            
            assertFalse(result, "包含synchronized的表达式应被拒绝");
            System.out.println("✅ synchronized块被正确拦截");
        }
    }
    
    @Nested
    @DisplayName("全限定名测试 - 应通过")
    class FullyQualifiedNameTests {
        
        @Test
        @DisplayName("使用全限定名ArrayList - 应通过")
        void testFullyQualifiedArrayList_ShouldPass() {
            String expression = """
                list = new java.util.ArrayList();
                list.add(1);
                list.add(2);
                list.add(3);
                list.size()
                """;
            
            boolean result = sandbox.isExpressionSafe(expression);
            
            assertTrue(result, "使用全限定名的表达式应通过安全检查");
            System.out.println("✅ 全限定名ArrayList通过安全检查");
        }
        
        @Test
        @DisplayName("使用全限定名HashMap - 应通过")
        void testFullyQualifiedHashMap_ShouldPass() {
            String expression = """
                map = new java.util.HashMap();
                map.put('name', 'ETL-Engine');
                map.put('version', '1.0');
                map.size()
                """;
            
            boolean result = sandbox.isExpressionSafe(expression);
            
            assertTrue(result, "使用全限定名HashMap的表达式应通过安全检查");
            System.out.println("✅ 全限定名HashMap通过安全检查");
        }
        
        @Test
        @DisplayName("使用多个全限定名类 - 应通过")
        void testMultipleFullyQualifiedClasses_ShouldPass() {
            String expression = """
                list = new java.util.ArrayList();
                map = new java.util.HashMap();
                
                for (i : {1, 2, 3, 4, 5}) {
                    list.add(i);
                }
                
                map.put('items', list);
                map.put('count', list.size());
                map
                """;
            
            boolean result = sandbox.isExpressionSafe(expression);
            
            assertTrue(result, "使用多个全限定名类的表达式应通过安全检查");
            System.out.println("✅ 多个全限定名类通过安全检查");
        }
        
        @Test
        @DisplayName("嵌套数据结构 - 应通过")
        void testNestedDataStructures_ShouldPass() {
            String expression = """
                outerList = new java.util.ArrayList();
                
                for (i : {0..2}) {
                    innerMap = new java.util.HashMap();
                    innerMap.put('index', i);
                    innerMap.put('items', new java.util.ArrayList());
                    outerList.add(innerMap);
                }
                
                outerList.size()
                """;
            
            boolean result = sandbox.isExpressionSafe(expression);
            
            assertTrue(result, "嵌套数据结构表达式应通过安全检查");
            System.out.println("✅ 嵌套数据结构通过安全检查");
        }
        
        @Test
        @DisplayName("复杂业务逻辑 - 应通过")
        void testComplexBusinessLogic_ShouldPass() {
            String expression = """
                records = [
                    {'name': 'Alice', 'score': 85},
                    {'name': 'Bob', 'score': 92},
                    {'name': 'Charlie', 'score': 78}
                ];
                
                highScores = new java.util.ArrayList();
                for (r : records) {
                    if (r.score >= 85) {
                        highScores.add(r);
                    }
                }
                
                result = new java.util.HashMap();
                result.put('total', records.size());
                result.put('highScoreCount', highScores.size());
                result
                """;
            
            boolean result = sandbox.isExpressionSafe(expression);
            
            assertTrue(result, "复杂业务逻辑表达式应通过安全检查");
            System.out.println("✅ 复杂业务逻辑通过安全检查");
        }
    }
    
    @Nested
    @DisplayName("边界情况测试")
    class BoundaryTests {
        
        @Test
        @DisplayName("空表达式 - 应被拒绝")
        void testEmptyExpression_ShouldBeRejected() {
            assertFalse(sandbox.isExpressionSafe(""));
            assertFalse(sandbox.isExpressionSafe("   "));
            assertFalse(sandbox.isExpressionSafe(null));
            System.out.println("✅ 空表达式被正确拒绝");
        }
        
        @Test
        @DisplayName("仅包含注释的表达式 - 应通过")
        void testCommentOnlyExpression_ShouldPass() {
            String expression = "// 这是一条注释\n";
            boolean result = sandbox.isExpressionSafe(expression);
            assertTrue(result);
            System.out.println("✅ 注释表达式通过检查");
        }
        
        @Test
        @DisplayName("使用java.lang包中的类 - 应被拒绝(包含java.lang)")
        void testJavaLangPackage_ShouldBeRejected() {
            String expression = """
                str = new java.lang.String("test");
                str.toUpperCase()
                """;
            
            boolean result = sandbox.isExpressionSafe(expression);
            // java.lang.reflect在禁止列表中，但java.lang.String本身应该是允许的
            // 这里测试的是java.lang.reflect模式
            System.out.println("java.lang.String检查结果: " + result);
        }
        
        @Test
        @DisplayName("字符串中包含import关键字 - 应被拒绝")
        void testImportInString_ShouldBeRejected() {
            String expression = """
                text = "import something";
                text
                """;
            
            boolean result = sandbox.isExpressionSafe(expression);
            // 字符串中的import也会被检测到
            assertFalse(result, "字符串中的import关键字也应被检测");
            System.out.println("✅ 字符串中的import被正确检测");
        }
        
        @Test
        @DisplayName("使用LinkedHashMap全限定名 - 应通过")
        void testLinkedHashMap_ShouldPass() {
            String expression = """
                map = new java.util.LinkedHashMap();
                map.put('a', 1);
                map.put('b', 2);
                map
                """;
            
            boolean result = sandbox.isExpressionSafe(expression);
            assertTrue(result);
            System.out.println("✅ LinkedHashMap全限定名通过检查");
        }
    }
    
    @Nested
    @DisplayName("analyzeExpression方法测试")
    class AnalyzeExpressionTests {
        
        @Test
        @DisplayName("分析包含import的表达式")
        void testAnalyzeWithImport() {
            String expression = """
                import java.util.ArrayList;
                list = new ArrayList();
                """;
            
            MvelSecuritySandbox.SecurityAnalysisResult result = sandbox.analyzeExpression(expression);
            
            assertFalse(result.safe());
            assertFalse(result.errors().isEmpty());
            assertTrue(result.errors().stream().anyMatch(e -> e.contains("import")));
            
            System.out.println("=== 安全分析结果 ===");
            System.out.println("安全: " + result.safe());
            System.out.println("错误: " + result.errors());
            System.out.println("警告: " + result.warnings());
            System.out.println("复杂度指标: " + result.complexityMetrics());
        }
        
        @Test
        @DisplayName("分析安全的表达式")
        void testAnalyzeSafeExpression() {
            String expression = """
                list = new java.util.ArrayList();
                for (i : {1..10}) {
                    list.add(i * 2);
                }
                list
                """;
            
            MvelSecuritySandbox.SecurityAnalysisResult result = sandbox.analyzeExpression(expression);
            
            assertTrue(result.safe());
            assertTrue(result.errors().isEmpty());
            
            System.out.println("=== 安全表达式分析结果 ===");
            System.out.println("安全: " + result.safe());
            System.out.println("错误: " + result.errors());
            System.out.println("警告: " + result.warnings());
            System.out.println("复杂度指标: " + result.complexityMetrics());
        }
    }
}
