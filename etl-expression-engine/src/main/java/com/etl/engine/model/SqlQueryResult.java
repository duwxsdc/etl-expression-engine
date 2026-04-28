package com.etl.engine.model;

import java.util.Collections;
import java.util.Map;

/**
 * SQL查询结果
 * 
 * @param success 是否成功
 * @param data 查询数据
 * @param rowCount 行数
 * @param errorMessage 错误信息
 */
public record SqlQueryResult(
    boolean success,
    Object data,
    int rowCount,
    String errorMessage
) {
    public static SqlQueryResult success(Object data, int rowCount) {
        return new SqlQueryResult(true, data, rowCount, null);
    }

    public static SqlQueryResult failure(String errorMessage) {
        return new SqlQueryResult(false, Collections.emptyList(), 0, errorMessage);
    }
}
