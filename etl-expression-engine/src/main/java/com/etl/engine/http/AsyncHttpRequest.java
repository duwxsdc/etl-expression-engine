package com.etl.engine.http;

import java.util.concurrent.CompletableFuture;

public sealed interface AsyncHttpRequest permits AsyncHttpRequestImpl {
    
    CompletableFuture<HttpResponse> future();
    
    void thenAccept(java.util.function.Consumer<HttpResponse> handler);
    
    void thenApply(java.util.function.Function<HttpResponse, ?> handler);
    
    HttpResponse get();
    
    HttpResponse get(long timeoutMillis);
}
