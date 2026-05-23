package com.etl.engine.http;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AsyncHttpRequestImpl implements AsyncHttpRequest {
    
    private final CompletableFuture<HttpResponse> future;
    
    AsyncHttpRequestImpl(CompletableFuture<HttpResponse> future) {
        this.future = future;
    }
    
    @Override
    public CompletableFuture<HttpResponse> future() {
        return future;
    }
    
    @Override
    public void thenAccept(Consumer<HttpResponse> handler) {
        future.thenAccept(handler);
    }
    
    @Override
    public void thenApply(Function<HttpResponse, ?> handler) {
        future.thenApply(handler);
    }
    
    @Override
    public HttpResponse get() {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpException("异步请求被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof HttpException he) {
                throw he;
            }
            throw new HttpException("异步请求失败: " + cause.getMessage(), cause);
        }
    }
    
    @Override
    public HttpResponse get(long timeoutMillis) {
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpException("异步请求被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof HttpException he) {
                throw he;
            }
            throw new HttpException("异步请求失败: " + cause.getMessage(), cause);
        } catch (TimeoutException e) {
            throw new HttpTimeoutException("异步请求超时: " + timeoutMillis + "ms", (int) timeoutMillis, e);
        }
    }
}
