package com.etl.engine.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.w3c.dom.Document;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public sealed interface HttpResponse permits HttpResponseImpl {
    
    int statusCode();
    
    String statusMessage();
    
    boolean isSuccess();
    
    boolean isRedirect();
    
    boolean isClientError();
    
    boolean isServerError();
    
    Map<String, String> headers();
    
    String header(String name);
    
    String asString();
    
    JsonNode asJson();
    
    Map<String, Object> asMap();
    
    Document asXml();
    
    <T> T asBean(Class<T> clazz);
    
    Object asJava(String className);
    
    <T> T asJava(Class<T> clazz);
    
    HttpResponse response(Consumer<HttpResponse> handler);
    
    <R> R response(Function<HttpResponse, R> handler);
    
    <T> T extract(String jsonPath);
    
    <T> T extract(String jsonPath, Class<T> type);
    
    <T> T custom(Function<String, T> parser);
    
    byte[] asBytes();
    
    long contentLength();
    
    String contentType();
    
    ResponseBody body();
}
