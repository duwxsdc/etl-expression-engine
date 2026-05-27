package com.etl.engine.rest.enhanced;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public final class ResponseEntity<T> {
    
    private final HttpStatusCode statusCode;
    private final HttpHeaders headers;
    private final T body;
    
    public ResponseEntity(org.springframework.http.ResponseEntity<T> delegate) {
        this.statusCode = delegate.getStatusCode();
        this.headers = delegate.getHeaders();
        this.body = delegate.getBody();
    }
    
    public ResponseEntity(HttpStatusCode statusCode, HttpHeaders headers, T body) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body;
    }
    
    public HttpStatusCode getStatusCode() {
        return statusCode;
    }
    
    public int getStatusCodeValue() {
        return statusCode.value();
    }
    
    public HttpStatus getStatus() {
        if (statusCode instanceof HttpStatus) {
            return (HttpStatus) statusCode;
        }
        return HttpStatus.resolve(statusCode.value());
    }
    
    public HttpHeaders getHeaders() {
        return headers;
    }
    
    public T getBody() {
        return body;
    }
    
    public boolean is2xxSuccessful() {
        return statusCode.is2xxSuccessful();
    }
    
    public boolean is3xxRedirection() {
        return statusCode.is3xxRedirection();
    }
    
    public boolean is4xxClientError() {
        return statusCode.is4xxClientError();
    }
    
    public boolean is5xxServerError() {
        return statusCode.is5xxServerError();
    }
    
    public boolean isError() {
        return statusCode.isError();
    }
    
    @Override
    public String toString() {
        return "ResponseEntity{" +
                "statusCode=" + statusCode +
                ", headers=" + headers +
                ", body=" + body +
                '}';
    }
}
