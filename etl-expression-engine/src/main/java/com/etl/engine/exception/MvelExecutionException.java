package com.etl.engine.exception;

import com.etl.engine.context.GlobalContext;
import lombok.Getter;

/**
 * MVEL表达式执行异常
 * 显式向外抛出MVEL链式调用中的异常，包含详细错误定位信息
 */
@Getter
public class MvelExecutionException extends RuntimeException {

    private final String requestId;
    private final String expression;
    private final String location;

    public MvelExecutionException(String message, Throwable cause, String expression) {
        super(message, cause);
        this.expression = expression;
        this.location = extractLocation(cause != null ? cause : this);

        GlobalContext ctx = GlobalContext.current();
        this.requestId = ctx != null ? ctx.getRequestId() : null;
    }

    /**
     * 提取异常发生位置
     */
    private static String extractLocation(Throwable t) {
        StackTraceElement[] stack = t.getStackTrace();
        for (StackTraceElement e : stack) {
            if (e.getClassName().startsWith("com.etl.engine")) {
                return e.getClassName() + "." + e.getMethodName() + ":" + e.getLineNumber();
            }
        }
        return "unknown";
    }
}
