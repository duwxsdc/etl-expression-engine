package com.etl.engine.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
public class TokenManager {
    
    private static final Logger logger = LoggerFactory.getLogger(TokenManager.class);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_INITIAL_DELAY = 1000L;
    private static final double DEFAULT_BACKOFF_FACTOR = 2.0;
    private static final long DEFAULT_REFRESH_BUFFER = 60_000L;
    private static final int DEFAULT_EXPIRY_BUFFER = 300;
    
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    private final Map<String, TokenInfo> tokenCache = new ConcurrentHashMap<>();
    private final Map<String, TokenConfig> tokenConfigs = new ConcurrentHashMap<>();
    private final AtomicReference<ScheduledFuture<?>> refreshTask = new AtomicReference<>();
    
    private volatile boolean initialized = false;
    
    public TokenManager() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "token-manager-scheduler");
            t.setDaemon(true);
            return t;
        });
    }
    
    public void registerToken(String tokenId, TokenConfig config) {
        Objects.requireNonNull(tokenId, "tokenId不能为空");
        Objects.requireNonNull(config, "config不能为空");
        
        tokenConfigs.put(tokenId, config);
        logger.info("注册令牌配置: id={}, endpoint={}, refreshBuffer={}ms", 
                tokenId, config.getTokenEndpoint(), config.getRefreshBufferMs());
        
        if (!initialized) {
            startRefreshTask();
            initialized = true;
        }
    }
    
    public String getToken(String tokenId) {
        return getToken(tokenId, true);
    }
    
    public String getToken(String tokenId, boolean autoRefresh) {
        lock.readLock().lock();
        try {
            TokenInfo info = tokenCache.get(tokenId);
            
            if (info == null || info.isExpired()) {
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    info = tokenCache.get(tokenId);
                    if (info == null || info.isExpired()) {
                        info = fetchTokenWithRetry(tokenId);
                        tokenCache.put(tokenId, info);
                    }
                    lock.readLock().lock();
                } finally {
                    lock.writeLock().unlock();
                }
            }
            
            if (autoRefresh && info.needsRefresh()) {
                scheduleRefresh(tokenId);
            }
            
            return info.getToken();
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public Optional<TokenInfo> getTokenInfo(String tokenId) {
        return Optional.ofNullable(tokenCache.get(tokenId));
    }
    
    public void refreshToken(String tokenId) {
        lock.writeLock().lock();
        try {
            TokenInfo info = fetchTokenWithRetry(tokenId);
            tokenCache.put(tokenId, info);
            logger.info("令牌刷新成功: id={}, expiresIn={}s", tokenId, info.getExpiresIn());
        } catch (Exception e) {
            logger.error("令牌刷新失败: id={}, error={}", tokenId, e.getMessage());
            throw e;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void invalidateToken(String tokenId) {
        lock.writeLock().lock();
        try {
            TokenInfo removed = tokenCache.remove(tokenId);
            if (removed != null) {
                clearTokenFromMemory(removed);
                logger.info("令牌已失效: id={}", tokenId);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void invalidateAllTokens() {
        lock.writeLock().lock();
        try {
            tokenCache.forEach((id, info) -> clearTokenFromMemory(info));
            tokenCache.clear();
            logger.info("所有令牌已失效");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private TokenInfo fetchTokenWithRetry(String tokenId) {
        TokenConfig config = tokenConfigs.get(tokenId);
        if (config == null) {
            throw new TokenException("未找到令牌配置: " + tokenId);
        }
        
        int maxRetries = config.getMaxRetries();
        long initialDelay = config.getInitialRetryDelayMs();
        double backoffFactor = config.getBackoffFactor();
        
        Exception lastException = null;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = (long) (initialDelay * Math.pow(backoffFactor, attempt - 1));
                    logger.info("令牌获取重试: id={}, attempt={}/{}, delay={}ms", 
                            tokenId, attempt, maxRetries, delay);
                    Thread.sleep(delay);
                }
                
                return fetchTokenFromServer(config);
                
            } catch (TokenException e) {
                lastException = e;
                logger.warn("令牌获取失败: id={}, attempt={}, error={}", 
                        tokenId, attempt, e.getMessage());
                
                if (!isRetryable(e) || attempt >= maxRetries) {
                    throw e;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TokenException("令牌获取被中断", e);
            } catch (Exception e) {
                lastException = e;
                logger.error("令牌获取异常: id={}, attempt={}, error={}", 
                        tokenId, attempt, e.getMessage());
                
                if (attempt >= maxRetries) {
                    throw new TokenException("令牌获取失败: " + e.getMessage(), e);
                }
            }
        }
        
        throw new TokenException("令牌获取失败，已达到最大重试次数", lastException);
    }
    
    private TokenInfo fetchTokenFromServer(TokenConfig config) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(config.getTokenEndpoint()))
                .timeout(Duration.ofSeconds(30));
        
        String requestBody;
        if (config.getGrantType() != null) {
            requestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
            StringBuilder form = new StringBuilder();
            form.append("grant_type=").append(config.getGrantType());
            if (config.getClientId() != null) {
                form.append("&client_id=").append(config.getClientId());
            }
            if (config.getClientSecret() != null) {
                form.append("&client_secret=").append(config.getClientSecret());
            }
            if (config.getScope() != null) {
                form.append("&scope=").append(config.getScope());
            }
            requestBody = form.toString();
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(requestBody));
        } else if (config.getCustomBodySupplier() != null) {
            requestBuilder.header("Content-Type", "application/json");
            requestBody = config.getCustomBodySupplier().get();
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(requestBody));
        } else {
            requestBuilder.GET();
        }
        
        if (config.getCustomHeaders() != null) {
            config.getCustomHeaders().forEach(requestBuilder::header);
        }
        
        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 400) {
            throw new TokenException("令牌获取失败: HTTP " + response.statusCode() + 
                    " - " + response.body(), response.statusCode());
        }
        
        return parseTokenResponse(response.body(), config);
    }
    
    private TokenInfo parseTokenResponse(String responseBody, TokenConfig config) throws Exception {
        Map<String, Object> responseMap = JSON_MAPPER.readValue(responseBody, Map.class);
        
        String accessToken = (String) responseMap.get("access_token");
        if (accessToken == null) {
            accessToken = (String) responseMap.get("accessToken");
        }
        if (accessToken == null) {
            throw new TokenException("响应中未找到access_token");
        }
        
        Integer expiresIn = null;
        Object expiresInObj = responseMap.get("expires_in");
        if (expiresInObj == null) {
            expiresInObj = responseMap.get("expiresIn");
        }
        if (expiresInObj instanceof Number) {
            expiresIn = ((Number) expiresInObj).intValue();
        } else if (expiresInObj instanceof String) {
            expiresIn = Integer.parseInt((String) expiresInObj);
        }
        
        if (expiresIn == null) {
            expiresIn = config.getDefaultExpirySeconds();
        }
        
        String refreshToken = (String) responseMap.get("refresh_token");
        if (refreshToken == null) {
            refreshToken = (String) responseMap.get("refreshToken");
        }
        
        String tokenType = (String) responseMap.get("token_type");
        if (tokenType == null) {
            tokenType = (String) responseMap.get("tokenType");
        }
        if (tokenType == null) {
            tokenType = "Bearer";
        }
        
        long now = System.currentTimeMillis();
        int effectiveExpiresIn = Math.max(expiresIn - config.getExpiryBufferSeconds(), 60);
        
        char[] secureToken = accessToken.toCharArray();
        
        return new TokenInfo(
                secureToken,
                tokenType,
                effectiveExpiresIn,
                refreshToken != null ? refreshToken.toCharArray() : null,
                now,
                now + effectiveExpiresIn * 1000L,
                config.getRefreshBufferMs()
        );
    }
    
    private void scheduleRefresh(String tokenId) {
        TokenInfo info = tokenCache.get(tokenId);
        TokenConfig config = tokenConfigs.get(tokenId);
        
        if (info == null || config == null) return;
        
        long refreshTime = info.getExpiresAt() - config.getRefreshBufferMs() - System.currentTimeMillis();
        refreshTime = Math.max(refreshTime, 1000);
        
        scheduler.schedule(() -> {
            try {
                refreshToken(tokenId);
            } catch (Exception e) {
                logger.error("定时刷新令牌失败: id={}", tokenId, e);
            }
        }, refreshTime, TimeUnit.MILLISECONDS);
        
        logger.debug("调度令牌刷新: id={}, after={}ms", tokenId, refreshTime);
    }
    
    private void startRefreshTask() {
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
                this::checkAndRefreshTokens,
                30_000,
                30_000,
                TimeUnit.MILLISECONDS
        );
        refreshTask.set(task);
        logger.info("令牌刷新任务已启动");
    }
    
    private void checkAndRefreshTokens() {
        tokenConfigs.keySet().forEach(tokenId -> {
            try {
                TokenInfo info = tokenCache.get(tokenId);
                if (info != null && info.needsRefresh()) {
                    logger.info("检测到令牌需要刷新: id={}", tokenId);
                    refreshToken(tokenId);
                }
            } catch (Exception e) {
                logger.error("检查刷新令牌失败: id={}", tokenId, e);
            }
        });
    }
    
    private boolean isRetryable(TokenException e) {
        int statusCode = e.getStatusCode();
        return statusCode == 0 || statusCode >= 500 || statusCode == 429;
    }
    
    private void clearTokenFromMemory(TokenInfo info) {
        if (info.getTokenChars() != null) {
            Arrays.fill(info.getTokenChars(), '\0');
        }
        if (info.getRefreshTokenChars() != null) {
            Arrays.fill(info.getRefreshTokenChars(), '\0');
        }
    }
    
    public void shutdown() {
        ScheduledFuture<?> task = refreshTask.get();
        if (task != null) {
            task.cancel(false);
        }
        scheduler.shutdown();
        invalidateAllTokens();
        logger.info("TokenManager已关闭");
    }
    
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("initialized", initialized);
        status.put("registeredTokens", tokenConfigs.keySet());
        status.put("cachedTokens", tokenCache.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> Map.of(
                                "tokenType", e.getValue().getTokenType(),
                                "expiresIn", e.getValue().getExpiresIn(),
                                "expired", e.getValue().isExpired(),
                                "needsRefresh", e.getValue().needsRefresh()
                        )
                )));
        return status;
    }
}
