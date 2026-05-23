package com.etl.engine.http;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public sealed interface HttpRequestBuilder permits HttpRequestBuilderImpl {
    
    HttpRequestBuilder header(String key, String value);
    
    HttpRequestBuilder headers(Map<String, String> headers);
    
    HttpRequestBuilder header(Consumer<HeadersBuilder> consumer);
    
    HttpRequestBuilder body(Object data);
    
    HttpRequestBuilder bodyJson(Object data);
    
    HttpRequestBuilder bodyXml(String xml);
    
    HttpRequestBuilder bodyForm(Map<String, String> formData);
    
    HttpRequestBuilder pathVariables(Map<String, Object> variables);
    
    HttpRequestBuilder pathVariable(String key, Object value);
    
    HttpRequestBuilder queryVariables(Map<String, Object> variables);
    
    HttpRequestBuilder queryVariable(String key, Object value);
    
    HttpRequestBuilder timeout(int millis);
    
    HttpRequestBuilder contentType(String contentType);
    
    HttpRequestBuilder accept(String accept);
    
    HttpRequestBuilder basicAuth(String username, String password);
    
    HttpRequestBuilder bearerAuth(String token);
    
    HttpRequestBuilder retry(int maxRetries);
    
    HttpRequestBuilder retry(int maxRetries, long delayMillis);
    
    HttpRequestBuilder interceptor(HttpInterceptor interceptor);
    
    HttpRequestBuilder configure(Consumer<HttpRequestBuilder> configurator);
    
    HttpRequestBuilder sync();
    
    HttpRequestBuilder async();
    
    HttpResponse get();
    
    HttpResponse post();
    
    HttpResponse put();
    
    HttpResponse delete();
    
    HttpResponse patch();
    
    HttpResponse request(String method);
    
    AsyncHttpRequest asyncGet();
    
    AsyncHttpRequest asyncPost();
    
    AsyncHttpRequest asyncPut();
    
    AsyncHttpRequest asyncDelete();
}
