package com.etl.engine.rest;

import java.util.Map;

/**
 * 响应拦截器接口，用于在HTTP响应返回后对响应进行拦截和处理。
 * 
 * <p>该接口是一个函数式接口，允许用户在收到HTTP响应后，
 * 对响应的状态码、响应头、响应体等进行自定义处理。常见的使用场景包括：
 * <ul>
 *   <li>记录响应日志（用于调试和监控）</li>
 *   <li>解析和转换响应体（如JSON解析、数据提取等）</li>
 *   <li>处理错误响应（如重试、抛出异常等）</li>
 *   <li>提取响应头信息（如获取Token、分页信息等）</li>
 *   <li>响应数据脱敏（如隐藏敏感信息）</li>
 *   <li>性能监控（记录响应时间）</li>
 * </ul>
 * 
 * <p>该接口设计为不可变模式，所有修改操作都返回新的实例，
 * 保证线程安全且支持链式调用。
 * 
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 示例1：记录响应日志
 * ResponseInterceptor loggingInterceptor = response -> {
 *     log.info("Response status: {}, duration: {}ms", 
 *         response.statusCode(), response.durationMs());
 *     return response;
 * };
 * 
 * // 示例2：处理错误响应
 * ResponseInterceptor errorHandler = response -> {
 *     if (!response.isSuccess()) {
 *         log.error("Request failed with status: {}, body: {}", 
 *             response.statusCode(), response.body());
 *     }
 *     return response;
 * };
 * 
 * // 示例3：解析响应体并记录
 * ResponseInterceptor parseInterceptor = response -> {
 *     if (response.isSuccess() && response.body() != null) {
 *         JsonObject json = JsonParser.parseString(response.body());
 *         log.debug("Parsed response: {}", json);
 *     }
 *     return response;
 * };
 * 
 * // 示例4：响应数据脱敏
 * ResponseInterceptor sensitiveDataFilter = response -> {
 *     String body = response.body();
 *     if (body != null && body.contains("password")) {
 *         body = body.replaceAll("\"password\":\"[^\"]*\"", "\"password\":\"***\"");
 *         return response.withBody(body);
 *     }
 *     return response;
 * };
 * 
 * // 示例5：性能监控
 * ResponseInterceptor performanceInterceptor = response -> {
 *     if (response.durationMs() > 1000) {
 *         log.warn("Slow response detected: {}ms for status {}", 
 *             response.durationMs(), response.statusCode());
 *     }
 *     return response;
 * };
 * 
 * // 示例6：组合多个拦截器
 * ResponseInterceptor combinedInterceptor = response -> {
 *     ResponseInterceptor logger = r -> {
 *         log.info("Status: {}, Time: {}ms", r.statusCode(), r.durationMs());
 *         return r;
 *     };
 *     ResponseInterceptor errorHandler = r -> {
 *         if (!r.isSuccess()) {
 *             log.error("Error: {}", r.body());
 *         }
 *         return r;
 *     };
 *     return errorHandler.intercept(logger.intercept(response));
 * };
 * }</pre>
 * 
 * @see RequestInterceptor
 * @see InterceptedResponse
 * @since 1.0
 */
@FunctionalInterface
public interface ResponseInterceptor {
    
    /**
     * 拦截并处理HTTP响应。
     * 
     * <p>该方法在收到HTTP响应后被调用，
     * 允许对响应进行任意处理。实现类可以：
     * <ul>
     *   <li>记录响应日志</li>
     *   <li>检查响应状态并处理错误</li>
     *   <li>解析响应体内容</li>
     *   <li>修改响应体（如脱敏处理）</li>
     *   <li>提取响应头信息</li>
     * </ul>
     * 
     * <h2>使用示例</h2>
     * <pre>{@code
     * // 处理响应
     * InterceptedResponse processedResponse = interceptor.intercept(
     *     InterceptedResponse.of(200, Map.of(), "{\"data\":\"value\"}", 150)
     * );
     * 
     * // 检查响应状态
     * if (processedResponse.isSuccess()) {
     *     String body = processedResponse.body();
     *     // 处理成功响应
     * } else {
     *     // 处理错误响应
     *     throw new RuntimeException("Request failed: " + processedResponse.statusCode());
     * }
     * }</pre>
     * 
     * @param response 原始响应对象，包含状态码、响应头、响应体和响应时间，
     *                 不应为null
     * @return 处理后的响应对象，可以返回原响应对象或新的响应对象，
     *         但不应返回null
     */
    InterceptedResponse intercept(InterceptedResponse response);
    
    /**
     * 被拦截的HTTP响应数据载体，包含响应的所有相关信息。
     * 
     * <p>这是一个不可变的Record类，用于封装HTTP响应的所有要素：
     * <ul>
     *   <li>{@code statusCode} - HTTP状态码（如200、404、500等）</li>
     *   <li>{@code headers} - 响应头Map，键值均为String类型</li>
     *   <li>{@code body} - 响应体内容，String类型</li>
     *   <li>{@code durationMs} - 请求耗时，单位为毫秒</li>
     * </ul>
     * 
     * <p>该类提供了一系列便捷方法用于响应处理：
     * <ul>
     *   <li>{@link #isSuccess()} - 快速判断响应是否成功</li>
     *   <li>{@link #withBody(String)} - 创建修改响应体后的新实例</li>
     * </ul>
     * 
     * <h2>使用示例</h2>
     * <pre>{@code
     * // 创建响应实例
     * InterceptedResponse response = InterceptedResponse.of(
     *     200,
     *     Map.of("Content-Type", "application/json"),
     *     "{\"id\":1,\"name\":\"John\"}",
     *     150
     * );
     * 
     * // 检查响应是否成功
     * if (response.isSuccess()) {
     *     System.out.println("Request succeeded!");
     * }
     * 
     * // 获取响应信息
     * int status = response.statusCode();           // 200
     * String body = response.body();                 // "{\"id\":1,\"name\":\"John\"}"
     * long duration = response.durationMs();         // 150
     * Map<String, String> headers = response.headers(); // 获取所有响应头
     * 
     * // 修改响应体（如脱敏）
     * InterceptedResponse sanitized = response.withBody(
     *     response.body().replace("\"password\":\"secret\"", "\"password\":\"***\"")
     * );
     * 
     * // 处理错误响应
     * InterceptedResponse errorResponse = InterceptedResponse.of(
     *     404,
     *     Map.of(),
     *     "{\"error\":\"Not Found\"}",
     *     50
     * );
     * if (!errorResponse.isSuccess()) {
     *     log.error("Error {}: {}", errorResponse.statusCode(), errorResponse.body());
     * }
     * }</pre>
     * 
     * @param statusCode HTTP状态码，如200（成功）、201（创建成功）、
     *                   400（请求错误）、401（未授权）、404（未找到）、
     *                   500（服务器错误）等
     * @param headers HTTP响应头Map，键为响应头名称，值为响应头值，
     *                如果为null则自动转换为空Map
     * @param body 响应体内容，String类型，可以是null
     * @param durationMs 请求耗时，单位为毫秒，从请求发送到收到响应的总时间，
     *                   包括网络传输时间和服务器处理时间
     */
    record InterceptedResponse(
        int statusCode,
        Map<String, String> headers,
        String body,
        long durationMs
    ) {
        /**
         * 判断HTTP响应是否成功（状态码在200-299范围内）。
         * 
         * <p>该方法根据HTTP状态码判断请求是否成功，
         * 符合HTTP标准规范中的成功状态码定义。
         * 
         * <p>常见的成功状态码包括：
         * <ul>
         *   <li>200 OK - 请求成功</li>
         *   <li>201 Created - 资源创建成功</li>
         *   <li>202 Accepted - 请求已接受，处理中</li>
         *   <li>204 No Content - 请求成功，无返回内容</li>
         * </ul>
         * 
         * <h2>使用示例</h2>
         * <pre>{@code
         * InterceptedResponse response = InterceptedResponse.of(200, Map.of(), "OK", 100);
         * 
         * if (response.isSuccess()) {
         *     // 处理成功响应
         *     System.out.println("Response body: " + response.body());
         * } else {
         *     // 处理错误响应
         *     System.err.println("Error: HTTP " + response.statusCode());
         * }
         * 
         * // 常见的状态码判断
         * InterceptedResponse created = InterceptedResponse.of(201, Map.of(), "{\"id\":1}", 50);
         * System.out.println(created.isSuccess()); // true
         * 
         * InterceptedResponse badRequest = InterceptedResponse.of(400, Map.of(), "Bad Request", 10);
         * System.out.println(badRequest.isSuccess()); // false
         * 
         * InterceptedResponse notFound = InterceptedResponse.of(404, Map.of(), "Not Found", 5);
         * System.out.println(notFound.isSuccess()); // false
         * }</pre>
         * 
         * @return 如果状态码在[200, 300)范围内返回{@code true}，否则返回{@code false}
         */
        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
        
        /**
         * 替换响应体，返回新的响应实例。
         * 
         * <p>该方法用于修改响应体内容，常用于：
         * <ul>
         *   <li>响应数据脱敏（隐藏敏感信息）</li>
         *   <li>解析并重新格式化响应体</li>
         *   <li>修复编码问题</li>
         *   <li>添加额外信息到响应体</li>
         * </ul>
         * 
         * <h2>使用示例</h2>
         * <pre>{@code
         * InterceptedResponse response = InterceptedResponse.of(
         *     200, 
         *     Map.of(), 
         *     "{\"name\":\"John\",\"password\":\"secret123\"}", 
         *     100
         * );
         * 
         * // 脱敏处理 - 隐藏密码
         * InterceptedResponse sanitized = response.withBody(
         *     response.body().replace("\"secret123\"", "\"***\"")
         * );
         * 
         * // 重新格式化JSON
         * String prettyJson = new GsonBuilder().setPrettyPrinting()
         *     .create().toJson(JsonParser.parseString(response.body()));
         * InterceptedResponse formatted = response.withBody(prettyJson);
         * }</pre>
         * 
         * @param newBody 新的响应体内容，可以是null
         * @return 包含新响应体的{@link InterceptedResponse}新实例
         */
        public InterceptedResponse withBody(String newBody) {
            return new InterceptedResponse(statusCode, headers, newBody, durationMs);
        }
        
        /**
         * 创建{@link InterceptedResponse}实例的静态工厂方法。
         * 
         * <p>该方法提供了一种简洁的方式来创建响应实例，
         * 并自动处理null的headers参数，将其转换为空Map。
         * 
         * <h2>使用示例</h2>
         * <pre>{@code
         * // 创建成功响应
         * InterceptedResponse successResponse = InterceptedResponse.of(
         *     200,
         *     Map.of(
         *         "Content-Type", "application/json",
         *         "X-Request-Id", "abc-123"
         *     ),
         *     "{\"status\":\"ok\",\"data\":{\"id\":1}}",
         *     150
         * );
         * 
         * // 创建带空响应头的响应
         * InterceptedResponse simpleResponse = InterceptedResponse.of(
         *     200,
         *     null,  // 自动转换为空Map
         *     "OK",
         *     50
         * );
         * 
         * // 创建创建成功的响应
         * InterceptedResponse createdResponse = InterceptedResponse.of(
         *     201,
         *     Map.of("Location", "/api/users/123"),
         *     "{\"id\":123,\"name\":\"John\"}",
         *     200
         * );
         * 
         * // 创建错误响应
         * InterceptedResponse errorResponse = InterceptedResponse.of(
         *     404,
         *     Map.of("Content-Type", "application/json"),
         *     "{\"error\":\"User not found\",\"code\":\"USER_404\"}",
         *     10
         * );
         * 
         * // 创建服务器错误响应
         * InterceptedResponse serverError = InterceptedResponse.of(
         *     500,
         *     null,
         *     "{\"error\":\"Internal Server Error\"}",
         *     5000
         * );
         * }</pre>
         * 
         * @param statusCode HTTP状态码，应为有效的HTTP状态码
         * @param headers HTTP响应头Map，如果为null则自动转换为空Map
         * @param body 响应体内容，可以是null
         * @param durationMs 请求耗时，单位为毫秒
         * @return 新创建的{@link InterceptedResponse}实例
         */
        public static InterceptedResponse of(int statusCode, Map<String, String> headers, String body, long durationMs) {
            return new InterceptedResponse(statusCode, headers != null ? headers : Map.of(), body, durationMs);
        }
    }
}
