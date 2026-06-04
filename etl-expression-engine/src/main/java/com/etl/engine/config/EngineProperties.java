package com.etl.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 引擎配置属性
 * 
 * @author ETL Engine
 * @version 1.0.0
 */
@Component
@ConfigurationProperties(prefix = "etl.engine")
public class EngineProperties {

    private long expressionTimeout = 5000L;
    private int maxExpressionLength = 10000;
    private boolean enableSqlExecution = true;
    private boolean sqlReadonly = true;
    private boolean interruptOnTimeout = true;
    private long interruptWaitMs = 100;

    public EngineProperties() {
    }

    public EngineProperties(long expressionTimeout, int maxExpressionLength, 
                           boolean enableSqlExecution, boolean sqlReadonly) {
        this.expressionTimeout = expressionTimeout;
        this.maxExpressionLength = maxExpressionLength;
        this.enableSqlExecution = enableSqlExecution;
        this.sqlReadonly = sqlReadonly;
    }

    public long getExpressionTimeout() {
        return expressionTimeout;
    }

    public void setExpressionTimeout(long expressionTimeout) {
        this.expressionTimeout = expressionTimeout;
    }

    public int getMaxExpressionLength() {
        return maxExpressionLength;
    }

    public void setMaxExpressionLength(int maxExpressionLength) {
        this.maxExpressionLength = maxExpressionLength;
    }

    public boolean isEnableSqlExecution() {
        return enableSqlExecution;
    }

    public void setEnableSqlExecution(boolean enableSqlExecution) {
        this.enableSqlExecution = enableSqlExecution;
    }

    public boolean isSqlReadonly() {
        return sqlReadonly;
    }

    public void setSqlReadonly(boolean sqlReadonly) {
        this.sqlReadonly = sqlReadonly;
    }

    public boolean isInterruptOnTimeout() {
        return interruptOnTimeout;
    }

    public void setInterruptOnTimeout(boolean interruptOnTimeout) {
        this.interruptOnTimeout = interruptOnTimeout;
    }

    public long getInterruptWaitMs() {
        return interruptWaitMs;
    }

    public void setInterruptWaitMs(long interruptWaitMs) {
        this.interruptWaitMs = interruptWaitMs;
    }
}
