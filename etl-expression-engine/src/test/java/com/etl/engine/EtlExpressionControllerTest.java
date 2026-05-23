package com.etl.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("HTTP接口测试")
class EtlExpressionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private String sessionId;

    @Test
    @DisplayName("POST /execute - 无Session-Id时自动创建会话")
    void testExecuteWithoutSessionId() throws Exception {
        MvcResult result = mockMvc.perform(post("/etl/expression/execute")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("a=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        sessionId = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .build()
                .readTree(body)
                .get("sessionId")
                .asText();
    }

    @Test
    @DisplayName("POST /execute - 算术表达式")
    void testArithmeticExpression() throws Exception {
        mockMvc.perform(post("/etl/expression/execute")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("10 + 20 * 3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.finalResult").value(70));
    }

    @Test
    @DisplayName("POST /execute - 三元运算表达式")
    void testTernaryExpression() throws Exception {
        mockMvc.perform(post("/etl/expression/execute")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("score=85; score >= 60 ? \"pass\" : \"fail\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.finalResult").value("pass"));
    }

    @Test
    @DisplayName("POST /execute - 多行表达式执行")
    void testMultiLineExpression() throws Exception {
        mockMvc.perform(post("/etl/expression/execute")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("x=10; y=20; z=x+y; z*2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.finalResult").value(60));
    }

    @Test
    @DisplayName("POST /execute - 空格表达式返回失败")
    void testEmptyExpression() throws Exception {
        mockMvc.perform(post("/etl/expression/execute")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /execute - 危险表达式被拦截")
    void testDangerousExpressionBlocked() throws Exception {
        mockMvc.perform(post("/etl/expression/execute")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Runtime.getRuntime()"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /execute - 布尔表达式")
    void testBooleanExpression() throws Exception {
        mockMvc.perform(post("/etl/expression/execute")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("flag=true; !flag"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.finalResult").value(false));
    }

    @Test
    @DisplayName("POST /execute - 字符串操作")
    void testStringExpression() throws Exception {
        mockMvc.perform(post("/etl/expression/execute")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name=\"hello\"; name + \" world\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.finalResult").value("hello world"));
    }

    @Test
    @DisplayName("POST /execute - SQL函数查询")
    void testSqlFunctionQuery() throws Exception {
        mockMvc.perform(post("/etl/expression/execute")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("sql(\"SELECT 1 AS test\")"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
