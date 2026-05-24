package com.etl.engine.security;

import java.util.Arrays;

public class TokenInfo {
    
    private final char[] token;
    private final String tokenType;
    private final int expiresIn;
    private final char[] refreshToken;
    private final long createdAt;
    private final long expiresAt;
    private final long refreshBufferMs;
    
    public TokenInfo(char[] token, String tokenType, int expiresIn, 
                     char[] refreshToken, long createdAt, long expiresAt, long refreshBufferMs) {
        this.token = token;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.refreshToken = refreshToken;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.refreshBufferMs = refreshBufferMs;
    }
    
    public String getToken() {
        return token != null ? new String(token) : null;
    }
    
    public char[] getTokenChars() {
        return token != null ? Arrays.copyOf(token, token.length) : null;
    }
    
    public String getTokenType() {
        return tokenType;
    }
    
    public int getExpiresIn() {
        return expiresIn;
    }
    
    public String getRefreshToken() {
        return refreshToken != null ? new String(refreshToken) : null;
    }
    
    public char[] getRefreshTokenChars() {
        return refreshToken != null ? Arrays.copyOf(refreshToken, refreshToken.length) : null;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public long getExpiresAt() {
        return expiresAt;
    }
    
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }
    
    public boolean needsRefresh() {
        return System.currentTimeMillis() >= (expiresAt - refreshBufferMs);
    }
    
    public long getTimeUntilExpiry() {
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }
    
    public long getTimeUntilRefresh() {
        return Math.max(0, (expiresAt - refreshBufferMs) - System.currentTimeMillis());
    }
    
    @Override
    public String toString() {
        return "TokenInfo{" +
                "tokenType='" + tokenType + '\'' +
                ", expiresIn=" + expiresIn +
                ", createdAt=" + createdAt +
                ", expiresAt=" + expiresAt +
                ", expired=" + isExpired() +
                ", needsRefresh=" + needsRefresh() +
                '}';
    }
}
