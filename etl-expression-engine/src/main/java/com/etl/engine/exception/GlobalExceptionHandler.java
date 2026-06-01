package com.etl.engine.exception;

import com.etl.engine.context.GlobalContext;
import com.etl.engine.model.ExecuteResult;
import com.etl.engine.model.ExtendedInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;

/**
 * 全局异常处理器
 * 在表达式引擎外层统一捕获和处理所有异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理MVEL执行异常
     */
    @ExceptionHandler(MvelExecutionException.class)
    public ResponseEntity<ExecuteResult> handleMvelException(MvelExecutionException e) {
        log.error("[GlobalExceptionHandler] MVEL执行异常: requestId={}, location={}, message={}",
                e.getRequestId(), e.getLocation(), e.getMessage(), e);

        GlobalContext ctx = GlobalContext.current();
        ExtendedInfo extended = ctx != null ? ctx.buildExtendedInfo(e.getMessage()) : null;

        ExecuteResult result = ExecuteResult.failure(
                e.getRequestId() != null ? e.getRequestId() : "unknown",
                e.getExpression(),
                e.getMessage(),
                Collections.emptyMap(),
                extended
        );

        return ResponseEntity.status(500).body(result);
    }

    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExecuteResult> handleGenericException(Exception e) {
        log.error("[GlobalExceptionHandler] 未知异常: {}", e.getMessage(), e);

        GlobalContext ctx = GlobalContext.current();
        String requestId = ctx != null ? ctx.getRequestId() : "unknown";
        ExtendedInfo extended = ctx != null ? ctx.buildExtendedInfo(e.getMessage()) : null;

        ExecuteResult result = ExecuteResult.failure(
                requestId,
                null,
                e.getMessage(),
                Collections.emptyMap(),
                extended
        );

        return ResponseEntity.status(500).body(result);
    }
}
