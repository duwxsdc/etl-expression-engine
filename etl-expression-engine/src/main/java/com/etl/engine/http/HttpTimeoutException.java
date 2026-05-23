package com.etl.engine.http;

public class HttpTimeoutException extends HttpException {
    
    private final int timeoutMillis;
    
    public HttpTimeoutException(String message, int timeoutMillis) {
        super(message);
        this.timeoutMillis = timeoutMillis;
    }
    
    public HttpTimeoutException(String message, int timeoutMillis, Throwable cause) {
        super(message, cause);
        this.timeoutMillis = timeoutMillis;
    }
    
    public int getTimeoutMillis() {
        return timeoutMillis;
    }
}
