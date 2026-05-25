package com.etl.engine.callback;

/**
 * 回调超时异常
 * 
 * <p>当异步回调等待超过指定时间仍未收到回调时抛出此异常。
 * 通常发生在第三方服务未在预期时间内完成回调的场景。</p>
 * 
 * <h3>触发场景：</h3>
 * <ul>
 *   <li>第三方服务处理超时，未发送回调</li>
 *   <li>网络故障导致回调丢失</li>
 *   <li>回调转发失败，目标节点不可达</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * try {
 *     Object result = builder.waitCallback();
 * } catch (CallbackTimeoutException e) {
 *     log.error("回调超时: eventId={}", e.getMessage());
 * }
 * }</pre>
 * 
 * @author ETL Engine Team
 * @version 1.0.0
 * @since 2026-05-24
 * @see LocalEventManager#registerEvent(String, long)
 */
public class CallbackTimeoutException extends RuntimeException {
    
    /**
     * 构造回调超时异常
     * 
     * @param message 异常描述信息，通常包含eventId和超时时间
     */
    public CallbackTimeoutException(String message) {
        super(message);
    }
    
    /**
     * 构造回调超时异常（带原因）
     * 
     * @param message 异常描述信息
     * @param cause 导致超时的原始异常
     */
    public CallbackTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
