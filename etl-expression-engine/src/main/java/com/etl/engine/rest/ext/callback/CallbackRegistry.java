package com.etl.engine.rest.ext.callback;

import com.etl.engine.callback.LocalEventManager;
import com.etl.engine.callback.LocalNodeInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 回调注册器（单例）
 * 
 * <p>委托LocalEventManager实现回调的注册、等待、唤醒机制。</p>
 * <p>支持分布式环境下的本机判断和转发。</p>
 */
@Slf4j
public final class CallbackRegistry {

    private static final CallbackRegistry INSTANCE = new CallbackRegistry();

    private LocalEventManager eventManager;
    private LocalNodeInfo nodeInfo;

    private CallbackRegistry() {}

    public static CallbackRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 初始化（由Spring自动配置调用）
     */
    public void init(LocalEventManager eventManager, LocalNodeInfo nodeInfo) {
        this.eventManager = eventManager;
        this.nodeInfo = nodeInfo;
        log.info("CallbackRegistry初始化完成 - 节点: {}", nodeInfo != null ? nodeInfo.getNodeId() : "unknown");
    }

    /**
     * 生成事件ID
     */
    public String generateEventId() {
        return eventManager != null ? eventManager.generateEventId()
                : java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 注册回调事件
     */
    public CompletableFuture<Object> register(String eventId, long timeoutMs) {
        if (eventManager != null) {
            eventManager.registerEvent(eventId, timeoutMs);
            return eventManager.getEvent(eventId)
                    .map(LocalEventManager.PendingEvent::future)
                    .orElseThrow(() -> new IllegalStateException("事件注册失败: " + eventId));
        }
        return CompletableFuture.supplyAsync(() -> null);
    }

    /**
     * 阻塞等待回调
     */
    public Object waitForCallback(String eventId, long timeoutMs) {
        try {
            Optional<LocalEventManager.PendingEvent> opt = eventManager.getEvent(eventId);
            if (opt.isPresent()) {
                return opt.get().future().get(timeoutMs, TimeUnit.MILLISECONDS);
            }
            throw new IllegalStateException("事件不存在: " + eventId);
        } catch (TimeoutException e) {
            throw new com.etl.engine.callback.CallbackTimeoutException(
                    "回调超时: eventId=" + eventId + ", timeout=" + timeoutMs + "ms", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new com.etl.engine.callback.CallbackTimeoutException(
                    "回调被中断: eventId=" + eventId, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                throw new com.etl.engine.callback.CallbackTimeoutException(
                        "回调超时: eventId=" + eventId, cause);
            }
            throw new RuntimeException("回调失败: eventId=" + eventId, cause);
        }
    }

    /**
     * 获取本机IP
     */
    public String getLocalIp() {
        return nodeInfo != null ? nodeInfo.getNodeIp() : "127.0.0.1";
    }

    /**
     * 获取本机端口
     */
    public int getLocalPort() {
        return nodeInfo != null ? nodeInfo.getServerPort() : 8080;
    }

    /**
     * 判断是否为本机节点
     */
    public boolean isLocalNode(String ip, int port) {
        return nodeInfo != null && nodeInfo.isLocalNode(ip, port);
    }
}
