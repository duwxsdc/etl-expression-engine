package com.etl.engine.callback;

/**
 * 回调转发异常
 * 
 * <p>在集群内部回调转发过程中发生错误时抛出此异常。
 * 通常发生在公共回调接口收到回调后，需要转发到原始发起节点
 * 但转发请求失败的场景。</p>
 * 
 * <h3>触发场景：</h3>
 * <ul>
 *   <li>目标节点不可达（网络故障、节点宕机）</li>
 *   <li>转发请求超时</li>
 *   <li>目标节点返回错误响应</li>
 * </ul>
 * 
 * @author ETL Engine Team
 * @version 1.0.0
 * @since 2026-05-24
 * @see com.etl.engine.rest.PublicCallbackController
 */
public class CallbackForwardException extends RuntimeException {
    
    /**
     * 构造回调转发异常
     * 
     * @param message 异常描述信息，通常包含目标节点和错误原因
     */
    public CallbackForwardException(String message) {
        super(message);
    }
    
    /**
     * 构造回调转发异常（带原因）
     * 
     * @param message 异常描述信息
     * @param cause 导致转发失败的原始异常
     */
    public CallbackForwardException(String message, Throwable cause) {
        super(message, cause);
    }
}
