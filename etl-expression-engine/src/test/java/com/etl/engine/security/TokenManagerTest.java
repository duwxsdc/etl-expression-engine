package com.etl.engine.security;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TokenManager单元测试")
class TokenManagerTest {
    
    private TokenManager tokenManager;
    
    @BeforeEach
    void setUp() {
        tokenManager = new TokenManager();
    }
    
    @AfterEach
    void tearDown() {
        tokenManager.shutdown();
    }
    
    @Test
    @DisplayName("注册Token配置")
    void testRegisterToken() {
        TokenConfig config = TokenConfig.oauth2("https://test.token.endpoint", "clientId", "clientSecret");
        tokenManager.registerToken("test-token", config);
        
        Map<String, Object> status = tokenManager.getStatus();
        assertTrue(status.containsKey("registeredTokens"));
    }
    
    @Test
    @DisplayName("TokenConfig构建-OAuth2方式")
    void testTokenConfigOAuth2() {
        TokenConfig config = TokenConfig.oauth2("https://endpoint", "clientId", "clientSecret");
        
        assertEquals("https://endpoint", config.getTokenEndpoint());
        assertEquals("client_credentials", config.getGrantType());
        assertEquals("clientId", config.getClientId());
        assertEquals("clientSecret", config.getClientSecret());
    }
    
    @Test
    @DisplayName("TokenConfig构建-API Key方式")
    void testTokenConfigApiKey() {
        TokenConfig config = TokenConfig.apiKey("https://endpoint", "X-API-Key", "my-api-key");
        
        assertEquals("https://endpoint", config.getTokenEndpoint());
        assertNotNull(config.getCustomHeaders());
        assertEquals("my-api-key", config.getCustomHeaders().get("X-API-Key"));
    }
    
    @Test
    @DisplayName("TokenConfig链式配置")
    void testTokenConfigChained() {
        TokenConfig config = new TokenConfig()
                .withMaxRetries(5)
                .withRetryDelay(2000, 2.5)
                .withRefreshBuffer(120_000)
                .withExpiryBuffer(600);
        
        assertEquals(5, config.getMaxRetries());
        assertEquals(2000, config.getInitialRetryDelayMs());
        assertEquals(2.5, config.getBackoffFactor());
        assertEquals(120_000, config.getRefreshBufferMs());
        assertEquals(600, config.getExpiryBufferSeconds());
    }
    
    @Test
    @DisplayName("TokenInfo过期检测")
    void testTokenInfoExpiry() throws InterruptedException {
        char[] token = "test-token".toCharArray();
        TokenInfo info = new TokenInfo(token, "Bearer", 1, null, 
                System.currentTimeMillis(), System.currentTimeMillis() + 1000, 500);
        
        assertFalse(info.isExpired());
        assertTrue(info.getTimeUntilExpiry() > 0);
        
        Thread.sleep(1100);
        assertTrue(info.isExpired());
    }
    
    @Test
    @DisplayName("TokenInfo刷新需求检测")
    void testTokenInfoNeedsRefresh() {
        char[] token = "test-token".toCharArray();
        long now = System.currentTimeMillis();
        TokenInfo info = new TokenInfo(token, "Bearer", 10, null, 
                now, now + 10000, 5000);
        
        assertFalse(info.needsRefresh());
        
        TokenInfo info2 = new TokenInfo(token, "Bearer", 10, null, 
                now, now + 3000, 5000);
        assertTrue(info2.needsRefresh());
    }
    
    @Test
    @DisplayName("TokenException状态判断")
    void testTokenExceptionStatus() {
        TokenException networkError = new TokenException("Network error");
        assertTrue(networkError.isNetworkError());
        assertFalse(networkError.isAuthenticationError());
        assertFalse(networkError.isServerError());
        
        TokenException authError = new TokenException("Auth failed", 401);
        assertTrue(authError.isAuthenticationError());
        
        TokenException serverError = new TokenException("Server error", 500);
        assertTrue(serverError.isServerError());
        
        TokenException rateLimit = new TokenException("Rate limited", 429);
        assertTrue(rateLimit.isRateLimited());
    }
    
    @Test
    @DisplayName("未注册Token获取应抛出异常")
    void testGetUnregisteredToken() {
        assertThrows(TokenException.class, () -> tokenManager.getToken("non-existent"));
    }
    
    @Test
    @DisplayName("Token失效操作")
    void testInvalidateToken() {
        TokenConfig config = TokenConfig.apiKey("https://endpoint", "X-API-Key", "test-key");
        tokenManager.registerToken("test-invalidate", config);
        
        tokenManager.invalidateToken("test-invalidate");
        
        assertFalse(tokenManager.getTokenInfo("test-invalidate").isPresent());
    }
    
    @Test
    @DisplayName("获取Token状态")
    void testGetStatus() {
        TokenConfig config = TokenConfig.apiKey("https://endpoint", "X-API-Key", "test-key");
        tokenManager.registerToken("status-test", config);
        
        Map<String, Object> status = tokenManager.getStatus();
        
        assertNotNull(status);
        assertTrue(status.containsKey("initialized"));
        assertTrue(status.containsKey("registeredTokens"));
    }
}
