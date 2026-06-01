package com.etl.engine.context;

import com.etl.engine.model.ExtendedInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局上下文
 * 使用ThreadLocal管理请求级别的全局数据，统一收集关键信息用于构建ExtendedInfo
 */
@Slf4j
public final class GlobalContext {

    private static final ThreadLocal<GlobalContext> HOLDER = new ThreadLocal<>();

    private final String requestId;
    private final long startTime;
    private Integer thirdPartyStatusCode;
    private Integer sqlAffectedRows;

    private GlobalContext(String requestId) {
        this.requestId = requestId;
        this.startTime = System.currentTimeMillis();
    }

    /**
     * 初始化全局上下文
     */
    public static void init(String requestId) {
        HOLDER.set(new GlobalContext(requestId));
        log.debug("[GlobalContext] 初始化: requestId={}", requestId);
    }

    /**
     * 获取当前全局上下文
     */
    public static GlobalContext current() {
        return HOLDER.get();
    }

    /**
     * 清理全局上下文
     */
    public static void clear() {
        GlobalContext ctx = HOLDER.get();
        if (ctx != null) {
            log.debug("[GlobalContext] 清理: requestId={}", ctx.requestId);
        }
        HOLDER.remove();
    }

    /**
     * 构建成功状态的扩展信息
     */
    public ExtendedInfo buildExtendedInfo() {
        return ExtendedInfo.builder()
                .requestId(requestId)
                .apiStatusCode(200)
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .thirdPartyStatusCode(thirdPartyStatusCode)
                .sqlAffectedRows(sqlAffectedRows)
                .build();
    }

    /**
     * 构建失败状态的扩展信息
     */
    public ExtendedInfo buildExtendedInfo(String errorDetail) {
        return ExtendedInfo.builder()
                .requestId(requestId)
                .apiStatusCode(500)
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .errorDetail(errorDetail)
                .build();
    }

    /**
     * 记录第三方接口响应状态码
     */
    public void setThirdPartyStatusCode(int code) {
        this.thirdPartyStatusCode = code;
    }

    /**
     * 记录SQL影响行数
     */
    public void setSqlAffectedRows(int rows) {
        this.sqlAffectedRows = rows;
    }

    public String getRequestId() {
        return requestId;
    }
}
