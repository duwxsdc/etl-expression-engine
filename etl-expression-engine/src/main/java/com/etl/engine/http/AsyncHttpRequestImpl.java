package com.etl.engine.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 异步HTTP请求实现类，封装了异步HTTP请求的执行和结果处理。
 * <p>
 * 该类实现了{@link AsyncHttpRequest}密封接口，内部使用{@link CompletableFuture}进行异步操作管理。
 * 提供了阻塞等待、回调注册、取消请求等功能，并与{@link AsyncRequestManager}集成实现请求生命周期管理。
 * </p>
 *
 * @author ETL Engine
 * @version 1.0
 * @since 1.0
 */
public final class AsyncHttpRequestImpl implements AsyncHttpRequest {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncHttpRequestImpl.class);
    
    /**
     * 底层的异步响应Future
     */
    private final CompletableFuture<HttpResponse> future;
    
    /**
     * 请求标识ID
     */
    private final long requestId;
    
    /**
     * 请求是否已被消费标记
     */
    private volatile boolean consumed = false;
    
    /**
     * 构造异步HTTP请求实现实例。
     * <p>
     * 自动将Future注册到{@link AsyncRequestManager}进行生命周期管理。
     * </p>
     *
     * @param future 异步响应Future对象
     */
    AsyncHttpRequestImpl(CompletableFuture<HttpResponse> future) {
        this.future = AsyncRequestManager.wrapFuture(future, "async-http-request");
        this.requestId = AsyncRequestManager.registerRequest(this.future, "async-http", "UNKNOWN");
    }
    
    /**
     * 获取底层CompletableFuture对象，用于高级异步操作。
     *
     * @return 异步请求的CompletableFuture对象
     */
    @Override
    public CompletableFuture<HttpResponse> future() {
        return future;
    }
    
    /**
     * 注册异步请求完成后的消费者回调。
     * <p>
     * 回调执行完毕后会自动通知{@link AsyncRequestManager}完成请求。
     * </p>
     *
     * @param handler 响应消费者处理器
     * @throws IllegalArgumentException 当处理器为null时抛出
     */
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
    
    /**
     * 注册异步请求完成后的函数回调，可对响应进行转换处理。
     * <p>
     * 回调执行完毕后会自动通知{@link AsyncRequestManager}完成请求。
     * </p>
     *
     * @param handler 响应函数处理器
     * @throws IllegalArgumentException 当处理器为null时抛出
     */
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
    
    /**
     * 阻塞等待异步请求完成并获取响应结果。
     *
     * @return HTTP响应对象
     * @throws HttpException 当请求失败或被中断时抛出
     */
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
    
    /**
     * 在指定超时时间内阻塞等待异步请求完成并获取响应结果。
     *
     * @param timeoutMillis 超时时间（毫秒）
     * @return HTTP响应对象
     * @throws HttpException 当请求失败或被中断时抛出
     * @throws HttpTimeoutException 当请求超时时抛出
     */
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
    
    /**
     * 检查异步请求是否已完成。
     *
     * @return 如果已完成返回true，否则返回false
     */
    public boolean isDone() {
        return future.isDone();
    }
    
    /**
     * 检查异步请求是否已取消。
     *
     * @return 如果已取消返回true，否则返回false
     */
    public boolean isCancelled() {
        return future.isCancelled();
    }
    
    /**
     * 取消异步请求。
     * <p>
     * 取消后会自动通知{@link AsyncRequestManager}完成请求。
     * </p>
     *
     * @return 如果成功取消返回true，否则返回false
     */
    public boolean cancel() {
        boolean cancelled = future.cancel(true);
        if (cancelled) {
            AsyncRequestManager.completeRequest(requestId);
            logger.debug("异步请求已取消: requestId={}", requestId);
        }
        return cancelled;
    }
    
    /**
     * 获取请求标识ID。
     *
     * @return 请求ID
     */
    public long getRequestId() {
        return requestId;
    }
    
    /**
     * 为异步请求设置超时时间。
     * <p>
     * 返回一个新的异步请求对象，该对象会在指定超时时间后自动超时。
     * </p>
     *
     * @param timeoutMillis 超时时间（毫秒）
     * @return 带超时设置的异步请求对象
     */
    public AsyncHttpRequest withTimeout(long timeoutMillis) {
        CompletableFuture<HttpResponse> timeoutFuture = AsyncRequestManager.withTimeout(future, timeoutMillis);
        return new AsyncHttpRequestImpl(timeoutFuture);
    }
    
    /**
     * 对象终结时检查请求状态。
     * <p>
     * 如果请求未被消费且未完成，将输出警告日志并取消请求，防止资源泄漏。
     * </p>
     *
     * @throws Throwable 终结过程中可能抛出的异常
     */
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
