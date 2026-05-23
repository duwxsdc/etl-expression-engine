package com.etl.engine.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AsyncHttpRequestImpl implements AsyncHttpRequest {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncHttpRequestImpl.class);
    
    private final CompletableFuture<HttpResponse> future;
    private final long requestId;
    private volatile boolean consumed = false;
    
    AsyncHttpRequestImpl(CompletableFuture<HttpResponse> future) {
        this.future = AsyncRequestManager.wrapFuture(future, "async-http-request");
        this.requestId = AsyncRequestManager.registerRequest(this.future, "async-http", "UNKNOWN");
    }
    
    @Override
    public CompletableFuture<HttpResponse> future() {
        return future;
    }
    
    @Override
    public void thenAccept(Consumer<HttpResponse> handler) {
        if (handler == null) {
            throw new IllegalArgumentException("处理器不能为null");
        }
        
        consumed = true;
        future.thenAccept(response -> {
            try {
                handler.accept(response);
            } catch (Exception e) {
                logger.error("异步请求回调处理异常: requestId={}, error={}", requestId, e.getMessage());
            } finally {
                AsyncRequestManager.completeRequest(requestId);
            }
        });
    }
    
    @Override
    public void thenApply(Function<HttpResponse, ?> handler) {
        if (handler == null) {
            throw new IllegalArgumentException("处理器不能为null");
        }
        
        consumed = true;
        future.thenApply(response -> {
            try {
                return handler.apply(response);
            } catch (Exception e) {
                logger.error("异步请求回调处理异常: requestId={}, error={}", requestId, e.getMessage());
                throw e;
            } finally {
                AsyncRequestManager.completeRequest(requestId);
            }
        });
    }
    
    @Override
    public HttpResponse get() {
        try {
            consumed = true;
            HttpResponse response = future.get();
            AsyncRequestManager.completeRequest(requestId);
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            AsyncRequestManager.completeRequest(requestId);
            throw new HttpException("异步请求被中断", e);
        } catch (ExecutionException e) {
            AsyncRequestManager.completeRequest(requestId);
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
            consumed = true;
            HttpResponse response = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            AsyncRequestManager.completeRequest(requestId);
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            AsyncRequestManager.completeRequest(requestId);
            throw new HttpException("异步请求被中断", e);
        } catch (ExecutionException e) {
            AsyncRequestManager.completeRequest(requestId);
            Throwable cause = e.getCause();
            if (cause instanceof HttpException he) {
                throw he;
            }
            throw new HttpException("异步请求失败: " + cause.getMessage(), cause);
        } catch (TimeoutException e) {
            AsyncRequestManager.completeRequest(requestId);
            throw new HttpTimeoutException("异步请求超时: " + timeoutMillis + "ms", (int) timeoutMillis, e);
        }
    }
    
    public boolean isDone() {
        return future.isDone();
    }
    
    public boolean isCancelled() {
        return future.isCancelled();
    }
    
    public boolean cancel() {
        boolean cancelled = future.cancel(true);
        if (cancelled) {
            AsyncRequestManager.completeRequest(requestId);
            logger.debug("异步请求已取消: requestId={}", requestId);
        }
        return cancelled;
    }
    
    public long getRequestId() {
        return requestId;
    }
    
    public AsyncHttpRequest withTimeout(long timeoutMillis) {
        CompletableFuture<HttpResponse> timeoutFuture = AsyncRequestManager.withTimeout(future, timeoutMillis);
        return new AsyncHttpRequestImpl(timeoutFuture);
    }
    
    @Override
    protected void finalize() throws Throwable {
        try {
            if (!consumed && !future.isDone()) {
                logger.warn("异步请求未被消费且未完成: requestId={}, 建议调用get()或thenAccept()", requestId);
                future.cancel(true);
            }
            AsyncRequestManager.completeRequest(requestId);
        } finally {
            super.finalize();
        }
    }
}
