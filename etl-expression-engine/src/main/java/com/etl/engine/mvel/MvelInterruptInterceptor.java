package com.etl.engine.mvel;

import org.mvel2.ast.ASTNode;
import org.mvel2.integration.Interceptor;
import org.mvel2.integration.VariableResolverFactory;

import java.io.Serial;
import java.io.Serializable;

/**
 * MVEL 中断响应拦截器
 * 
 * <p>在MVEL执行循环/代码时，主动检测线程中断状态，实现可靠终止。</p>
 * 
 * <h3>工作原理：</h3>
 * <ol>
 *   <li>超时后 future.cancel(true) 设置虚拟线程的中断标志</li>
 *   <li>拦截器在每个AST节点执行前检查中断标志</li>
 *   <li>检测到中断后抛出 MvelExecutionInterruptedException，强制终止MVEL执行</li>
 * </ol>
 * 
 * <h3>性能影响：</h3>
 * <p>每个AST节点仅增加一次 Thread.currentThread().isInterrupted() 调用，
 * 等价于一次 volatile 读，开销可忽略。</p>
 */
public class MvelInterruptInterceptor implements Interceptor, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public int doBefore(ASTNode node, VariableResolverFactory factory) {
        if (Thread.currentThread().isInterrupted()) {
            throw new MvelExecutionInterruptedException(
                "MVEL 执行被中断：线程已标记中断状态，强制终止执行");
        }
        return 0;
    }

    @Override
    public int doAfter(Object exitValue, ASTNode node, VariableResolverFactory factory) {
        return 0;
    }

    /**
     * MVEL执行中断异常
     * 
     * <p>用于区分业务异常和执行终止异常，当线程被中断时抛出。</p>
     */
    public static class MvelExecutionInterruptedException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        public MvelExecutionInterruptedException(String message) {
            super(message);
        }
    }
}
