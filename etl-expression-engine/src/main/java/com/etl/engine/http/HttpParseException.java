package com.etl.engine.http;

public class HttpParseException extends HttpException {
    
    public HttpParseException(String message) {
        super(message);
    }
    
    public HttpParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
