package com.etl.engine.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
public class SplitExpressionTest {
    // =========================================================================
    // 自动化断言工具
    // =========================================================================
    private static int passedCount = 0;
    private static int failedCount = 0;
    private static void assertEquals(String testName, List<String> expected, List<String> actual) {
        if (expected.equals(actual)) {
            passedCount++;
            System.out.printf("✅ 通过: %-60s%n", testName);
        } else {
            failedCount++;
            System.err.printf("❌ 不通过: %-60s%n", testName);
            System.err.printf("         预期: %s%n", expected);
            System.err.printf("         实际: %s%n", actual);
        }
    }
    private static List<String> list(String... items) {
        return Arrays.asList(items);
    }
    // =========================================================================
    // 待测方法（原样搬入）
    // =========================================================================
    private static List<String> splitExpression(String expression) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;      // 单引号字符串内
        boolean inDoubleQuote = false;      // 双引号字符串内
        boolean inLineComment = false;      // 行注释内 //
        boolean inBlockComment = false;     // 块注释内 /* */
        char prevChar = '\0';
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            // 处理块注释结束
            if (inBlockComment) {
                if (prevChar == '*' && c == '/') {
                    inBlockComment = false;
                    prevChar = '\0';
                    continue;
                }
                prevChar = c;
                continue;
            }
            // 处理行注释结束（遇到换行）
            if (inLineComment) {
                if (c == '\n' || c == '\r') {
                    inLineComment = false;
                }
                continue;
            }
            // 处理转义字符
            if (prevChar == '\\') {
                current.append(c);
                prevChar = '\0';
                continue;
            }
            // 检查注释开始（只有不在字符串内才处理）
            if (!inSingleQuote && !inDoubleQuote) {
                if (prevChar == '/' && c == '/') {
                    if (current.length() > 0) {
                        current.deleteCharAt(current.length() - 1);
                    }
                    inLineComment = true;
                    prevChar = '\0';
                    continue;
                }
                if (prevChar == '/' && c == '*') {
                    if (current.length() > 0) {
                        current.deleteCharAt(current.length() - 1);
                    }
                    inBlockComment = true;
                    prevChar = '\0';
                    continue;
                }
            }
            // 处理引号切换
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                current.append(c);
                prevChar = c;
                continue;
            }
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                current.append(c);
                prevChar = c;
                continue;
            }
            // 只有不在字符串内时才按分号分割
            if (c == ';' && !inSingleQuote && !inDoubleQuote) {
                String segment = current.toString().trim();
                if (!segment.isEmpty()) {
                    result.add(segment);
                }
                current.setLength(0);
                prevChar = '\0';
                continue;
            }
            current.append(c);
            prevChar = c;
        }
        // 添加最后一段
        String segment = current.toString().trim();
        if (!segment.isEmpty()) {
            result.add(segment);
        }
        return result;
    }
    // =========================================================================
    // 核心测试入口
    // =========================================================================
    public static void main(String[] args) {
        System.out.println("========================================================");
        System.out.println("      启动 splitExpression 生产级严苛单元测试      ");
        System.out.println("========================================================\n");
        // -----------------------------------------------------------------
        // 1. 基础场景与空格修剪
        // -----------------------------------------------------------------
        assertEquals("基础多语句分割", list("a", "b", "c"), splitExpression("a; b; c"));
        assertEquals("末尾多余分号(空段过滤)", list("a", "b"), splitExpression("a;; b;"));
        assertEquals("无分号单语句", list("single"), splitExpression("single"));
        assertEquals("纯空格与分号", Collections.emptyList(), splitExpression(" ; ; "));
        assertEquals("空字符串", Collections.emptyList(), splitExpression(""));
        assertEquals("包含空格的语句(Trim)", list("a b", "c d"), splitExpression(" a b ; c d "));
        // -----------------------------------------------------------------
        // 2. 引号边界与嵌套
        // -----------------------------------------------------------------
        assertEquals("单引号内的分号免疫", list("a", "'b;c'", "d"), splitExpression("a; 'b;c'; d"));
        assertEquals("双引号内的分号免疫", list("a", "\"b;c\"", "d"), splitExpression("a; \"b;c\"; d"));
        assertEquals("空引号", list("a", "''", "\"\""), splitExpression("a; ''; \"\""));
        assertEquals("单双引号交错嵌套", list("\"a'b\"", "'c\"d'"), splitExpression("\"a'b\"; 'c\"d'"));
        assertEquals("未闭合的单引号(吞没后续)", list("a", "'unclosed"), splitExpression("a; 'unclosed"));
        assertEquals("未闭合的双引号(吞没后续)", list("a", "\"unclosed"), splitExpression("a; \"unclosed"));
        // -----------------------------------------------------------------
        // 3. 转义字符极端场景 (重点！)
        // -----------------------------------------------------------------
        assertEquals("双引号内转义双引号", list("\"a\\\"b\""), splitExpression("\"a\\\"b\""));
        assertEquals("单引号内转义单引号", list("'a\\'b'"), splitExpression("'a\\'b'"));
        // 连续转义：\\" 实际代表 \"，第一个\转义第二个\，第三个"作为字符串结束符
        assertEquals("连续转义反斜杠导致引号闭合", list("\"a\\\\\""), splitExpression("\"a\\\\\""));
        // 【⚠️特性/陷阱】：原代码中，转义逻辑优先于分号判断，且不限制必须在字符串内！
        // 因此，在字符串外部使用 \; 会导致分号被转义，从而丧失分割功能！
        assertEquals("字符串外转义分号(陷阱:分号失效)", list("a\\; b"), splitExpression("a\\; b"));
        assertEquals("字符串外转义普通字符", list("a\\b"), splitExpression("a\\b"));
        assertEquals("字符串外连续转义反斜杠(第二斜杠变普通字符)", list("a\\\\", "b"), splitExpression("a\\\\; b"));
        assertEquals("字符串外三个转义符加引号(引号变普通字符)", list("a\\\""), splitExpression("a\\\"")); // \" 外部转义，"不作为引号
        // -----------------------------------------------------------------
        // 4. 注释处理
        // -----------------------------------------------------------------
        assertEquals("行注释免疫分号", list("a", "b"), splitExpression("a; // comment; \n b"));
        assertEquals("块注释免疫分号", list("a", "b"), splitExpression("a; /* comment; */ b"));
        assertEquals("块注释跨行", list("a", "b"), splitExpression("a; /* line1 \n line2; */ b"));
        assertEquals("行注释覆盖到行尾(换行符)", list("a", "b"), splitExpression("a; // comment \n b"));
        assertEquals("行注释覆盖到行尾(回车符)", list("a", "b"), splitExpression("a; // comment \r b"));
        assertEquals("行注释覆盖到行尾(回车换行符)", list("a", "b"), splitExpression("a; // comment \r\n b"));
        assertEquals("未闭合的块注释(吞没后续)", list("a"), splitExpression("a; /* unclosed comment"));
        assertEquals("未闭合的行注释(吞没后续)", list("a"), splitExpression("a; // unclosed comment"));
        assertEquals("行注释后的分号被忽略", list("a", "b"), splitExpression("a; // comment \n ; b"));
        // -----------------------------------------------------------------
        // 5. 混合与防破坏场景 (地狱级)
        // -----------------------------------------------------------------
        assertEquals("引号内的注释符无效", list("\"//not_comment;\""), splitExpression("\"//not_comment;\""));
        assertEquals("引号内的块注释符无效", list("\"/*not_comment;*/\""), splitExpression("\"/*not_comment;*/\""));
        assertEquals("注释内的引号不触发字符串状态", list("a", "b"), splitExpression("a; /* ' \" ; */ b"));
        // 修正后的预期：/ / 中间有空格，不是注释，作为普通文本保留
        assertEquals("斜杠中间有空格(被当普通字符)", list("a", "/ / fake", "b"), splitExpression("a; / / fake \n ; /* real */ b"));
        assertEquals("真实SQL场景", list("SELECT * FROM t WHERE name = 'O\\'Reilly'", "UPDATE t SET flag = true"), splitExpression("SELECT * FROM t WHERE name = 'O\\'Reilly'; // comment; \n UPDATE t SET flag = true;"));
        assertEquals("真实JS场景", list("let a = \"hello\\\\\"", "let b = 2"), splitExpression("let a = \"hello\\\\\"; /* comment ; */ let b = 2;"));
        assertEquals("块注释紧跟分号", list("a", "b"), splitExpression("a/*comment*/;b"));
        assertEquals("多个块注释交错", list("a", "b"), splitExpression("a; /* c1 */ /* c2 ; */ b"));
        // 新增极端混合场景
        assertEquals("块注释结束后紧跟字符串", list("a", "\"b\""), splitExpression("a; /* comment */\"b\""));
        assertEquals("字符串结束后紧跟块注释", list("\"a\"", "b"), splitExpression("\"a\"/*comment*/; b"));
        assertEquals("行注释后紧跟代码(无空格)", list("ab"), splitExpression("a//comment\nb"));
        assertEquals("嵌套块注释(只识别第一个结束符)", list("a", "*/"), splitExpression("a; /* /* inner */ */")); // 遇到第一个 */ 退出注释，后面的 */ 作为普通文本
        assertEquals("行注释吃掉换行符不连接上下文", list("a", "b"), splitExpression("a; // comment \n b"));
        System.out.println("\n========================================================");
        System.out.printf("测试完成: ✅ 通过 %d 项 | ❌ 不通过 %d 项%n", passedCount, failedCount);
        System.out.println("========================================================");
    }
}