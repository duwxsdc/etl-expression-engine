package com.etl.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 执行结果扩展信息
 * 与ExecuteResult字段语义呼应，提供API状态、第三方调用、SQL执行等额外信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtendedInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /** 请求ID（更细粒度的追踪标识） */
    private String requestId;
    
    /** API状态码：200成功/400参数错误/500服务器错误 */
    private Integer apiStatusCode;
    
    /** 执行耗时（毫秒） */
    private Long executionTimeMs;
    
    /** 第三方接口响应状态码（可选） */
    private Integer thirdPartyStatusCode;
    
    /** SQL操作影响行数（可选） */
    private Integer sqlAffectedRows;
    
    /** 错误详情（失败时） */
    private String errorDetail;
}
