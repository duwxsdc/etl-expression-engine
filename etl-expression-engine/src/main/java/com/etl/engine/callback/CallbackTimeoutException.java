package com.etl.engine.callback;

public class CallbackTimeoutException extends RuntimeException {
    public CallbackTimeoutException(String message) {
        super(message);
    }
    
    public CallbackTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
