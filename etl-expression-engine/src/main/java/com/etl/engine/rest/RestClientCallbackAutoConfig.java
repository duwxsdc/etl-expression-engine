package com.etl.engine.rest;

import com.etl.engine.callback.LocalEventManager;
import com.etl.engine.callback.LocalNodeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * REST客户端回调自动配置类。
 * 
 * <p>该配置类是Spring Boot自动配置的核心组件，负责初始化ETL引擎中所有与REST客户端
 * 和回调机制相关的Bean。通过{@link AutoConfiguration}注解，该配置会在Spring Boot
 * 启动时自动加载，无需手动配置。</p>
 * 
 * <h2>配置属性绑定</h2>
 * <p>通过{@link EnableConfigurationProperties}注解启用{@link RestClientProperties}，
 * 允许用户在application.yml或application.properties中自定义以下配置：</p>
 * <ul>
 *   <li>连接超时时间（connect-timeout）</li>
 *   <li>读取超时时间（read-timeout）</li>
 *   <li>默认超时时间（default-timeout）</li>
 *   <li>自动Token功能开关（auto-token-enabled）</li>
 *   <li>默认请求头（default-headers）</li>
 * </ul>
 * 
 * <h2>Bean初始化顺序与依赖关系</h2>
 * <p>该配置类创建的Bean之间存在明确的依赖链，Spring会按照依赖关系自动确定初始化顺序：</p>
 * <ol>
 *   <li><b>HttpClient</b> - 底层HTTP客户端，无前置依赖</li>
 *   <li><b>ClientHttpRequestFactory</b> - 依赖HttpClient</li>
 *   <li><b>RestClient</b> - 依赖ClientHttpRequestFactory</li>
 *   <li><b>LocalNodeInfo</b> - 本地节点信息，无前置依赖</li>
 *   <li><b>LocalEventManager</b> - 本地事件管理器，无前置依赖</li>
 *   <li><b>MvelRestClientInitializer</b> - 依赖上述所有核心Bean，负责初始化MvelRestClient</li>
 *   <li><b>PublicCallbackController</b> - 依赖RestClient和LocalNodeInfo</li>
 *   <li><b>InternalCallbackController</b> - 依赖LocalEventManager</li>
 * </ol>
 * 
 * <h2>条件注解说明</h2>
 * <p>核心Bean均使用{@link ConditionalOnMissingBean}注解，实现以下功能：</p>
 * <ul>
 *   <li>允许用户自定义替换默认的HttpClient实现</li>
 *   <li>允许用户自定义替换默认的ClientHttpRequestFactory实现</li>
 *   <li>允许用户自定义替换默认的RestClient实现</li>
 *   <li>允许用户自定义替换默认的LocalNodeInfo实现</li>
 *   <li>允许用户自定义替换默认的LocalEventManager实现</li>
 * </ul>
 * <p>这种设计遵循"约定优于配置"原则，同时保留足够的扩展灵活性。</p>
 * 
 * <h2>虚拟线程支持</h2>
 * <p>HttpClient配置使用{@code Executors.newVirtualThreadPerTaskExecutor()}，
 * 充分利用Java 21的虚拟线程特性，显著提升高并发场景下的性能表现。</p>
 * 
 * @author ETL Engine Team
 * @see RestClientProperties
 * @see MvelRestClient
 * @see LocalEventManager
 * @see LocalNodeInfo
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(RestClientProperties.class)
public class RestClientCallbackAutoConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(RestClientCallbackAutoConfig.class);
    // ====================== 修复方式 1：构造器注入（最推荐）======================
    private final RestClientProperties properties;

    public RestClientCallbackAutoConfig(RestClientProperties properties) {
        this.properties = properties;
    }
    /**
     * 创建配置了虚拟线程支持的HttpClient实例。
     * 
     * <p>该方法创建一个Java标准库的HttpClient，具有以下特性：</p>
     * <ul>
     *   <li><b>虚拟线程执行器</b>：使用{@code Executors.newVirtualThreadPerTaskExecutor()}，
     *       每个HTTP请求在独立的虚拟线程中执行，避免阻塞平台线程</li>
     *   <li><b>连接超时</b>：从{@link RestClientProperties}获取配置，默认10秒</li>
     * </ul>
     * 
     * <h3>条件注解行为</h3>
     * <p>{@link ConditionalOnMissingBean}确保仅当容器中不存在其他HttpClient Bean时
     * 才创建此实例，允许用户通过自定义Bean覆盖默认配置。</p>
     * 
     * @param properties REST客户端配置属性，可能为null（使用默认值）
     * @return 配置完成的HttpClient实例
     */
    @Bean
    @ConditionalOnMissingBean
    public HttpClient httpClient() {
        logger.info("创建HttpClient（虚拟线程支持）");
        HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout(properties != null ? properties.getConnectTimeout() : Duration.ofSeconds(10))
            .executor(Executors.newVirtualThreadPerTaskExecutor());
        
        return builder.build();
    }
    
    /**
     * 创建Spring的ClientHttpRequestFactory实例。
     * 
     * <p>该方法创建{@link JdkClientHttpRequestFactory}，作为Spring RestClient与
     * 底层HttpClient之间的适配器。主要功能：</p>
     * <ul>
     *   <li>封装上述创建的HttpClient实例</li>
     *   <li>配置读取超时时间，从{@link RestClientProperties}获取，默认60秒</li>
     * </ul>
     * 
     * <h3>条件注解行为</h3>
     * <p>{@link ConditionalOnMissingBean}允许用户自定义ClientHttpRequestFactory实现，
     * 例如使用Apache HttpClient或OkHttp等第三方库。</p>
     * 
     * @param httpClient 已配置的HttpClient实例
     * @param properties REST客户端配置属性，可能为null
     * @return 配置完成的JdkClientHttpRequestFactory实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ClientHttpRequestFactory clientHttpRequestFactory(HttpClient httpClient, RestClientProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties != null ? properties.getReadTimeout() : Duration.ofSeconds(60));
        return factory;
    }
    
    /**
     * 创建Spring 6.1+的RestClient实例。
     * 
     * <p>RestClient是Spring 6.1引入的现代化HTTP客户端，替代传统的RestTemplate。
     * 该方法创建的RestClient具有以下特性：</p>
     * <ul>
     *   <li>使用上述配置的ClientHttpRequestFactory作为底层请求执行器</li>
     *   <li>支持配置默认请求头（X-App-Name、X-App-Version）</li>
     *   <li>提供流畅的Builder API，便于后续扩展</li>
     * </ul>
     * 
     * <h3>默认请求头配置</h3>
     * <p>当{@link RestClientProperties}中配置了default-headers时，
     * 会自动添加X-App-Name和X-App-Version请求头，用于服务间调用的应用标识。</p>
     * 
     * <h3>条件注解行为</h3>
     * <p>{@link ConditionalOnMissingBean}允许用户完全自定义RestClient配置，
     * 例如添加拦截器、消息转换器等高级功能。</p>
     * 
     * @param clientHttpRequestFactory 已配置的请求工厂
     * @param properties REST客户端配置属性，可能为null
     * @return 配置完成的RestClient实例
     */
    @Bean
    @ConditionalOnMissingBean
    public RestClient restClient(ClientHttpRequestFactory clientHttpRequestFactory, RestClientProperties properties) {
        logger.info("创建RestClient实例");
        
        RestClient.Builder builder = RestClient.builder()
            .requestFactory(clientHttpRequestFactory);
        
        if (properties != null && properties.getDefaultHeaders() != null && !properties.getDefaultHeaders().isEmpty()) {
            builder.defaultHeader("X-App-Name", properties.getDefaultHeaders().get("X-App-Name"));
            builder.defaultHeader("X-App-Version", properties.getDefaultHeaders().get("X-App-Version"));
        }
        
        return builder.build();
    }
    
    /**
     * 创建本地节点信息实例。
     * 
     * <p>{@link LocalNodeInfo}用于存储当前ETL引擎实例的节点信息，包括：</p>
     * <ul>
     *   <li>节点唯一标识符</li>
     *   <li>节点网络地址</li>
     *   <li>节点状态信息</li>
     * </ul>
     * <p>该信息在分布式回调场景中用于标识请求来源。</p>
     * 
     * <h3>条件注解行为</h3>
     * <p>{@link ConditionalOnMissingBean}允许用户自定义LocalNodeInfo实现，
     * 例如从配置中心或服务发现组件获取节点信息。</p>
     * 
     * @return 新创建的LocalNodeInfo实例
     */
    @Bean
    @ConditionalOnMissingBean
    public LocalNodeInfo localNodeInfo() {
        logger.info("创建LocalNodeInfo实例");
        return new LocalNodeInfo();
    }
    
    /**
     * 创建本地事件管理器实例。
     * 
     * <p>{@link LocalEventManager}是ETL引擎回调机制的核心组件，负责：</p>
     * <ul>
     *   <li>管理本地注册的回调监听器</li>
     *   <li>处理来自其他节点的回调请求</li>
     *   <li>协调异步回调的执行和结果收集</li>
     * </ul>
     * 
     * <h3>条件注解行为</h3>
     * <p>{@link ConditionalOnMissingBean}允许用户自定义事件管理实现，
     * 例如集成消息队列或分布式事件总线。</p>
     * 
     * @return 新创建的LocalEventManager实例
     */
    @Bean
    @ConditionalOnMissingBean
    public LocalEventManager localEventManager() {
        logger.info("创建LocalEventManager实例");
        return new LocalEventManager();
    }
    
    /**
     * 创建MvelRestClient初始化器并执行初始化逻辑。
     * 
     * <p>该方法是整个自动配置的核心，负责完成以下关键任务：</p>
     * <ol>
     *   <li><b>初始化MvelRestClient</b>：调用{@link MvelRestClient#init}方法，
     *       注入RestClient、LocalNodeInfo、LocalEventManager和配置属性</li>
     *   <li><b>注册全局请求拦截器</b>：将所有{@link RequestInterceptor}实现类
     *       注册为全局拦截器，在每次请求前执行</li>
     *   <li><b>注册全局响应拦截器</b>：将所有{@link ResponseInterceptor}实现类
     *       注册为全局拦截器，在每次响应后执行</li>
     *   <li><b>绑定事件管理器</b>：将LocalEventManager绑定到PublicCallbackController，
     *       使其能够处理外部回调请求</li>
     * </ol>
     * 
     * <h3>拦截器自动发现</h3>
     * <p>通过Spring的依赖注入，所有实现了{@link RequestInterceptor}和
     * {@link ResponseInterceptor}接口的Bean会自动收集并注入到{@code requestInterceptors}
     * 和{@code responseInterceptors}列表中，无需手动配置。</p>
     * 
     * <h3>初始化日志</h3>
     * <p>启动时会输出详细的初始化信息，包括自动Token功能状态、默认超时时间和拦截器数量，
     * 便于问题排查和配置验证。</p>
     * 
     * <h3>为什么不使用@ConditionalOnMissingBean</h3>
     * <p>该Bean是初始化流程的核心组件，必须且只能创建一次，因此不使用条件注解。
     * 如果需要自定义初始化逻辑，应通过自定义拦截器或配置属性实现。</p>
     * 
     * @param restClient 已配置的RestClient实例
     * @param localNodeInfo 本地节点信息
     * @param eventManager 本地事件管理器
     * @param properties REST客户端配置属性
     * @param requestInterceptors 所有请求拦截器实现（自动收集）
     * @param responseInterceptors 所有响应拦截器实现（自动收集）
     * @return 初始化完成后的MvelRestClientInitializer实例
     */
    @Bean
    public MvelRestClientInitializer mvelRestClientInitializer(
            RestClient restClient,
            LocalNodeInfo localNodeInfo,
            LocalEventManager eventManager,
            RestClientProperties properties,
            List<RequestInterceptor> requestInterceptors,
            List<ResponseInterceptor> responseInterceptors) {
        
        logger.info("初始化MvelRestClient - 自动Token: {}, 默认超时: {}ms, 全局拦截器: 请求={}, 响应={}",
            properties.isAutoTokenEnabled(),
            properties.getDefaultTimeout().toMillis(),
            requestInterceptors.size(),
            responseInterceptors.size());
        
        MvelRestClient.init(restClient, localNodeInfo, eventManager, properties);
        
        for (RequestInterceptor interceptor : requestInterceptors) {
            MvelRestClient.addGlobalRequestInterceptor(interceptor);
        }
        
        for (ResponseInterceptor interceptor : responseInterceptors) {
            MvelRestClient.addGlobalResponseInterceptor(interceptor);
        }
        
        PublicCallbackController.LocalEventManagerHolder.setEventManager(eventManager);
        
        return new MvelRestClientInitializer(properties);
    }
    
    /**
     * 创建公开回调控制器实例。
     * 
     * <p>{@link PublicCallbackController}提供对外公开的REST API端点，
     * 用于接收来自其他ETL节点或外部系统的回调请求。主要功能：</p>
     * <ul>
     *   <li>接收并验证回调请求</li>
     *   <li>将回调委托给LocalEventManager处理</li>
     *   <li>返回回调执行结果</li>
     * </ul>
     * 
     * <h3>依赖说明</h3>
     * <p>该控制器依赖RestClient用于可能的远程调用，依赖LocalNodeInfo用于节点标识。</p>
     * 
     * @param restClient 已配置的RestClient实例
     * @param localNodeInfo 本地节点信息
     * @return 新创建的PublicCallbackController实例
     */
    @Bean
    public PublicCallbackController publicCallbackController(RestClient restClient, LocalNodeInfo localNodeInfo) {
        return new PublicCallbackController(restClient, localNodeInfo);
    }
    
    /**
     * 创建内部回调控制器实例。
     * 
     * <p>{@link InternalCallbackController}提供内部使用的REST API端点，
     * 用于ETL引擎内部的回调协调。主要功能：</p>
     * <ul>
     *   <li>处理内部回调请求</li>
     *   <li>与LocalEventManager协作完成回调流程</li>
     *   <li>支持内部节点间的回调转发</li>
     * </ul>
     * 
     * @param eventManager 本地事件管理器
     * @return 新创建的InternalCallbackController实例
     */
    @Bean
    public InternalCallbackController internalCallbackController(LocalEventManager eventManager) {
        return new InternalCallbackController(eventManager);
    }
    
    /**
     * MvelRestClient初始化状态持有类。
     * 
     * <p>该内部类是一个简单的状态持有对象，用于：</p>
     * <ul>
     *   <li>标记MvelRestClient初始化已完成</li>
     *   <li>保存初始化时使用的配置属性引用</li>
     *   <li>提供运行时配置查询能力</li>
     * </ul>
     * 
     * <h3>设计说明</h3>
     * <p>该类不执行实际初始化逻辑（初始化在创建该Bean的方法中完成），
     * 主要作为Spring Bean存在，便于其他组件通过依赖注入获取配置信息。</p>
     */
    public static class MvelRestClientInitializer {
        private final RestClientProperties properties;
        
        /**
         * 构造初始化器实例。
         * 
         * @param properties REST客户端配置属性
         */
        public MvelRestClientInitializer(RestClientProperties properties) {
            this.properties = properties;
        }
        
        /**
         * 获取初始化时使用的配置属性。
         * 
         * @return REST客户端配置属性实例
         */
        public RestClientProperties getProperties() {
            return properties;
        }
    }
}
