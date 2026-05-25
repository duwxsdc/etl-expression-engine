package com.etl.engine.rest;

import java.util.Map;

/**
 * 请求拦截器接口，用于在HTTP请求发送前对请求进行拦截和修改。
 * 
 * <p>该接口是一个函数式接口，允许用户在请求发送到目标服务器之前，
 * 对请求的URL、方法、请求头或请求体进行自定义处理。常见的使用场景包括：
 * <ul>
 *   <li>添加认证信息（如Bearer Token、API Key等）</li>
 *   <li>添加自定义请求头（如User-Agent、Content-Type等）</li>
 *   <li>记录请求日志</li>
 *   <li>修改请求体（如加密、签名等）</li>
 *   <li>动态修改请求URL</li>
 * </ul>
 * 
 * <p>该接口设计为不可变模式，所有修改操作都返回新的实例，
 * 保证线程安全且支持链式调用。
 * 
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 示例1：添加认证头
 * RequestInterceptor authInterceptor = request -> 
 *     request.withHeader("Authorization", "Bearer " + token);
 * 
 * // 示例2：添加多个自定义头
 * RequestInterceptor customHeadersInterceptor = request -> 
 *     request
 *         .withHeader("X-Api-Key", "my-api-key")
 *         .withHeader("X-Request-Id", UUID.randomUUID().toString());
 * 
 * // 示例3：修改请求URL和添加签名
 * RequestInterceptor signatureInterceptor = request -> {
 *     String signature = calculateSignature(request.body());
 *     return request
 *         .withUrl(request.url() + "?signature=" + signature)
 *         .withHeader("X-Signature", signature);
 * };
 * 
 * // 示例4：记录请求日志
 * RequestInterceptor loggingInterceptor = request -> {
 *     log.info("Sending {} request to {}", request.method(), request.url());
 *     return request;
 * };
 * 
 * // 示例5：组合多个拦截器
 * RequestInterceptor combinedInterceptor = request -> {
 *     RequestInterceptor auth = r -> r.withHeader("Authorization", "Bearer token");
 *     RequestInterceptor log = r -> {
 *         System.out.println("Request to: " + r.url());
 *         return r;
 *     };
 *     return log.intercept(auth.intercept(request));
 * };
 * }</pre>
 * 
 * @see ResponseInterceptor
 * @see InterceptedRequest
 * @since 1.0
 */
@FunctionalInterface
public interface RequestInterceptor {
    
    /**
     * 拦截并处理HTTP请求。
     * 
     * <p>该方法在请求发送到目标服务器之前被调用，
     * 允许对请求进行任意修改。实现类可以：
     * <ul>
     *   <li>添加或修改请求头</li>
     *   <li>修改请求URL</li>
     *   <li>修改请求体</li>
     *   <li>记录请求日志</li>
     * </ul>
     * 
     * <h2>使用示例</h2>
     * <pre>{@code
     * // 添加认证头
     * InterceptedRequest modifiedRequest = interceptor.intercept(
     *     InterceptedRequest.of("https://api.example.com/users", "GET", Map.of(), null)
     * );
     * }</pre>
     * 
     * @param request 原始请求对象，包含URL、HTTP方法、请求头和请求体，
     *                不应为null
     * @return 修改后的请求对象，可以返回原请求对象或新的请求对象，
     *         但不应返回null
     */
    InterceptedRequest intercept(InterceptedRequest request);
    
    /**
     * 被拦截的HTTP请求数据载体，包含请求的所有相关信息。
     * 
     * <p>这是一个不可变的Record类，用于封装HTTP请求的所有要素：
     * <ul>
     *   <li>{@code url} - 请求的目标URL</li>
     *   <li>{@code method} - HTTP方法（GET、POST、PUT、DELETE等）</li>
     *   <li>{@code headers} - 请求头Map，键值均为String类型</li>
     *   <li>{@code body} - 请求体对象，可以是任意类型</li>
     * </ul>
     * 
     * <p>该类提供了一系列{@code with*}方法用于创建修改后的新实例，
     * 支持链式调用，方便在拦截器中进行请求修改。
     * 
     * <h2>使用示例</h2>
     * <pre>{@code
     * // 创建基本请求
     * InterceptedRequest request = InterceptedRequest.of(
     *     "https://api.example.com/users",
     *     "POST",
     *     Map.of("Content-Type", "application/json"),
     *     "{\"name\":\"John\"}"
     * );
     * 
     * // 添加请求头
     * InterceptedRequest withAuth = request.withHeader("Authorization", "Bearer token");
     * 
     * // 修改请求体
     * InterceptedRequest withNewBody = request.withBody("{\"name\":\"Jane\"}");
     * 
     * // 修改URL
     * InterceptedRequest withNewUrl = request.withUrl("https://api.example.com/users/123");
     * 
     * // 链式调用
     * InterceptedRequest modified = request
     *     .withHeader("Authorization", "Bearer token")
     *     .withHeader("X-Request-Id", UUID.randomUUID().toString())
     *     .withBody("{\"name\":\"Updated\"}");
     * 
     * // 获取请求信息
     * String requestUrl = request.url();
     * String httpMethod = request.method();
     * Map<String, String> requestHeaders = request.headers();
     * Object requestBody = request.body();
     * }</pre>
     * 
     * @param url 请求的目标URL，应为有效的HTTP/HTTPS URL
     * @param method HTTP方法名称，如"GET"、"POST"、"PUT"、"DELETE"等，
     *               区分大小写，通常使用大写
     * @param headers HTTP请求头Map，键为请求头名称，值为请求头值，
     *                如果为null则自动转换为空Map
     * @param body 请求体对象，对于GET请求通常为null，
     *             对于POST/PUT请求可以是String、Map或任意可序列化对象
     */
    record InterceptedRequest(
        String url,
        String method,
        Map<String, String> headers,
        Object body
    ) {
        /**
         * 添加或更新一个请求头，返回新的请求实例。
         * 
         * <p>如果指定的请求头名称已存在，则覆盖原有值。
         * 该方法返回一个新的{@link InterceptedRequest}实例，
         * 原实例保持不变，保证线程安全。
         * 
         * <h2>使用示例</h2>
         * <pre>{@code
         * InterceptedRequest request = InterceptedRequest.of(url, "GET", Map.of(), null);
         * 
         * // 添加单个请求头
         * InterceptedRequest withAuth = request.withHeader("Authorization", "Bearer token123");
         * 
         * // 添加Content-Type
         * InterceptedRequest withContentType = request.withHeader("Content-Type", "application/json");
         * 
         * // 覆盖已存在的请求头
         * InterceptedRequest overridden = request
         *     .withHeader("Accept", "application/json")
         *     .withHeader("Accept", "text/html"); // 最终Accept为text/html
         * }</pre>
         * 
         * @param name 请求头名称，如"Content-Type"、"Authorization"等，
         *             区分大小写，不应为null
         * @param value 请求头值，不应为null
         * @return 包含新请求头的{@link InterceptedRequest}新实例
         */
        public InterceptedRequest withHeader(String name, String value) {
            var newHeaders = new java.util.LinkedHashMap<>(headers);
            newHeaders.put(name, value);
            return new InterceptedRequest(url, method, newHeaders, body);
        }
        
        /**
         * 替换请求体，返回新的请求实例。
         * 
         * <p>该方法用于修改请求体内容，常用于：
         * <ul>
         *   <li>序列化请求体</li>
         *   <li>加密请求体</li>
         *   <li>添加签名</li>
         *   <li>转换请求体格式</li>
         * </ul>
         * 
         * <h2>使用示例</h2>
         * <pre>{@code
         * InterceptedRequest request = InterceptedRequest.of(url, "POST", headers, null);
         * 
         * // 设置JSON请求体
         * InterceptedRequest withJson = request.withBody("{\"name\":\"John\"}");
         * 
         * // 设置Map作为请求体（后续可序列化为JSON）
         * Map<String, Object> data = new HashMap<>();
         * data.put("name", "John");
         * data.put("age", 30);
         * InterceptedRequest withMap = request.withBody(data);
         * }</pre>
         * 
         * @param newBody 新的请求体对象，可以是null、String、Map或其他可序列化对象
         * @return 包含新请求体的{@link InterceptedRequest}新实例
         */
        public InterceptedRequest withBody(Object newBody) {
            return new InterceptedRequest(url, method, headers, newBody);
        }
        
        /**
         * 替换请求URL，返回新的请求实例。
         * 
         * <p>该方法用于动态修改请求的目标URL，常见使用场景：
         * <ul>
         *   <li>添加查询参数</li>
         *   <li>切换API版本</li>
         *   <li>重定向到备用服务器</li>
         *   <li>动态拼接路径参数</li>
         * </ul>
         * 
         * <h2>使用示例</h2>
         * <pre>{@code
         * InterceptedRequest request = InterceptedRequest.of(
         *     "https://api.example.com/users",
         *     "GET",
         *     Map.of(),
         *     null
         * );
         * 
         * // 添加查询参数
         * InterceptedRequest withQuery = request.withUrl(
         *     "https://api.example.com/users?page=1&size=10"
         * );
         * 
         * // 切换到v2版本API
         * InterceptedRequest v2 = request.withUrl(
         *     "https://api.example.com/v2/users"
         * );
         * }</pre>
         * 
         * @param newUrl 新的请求URL，应为有效的HTTP/HTTPS URL，不应为null
         * @return 包含新URL的{@link InterceptedRequest}新实例
         */
        public InterceptedRequest withUrl(String newUrl) {
            return new InterceptedRequest(newUrl, method, headers, body);
        }
        
        /**
         * 创建{@link InterceptedRequest}实例的静态工厂方法。
         * 
         * <p>该方法提供了一种简洁的方式来创建请求实例，
         * 并自动处理null的headers参数，将其转换为空Map。
         * 
         * <h2>使用示例</h2>
         * <pre>{@code
         * // 创建GET请求（无请求体）
         * InterceptedRequest getRequest = InterceptedRequest.of(
         *     "https://api.example.com/users",
         *     "GET",
         *     null,  // 自动转换为空Map
         *     null
         * );
         * 
         * // 创建POST请求（带请求头和请求体）
         * InterceptedRequest postRequest = InterceptedRequest.of(
         *     "https://api.example.com/users",
         *     "POST",
         *     Map.of(
         *         "Content-Type", "application/json",
         *         "Authorization", "Bearer token"
         *     ),
         *     "{\"name\":\"John\",\"age\":30}"
         * );
         * 
         * // 创建PUT请求
         * InterceptedRequest putRequest = InterceptedRequest.of(
         *     "https://api.example.com/users/123",
         *     "PUT",
         *     Map.of("Authorization", "Bearer token"),
         *     "{\"name\":\"Jane\"}"
         * );
         * 
         * // 创建DELETE请求
         * InterceptedRequest deleteRequest = InterceptedRequest.of(
         *     "https://api.example.com/users/123",
         *     "DELETE",
         *     Map.of("Authorization", "Bearer token"),
         *     null
         * );
         * }</pre>
         * 
         * @param url 请求的目标URL，应为有效的HTTP/HTTPS URL，不应为null
         * @param method HTTP方法名称，如"GET"、"POST"、"PUT"、"DELETE"等，不应为null
         * @param headers HTTP请求头Map，如果为null则自动转换为空Map
         * @param body 请求体对象，对于GET请求通常为null
         * @return 新创建的{@link InterceptedRequest}实例
         */
        public static InterceptedRequest of(String url, String method, Map<String, String> headers, Object body) {
            return new InterceptedRequest(url, method, headers != null ? headers : Map.of(), body);
        }
    }
}
