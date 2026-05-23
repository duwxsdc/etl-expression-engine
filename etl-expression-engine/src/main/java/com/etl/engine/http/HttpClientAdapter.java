package com.etl.engine.http;

public interface HttpClientAdapter {
    
    HttpResponse execute(String method, String url, 
                        java.util.Map<String, String> headers, 
                        byte[] body, 
                        int timeoutMillis);
    
    void shutdown();
    
    String getName();
}
