package com.etl.engine.rest.enhanced;

public class CallbackTimeoutException extends CallbackException {
    
    public CallbackTimeoutException(String message) {
        super(message);
    }
    
    public CallbackTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}