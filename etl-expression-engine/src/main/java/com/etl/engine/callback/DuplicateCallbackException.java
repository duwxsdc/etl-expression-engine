package com.etl.engine.callback;

/**
 * 重复回调异常
 * 
 * <p>当同一个eventId被多次回调时，第二次及之后的回调会被忽略，
 * 并可能在某些场景下抛出此异常以提示调用方。</p>
 * 
 * <h3>设计说明：</h3>
 * <p>在分布式回调场景中，由于网络重传或第三方服务重试，
 * 可能出现同一事件被多次回调的情况。系统通过eventId去重，
 * 确保每个事件只被处理一次，保证幂等性。</p>
 * 
 * @author ETL Engine Team
 * @version 1.0.0
 * @since 2026-05-24
 * @see LocalEventManager#completeEvent(String, Object)
 */
public class DuplicateCallbackException extends RuntimeException {
    
    /**
     * 构造重复回调异常
     * 
     * @param message 异常描述信息，通常包含重复的eventId
     */
    public DuplicateCallbackException(String message) {
        super(message);
    }
    
    /**
     * 构造重复回调异常（带原因）
     * 
     * @param message 异常描述信息
     * @param cause 导致重复的原始异常
     */
    public DuplicateCallbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
