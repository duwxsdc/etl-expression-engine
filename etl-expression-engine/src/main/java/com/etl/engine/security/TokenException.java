package com.etl.engine.security;

public class TokenException extends RuntimeException {
    
    private final int statusCode;
    
    public TokenException(String message) {
        super(message);
        this.statusCode = 0;
    }
    
    public TokenException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
    
    public TokenException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }
    
    public TokenException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public boolean isNetworkError() {
        return statusCode == 0;
    }
    
    public boolean isAuthenticationError() {
        return statusCode == 401 || statusCode == 403;
    }
    
    public boolean isServerError() {
        return statusCode >= 500;
    }
    
    public boolean isRateLimited() {
        return statusCode == 429;
    }
}
