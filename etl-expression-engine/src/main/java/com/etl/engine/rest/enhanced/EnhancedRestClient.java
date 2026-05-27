package com.etl.engine.rest.enhanced;

import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

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
        return new EnhancedRequestSpec(delegate, HttpMethod.GET, extensionRegistry);
    }
    
    public EnhancedRequestSpec post() {
        return new EnhancedRequestSpec(delegate, HttpMethod.POST, extensionRegistry);
    }
    
    public EnhancedRequestSpec put() {
        return new EnhancedRequestSpec(delegate, HttpMethod.PUT, extensionRegistry);
    }
    
    public EnhancedRequestSpec delete() {
        return new EnhancedRequestSpec(delegate, HttpMethod.DELETE, extensionRegistry);
    }
    
    public EnhancedRequestSpec patch() {
        return new EnhancedRequestSpec(delegate, HttpMethod.PATCH, extensionRegistry);
    }
    
    public EnhancedRequestSpec head() {
        return new EnhancedRequestSpec(delegate, HttpMethod.HEAD, extensionRegistry);
    }
    
    public EnhancedRequestSpec options() {
        return new EnhancedRequestSpec(delegate, HttpMethod.OPTIONS, extensionRegistry);
    }
    
    public EnhancedRequestSpec method(String method) {
        return new EnhancedRequestSpec(delegate, HttpMethod.valueOf(method.toUpperCase()), extensionRegistry);
    }
    
    public EnhancedRequestSpec method(HttpMethod method) {
        return new EnhancedRequestSpec(delegate, method, extensionRegistry);
    }
    
    public RestClient getDelegate() {
        return delegate;
    }
    
    public ExtensionRegistry getExtensionRegistry() {
        return extensionRegistry;
    }
}
