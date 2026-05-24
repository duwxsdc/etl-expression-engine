package com.etl.engine.mvel;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("MVEL安全沙箱 - 真实业务场景测试")
class MvelSecuritySandboxBusinessTest {

    @Autowired
    private MvelSecuritySandbox sandbox;

    @Nested
    @DisplayName("真实业务场景表达式测试")
    class RealBusinessScenarioTests {

        @Test
        @DisplayName("场景1: ETL数据转换管道")
        void testDataTransformationPipeline() {
            String expression = """
                sourceData = inputRecords;
                
                transformedList = new java.util.ArrayList();
                errorList = new java.util.ArrayList();
                stats = new java.util.HashMap();
                
                successCount = 0;
                errorCount = 0;
                totalAmount = 0.0;
                
                for (record : sourceData) {
                    transformed = new java.util.HashMap();
                    
                    transformed.put('id', record.get('id'));
                    transformed.put('originalName', record.get('name'));
                    transformed.put('processedName', record.get('name') != null ? 
                        record.get('name').toString().toUpperCase() : 'UNKNOWN');
                    
                    amount = record.get('amount');
                    if (amount != null && amount instanceof java.lang.Number) {
                        numAmount = ((java.lang.Number) amount).doubleValue();
                        taxRate = 0.15;
                        taxAmount = numAmount * taxRate;
                        totalWithTax = numAmount + taxAmount;
                        
                        transformed.put('originalAmount', numAmount);
                        transformed.put('taxAmount', taxAmount);
                        transformed.put('totalWithTax', totalWithTax);
                        transformed.put('currency', record.get('currency') != null ? 
                            record.get('currency') : 'CNY');
                        
                        totalAmount = totalAmount + numAmount;
                        successCount = successCount + 1;
                        transformedList.add(transformed);
                    } else {
                        errorRecord = new java.util.HashMap();
                        errorRecord.put('record', record);
                        errorRecord.put('error', 'Invalid amount value');
                        errorList.add(errorRecord);
                        errorCount = errorCount + 1;
                    }
                }
                
                stats.put('totalRecords', sourceData.size());
                stats.put('successCount', successCount);
                stats.put('errorCount', errorCount);
                stats.put('totalAmount', totalAmount);
                stats.put('avgAmount', successCount > 0 ? totalAmount / successCount : 0);
                
                result = new java.util.HashMap();
                result.put('transformed', transformedList);
                result.put('errors', errorList);
                result.put('statistics', stats);
                result
                """;

            long startTime = System.nanoTime();
            boolean safe = sandbox.isExpressionSafe(expression);
            long endTime = System.nanoTime();
            
            assertTrue(safe, "ETL数据转换管道表达式应通过安全检查");
            
            double checkTimeMs = (endTime - startTime) / 1_000_000.0;
            System.out.println("=== ETL数据转换管道 ===");
            System.out.println("表达式长度: " + expression.length() + " 字符");
            System.out.println("安全检查耗时: " + checkTimeMs + " ms");
            System.out.println("检查结果: ✅ 通过");
            
            assertTrue(checkTimeMs < 100, "安全检查应在100ms内完成");
        }

        @Test
        @DisplayName("场景2: 多层嵌套条件判断")
        void testMultiLevelNestedConditions() {
            String expression = """
                orders = orderList;
                
                categorized = new java.util.HashMap();
                highPriority = new java.util.ArrayList();
                mediumPriority = new java.util.ArrayList();
                lowPriority = new java.util.ArrayList();
                urgentOrders = new java.util.ArrayList();
                
                for (order : orders) {
                    amount = order.get('amount') != null ? 
                        ((java.lang.Number) order.get('amount')).doubleValue() : 0;
                    status = order.get('status');
                    daysSinceCreated = order.get('daysSinceCreated') != null ?
                        ((java.lang.Number) order.get('daysSinceCreated')).intValue() : 0;
                    customerTier = order.get('customerTier');
                    
                    isUrgent = false;
                    isHigh = false;
                    isMedium = false;
                    isLow = false;
                    
                    if (status == 'PENDING' && daysSinceCreated > 7) {
                        isUrgent = true;
                    } else if (amount > 10000 || customerTier == 'VIP') {
                        isHigh = true;
                    } else if (amount > 5000 || customerTier == 'GOLD') {
                        isMedium = true;
                    } else {
                        isLow = true;
                    }
                    
                    processedOrder = new java.util.HashMap();
                    processedOrder.put('orderId', order.get('id'));
                    processedOrder.put('amount', amount);
                    processedOrder.put('originalStatus', status);
                    processedOrder.put('daysSinceCreated', daysSinceCreated);
                    
                    if (isUrgent) {
                        processedOrder.put('priority', 'URGENT');
                        processedOrder.put('estimatedProcessHours', 2);
                        urgentOrders.add(processedOrder);
                    } else if (isHigh) {
                        processedOrder.put('priority', 'HIGH');
                        processedOrder.put('estimatedProcessHours', 8);
                        highPriority.add(processedOrder);
                    } else if (isMedium) {
                        processedOrder.put('priority', 'MEDIUM');
                        processedOrder.put('estimatedProcessHours', 24);
                        mediumPriority.add(processedOrder);
                    } else {
                        processedOrder.put('priority', 'LOW');
                        processedOrder.put('estimatedProcessHours', 72);
                        lowPriority.add(processedOrder);
                    }
                }
                
                categorized.put('urgent', urgentOrders);
                categorized.put('high', highPriority);
                categorized.put('medium', mediumPriority);
                categorized.put('low', lowPriority);
                categorized.put('totalUrgent', urgentOrders.size());
                categorized.put('totalHigh', highPriority.size());
                categorized.put('totalMedium', mediumPriority.size());
                categorized.put('totalLow', lowPriority.size());
                categorized
                """;

            long startTime = System.nanoTime();
            boolean safe = sandbox.isExpressionSafe(expression);
            long endTime = System.nanoTime();
            
            assertTrue(safe, "多层嵌套条件判断表达式应通过安全检查");
            
            double checkTimeMs = (endTime - startTime) / 1_000_000.0;
            System.out.println("=== 多层嵌套条件判断 ===");
            System.out.println("表达式长度: " + expression.length() + " 字符");
            System.out.println("安全检查耗时: " + checkTimeMs + " ms");
            System.out.println("检查结果: ✅ 通过");
        }

        @Test
        @DisplayName("场景3: 动态聚合计算")
        void testDynamicAggregation() {
            String expression = """
                salesData = salesRecords;
                
                byRegion = new java.util.HashMap();
                byCategory = new java.util.HashMap();
                byMonth = new java.util.HashMap();
                
                for (sale : salesData) {
                    region = sale.get('region');
                    category = sale.get('category');
                    month = sale.get('month');
                    amount = ((java.lang.Number) sale.get('amount')).doubleValue();
                    quantity = ((java.lang.Number) sale.get('quantity')).intValue();
                    
                    if (!byRegion.containsKey(region)) {
                        regionStats = new java.util.HashMap();
                        regionStats.put('totalAmount', 0.0);
                        regionStats.put('totalQuantity', 0);
                        regionStats.put('count', 0);
                        byRegion.put(region, regionStats);
                    }
                    regionData = byRegion.get(region);
                    regionData.put('totalAmount', ((java.lang.Number) regionData.get('totalAmount')).doubleValue() + amount);
                    regionData.put('totalQuantity', ((java.lang.Number) regionData.get('totalQuantity')).intValue() + quantity);
                    regionData.put('count', ((java.lang.Number) regionData.get('count')).intValue() + 1);
                    
                    if (!byCategory.containsKey(category)) {
                        catStats = new java.util.HashMap();
                        catStats.put('totalAmount', 0.0);
                        catStats.put('totalQuantity', 0);
                        catStats.put('count', 0);
                        byCategory.put(category, catStats);
                    }
                    catData = byCategory.get(category);
                    catData.put('totalAmount', ((java.lang.Number) catData.get('totalAmount')).doubleValue() + amount);
                    catData.put('totalQuantity', ((java.lang.Number) catData.get('totalQuantity')).intValue() + quantity);
                    catData.put('count', ((java.lang.Number) catData.get('count')).intValue() + 1);
                    
                    if (!byMonth.containsKey(month)) {
                        monthStats = new java.util.HashMap();
                        monthStats.put('totalAmount', 0.0);
                        monthStats.put('totalQuantity', 0);
                        monthStats.put('count', 0);
                        byMonth.put(month, monthStats);
                    }
                    monthData = byMonth.get(month);
                    monthData.put('totalAmount', ((java.lang.Number) monthData.get('totalAmount')).doubleValue() + amount);
                    monthData.put('totalQuantity', ((java.lang.Number) monthData.get('totalQuantity')).intValue() + quantity);
                    monthData.put('count', ((java.lang.Number) monthData.get('count')).intValue() + 1);
                }
                
                result = new java.util.HashMap();
                result.put('byRegion', byRegion);
                result.put('byCategory', byCategory);
                result.put('byMonth', byMonth);
                result.put('totalRecords', salesData.size());
                result
                """;

            long startTime = System.nanoTime();
            boolean safe = sandbox.isExpressionSafe(expression);
            long endTime = System.nanoTime();
            
            assertTrue(safe, "动态聚合计算表达式应通过安全检查");
            
            double checkTimeMs = (endTime - startTime) / 1_000_000.0;
            System.out.println("=== 动态聚合计算 ===");
            System.out.println("表达式长度: " + expression.length() + " 字符");
            System.out.println("安全检查耗时: " + checkTimeMs + " ms");
            System.out.println("检查结果: ✅ 通过");
        }

        @Test
        @DisplayName("场景4: 字符串处理与格式化")
        void testStringProcessingAndFormatting() {
            String expression = """
                rawMessages = messageList;
                
                processedMessages = new java.util.ArrayList();
                
                for (msg : rawMessages) {
                    content = msg.get('content');
                    type = msg.get('type');
                    priority = msg.get('priority');
                    
                    processed = new java.util.HashMap();
                    
                    if (content != null && content instanceof java.lang.String) {
                        strContent = (java.lang.String) content;
                        
                        trimmed = strContent.trim();
                        upper = trimmed.toUpperCase();
                        lower = trimmed.toLowerCase();
                        length = trimmed.length();
                        
                        words = new java.util.ArrayList();
                        parts = trimmed.split(' ');
                        for (part : parts) {
                            if (part != null && part.length() > 0) {
                                words.add(part);
                            }
                        }
                        
                        wordCount = words.size();
                        charCount = length;
                        
                        processed.put('original', strContent);
                        processed.put('trimmed', trimmed);
                        processed.put('upperCase', upper);
                        processed.put('lowerCase', lower);
                        processed.put('wordCount', wordCount);
                        processed.put('charCount', charCount);
                        processed.put('words', words);
                        processed.put('isValid', length > 0 && wordCount > 0);
                    } else {
                        processed.put('original', content);
                        processed.put('isValid', false);
                        processed.put('error', 'Invalid content type');
                    }
                    
                    processed.put('type', type);
                    processed.put('priority', priority);
                    processedMessages.add(processed);
                }
                
                result = new java.util.HashMap();
                result.put('processed', processedMessages);
                result.put('total', processedMessages.size());
                result
                """;

            long startTime = System.nanoTime();
            boolean safe = sandbox.isExpressionSafe(expression);
            long endTime = System.nanoTime();
            
            assertTrue(safe, "字符串处理与格式化表达式应通过安全检查");
            
            double checkTimeMs = (endTime - startTime) / 1_000_000.0;
            System.out.println("=== 字符串处理与格式化 ===");
            System.out.println("表达式长度: " + expression.length() + " 字符");
            System.out.println("安全检查耗时: " + checkTimeMs + " ms");
            System.out.println("检查结果: ✅ 通过");
        }
    }

    @Nested
    @DisplayName("危险表达式拦截测试")
    class DangerousExpressionTests {

        @Test
        @DisplayName("拦截: 包含Runtime.getRuntime()")
        void testBlockRuntimeGetRuntime() {
            String expression = """
                data = new java.util.HashMap();
                data.put('result', Runtime.getRuntime().exec('cmd'));
                data
                """;

            long startTime = System.nanoTime();
            boolean safe = sandbox.isExpressionSafe(expression);
            long endTime = System.nanoTime();
            
            assertFalse(safe, "包含Runtime的表达式应被拦截");
            
            double checkTimeMs = (endTime - startTime) / 1_000_000.0;
            System.out.println("=== 拦截Runtime.getRuntime() ===");
            System.out.println("安全检查耗时: " + checkTimeMs + " ms");
            System.out.println("检查结果: ✅ 正确拦截");
        }

        @Test
        @DisplayName("拦截: 包含System.exit()")
        void testBlockSystemExit() {
            String expression = """
                result = process(data);
                if (result == null) {
                    System.exit(1);
                }
                result
                """;

            boolean safe = sandbox.isExpressionSafe(expression);
            assertFalse(safe, "包含System.exit的表达式应被拦截");
            System.out.println("✅ System.exit被正确拦截");
        }

        @Test
        @DisplayName("拦截: 包含无限循环")
        void testBlockInfiniteLoop() {
            String expression = """
                counter = 0;
                while (true) {
                    counter = counter + 1;
                }
                counter
                """;

            boolean safe = sandbox.isExpressionSafe(expression);
            assertFalse(safe, "包含无限循环的表达式应被拦截");
            System.out.println("✅ 无限循环被正确拦截");
        }

        @Test
        @DisplayName("拦截: 包含ProcessBuilder")
        void testBlockProcessBuilder() {
            String expression = """
                pb = new ProcessBuilder('ls', '-la');
                process = pb.start();
                process.waitFor()
                """;

            boolean safe = sandbox.isExpressionSafe(expression);
            assertFalse(safe, "包含ProcessBuilder的表达式应被拦截");
            System.out.println("✅ ProcessBuilder被正确拦截");
        }

        @Test
        @DisplayName("拦截: 包含反射调用")
        void testBlockReflection() {
            String expression = """
                clazz = Class.forName('java.lang.Runtime');
                method = clazz.getMethod('getRuntime');
                runtime = method.invoke(null);
                runtime
                """;

            boolean safe = sandbox.isExpressionSafe(expression);
            assertFalse(safe, "包含反射调用的表达式应被拦截");
            System.out.println("✅ 反射调用被正确拦截");
        }
    }

    @Nested
    @DisplayName("性能基准测试")
    class PerformanceBenchmarkTests {

        @Test
        @DisplayName("性能测试: 多次安全检查")
        void testMultipleSecurityChecks() {
            String expression = """
                data = new java.util.ArrayList();
                for (i : {0..100}) {
                    item = new java.util.HashMap();
                    item.put('id', i);
                    item.put('value', i * 2);
                    data.add(item);
                }
                data
                """;

            int iterations = 1000;
            long totalTime = 0;
            
            for (int i = 0; i < iterations; i++) {
                long start = System.nanoTime();
                sandbox.isExpressionSafe(expression);
                long end = System.nanoTime();
                totalTime += (end - start);
            }
            
            double avgTimeMs = totalTime / iterations / 1_000_000.0;
            double throughput = iterations / (totalTime / 1_000_000_000.0);
            
            System.out.println("=== 性能基准测试 ===");
            System.out.println("表达式长度: " + expression.length() + " 字符");
            System.out.println("迭代次数: " + iterations);
            System.out.println("平均检查耗时: " + avgTimeMs + " ms");
            System.out.println("吞吐量: " + String.format("%.0f", throughput) + " checks/sec");
            
            assertTrue(avgTimeMs < 10, "平均检查时间应小于10ms");
        }

        @Test
        @DisplayName("性能测试: 不同复杂度表达式对比")
        void testDifferentComplexityExpressions() {
            String simpleExpr = "a + b";
            String mediumExpr = """
                result = new java.util.HashMap();
                for (i : {0..10}) {
                    result.put(i, i * 2);
                }
                result
                """;
            String complexExpr = """
                data = new java.util.ArrayList();
                for (i : {0..50}) {
                    item = new java.util.HashMap();
                    for (j : {0..10}) {
                        item.put('key' + j, i * j);
                    }
                    data.add(item);
                }
                
                result = new java.util.HashMap();
                for (item : data) {
                    sum = 0;
                    for (entry : item.entrySet()) {
                        sum = sum + ((java.lang.Number) entry.getValue()).intValue();
                    }
                    item.put('sum', sum);
                }
                data
                """;

            System.out.println("=== 不同复杂度性能对比 ===");
            
            long start1 = System.nanoTime();
            sandbox.isExpressionSafe(simpleExpr);
            long end1 = System.nanoTime();
            double time1 = (end1 - start1) / 1_000_000.0;
            System.out.println("简单表达式(" + simpleExpr.length() + "字符): " + time1 + " ms");
            
            long start2 = System.nanoTime();
            sandbox.isExpressionSafe(mediumExpr);
            long end2 = System.nanoTime();
            double time2 = (end2 - start2) / 1_000_000.0;
            System.out.println("中等表达式(" + mediumExpr.length() + "字符): " + time2 + " ms");
            
            long start3 = System.nanoTime();
            sandbox.isExpressionSafe(complexExpr);
            long end3 = System.nanoTime();
            double time3 = (end3 - start3) / 1_000_000.0;
            System.out.println("复杂表达式(" + complexExpr.length() + "字符): " + time3 + " ms");
            
            assertTrue(time1 < 50, "简单表达式检查应在50ms内完成");
            assertTrue(time2 < 50, "中等表达式检查应在50ms内完成");
            assertTrue(time3 < 50, "复杂表达式检查应在50ms内完成");
            assertTrue(time3 > time1, "复杂表达式检查时间应大于简单表达式");
            System.out.println("✅ 所有复杂度检查时间在合理范围内");
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryConditionTests {

        @Test
        @DisplayName("边界: 最大嵌套深度")
        void testMaxNestingDepth() {
            StringBuilder sb = new StringBuilder();
            sb.append("result = ");
            for (int i = 0; i < 15; i++) {
                sb.append("(");
            }
            sb.append("1 + 1");
            for (int i = 0; i < 15; i++) {
                sb.append(")");
            }
            
            String expression = sb.toString();
            boolean safe = sandbox.isExpressionSafe(expression);
            
            System.out.println("=== 最大嵌套深度测试 ===");
            System.out.println("嵌套深度: 15");
            System.out.println("检查结果: " + (safe ? "通过" : "拒绝"));
        }

        @Test
        @DisplayName("边界: 最大循环数量")
        void testMaxLoopCount() {
            StringBuilder sb = new StringBuilder();
            sb.append("result = new java.util.ArrayList();\n");
            for (int i = 0; i < 8; i++) {
                sb.append("for (item").append(i).append(" : data) { result.add(item").append(i).append("); }\n");
            }
            sb.append("result");
            
            String expression = sb.toString();
            boolean safe = sandbox.isExpressionSafe(expression);
            
            System.out.println("=== 最大循环数量测试 ===");
            System.out.println("循环数量: 8");
            System.out.println("检查结果: " + (safe ? "通过" : "拒绝"));
        }

        @Test
        @DisplayName("边界: 超长表达式")
        void testVeryLongExpression() {
            StringBuilder sb = new StringBuilder();
            sb.append("result = 0;\n");
            for (int i = 0; i < 1000; i++) {
                sb.append("result = result + ").append(i).append(";\n");
            }
            
            String expression = sb.toString();
            boolean safe = sandbox.isExpressionSafe(expression);
            
            System.out.println("=== 超长表达式测试 ===");
            System.out.println("表达式长度: " + expression.length() + " 字符");
            System.out.println("检查结果: " + (safe ? "通过" : "拒绝"));
        }
    }
}
