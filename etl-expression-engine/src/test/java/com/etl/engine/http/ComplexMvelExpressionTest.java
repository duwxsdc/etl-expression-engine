package com.etl.engine.http;

import com.etl.engine.context.EtlContext;
import com.etl.engine.context.EtlContextManager;
import com.etl.engine.mvel.MvelExpressionEngine;
import com.etl.engine.model.ExecuteResult;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ComplexMvelExpressionTest {

    @Autowired
    private MvelExpressionEngine engine;

    @Autowired
    private EtlContextManager contextManager;

    private EtlContext context;

    @BeforeEach
    void setUp() {
        context = contextManager.createSession();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            contextManager.destroySession(context.getSessionId());
        }
    }

    @Test
    @Order(1)
    @DisplayName("复杂嵌套逻辑 - 数据分类聚合")
    void testComplexNestedLogic_DataAggregation() {
        String expression = """
            data = new java.util.ArrayList();
            for (i : {0..9}) {
                item = new java.util.HashMap();
                item.put('id', i);
                item.put('value', i * i);
                item.put('category', i % 3);
                data.add(item);
            }
            
            categories = new java.util.HashMap();
            for (i : {0..2}) {
                categories.put(i, new java.util.ArrayList());
            }
            
            for (item : data) {
                cat = item.get('category');
                list = categories.get(cat);
                list.add(item);
            }
            
            stats = new java.util.HashMap();
            for (entry : categories.entrySet()) {
                sum = 0;
                count = 0;
                for (item : entry.getValue()) {
                    sum = sum + item.get('value');
                    count = count + 1;
                }
                stats.put(entry.getKey(), {'sum': sum, 'count': count, 'avg': sum / count});
            }
            
            stats
            """;

        ExecuteResult result = engine.execute(expression, context);
        
        assertTrue(result.success(), "表达式执行应成功: " + result.errorMsg());
        assertNotNull(result.finalResult());
        
        @SuppressWarnings("unchecked")
        Map<Integer, Map<String, Object>> stats = (Map<Integer, Map<String, Object>>) result.finalResult();
        
        assertEquals(3, stats.size());
        
        Map<String, Object> cat0Stats = stats.get(0);
        assertNotNull(cat0Stats);
        assertTrue((Integer) cat0Stats.get("count") > 0);
        
        System.out.println("数据聚合结果: " + stats);
    }

    @Test
    @Order(2)
    @DisplayName("HTTP调用 - GET请求获取数据")
    void testHttpCall_GetRequest() {
        String expression = """
            response = http('https://httpbin.org/get')
                .queryVariable('name', 'ETL-Test')
                .queryVariable('version', '1.0')
                .header('X-Test-Header', 'Integration-Test')
                .get();
            
            statusCode = response.statusCode();
            body = response.body().asMap();
            
            {
                'statusCode': statusCode,
                'hasArgs': body.containsKey('args'),
                'hasHeaders': body.containsKey('headers')
            }
            """;

        ExecuteResult result = engine.execute(expression, context);
        
        assertTrue(result.success(), "HTTP GET请求应成功: " + result.errorMsg());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) result.finalResult();
        
        assertEquals(200, response.get("statusCode"));
        assertEquals(true, response.get("hasArgs"));
        
        System.out.println("HTTP GET结果: " + response);
    }

    @Test
    @Order(3)
    @DisplayName("HTTP调用 - POST JSON数据")
    void testHttpCall_PostJson() {
        String expression = """
            payload = new java.util.HashMap();
            payload.put('name', 'ETL-Engine');
            payload.put('type', 'Integration-Test');
            payload.put('timestamp', System.currentTimeMillis());
            
            response = http('https://httpbin.org/post')
                .contentType('application/json')
                .bodyJson(payload)
                .post();
            
            statusCode = response.statusCode();
            body = response.body().asMap();
            
            {
                'statusCode': statusCode,
                'hasData': body.containsKey('data'),
                'hasJson': body.containsKey('json')
            }
            """;

        ExecuteResult result = engine.execute(expression, context);
        
        assertTrue(result.success(), "HTTP POST请求应成功: " + result.errorMsg());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) result.finalResult();
        
        assertEquals(200, response.get("statusCode"));
        
        System.out.println("HTTP POST JSON结果: " + response);
    }

    @Test
    @Order(4)
    @DisplayName("复杂嵌套逻辑 + HTTP调用 - 数据处理管道")
    void testComplexExpression_DataPipeline() {
        String expression = """
            records = [
                {'id': 1, 'name': 'Alice', 'score': 85, 'active': true},
                {'id': 2, 'name': 'Bob', 'score': 92, 'active': true},
                {'id': 3, 'name': 'Charlie', 'score': 78, 'active': false},
                {'id': 4, 'name': 'Diana', 'score': 88, 'active': true},
                {'id': 5, 'name': 'Eve', 'score': 95, 'active': true}
            ];
            
            activeRecords = new java.util.ArrayList();
            for (r : records) {
                if (r.active == true) {
                    activeRecords.add(r);
                }
            }
            
            highScoreRecords = new java.util.ArrayList();
            for (r : activeRecords) {
                if (r.score >= 85) {
                    highScoreRecords.add(r);
                }
            }
            
            totalScore = 0;
            for (r : highScoreRecords) {
                totalScore = totalScore + r.score;
            }
            
            avgScore = highScoreRecords.size() > 0 ? totalScore / highScoreRecords.size() : 0;
            
            result = new java.util.HashMap();
            result.put('totalRecords', records.size());
            result.put('activeCount', activeRecords.size());
            result.put('highScoreCount', highScoreRecords.size());
            result.put('averageScore', avgScore);
            result.put('topScorer', highScoreRecords.size() > 0 ? highScoreRecords[0].name : 'N/A');
            
            result
            """;

        ExecuteResult result = engine.execute(expression, context);
        
        assertTrue(result.success(), "数据管道处理应成功: " + result.errorMsg());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) result.finalResult();
        
        assertEquals(5, stats.get("totalRecords"));
        assertEquals(4, stats.get("activeCount"));
        assertTrue((Double) stats.get("averageScore") >= 85);
        
        System.out.println("数据管道处理结果: " + stats);
    }

    @Test
    @Order(5)
    @DisplayName("HTTP异步请求 - 并发获取多个数据")
    void testHttpAsync_ConcurrentRequests() {
        String expression = """
            async1 = http('https://httpbin.org/get').queryVariable('req', '1').asyncGet();
            async2 = http('https://httpbin.org/get').queryVariable('req', '2').asyncGet();
            async3 = http('https://httpbin.org/get').queryVariable('req', '3').asyncGet();
            
            r1 = async1.get(10000);
            r2 = async2.get(10000);
            r3 = async3.get(10000);
            
            {
                'totalRequests': 3,
                'status1': r1.statusCode(),
                'status2': r2.statusCode(),
                'status3': r3.statusCode()
            }
            """;

        ExecuteResult result = engine.execute(expression, context);
        
        assertTrue(result.success(), "异步HTTP请求应成功: " + result.errorMsg());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.finalResult();
        
        assertEquals(3, summary.get("totalRequests"));
        assertEquals(200, summary.get("status1"));
        assertEquals(200, summary.get("status2"));
        assertEquals(200, summary.get("status3"));
        
        System.out.println("异步HTTP请求结果: " + summary);
    }

    @Test
    @Order(6)
    @DisplayName("HTTP Basic认证测试")
    void testHttpBasicAuth() {
        String expression = """
            response = http('https://httpbin.org/basic-auth/testuser/testpass')
                .basicAuth('testuser', 'testpass')
                .get();
            
            {
                'statusCode': response.statusCode(),
                'authenticated': response.statusCode() == 200
            }
            """;

        ExecuteResult result = engine.execute(expression, context);
        
        assertTrue(result.success(), "Basic认证请求应成功: " + result.errorMsg());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> authResult = (Map<String, Object>) result.finalResult();
        
        assertEquals(200, authResult.get("statusCode"));
        assertEquals(true, authResult.get("authenticated"));
        
        System.out.println("Basic认证结果: " + authResult);
    }

    @Test
    @Order(7)
    @DisplayName("HTTP重试机制测试")
    void testHttpRetry() {
        String expression = """
            response = http('https://httpbin.org/status/200')
                .retry(2, 500)
                .get();
            
            response.statusCode()
            """;

        ExecuteResult result = engine.execute(expression, context);
        
        assertTrue(result.success(), "重试机制测试应成功: " + result.errorMsg());
        assertEquals(200, result.finalResult());
        
        System.out.println("HTTP重试机制测试通过");
    }

    @Test
    @Order(8)
    @DisplayName("条件逻辑与字符串处理组合")
    void testConditionalLogic_WithStringProcessing() {
        String expression = """
            items = ['apple', 'BANANA', 'Cherry', 'DATE', 'elderberry'];
            
            processed = new java.util.ArrayList();
            shortNames = new java.util.ArrayList();
            longNames = new java.util.ArrayList();
            
            for (item : items) {
                lower = item.toLowerCase();
                upper = item.toUpperCase();
                length = item.length();
                
                processedItem = new java.util.HashMap();
                processedItem.put('original', item);
                processedItem.put('lower', lower);
                processedItem.put('upper', upper);
                processedItem.put('length', length);
                processedItem.put('isShort', length < 6);
                
                processed.add(processedItem);
                
                if (length < 6) {
                    shortNames.add(item);
                } else {
                    longNames.add(item);
                }
            }
            
            result = new java.util.HashMap();
            result.put('total', items.size());
            result.put('processed', processed);
            result.put('shortNames', shortNames);
            result.put('longNames', longNames);
            result.put('shortCount', shortNames.size());
            result.put('longCount', longNames.size());
            
            result
            """;

        ExecuteResult result = engine.execute(expression, context);
        
        assertTrue(result.success(), "字符串处理应成功: " + result.errorMsg());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.finalResult();
        
        assertEquals(5, data.get("total"));
        assertEquals(3, data.get("shortCount"));
        assertEquals(2, data.get("longCount"));
        
        System.out.println("字符串处理结果: " + data);
    }

    @Test
    @Order(9)
    @DisplayName("SQL查询与HTTP调用组合")
    void testSqlAndHttpCombination() {
        String expression = """
            count = sqlValue("SELECT count(*) FROM etl_config");
            
            apiUrl = 'https://httpbin.org/post';
            
            payload = {
                'database': 'H2',
                'table': 'etl_config',
                'recordCount': count,
                'timestamp': System.currentTimeMillis()
            };
            
            response = http(apiUrl)
                .contentType('application/json')
                .bodyJson(payload)
                .post();
            
            {
                'recordCount': count,
                'httpStatus': response.statusCode(),
                'success': count > 0 && response.statusCode() == 200
            }
            """;

        ExecuteResult result = engine.execute(expression, context);
        
        assertTrue(result.success(), "SQL和HTTP组合应成功: " + result.errorMsg());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.finalResult();
        
        assertTrue((Integer) data.get("recordCount") > 0);
        assertEquals(200, data.get("httpStatus"));
        assertEquals(true, data.get("success"));
        
        System.out.println("SQL和HTTP组合结果: " + data);
    }
}
