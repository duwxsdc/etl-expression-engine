package com.etl.engine.mvel;

/**
 * MVEL中断检查工具类
 *
 * <p>作为MVEL表达式内部可调用的函数，在循环体执行时检查线程中断状态。</p>
 *
 * <h3>工作原理：</h3>
 * <ol>
 *   <li>在ParserContext中注册为函数导入：{@code parserContext.addImport("_ic", InterruptCheck.class.getMethod("check"))}</li>
 *   <li>表达式转换器在循环体开头插入 {@code _ic();} 调用</li>
 *   <li>MVEL执行到 {@code _ic();} 时，调用本类的 {@link #check()} 方法</li>
 *   <li>若线程已被中断（超时后 future.cancel(true) 设置中断标志），抛出 {@link MvelInterruptInterceptor.MvelExecutionInterruptedException}</li>
 * </ol>
 *
 * <h3>性能影响：</h3>
 * <p>每次调用仅执行一次 {@code Thread.currentThread().isInterrupted()}，
 * 等价于一次 volatile 读，开销约10ns。</p>
 */
public final class InterruptCheck {

    private InterruptCheck() {}

    /**
     * 中断检查方法，供MVEL表达式内部调用
     *
     * @return 始终返回null（MVEL需要返回值）
     * @throws MvelInterruptInterceptor.MvelExecutionInterruptedException 如果当前线程已被中断
     */
    public static Object check() {
        if (Thread.currentThread().isInterrupted()) {
            throw new MvelInterruptInterceptor.MvelExecutionInterruptedException(
                "MVEL 执行被中断：线程已标记中断状态，强制终止执行");
        }
        return null;
    }
}
