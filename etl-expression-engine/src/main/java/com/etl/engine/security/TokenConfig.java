package com.etl.engine.security;

import java.util.Map;
import java.util.function.Supplier;

public class TokenConfig {
    
    private String tokenEndpoint;
    private String grantType;
    private String clientId;
    private String clientSecret;
    private String scope;
    private Map<String, String> customHeaders;
    private Supplier<String> customBodySupplier;
    
    private int maxRetries = 3;
    private long initialRetryDelayMs = 1000L;
    private double backoffFactor = 2.0;
    private long refreshBufferMs = 60_000L;
    private int expiryBufferSeconds = 300;
    private int defaultExpirySeconds = 3600;
    
    public TokenConfig() {
    }
    
    public static TokenConfig oauth2(String endpoint, String clientId, String clientSecret) {
        TokenConfig config = new TokenConfig();
        config.setTokenEndpoint(endpoint);
        config.setGrantType("client_credentials");
        config.setClientId(clientId);
        config.setClientSecret(clientSecret);
        return config;
    }
    
    public static TokenConfig custom(String endpoint, Map<String, String> headers, Supplier<String> bodySupplier) {
        TokenConfig config = new TokenConfig();
        config.setTokenEndpoint(endpoint);
        config.setCustomHeaders(headers);
        config.setCustomBodySupplier(bodySupplier);
        return config;
    }
    
    public static TokenConfig apiKey(String endpoint, String apiKeyHeader, String apiKey) {
        TokenConfig config = new TokenConfig();
        config.setTokenEndpoint(endpoint);
        config.setCustomHeaders(Map.of(apiKeyHeader, apiKey));
        return config;
    }
    
    public String getTokenEndpoint() {
        return tokenEndpoint;
    }
    
    public void setTokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }
    
    public String getGrantType() {
        return grantType;
    }
    
    public void setGrantType(String grantType) {
        this.grantType = grantType;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public String getClientSecret() {
        return clientSecret;
    }
    
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
    
    public String getScope() {
        return scope;
    }
    
    public void setScope(String scope) {
        this.scope = scope;
    }
    
    public Map<String, String> getCustomHeaders() {
        return customHeaders;
    }
    
    public void setCustomHeaders(Map<String, String> customHeaders) {
        this.customHeaders = customHeaders;
    }
    
    public Supplier<String> getCustomBodySupplier() {
        return customBodySupplier;
    }
    
    public void setCustomBodySupplier(Supplier<String> customBodySupplier) {
        this.customBodySupplier = customBodySupplier;
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = Math.max(maxRetries, 1);
    }
    
    public long getInitialRetryDelayMs() {
        return initialRetryDelayMs;
    }
    
    public void setInitialRetryDelayMs(long initialRetryDelayMs) {
        this.initialRetryDelayMs = Math.max(initialRetryDelayMs, 100);
    }
    
    public double getBackoffFactor() {
        return backoffFactor;
    }
    
    public void setBackoffFactor(double backoffFactor) {
        this.backoffFactor = Math.max(backoffFactor, 1.0);
    }
    
    public long getRefreshBufferMs() {
        return refreshBufferMs;
    }
    
    public void setRefreshBufferMs(long refreshBufferMs) {
        this.refreshBufferMs = Math.max(refreshBufferMs, 10_000L);
    }
    
    public int getExpiryBufferSeconds() {
        return expiryBufferSeconds;
    }
    
    public void setExpiryBufferSeconds(int expiryBufferSeconds) {
        this.expiryBufferSeconds = Math.max(expiryBufferSeconds, 30);
    }
    
    public int getDefaultExpirySeconds() {
        return defaultExpirySeconds;
    }
    
    public void setDefaultExpirySeconds(int defaultExpirySeconds) {
        this.defaultExpirySeconds = Math.max(defaultExpirySeconds, 60);
    }
    
    public TokenConfig withMaxRetries(int maxRetries) {
        this.setMaxRetries(maxRetries);
        return this;
    }
    
    public TokenConfig withRetryDelay(long initialDelayMs, double backoffFactor) {
        this.setInitialRetryDelayMs(initialDelayMs);
        this.setBackoffFactor(backoffFactor);
        return this;
    }
    
    public TokenConfig withRefreshBuffer(long bufferMs) {
        this.setRefreshBufferMs(bufferMs);
        return this;
    }
    
    public TokenConfig withExpiryBuffer(int bufferSeconds) {
        this.setExpiryBufferSeconds(bufferSeconds);
        return this;
    }
    
    public TokenConfig withDefaultExpiry(int expirySeconds) {
        this.setDefaultExpirySeconds(expirySeconds);
        return this;
    }
}
