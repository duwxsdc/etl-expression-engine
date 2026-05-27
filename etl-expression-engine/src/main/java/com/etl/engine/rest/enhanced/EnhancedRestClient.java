package com.etl.engine.rest.enhanced;

import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

import java.util.Map;

public final class EnhancedRestClient {
    
    private final RestClient delegate;
    private final ExtensionRegistry extensionRegistry;
    
    private EnhancedRestClient(RestClient delegate, ExtensionRegistry extensionRegistry) {
        this.delegate = delegate;
        this.extensionRegistry = extensionRegistry;
    }
    
    public static EnhancedRestClient create(RestClient restClient) {
        return new EnhancedRestClient(restClient, ExtensionRegistry.getInstance());
    }
    
    public static EnhancedRestClient create(RestClient restClient, ExtensionRegistry registry) {
        return new EnhancedRestClient(restClient, registry);
    }
    
    public EnhancedRequestSpec get() {
        return new EnhancedRequestSpec(delegate.get(), "GET", extensionRegistry);
    }
    
    public EnhancedRequestSpec post() {
        return new EnhancedRequestSpec(delegate.post(), "POST", extensionRegistry);
    }
    
    public EnhancedRequestSpec put() {
        return new EnhancedRequestSpec(delegate.put(), "PUT", extensionRegistry);
    }
    
    public EnhancedRequestSpec delete() {
        return new EnhancedRequestSpec(delegate.delete(), "DELETE", extensionRegistry);
    }
    
    public EnhancedRequestSpec patch() {
        return new EnhancedRequestSpec(delegate.patch(), "PATCH", extensionRegistry);
    }
    
    public EnhancedRequestSpec head() {
        return new EnhancedRequestSpec(delegate.head(), "HEAD", extensionRegistry);
    }
    
    public EnhancedRequestSpec options() {
        return new EnhancedRequestSpec(delegate.options(), "OPTIONS", extensionRegistry);
    }
    
    public EnhancedRequestSpec method(String method) {
        return new EnhancedRequestSpec(delegate.method(HttpMethod.valueOf(method.toUpperCase())), method.toUpperCase(), extensionRegistry);
    }
    
    public EnhancedRequestSpec method(HttpMethod method) {
        return new EnhancedRequestSpec(delegate.method(method), method.name(), extensionRegistry);
    }
    
    public RestClient getDelegate() {
        return delegate;
    }
    
    public ExtensionRegistry getExtensionRegistry() {
        return extensionRegistry;
    }
}
