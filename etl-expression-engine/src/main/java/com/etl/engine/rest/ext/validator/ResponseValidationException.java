package com.etl.engine.rest.ext.validator;

/**
 * 响应校验异常
 */
public class ResponseValidationException extends RuntimeException {

    public ResponseValidationException(String message) {
        super(message);
    }

    public ResponseValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
