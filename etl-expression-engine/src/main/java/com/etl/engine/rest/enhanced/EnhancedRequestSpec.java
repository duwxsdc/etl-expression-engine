package com.etl.engine.rest.enhanced;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.function.Function;

public final class EnhancedRequestSpec {
    
    private final RestClient.RequestHeadersSpec<?> delegate;
    private final String method;
    private final ExtensionRegistry extensionRegistry;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final Set<String> enabledExtensions = new LinkedHashSet<>();
    private final Map<String, Object> attributes = new HashMap<>();
    private String url;
    private Object body;
    private MediaType contentType;
    private boolean callbackEnabled = false;
    private long callbackTimeoutMs = 30000;
    private String callbackEventId;
    
    EnhancedRequestSpec(RestClient.RequestHeadersSpec<?> delegate, String method, ExtensionRegistry registry) {
        this.delegate = delegate;
        this.method = method;
        this.extensionRegistry = registry;
    }
    
    public EnhancedRequestSpec uri(String uri) {
        this.url = uri;
        return this;
    }
    
    public EnhancedRequestSpec uri(String uri, Object... uriVariables) {
        this.url = uri;
        return this;
    }
    
    public EnhancedRequestSpec header(String headerName, String... headerValues) {
        if (headerValues.length == 1) {
            headers.put(headerName, headerValues[0]);
        }
        return this;
    }
    
    public EnhancedRequestSpec headers(Consumer<org.springframework.http.HttpHeaders> headersConsumer) {
        return this;
    }
    
    public EnhancedRequestSpec accept(MediaType... acceptableMediaTypes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < acceptableMediaTypes.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(acceptableMediaTypes[i].toString());
        }
        headers.put("Accept", sb.toString());
        return this;
    }
    
    public EnhancedRequestSpec accept(String... acceptableMediaTypes) {
        headers.put("Accept", String.join(", ", acceptableMediaTypes));
        return this;
    }
    
    public EnhancedRequestBodySpec contentType(MediaType contentType) {
        this.contentType = contentType;
        headers.put("Content-Type", contentType.toString());
        return new EnhancedRequestBodySpec(this);
    }
    
    public EnhancedRequestBodySpec contentType(String contentType) {
        this.contentType = MediaType.parseMediaType(contentType);
        headers.put("Content-Type", contentType);
        return new EnhancedRequestBodySpec(this);
    }
    
    public EnhancedRequestSpec ifModifiedSince(long ifModifiedSince) {
        headers.put("If-Modified-Since", String.valueOf(ifModifiedSince));
        return this;
    }
    
    public EnhancedRequestSpec ifNoneMatch(String... ifNoneMatches) {
        headers.put("If-None-Match", String.join(", ", ifNoneMatches));
        return this;
    }
    
    public EnhancedResponseSpec retrieve() {
        applyExtensions();
        applyHeadersToDelegate();
        return new EnhancedResponseSpec(delegate.retrieve(), this);
    }
    
    public <T> T body(Class<T> bodyType) {
        applyExtensions();
        applyHeadersToDelegate();
        return delegate.retrieve().body(bodyType);
    }
    
    public <T> ResponseEntity<T> toEntity(Class<T> bodyType) {
        applyExtensions();
        applyHeadersToDelegate();
        org.springframework.http.ResponseEntity<T> response = delegate.retrieve().toEntity(bodyType);
        return new ResponseEntity<>(response);
    }
    
    public EnhancedRequestSpec defaultToken() {
        enabledExtensions.add("defaultToken");
        return this;
    }
    
    public EnhancedRequestSpec defaultToken(Function<String, String> tokenProvider) {
        attributes.put("tokenProvider", tokenProvider);
        enabledExtensions.add("defaultToken");
        return this;
    }
    
    public EnhancedRequestSpec callback() {
        this.callbackEnabled = true;
        enabledExtensions.add("callback");
        return this;
    }
    
    public EnhancedRequestSpec callback(long timeoutMs) {
        this.callbackEnabled = true;
        this.callbackTimeoutMs = timeoutMs;
        enabledExtensions.add("callback");
        return this;
    }
    
    public EnhancedRequestSpec callback(String eventId, long timeoutMs) {
        this.callbackEnabled = true;
        this.callbackEventId = eventId;
        this.callbackTimeoutMs = timeoutMs;
        enabledExtensions.add("callback");
        return this;
    }
    
    public EnhancedRequestSpec enable(String extensionName) {
        enabledExtensions.add(extensionName);
        return this;
    }
    
    public EnhancedRequestSpec enable(String... extensionNames) {
        Collections.addAll(enabledExtensions, extensionNames);
        return this;
    }
    
    public EnhancedRequestSpec attribute(String key, Object value) {
        attributes.put(key, value);
        return this;
    }
    
    @SuppressWarnings("unchecked")
    public <T> T attribute(String key) {
        return (T) attributes.get(key);
    }
    
    String getUrl() {
        return url;
    }
    
    String getMethod() {
        return method;
    }
    
    Map<String, String> getHeaders() {
        return headers;
    }
    
    Object getBody() {
        return body;
    }
    
    void setBody(Object body) {
        this.body = body;
    }
    
    MediaType getContentType() {
        return contentType;
    }
    
    boolean isCallbackEnabled() {
        return callbackEnabled;
    }
    
    long getCallbackTimeoutMs() {
        return callbackTimeoutMs;
    }
    
    String getCallbackEventId() {
        return callbackEventId;
    }
    
    void setCallbackEventId(String eventId) {
        this.callbackEventId = eventId;
    }
    
    Map<String, Object> getAttributes() {
        return attributes;
    }
    
    RestClient.RequestHeadersSpec<?> getDelegate() {
        return delegate;
    }
    
    private void applyExtensions() {
        if (!enabledExtensions.isEmpty()) {
            ExtensionContextImpl context = new ExtensionContextImpl();
            extensionRegistry.applySelected(context, enabledExtensions);
        }
    }
    
    private void applyHeadersToDelegate() {
        for (Map.Entry<String, String> header : headers.entrySet()) {
            delegate.header(header.getKey(), header.getValue());
        }
    }
    
    @FunctionalInterface
    public interface Consumer<T> {
        void accept(T t);
    }
    
    private class ExtensionContextImpl implements RestClientExtension.ExtensionContext {
        @Override
        public String getUrl() {
            return url;
        }
        
        @Override
        public String getMethod() {
            return method;
        }
        
        @Override
        public Map<String, String> getHeaders() {
            return headers;
        }
        
        @Override
        public Object getBody() {
            return body;
        }
        
        @Override
        public void addHeader(String name, String value) {
            headers.put(name, value);
        }
        
        @Override
        public void setBody(Object body) {
            EnhancedRequestSpec.this.body = body;
        }
        
        @Override
        public <T> void setAttribute(String key, T value) {
            attributes.put(key, value);
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public <T> T getAttribute(String key) {
            return (T) attributes.get(key);
        }
    }
}
