package com.etl.engine.http;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HeadersBuilder {
    
    private final Map<String, String> headers = new LinkedHashMap<>();
    
    public HeadersBuilder add(String key, String value) {
        headers.put(key, value);
        return this;
    }
    
    public HeadersBuilder contentType(String value) {
        headers.put("Content-Type", value);
        return this;
    }
    
    public HeadersBuilder accept(String value) {
        headers.put("Accept", value);
        return this;
    }
    
    public HeadersBuilder authorization(String value) {
        headers.put("Authorization", value);
        return this;
    }
    
    public HeadersBuilder bearerAuth(String token) {
        headers.put("Authorization", "Bearer " + token);
        return this;
    }
    
    public HeadersBuilder basicAuth(String username, String password) {
        String encoded = java.util.Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes());
        headers.put("Authorization", "Basic " + encoded);
        return this;
    }
    
    Map<String, String> build() {
        return Map.copyOf(headers);
    }
}
