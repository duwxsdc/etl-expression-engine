package com.etl.engine.rest.enhanced;

import java.util.function.Function;

public interface RestClientExtension {
    
    String name();
    
    int order();
    
    void apply(ExtensionContext context);
    
    interface ExtensionContext {
        String getUrl();
        String getMethod();
        java.util.Map<String, String> getHeaders();
        Object getBody();
        void addHeader(String name, String value);
        void setBody(Object body);
        <T> void setAttribute(String key, T value);
        <T> T getAttribute(String key);
    }
}
