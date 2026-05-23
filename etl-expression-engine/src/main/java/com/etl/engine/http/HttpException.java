package com.etl.engine.http;

public class HttpException extends RuntimeException {
    
    private final int statusCode;
    private final String url;
    
    public HttpException(String message) {
        super(message);
        this.statusCode = -1;
        this.url = null;
    }
    
    public HttpException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.url = null;
    }
    
    public HttpException(String message, int statusCode, String url) {
        super(message);
        this.statusCode = statusCode;
        this.url = url;
    }
    
    public HttpException(String message, int statusCode, String url, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.url = url;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public String getUrl() {
        return url;
    }
}
