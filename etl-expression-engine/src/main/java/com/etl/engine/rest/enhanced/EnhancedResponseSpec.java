package com.etl.engine.rest.enhanced;

import org.springframework.web.client.RestClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class EnhancedResponseSpec {
    
    private final RestClient.ResponseSpec delegate;
    private final EnhancedRequestSpec requestSpec;
    
    EnhancedResponseSpec(RestClient.ResponseSpec delegate, EnhancedRequestSpec requestSpec) {
        this.delegate = delegate;
        this.requestSpec = requestSpec;
    }
    
    public <T> T body(Class<T> bodyType) {
        T result = delegate.body(bodyType);
        if (requestSpec.isCallbackEnabled()) {
            return handleCallback(result);
        }
        return result;
    }
    
    public String body(String bodyType) {
        String result = delegate.body(String.class);
        if (requestSpec.isCallbackEnabled()) {
            return handleCallback(result);
        }
        return result;
    }
    
    public <T> ResponseEntity<T> toEntity(Class<T> bodyType) {
        org.springframework.http.ResponseEntity<T> response = delegate.toEntity(bodyType);
        if (requestSpec.isCallbackEnabled()) {
            T callbackResult = handleCallback(response.getBody());
            return new ResponseEntity<>(response.getStatusCode(), response.getHeaders(), callbackResult);
        }
        return new ResponseEntity<>(response);
    }
    
    public org.springframework.http.ResponseEntity<Void> toBodilessEntity() {
        return delegate.toBodilessEntity();
    }
    
    @SuppressWarnings("unchecked")
    private <T> T handleCallback(T initialResult) {
        CallbackManager callbackManager = CallbackManager.getInstance();
        String eventId = requestSpec.getCallbackEventId();
        
        if (eventId == null) {
            eventId = callbackManager.generateEventId();
            requestSpec.setCallbackEventId(eventId);
        }
        
        long timeoutMs = requestSpec.getCallbackTimeoutMs();
        CompletableFuture<Object> future = callbackManager.register(eventId, timeoutMs);
        
        try {
            Object callbackResult = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return (T) callbackResult;
        } catch (TimeoutException e) {
            callbackManager.cleanup(eventId);
            throw new CallbackTimeoutException("Callback timeout: eventId=" + eventId + ", timeout=" + timeoutMs + "ms", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            callbackManager.cleanup(eventId);
            throw new CallbackInterruptedException("Callback interrupted: eventId=" + eventId, e);
        } catch (ExecutionException e) {
            callbackManager.cleanup(eventId);
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                throw new CallbackTimeoutException("Callback timeout: eventId=" + eventId, cause);
            }
            throw new CallbackException("Callback failed: eventId=" + eventId, cause);
        }
    }
}
