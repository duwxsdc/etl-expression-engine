package com.etl.engine.rest.enhanced;

public class CallbackInterruptedException extends CallbackException {
    
    public CallbackInterruptedException(String message) {
        super(message);
    }
    
    public CallbackInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }
}