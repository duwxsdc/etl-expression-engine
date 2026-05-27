package com.etl.engine.rest.enhanced;

import org.springframework.http.MediaType;

import java.util.Map;

public final class EnhancedRequestBodySpec {
    
    private final EnhancedRequestSpec requestSpec;
    
    EnhancedRequestBodySpec(EnhancedRequestSpec requestSpec) {
        this.requestSpec = requestSpec;
    }
    
    public EnhancedRequestBodySpec body(Object body) {
        requestSpec.setBody(body);
        return this;
    }
    
    public EnhancedRequestBodySpec body(String body) {
        requestSpec.setBody(body);
        return this;
    }
    
    public EnhancedResponseSpec retrieve() {
        return requestSpec.retrieve();
    }
    
    public <T> T body(Class<T> bodyType) {
        return requestSpec.body(bodyType);
    }
    
    public <T> ResponseEntity<T> toEntity(Class<T> bodyType) {
        return requestSpec.toEntity(bodyType);
    }
    
    public EnhancedRequestBodySpec header(String headerName, String... headerValues) {
        requestSpec.header(headerName, headerValues);
        return this;
    }
    
    public EnhancedRequestBodySpec defaultToken() {
        requestSpec.defaultToken();
        return this;
    }
    
    public EnhancedRequestBodySpec callback() {
        requestSpec.callback();
        return this;
    }
    
    public EnhancedRequestBodySpec callback(long timeoutMs) {
        requestSpec.callback(timeoutMs);
        return this;
    }
    
    public EnhancedRequestBodySpec callback(String eventId, long timeoutMs) {
        requestSpec.callback(eventId, timeoutMs);
        return this;
    }
    
    public EnhancedRequestBodySpec enable(String... extensionNames) {
        requestSpec.enable(extensionNames);
        return this;
    }
    
    EnhancedRequestSpec getRequestSpec() {
        return requestSpec;
    }
}
