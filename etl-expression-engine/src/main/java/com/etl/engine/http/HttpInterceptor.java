package com.etl.engine.http;

public interface HttpInterceptor {
    
    default boolean beforeRequest(HttpRequestContext context) {
        return true;
    }
    
    default void afterResponse(HttpRequestContext context, HttpResponse response) {
    }
    
    default void onError(HttpRequestContext context, Exception error) {
    }
}
