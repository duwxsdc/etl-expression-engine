package com.etl.engine.mvel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MVEL表达式中断检查注入器
 *
 * <p>通过在表达式循环体中插入 {@code _ic();} 函数调用，实现循环执行时的线程中断检测。</p>
 *
 * <h3>工作原理：</h3>
 * <ol>
 *   <li>在ParserContext中注册 {@link InterruptCheck#check()} 为 {@code _ic} 函数</li>
 *   <li>本类在表达式编译前，扫描循环结构并在循环体开头插入 {@code _ic();} 调用</li>
 *   <li>MVEL执行到 {@code _ic();} 时，调用 {@link InterruptCheck#check()} 检查线程中断状态</li>
 *   <li>若线程已被中断，抛出 {@link MvelInterruptInterceptor.MvelExecutionInterruptedException}</li>
 * </ol>
 *
 * <h3>转换示例：</h3>
 * <pre>
 * while(i &lt; 10) { i = i + 1 }  →  while(i &lt; 10) { _ic(); i = i + 1 }
 * for(i=0; i&lt;5; i=i+1) { sum+=i }  →  for(i=0; i&lt;5; i=i+1) { _ic(); sum+=i }
 * do { x = x - 1 } while(x > 0)  →  do { _ic(); x = x - 1 } while(x > 0)
 * </pre>
 *
 * <h3>设计优势（vs 反射注入）：</h3>
 * <ul>
 *   <li>不依赖MVEL内部AST结构（CompiledExpression.nodes/interceptors字段在2.5.2.Final中不存在）</li>
 *   <li>兼容所有MVEL版本，不受内部实现变化影响</li>
 *   <li>精确控制检查点位置，仅在循环体内插入，非循环代码零开销</li>
 * </ul>
 */
public final class MvelInterceptorInjector {

    private static final Logger logger = LoggerFactory.getLogger(MvelInterceptorInjector.class);

    /** 中断检查函数名 */
    private static final String IC_CALL = "_ic(); ";

    private MvelInterceptorInjector() {}

    /**
     * 在表达式的循环体中注入中断检查调用
     *
     * <p>扫描表达式中的 while/for/do 循环结构，在循环体开头插入 {@code _ic();} 调用。</p>
     * <p>正确处理字符串字面量，避免在字符串内误插入。</p>
     *
     * @param expression 原始表达式
     * @return 注入中断检查后的表达式
     */
    public static String injectInterruptChecks(String expression) {
        if (expression == null || expression.isEmpty()) {
            return expression;
        }

        StringBuilder result = new StringBuilder(expression.length() + 64);
        int len = expression.length();
        int i = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        char prevChar = '\0';
        int injectedCount = 0;

        while (i < len) {
            char c = expression.charAt(i);

            // 处理转义字符（在字符串内）
            if (prevChar == '\\' && (inSingleQuote || inDoubleQuote)) {
                result.append(c);
                prevChar = c;
                i++;
                continue;
            }

            // 处理字符串引号切换
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                result.append(c);
                prevChar = c;
                i++;
                continue;
            }
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                result.append(c);
                prevChar = c;
                i++;
                continue;
            }

            // 在字符串内直接追加
            if (inSingleQuote || inDoubleQuote) {
                result.append(c);
                prevChar = c;
                i++;
                continue;
            }

            // 检查循环关键字（不在字符串内）
            if (c == 'w' && isKeyword(expression, i, "while")) {
                int bracePos = findOpeningBrace(expression, i + 5);
                if (bracePos != -1) {
                    result.append(expression, i, bracePos + 1);
                    result.append(IC_CALL);
                    i = bracePos + 1;
                    injectedCount++;
                    prevChar = '{';
                    continue;
                }
            } else if (c == 'f' && isKeyword(expression, i, "for")) {
                int bracePos = findOpeningBrace(expression, i + 3);
                if (bracePos != -1) {
                    result.append(expression, i, bracePos + 1);
                    result.append(IC_CALL);
                    i = bracePos + 1;
                    injectedCount++;
                    prevChar = '{';
                    continue;
                }
            } else if (c == 'd' && isKeyword(expression, i, "do")) {
                int bracePos = findOpeningBrace(expression, i + 2);
                if (bracePos != -1) {
                    result.append(expression, i, bracePos + 1);
                    result.append(IC_CALL);
                    i = bracePos + 1;
                    injectedCount++;
                    prevChar = '{';
                    continue;
                }
            }

            result.append(c);
            prevChar = c;
            i++;
        }

        if (injectedCount > 0) {
            logger.debug("中断检查注入完成: 在 {} 个循环体中插入 _ic() 调用", injectedCount);
        }

        return result.toString();
    }

    /**
     * 检查指定位置是否是独立的关键字（前后不是字母数字）
     */
    private static boolean isKeyword(String expr, int pos, String keyword) {
        int end = pos + keyword.length();
        if (end > expr.length()) return false;
        if (!expr.substring(pos, end).equals(keyword)) return false;

        // 前一个字符不能是字母数字（排除如 "awhile" 的情况）
        if (pos > 0 && Character.isLetterOrDigit(expr.charAt(pos - 1))) return false;

        // 后一个字符不能是字母数字（排除如 "whilex" 的情况）
        if (end < expr.length() && Character.isLetterOrDigit(expr.charAt(end))) return false;

        return true;
    }

    /**
     * 从指定位置开始，找到循环条件后的第一个左大括号
     *
     * <p>正确处理嵌套括号，确保找到的是循环体的左大括号而非条件中的括号。</p>
     *
     * @param expression 表达式
     * @param start 开始搜索的位置（关键字之后）
     * @return 左大括号的位置，未找到返回-1
     */
    private static int findOpeningBrace(String expression, int start) {
        int parenCount = 0;
        for (int i = start; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '(') {
                parenCount++;
            } else if (c == ')') {
                parenCount--;
            } else if (c == '{' && parenCount <= 0) {
                return i;
            }
        }
        return -1;
    }
}
