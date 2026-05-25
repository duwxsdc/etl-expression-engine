package com.etl.engine.rest;

import com.etl.engine.callback.LocalEventManager;
import com.etl.engine.callback.LocalNodeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * MVEL表达式引擎中的HTTP客户端工具类。
 *
 * <p>该类提供了一个静态工具类，用于在MVEL表达式中执行HTTP请求。
 * 它封装了Spring的{@link RestClient}，提供流畅的API接口，
 * 支持所有标准HTTP方法（GET、POST、PUT、DELETE、PATCH、HEAD、OPTIONS），
 * 并集成了请求/响应拦截器、自动Token管理和事件回调机制。</p>
 *
 * <h3>主要功能：</h3>
 * <ul>
 *   <li>提供静态HTTP方法调用接口（get、post、put、delete等）</li>
 *   <li>支持全局请求/响应拦截器链</li>
 *   <li>自动Token管理机制</li>
 *   <li>与本地节点信息和事件管理器集成</li>
 *   <li>线程安全的单例初始化机制</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 1. 初始化（通常由Spring自动配置完成）
 * RestClient restClient = RestClient.create();
 * LocalNodeInfo nodeInfo = new LocalNodeInfo("node-1", "localhost", 8080);
 * LocalEventManager eventManager = new LocalEventManager();
 * RestClientProperties properties = new RestClientProperties();
 * MvelRestClient.init(restClient, nodeInfo, eventManager, properties);
 *
 * // 2. 在MVEL表达式中使用
 * // GET请求
 * var response = MvelRestClient.get("https://api.example.com/users")
 *     .header("Accept", "application/json")
 *     .execute();
 *
 * // POST请求
 * var response = MvelRestClient.post("https://api.example.com/users")
 *     .body(userJsonString)
 *     .contentType("application/json")
 *     .execute();
 *
 * // 3. 添加全局拦截器
 * MvelRestClient.addGlobalRequestInterceptor(new LoggingInterceptor());
 *
 * // 4. 设置自定义Token提供者
 * MvelRestClient.setTokenProvider(url -> tokenService.getToken(url));
 * }</pre>
 *
 * <h3>注意事项：</h3>
 * <ul>
 *   <li>该类使用静态变量存储状态，确保在使用前调用{@link #init}方法进行初始化</li>
 *   <li>初始化只能执行一次，后续调用将被忽略</li>
 *   <li>所有HTTP方法都返回{@link MvelRestClientBuilder}，支持链式调用</li>
 *   <li>拦截器按添加顺序执行</li>
 * </ul>
 *
 * @author ETL Engine Team
 * @version 1.0
 * @see MvelRestClientBuilder
 * @see RequestInterceptor
 * @see ResponseInterceptor
 * @see RestClientProperties
 * @since 1.0
 */
public final class MvelRestClient {
    
    private static final Logger logger = LoggerFactory.getLogger(MvelRestClient.class);
    
    private static RestClient restClient;
    private static LocalNodeInfo localNodeInfo;
    private static LocalEventManager eventManager;
    private static RestClientProperties properties;
    private static final List<RequestInterceptor> globalRequestInterceptors = new ArrayList<>();
    private static final List<ResponseInterceptor> globalResponseInterceptors = new ArrayList<>();
    private static volatile boolean initialized = false;
    
    /**
     * 私有构造函数，防止实例化。
     *
     * <p>该类设计为静态工具类，所有方法都是静态方法，
     * 不应被实例化。</p>
     */
    private MvelRestClient() {
    }
    
    /**
     * 使用基本参数初始化MvelRestClient。
     *
     * <p>该方法调用完整版本的{@link #init(RestClient, LocalNodeInfo, LocalEventManager, RestClientProperties)}
     * 方法，properties参数使用默认值null。</p>
     *
     * @param client Spring的RestClient实例，用于执行实际的HTTP请求
     * @param nodeInfo 本地节点信息，包含节点ID、主机地址等信息
     * @param manager 本地事件管理器，用于处理回调事件
     * @see #init(RestClient, LocalNodeInfo, LocalEventManager, RestClientProperties)
     */
    public static void init(RestClient client, LocalNodeInfo nodeInfo, LocalEventManager manager) {
        init(client, nodeInfo, manager, null);
    }
    
    /**
     * 使用完整参数初始化MvelRestClient。
     *
     * <p>该方法执行一次性初始化，后续调用将被忽略。初始化时会设置：
     * <ul>
     *   <li>RestClient实例 - 用于HTTP请求执行</li>
     *   <li>本地节点信息 - 用于请求标识和路由</li>
     *   <li>事件管理器 - 用于处理异步回调</li>
     *   <li>配置属性 - 用于Token管理等高级功能</li>
     * </ul></p>
     *
     * <p>初始化完成后会记录日志，包括节点ID和自动Token状态。</p>
     *
     * @param client Spring的RestClient实例，用于执行实际的HTTP请求，不能为null
     * @param nodeInfo 本地节点信息，包含节点ID、主机地址等，不能为null
     * @param manager 本地事件管理器，用于处理回调事件，不能为null
     * @param props RestClient配置属性，包含Token管理、超时等配置，可为null表示使用默认配置
     */
    public static void init(RestClient client, LocalNodeInfo nodeInfo, LocalEventManager manager, 
                           RestClientProperties props) {
        if (!initialized) {
            restClient = client;
            localNodeInfo = nodeInfo;
            eventManager = manager;
            properties = props;
            initialized = true;
            logger.info("MvelRestClient初始化完成 - 节点: {}, 自动Token: {}", 
                nodeInfo.getNodeId(), 
                props != null && props.isAutoTokenEnabled() ? "已启用" : "未启用");
        }
    }
    
    /**
     * 创建MvelRestClientBuilder实例。
     *
     * <p>内部方法，用于创建配置好的构建器实例。构建器会继承：
     * <ul>
     *   <li>当前RestClient实例</li>
     *   <li>本地节点信息</li>
     *   <li>事件管理器</li>
     *   <li>配置属性</li>
     *   <li>全局拦截器的副本</li>
     * </ul></p>
     *
     * @param url 请求的目标URL
     * @param method HTTP方法名称（GET、POST等）
     * @return 配置好的{@link MvelRestClientBuilder}实例
     * @throws IllegalStateException 如果MvelRestClient未初始化
     */
    private static MvelRestClientBuilder createBuilder(String url, String method) {
        ensureInitialized();
        logger.debug("MVEL RestClient {}: {}", method, url);
        return new MvelRestClientBuilder(url, method, restClient, localNodeInfo, eventManager,
            properties, new ArrayList<>(globalRequestInterceptors), new ArrayList<>(globalResponseInterceptors));
    }
    
    /**
     * 创建HTTP GET请求构建器。
     *
     * <p>GET请求通常用于获取资源，不应包含请求体。</p>
     *
     * <p>使用示例：
     * <pre>{@code
     * var response = MvelRestClient.get("https://api.example.com/users")
     *     .header("Accept", "application/json")
     *     .execute();
     * }</pre></p>
     *
     * @param url 请求的目标URL，必须是一个有效的HTTP/HTTPS URL
     * @return {@link MvelRestClientBuilder}实例，用于链式构建请求
     * @throws IllegalStateException 如果MvelRestClient未初始化
     */
    public static MvelRestClientBuilder get(String url) {
        return createBuilder(url, "GET");
    }
    
    /**
     * 创建HTTP POST请求构建器。
     *
     * <p>POST请求通常用于创建资源或提交数据，可以包含请求体。</p>
     *
     * <p>使用示例：
     * <pre>{@code
     * var response = MvelRestClient.post("https://api.example.com/users")
     *     .body("{\"name\":\"张三\"}")
     *     .contentType("application/json")
     *     .execute();
     * }</pre></p>
     *
     * @param url 请求的目标URL，必须是一个有效的HTTP/HTTPS URL
     * @return {@link MvelRestClientBuilder}实例，用于链式构建请求
     * @throws IllegalStateException 如果MvelRestClient未初始化
     */
    public static MvelRestClientBuilder post(String url) {
        return createBuilder(url, "POST");
    }
    
    /**
     * 创建HTTP PUT请求构建器。
     *
     * <p>PUT请求通常用于更新或替换资源，可以包含请求体。</p>
     *
     * <p>使用示例：
     * <pre>{@code
     * var response = MvelRestClient.put("https://api.example.com/users/123")
     *     .body("{\"name\":\"李四\"}")
     *     .contentType("application/json")
     *     .execute();
     * }</pre></p>
     *
     * @param url 请求的目标URL，必须是一个有效的HTTP/HTTPS URL
     * @return {@link MvelRestClientBuilder}实例，用于链式构建请求
     * @throws IllegalStateException 如果MvelRestClient未初始化
     */
    public static MvelRestClientBuilder put(String url) {
        return createBuilder(url, "PUT");
    }
    
    /**
     * 创建HTTP DELETE请求构建器。
     *
     * <p>DELETE请求用于删除指定资源。</p>
     *
     * <p>使用示例：
     * <pre>{@code
     * var response = MvelRestClient.delete("https://api.example.com/users/123")
     *     .execute();
     * }</pre></p>
     *
     * @param url 请求的目标URL，必须是一个有效的HTTP/HTTPS URL
     * @return {@link MvelRestClientBuilder}实例，用于链式构建请求
     * @throws IllegalStateException 如果MvelRestClient未初始化
     */
    public static MvelRestClientBuilder delete(String url) {
        return createBuilder(url, "DELETE");
    }
    
    /**
     * 创建HTTP PATCH请求构建器。
     *
     * <p>PATCH请求用于部分更新资源，与PUT不同，它只更新指定的字段。</p>
     *
     * <p>使用示例：
     * <pre>{@code
     * var response = MvelRestClient.patch("https://api.example.com/users/123")
     *     .body("{\"name\":\"王五\"}")
     *     .contentType("application/json")
     *     .execute();
     * }</pre></p>
     *
     * @param url 请求的目标URL，必须是一个有效的HTTP/HTTPS URL
     * @return {@link MvelRestClientBuilder}实例，用于链式构建请求
     * @throws IllegalStateException 如果MvelRestClient未初始化
     */
    public static MvelRestClientBuilder patch(String url) {
        return createBuilder(url, "PATCH");
    }
    
    /**
     * 创建HTTP HEAD请求构建器。
     *
     * <p>HEAD请求与GET类似，但服务器只返回响应头，不返回响应体。
     * 常用于检查资源是否存在或获取元数据。</p>
     *
     * <p>使用示例：
     * <pre>{@code
     * var response = MvelRestClient.head("https://api.example.com/users/123")
     *     .execute();
     * // 检查Content-Length头
     * long contentLength = response.getHeaders().getContentLength();
     * }</pre></p>
     *
     * @param url 请求的目标URL，必须是一个有效的HTTP/HTTPS URL
     * @return {@link MvelRestClientBuilder}实例，用于链式构建请求
     * @throws IllegalStateException 如果MvelRestClient未初始化
     */
    public static MvelRestClientBuilder head(String url) {
        return createBuilder(url, "HEAD");
    }
    
    /**
     * 创建HTTP OPTIONS请求构建器。
     *
     * <p>OPTIONS请求用于获取服务器支持的HTTP方法列表，
     * 常用于CORS预检请求。</p>
     *
     * <p>使用示例：
     * <pre>{@code
     * var response = MvelRestClient.options("https://api.example.com/users")
     *     .execute();
     * // 检查Allow头
     * String allow = response.getHeaders().getFirst("Allow");
     * }</pre></p>
     *
     * @param url 请求的目标URL，必须是一个有效的HTTP/HTTPS URL
     * @return {@link MvelRestClientBuilder}实例，用于链式构建请求
     * @throws IllegalStateException 如果MvelRestClient未初始化
     */
    public static MvelRestClientBuilder options(String url) {
        return createBuilder(url, "OPTIONS");
    }
    
    /**
     * 使用自定义HTTP方法创建请求构建器。
     *
     * <p>该方法允许使用任意HTTP方法，方法名会被转换为大写。
     * 除了标准HTTP方法外，还可用于WebDAV等扩展方法
     * （如PROPFIND、COPY、MOVE等）。</p>
     *
     * <p>使用示例：
     * <pre>{@code
     * // 使用自定义方法
     * var response = MvelRestClient.request("https://api.example.com/resource", "PROPFIND")
     *     .execute();
     * }</pre></p>
     *
     * @param url 请求的目标URL，必须是一个有效的HTTP/HTTPS URL
     * @param method HTTP方法名称，会被转换为大写
     * @return {@link MvelRestClientBuilder}实例，用于链式构建请求
     * @throws IllegalStateException 如果MvelRestClient未初始化
     */
    public static MvelRestClientBuilder request(String url, String method) {
        return createBuilder(url, method.toUpperCase());
    }
    
    /**
     * 添加全局请求拦截器。
     *
     * <p>全局请求拦截器会应用于所有通过该客户端发出的请求。
     * 拦截器按添加顺序执行，可用于：
     * <ul>
     *   <li>添加通用请求头（如User-Agent、Accept等）</li>
     *   <li>请求日志记录</li>
     *   <li>请求签名</li>
     *   <li>请求参数处理</li>
     * </ul></p>
     *
     * <p>使用示例：
     * <pre>{@code
     * MvelRestClient.addGlobalRequestInterceptor(new RequestInterceptor() {
     *     @Override
     *     public void intercept(RequestContext context) {
     *         context.addHeader("X-Request-Id", UUID.randomUUID().toString());
     *     }
     * });
     * }</pre></p>
     *
     * @param interceptor 要添加的请求拦截器实例，不能为null
     * @see RequestInterceptor
     */
    public static void addGlobalRequestInterceptor(RequestInterceptor interceptor) {
        globalRequestInterceptors.add(interceptor);
        logger.info("添加全局请求拦截器: {}", interceptor.getClass().getSimpleName());
    }
    
    /**
     * 添加全局响应拦截器。
     *
     * <p>全局响应拦截器会应用于所有通过该客户端收到的响应。
     * 拦截器按添加顺序执行，可用于：
     * <ul>
     *   <li>响应日志记录</li>
     *   <li>响应状态检查和处理</li>
     *   <li>响应数据转换</li>
     *   <li>错误处理和重试</li>
     * </ul></p>
     *
     * <p>使用示例：
     * <pre>{@code
     * MvelRestClient.addGlobalResponseInterceptor(new ResponseInterceptor() {
     *     @Override
     *     public void intercept(ResponseContext context) {
     *         if (context.getStatus() >= 400) {
     *             logger.error("请求失败: {} {}", context.getUrl(), context.getStatus());
     *         }
     *     }
     * });
     * }</pre></p>
     *
     * @param interceptor 要添加的响应拦截器实例，不能为null
     * @see ResponseInterceptor
     */
    public static void addGlobalResponseInterceptor(ResponseInterceptor interceptor) {
        globalResponseInterceptors.add(interceptor);
        logger.info("添加全局响应拦截器: {}", interceptor.getClass().getSimpleName());
    }
    
    /**
     * 移除全局请求拦截器。
     *
     * <p>从全局请求拦截器列表中移除指定的拦截器。
     * 如果拦截器不存在于列表中，则不执行任何操作。</p>
     *
     * @param interceptor 要移除的请求拦截器实例
     */
    public static void removeGlobalRequestInterceptor(RequestInterceptor interceptor) {
        globalRequestInterceptors.remove(interceptor);
    }
    
    /**
     * 移除全局响应拦截器。
     *
     * <p>从全局响应拦截器列表中移除指定的拦截器。
     * 如果拦截器不存在于列表中，则不执行任何操作。</p>
     *
     * @param interceptor 要移除的响应拦截器实例
     */
    public static void removeGlobalResponseInterceptor(ResponseInterceptor interceptor) {
        globalResponseInterceptors.remove(interceptor);
    }
    
    /**
     * 清除所有全局拦截器。
     *
     * <p>移除所有已注册的全局请求拦截器和响应拦截器。
     * 该操作不可恢复，谨慎使用。</p>
     *
     * <p>使用示例：
     * <pre>{@code
     * // 重置拦截器配置
     * MvelRestClient.clearGlobalInterceptors();
     * // 重新添加必要的拦截器
     * MvelRestClient.addGlobalRequestInterceptor(new AuthInterceptor());
     * }</pre></p>
     */
    public static void clearGlobalInterceptors() {
        globalRequestInterceptors.clear();
        globalResponseInterceptors.clear();
    }
    
    /**
     * 设置自定义Token提供者。
     *
     * <p>Token提供者是一个函数式接口，接收URL作为参数，返回对应的Token字符串。
     * 当启用了自动Token功能时，请求会自动调用此提供者获取Token
     * 并添加到请求头中。</p>
     *
     * <p>使用示例：
     * <pre>{@code
     * MvelRestClient.setTokenProvider(url -> {
     *     if (url.contains("/api/")) {
     *         return "Bearer " + tokenService.getAccessToken();
     *     }
     *     return null;
     * });
     * }</pre></p>
     *
     * @param provider Token提供者函数，接收URL字符串，返回Token字符串，返回null表示不添加Token
     * @see RestClientProperties#isAutoTokenEnabled()
     */
    public static void setTokenProvider(Function<String, String> provider) {
        if (properties != null) {
            properties.setTokenProvider(provider);
            logger.info("设置自定义TokenProvider");
        }
    }
    
    /**
     * 获取当前本地节点信息。
     *
     * <p>本地节点信息包含当前节点的标识、网络地址等元数据，
     * 用于请求追踪和分布式系统中的节点识别。</p>
     *
     * @return 当前节点的{@link LocalNodeInfo}实例，如果未初始化则返回null
     */
    public static LocalNodeInfo getNodeInfo() {
        return localNodeInfo;
    }
    
    /**
     * 获取当前事件管理器。
     *
     * <p>事件管理器用于处理异步回调事件，管理事件队列和分发。</p>
     *
     * @return 当前的{@link LocalEventManager}实例，如果未初始化则返回null
     */
    public static LocalEventManager getEventManager() {
        return eventManager;
    }
    
    /**
     * 获取当前RestClient配置属性。
     *
     * <p>配置属性包含超时设置、Token管理、重试策略等配置项。</p>
     *
     * @return 当前的{@link RestClientProperties}实例，如果未初始化或未设置则返回null
     */
    public static RestClientProperties getProperties() {
        return properties;
    }
    
    /**
     * 获取待处理事件的数量。
     *
     * <p>返回事件管理器队列中等待处理的事件数量。
     * 如果事件管理器未初始化，返回0。</p>
     *
     * <p>可用于监控和健康检查：
     * <pre>{@code
     * int pending = MvelRestClient.getPendingEventCount();
     * if (pending > 1000) {
     *     logger.warn("待处理事件过多: {}", pending);
     * }
     * }</pre></p>
     *
     * @return 待处理事件数量，如果事件管理器未初始化则返回0
     */
    public static int getPendingEventCount() {
        return eventManager != null ? eventManager.getPendingCount() : 0;
    }
    
    /**
     * 检查自动Token功能是否启用。
     *
     * <p>自动Token功能启用后，系统会自动为请求添加认证Token。
     * 需要配合{@link #setTokenProvider(Function)}设置Token提供者。</p>
     *
     * @return 如果配置属性已设置且自动Token功能已启用，返回true；否则返回false
     */
    public static boolean isAutoTokenEnabled() {
        return properties != null && properties.isAutoTokenEnabled();
    }
    
    /**
     * 获取全局请求拦截器的不可变副本。
     *
     * <p>返回的列表是当前全局请求拦截器的快照，
     * 对返回列表的修改不会影响实际拦截器列表。</p>
     *
     * @return 包含所有全局请求拦截器的不可变列表
     */
    public static List<RequestInterceptor> getGlobalRequestInterceptors() {
        return List.copyOf(globalRequestInterceptors);
    }
    
    /**
     * 获取全局响应拦截器的不可变副本。
     *
     * <p>返回的列表是当前全局响应拦截器的快照，
     * 对返回列表的修改不会影响实际拦截器列表。</p>
     *
     * @return 包含所有全局响应拦截器的不可变列表
     */
    public static List<ResponseInterceptor> getGlobalResponseInterceptors() {
        return List.copyOf(globalResponseInterceptors);
    }
    
    /**
     * 确保客户端已初始化。
     *
     * <p>内部方法，在每个公开方法调用前进行检查，
     * 确保RestClient已正确初始化。</p>
     *
     * @throws IllegalStateException 如果MvelRestClient未初始化
     */
    private static void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("MvelRestClient未初始化，请确保RestClientCallbackAutoConfig已加载");
        }
    }
}
