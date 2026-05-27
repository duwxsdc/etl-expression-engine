package com.etl.engine.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;

/**
 * RestClient配置属性类，用于绑定外部化配置参数。
 * 
 * <p>该类通过Spring Boot的{@code @ConfigurationProperties}机制，将YAML配置文件中
 * 以{@code etl.engine.rest-client}为前缀的配置项绑定到对应的属性字段。
 * 
 * <h2>配置示例</h2>
 * <pre>{@code
 * etl:
 *   engine:
 *     rest-client:
 *       auto-token-enabled: true
 *       auto-token-value: "your-api-token"
 *       default-timeout: 30s
 *       connect-timeout: 10s
 *       max-retries: 3
 *       enable-circuit-breaker: true
 * }</pre>
 * 
 * <h2>配置模块</h2>
 * <ul>
 *   <li><b>自动Token配置</b>：支持自动为请求添加认证Token，支持静态配置、环境变量、自定义Provider三种方式</li>
 *   <li><b>超时配置</b>：控制连接、读取、默认超时时间</li>
 *   <li><b>重试配置</b>：支持指数退避重试机制</li>
 *   <li><b>日志配置</b>：控制请求/响应日志的输出行为</li>
 *   <li><b>熔断器配置</b>：基于失败次数的熔断保护机制</li>
 *   <li><b>端点配置</b>：支持为不同URL模式配置独立的连接参数</li>
 * </ul>
 * 
 * @author ETL Engine Team
 * @since 1.0.0
 * @see ConfigurationProperties
 * @see EndpointConfig
 */
@ConfigurationProperties(prefix = "etl.engine.rest-client1")
public class RestClientProperties {
    
    private static final Logger logger = LoggerFactory.getLogger(RestClientProperties.class);

    public RestClientProperties(){
        System.out.println(1111);
    }
    /**
     * 是否启用自动Token注入功能。
     * 
     * <p>启用后，RestClient会自动为匹配{@link #methodsRequireToken}的HTTP方法
     * 添加认证Token到请求头中。
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.auto-token-enabled}
     * <br><b>默认值：</b>{@code false}
     * <br><b>取值范围：</b>{@code true} | {@code false}
     * <br><b>生效条件：</b>始终生效，作为Token功能的总开关
     */
    private boolean autoTokenEnabled = false;
    
    /**
     * Token注入的请求头名称。
     * 
     * <p>指定自动注入Token时使用的HTTP请求头名称。
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.auto-token-header}
     * <br><b>默认值：</b>{@code "Authorization"}
     * <br><b>取值范围：</b>任意有效的HTTP头名称字符串
     * <br><b>生效条件：</b>{@link #autoTokenEnabled}为{@code true}时生效
     */
    private String autoTokenHeader = "Authorization";
    
    /**
     * Token值的前缀字符串。
     * 
     * <p>该前缀会自动添加到Token值之前，形成完整的请求头值。
     * 例如：前缀为"Bearer "时，最终请求头值为"Bearer your-token"。
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.auto-token-prefix}
     * <br><b>默认值：</b>{@code "Bearer "}
     * <br><b>取值范围：</b>任意字符串，常见值包括"Bearer "、"Basic "、空字符串
     * <br><b>生效条件：</b>{@link #autoTokenEnabled}为{@code true}时生效
     */
    private String autoTokenPrefix = "Bearer ";
    
    /**
     * 静态配置的Token值。
     * 
     * <p>当{@link #autoTokenFromEnv}为{@code false}且未设置{@link #tokenProvider}时，
     * 使用此静态值作为Token。
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.auto-token-value}
     * <br><b>默认值：</b>空字符串
     * <br><b>取值范围：</b>任意字符串，建议使用加密配置或环境变量
     * <br><b>生效条件：</b>{@link #autoTokenEnabled}为{@code true}且未使用环境变量或自定义Provider时生效
     * <br><b>安全提示：</b>不建议直接在配置文件中明文存储Token，推荐使用环境变量或密钥管理服务
     */
    private String autoTokenValue = "";
    
    /**
     * 是否从环境变量获取Token。
     * 
     * <p>启用后，会从系统环境变量中读取Token，环境变量名由{@link #autoTokenEnvName}指定。
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.auto-token-from-env}
     * <br><b>默认值：</b>{@code false}
     * <br><b>取值范围：</b>{@code true} | {@code false}
     * <br><b>生效条件：</b>{@link #autoTokenEnabled}为{@code true}时生效
     */
    private boolean autoTokenFromEnv = false;
    
    /**
     * 存储Token的环境变量名称。
     * 
     * <p>当{@link #autoTokenFromEnv}为{@code true}时，从该环境变量读取Token值。
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.auto-token-env-name}
     * <br><b>默认值：</b>{@code "ETL_API_TOKEN"}
     * <br><b>取值范围：</b>有效的环境变量名称，需符合操作系统命名规范
     * <br><b>生效条件：</b>{@link #autoTokenEnabled}和{@link #autoTokenFromEnv}均为{@code true}时生效
     */
    private String autoTokenEnvName = "ETL_API_TOKEN";
    
    /**
     * 默认请求超时时间。
     * 
     * <p>当未单独配置连接超时或读取超时时，作为通用的超时时间参考。
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.default-timeout}
     * <br><b>默认值：</b>{@code 30s}（30秒）
     * <br><b>取值范围：</b>正数Duration值，格式如"30s"、"1m"、"500ms"
     * <br><b>生效条件：</b>始终生效
     */
    private Duration defaultTimeout = Duration.ofSeconds(30);
    
    /**
     * 连接建立超时时间。
     * 
     * <p>控制TCP连接建立的最大等待时间，超时后将抛出连接超时异常。
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.connect-timeout}
     * <br><b>默认值：</b>{@code 10s}（10秒）
     * <br><b>取值范围：</b>正数Duration值，建议值1s-60s
     * <br><b>生效条件：</b>始终生效
     */
    private Duration connectTimeout = Duration.ofSeconds(10);
    
    /**
     * 读取数据超时时间。
     * 
     * <p>控制从服务器读取响应数据的最大等待时间，适用于大文件下载或慢速响应场景。
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.read-timeout}
     * <br><b>默认值：</b>{@code 60s}（60秒）
     * <br><b>取值范围：</b>正数Duration值，建议根据业务场景调整
     * <br><b>生效条件：</b>始终生效
     */
    private Duration readTimeout = Duration.ofSeconds(60);
    
    /**
     * 最大重试次数。
     * 
     * <p>当请求失败时自动重试的最大次数，0表示不重试。
     * 重试间隔由{@link #retryDelayMs}和{@link #retryBackoffFactor}控制。
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.max-retries}
     * <br><b>默认值：</b>{@code 0}
     * <br><b>取值范围：</b>非负整数，建议值0-5
     * <br><b>生效条件：</b>始终生效，仅在请求失败时触发
     */
    private int maxRetries = 0;
    
    /**
     * 重试初始延迟时间（毫秒）。
     * 
     * <p>第一次重试前的等待时间，后续重试会根据{@link #retryBackoffFactor}进行指数退避。
     * 例如：初始延迟1000ms，退避因子2.0，则重试间隔为1000ms、2000ms、4000ms...
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.retry-delay-ms}
     * <br><b>默认值：</b>{@code 1000}（1秒）
     * <br><b>取值范围：</b>正整数，单位毫秒
     * <br><b>生效条件：</b>{@link #maxRetries}大于0时生效
     */
    private long retryDelayMs = 1000;
    
    /**
     * 重试退避因子。
     * 
     * <p>用于计算指数退避的重试间隔。每次重试的延迟时间为：
     * {@code retryDelayMs * (retryBackoffFactor ^ retryCount)}
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.retry-backoff-factor}
     * <br><b>默认值：</b>{@code 2.0}
     * <br><b>取值范围：</b>正浮点数，1.0表示固定间隔，大于1.0表示指数递增
     * <br><b>生效条件：</b>{@link #maxRetries}大于0时生效
     */
    private double retryBackoffFactor = 2.0;
    
    /**
     * 是否启用请求日志。
     * 
     * <p>启用后会记录HTTP请求的详细信息，包括URL、方法、请求体等。
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.log-request-enabled}
     * <br><b>默认值：</b>{@code true}
     * <br><b>取值范围：</b>{@code true} | {@code false}
     * <br><b>生效条件：</b>始终生效
     * <br><b>性能提示：</b>生产环境可考虑关闭以减少日志量
     */
    private boolean logRequestEnabled = true;
    
    /**
     * 是否启用响应日志。
     * 
     * <p>启用后会记录HTTP响应的详细信息，包括状态码、响应体等。
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.log-response-enabled}
     * <br><b>默认值：</b>{@code true}
     * <br><b>取值范围：</b>{@code true} | {@code false}
     * <br><b>生效条件：</b>始终生效
     */
    private boolean logResponseEnabled = true;
    
    /**
     * 是否启用请求/响应头日志。
     * 
     * <p>启用后会记录HTTP请求和响应的完整头信息，用于调试场景。
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.log-headers-enabled}
     * <br><b>默认值：</b>{@code false}
     * <br><b>取值范围：</b>{@code true} | {@code false}
     * <br><b>生效条件：</b>{@link #logRequestEnabled}或{@link #logResponseEnabled}为{@code true}时生效
     * <br><b>安全提示：</b>可能记录敏感信息（如Authorization头），生产环境慎用
     */
    private boolean logHeadersEnabled = false;
    
    /**
     * 日志输出最大请求/响应体长度。
     * 
     * <p>超过此长度的请求体或响应体将被截断，避免日志过大。
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.max-log-body-length}
     * <br><b>默认值：</b>{@code 1000}（1000字符）
     * <br><b>取值范围：</b>正整数，0表示不记录body
     * <br><b>生效条件：</b>{@link #logRequestEnabled}或{@link #logResponseEnabled}为{@code true}时生效
     */
    private int maxLogBodyLength = 1000;
    
    /**
     * 默认请求头映射。
     * 
     * <p>这些请求头会自动添加到所有HTTP请求中，可用于设置User-Agent、Content-Type等通用头。
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.default-headers}
     * <br><b>默认值：</b>空Map
     * <br><b>取值范围：</b>键值对形式，键为HTTP头名称，值为头值
     * <br><b>生效条件：</b>始终生效
     * <br><b>配置示例：</b>
     * <pre>{@code
     * default-headers:
     *   User-Agent: "ETL-Engine/1.0"
     *   Accept: "application/json"
     * }</pre>
     */
    private Map<String, String> defaultHeaders = new LinkedHashMap<>();
    
    /**
     * 需要自动注入Token的HTTP方法列表。
     * 
     * <p>只有在此列表中的HTTP方法才会自动添加认证Token。
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.methods-require-token}
     * <br><b>默认值：</b>{@code ["GET", "POST", "PUT", "DELETE", "PATCH"]}
     * <br><b>取值范围：</b>HTTP方法名称列表，不区分大小写
     * <br><b>生效条件：</b>{@link #autoTokenEnabled}为{@code true}时生效
     */
    private List<String> methodsRequireToken = List.of("GET", "POST", "PUT", "DELETE", "PATCH");
    
    /**
     * 是否启用熔断器。
     * 
     * <p>启用后，当连续失败次数达到{@link #circuitBreakerThreshold}时，
     * 熔断器将打开，拒绝后续请求，直到{@link #circuitBreakerResetTime}后重置。
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.enable-circuit-breaker}
     * <br><b>默认值：</b>{@code false}
     * <br><b>取值范围：</b>{@code true} | {@code false}
     * <br><b>生效条件：</b>始终生效
     */
    private boolean enableCircuitBreaker = false;
    
    /**
     * 熔断器触发阈值（连续失败次数）。
     * 
     * <p>当连续失败次数达到此阈值时，熔断器打开，开始拒绝请求。
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.circuit-breaker-threshold}
     * <br><b>默认值：</b>{@code 5}
     * <br><b>取值范围：</b>正整数，建议值3-10
     * <br><b>生效条件：</b>{@link #enableCircuitBreaker}为{@code true}时生效
     */
    private int circuitBreakerThreshold = 5;
    
    /**
     * 熔断器重置时间。
     * 
     * <p>熔断器打开后，经过此时间将尝试半开状态，允许一次请求通过以测试服务是否恢复。
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.circuit-breaker-reset-time}
     * <br><b>默认值：</b>{@code 60s}（60秒）
     * <br><b>取值范围：</b>正数Duration值
     * <br><b>生效条件：</b>{@link #enableCircuitBreaker}为{@code true}时生效
     */
    private Duration circuitBreakerResetTime = Duration.ofSeconds(60);
    
    /**
     * 端点特定配置映射。
     * 
     * <p>支持为不同的URL模式配置独立的连接参数。键为URL匹配模式（支持contains或startsWith匹配），
     * 值为对应的端点配置。
     * 
     * <p><b>YAML配置键：</b>{@code etl.engine.rest-client.endpoints}
     * <br><b>默认值：</b>空Map
     * <br><b>取值范围：</b>键为URL模式字符串，值为{@link EndpointConfig}配置
     * <br><b>生效条件：</b>始终生效，端点配置优先级高于全局配置
     * <br><b>配置示例：</b>
     * <pre>{@code
     * endpoints:
     *   "https://api.example.com":
     *     base-url: "https://api.example.com"
     *     timeout: 120s
     *     token: "endpoint-specific-token"
     * }</pre>
     */
    private Map<String, EndpointConfig> endpoints = new LinkedHashMap<>();
    
    /**
     * 自定义Token提供器函数。
     * 
     * <p>用于运行时动态获取Token，优先级最高。当设置此Provider后，
     * 会优先调用此函数获取Token，忽略环境变量和静态配置值。
     * 
     * <p>该字段为transient，不会被序列化或绑定到配置文件。
     * 
     * <br><b>优先级：</b>tokenProvider > 环境变量 > 静态配置值
     * <br><b>生效条件：</b>{@link #autoTokenEnabled}为{@code true}且provider不为null时生效
     */
    private transient Function<String, String> tokenProvider = null;
    
    /**
     * 解析并获取最终的Token值。
     * 
     * <p>按照以下优先级顺序尝试获取Token：
     * <ol>
     *   <li>自定义TokenProvider（如果已设置）</li>
     *   <li>环境变量（如果{@link #autoTokenFromEnv}为true）</li>
     *   <li>静态配置值{@link #autoTokenValue}</li>
     * </ol>
     * 
     * <p>获取到Token后，会自动添加{@link #autoTokenPrefix}前缀。
     * 
     * @return 完整的Token字符串（包含前缀），如果无法获取有效Token则返回null
     */
    public String resolveToken() {
        if (!autoTokenEnabled) {
            return null;
        }
        
        if (tokenProvider != null) {
            String token = tokenProvider.apply(autoTokenEnvName);
            if (token != null && !token.isBlank()) {
                logger.debug("使用自定义TokenProvider获取Token");
                return autoTokenPrefix + token;
            }
        }
        
        if (autoTokenFromEnv) {
            String envToken = System.getenv(autoTokenEnvName);
            if (envToken != null && !envToken.isBlank()) {
                logger.debug("从环境变量获取Token: {}", autoTokenEnvName);
                return autoTokenPrefix + envToken;
            }
        }
        
        if (autoTokenValue != null && !autoTokenValue.isBlank()) {
            logger.debug("使用配置的Token值");
            return autoTokenPrefix + autoTokenValue;
        }
        
        return null;
    }
    
    /**
     * 判断指定HTTP方法是否需要添加Token。
     * 
     * <p>检查自动Token功能是否启用，以及给定的HTTP方法是否在{@link #methodsRequireToken}列表中。
     * 
     * @param method HTTP方法名称，如"GET"、"POST"等，不区分大小写
     * @return 如果需要添加Token返回{@code true}，否则返回{@code false}
     */
    public boolean shouldAddToken(String method) {
        if (!autoTokenEnabled) {
            return false;
        }
        return methodsRequireToken.contains(method.toUpperCase());
    }
    
    /**
     * 根据URL获取匹配的端点配置。
     * 
     * <p>遍历{@link #endpoints}映射，查找URL包含或以某个模式开头的端点配置。
     * 第一个匹配的配置将被返回。
     * 
     * @param url 请求的完整URL
     * @return 匹配的端点配置，如果没有匹配则返回null
     */
    public EndpointConfig getEndpointConfig(String url) {
        if (endpoints == null || url == null) {
            return null;
        }
        
        for (Map.Entry<String, EndpointConfig> entry : endpoints.entrySet()) {
            if (url.contains(entry.getKey()) || url.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
    
    /**
     * 设置自定义Token提供器。
     * 
     * <p>用于在运行时动态提供Token，例如从OAuth2服务获取或使用密钥管理服务。
     * 设置后，Token解析时会优先使用此Provider。
     * 
     * @param provider Token提供函数，输入为环境变量名，输出为Token值
     */
    public void setTokenProvider(Function<String, String> provider) {
        this.tokenProvider = provider;
    }
    
    /**
     * 获取自动Token功能是否启用。
     * @return 自动Token功能启用状态
     */
    public boolean isAutoTokenEnabled() { return autoTokenEnabled; }
    
    /**
     * 设置自动Token功能是否启用。
     * @param autoTokenEnabled 启用状态
     */
    public void setAutoTokenEnabled(boolean autoTokenEnabled) { this.autoTokenEnabled = autoTokenEnabled; }
    
    /**
     * 获取Token注入的请求头名称。
     * @return 请求头名称
     */
    public String getAutoTokenHeader() { return autoTokenHeader; }
    
    /**
     * 设置Token注入的请求头名称。
     * @param autoTokenHeader 请求头名称
     */
    public void setAutoTokenHeader(String autoTokenHeader) { this.autoTokenHeader = autoTokenHeader; }
    
    /**
     * 获取Token值的前缀字符串。
     * @return Token前缀
     */
    public String getAutoTokenPrefix() { return autoTokenPrefix; }
    
    /**
     * 设置Token值的前缀字符串。
     * @param autoTokenPrefix Token前缀
     */
    public void setAutoTokenPrefix(String autoTokenPrefix) { this.autoTokenPrefix = autoTokenPrefix; }
    
    /**
     * 获取静态配置的Token值。
     * @return Token值
     */
    public String getAutoTokenValue() { return autoTokenValue; }
    
    /**
     * 设置静态配置的Token值。
     * @param autoTokenValue Token值
     */
    public void setAutoTokenValue(String autoTokenValue) { this.autoTokenValue = autoTokenValue; }
    
    /**
     * 获取是否从环境变量获取Token。
     * @return 是否从环境变量获取
     */
    public boolean isAutoTokenFromEnv() { return autoTokenFromEnv; }
    
    /**
     * 设置是否从环境变量获取Token。
     * @param autoTokenFromEnv 是否从环境变量获取
     */
    public void setAutoTokenFromEnv(boolean autoTokenFromEnv) { this.autoTokenFromEnv = autoTokenFromEnv; }
    
    /**
     * 获取存储Token的环境变量名称。
     * @return 环境变量名称
     */
    public String getAutoTokenEnvName() { return autoTokenEnvName; }
    
    /**
     * 设置存储Token的环境变量名称。
     * @param autoTokenEnvName 环境变量名称
     */
    public void setAutoTokenEnvName(String autoTokenEnvName) { this.autoTokenEnvName = autoTokenEnvName; }
    
    /**
     * 获取默认请求超时时间。
     * @return 默认超时时间
     */
    public Duration getDefaultTimeout() { return defaultTimeout; }
    
    /**
     * 设置默认请求超时时间。
     * @param defaultTimeout 默认超时时间
     */
    public void setDefaultTimeout(Duration defaultTimeout) { this.defaultTimeout = defaultTimeout; }
    
    /**
     * 获取连接建立超时时间。
     * @return 连接超时时间
     */
    public Duration getConnectTimeout() { return connectTimeout; }
    
    /**
     * 设置连接建立超时时间。
     * @param connectTimeout 连接超时时间
     */
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    
    /**
     * 获取读取数据超时时间。
     * @return 读取超时时间
     */
    public Duration getReadTimeout() { return readTimeout; }
    
    /**
     * 设置读取数据超时时间。
     * @param readTimeout 读取超时时间
     */
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    
    /**
     * 获取最大重试次数。
     * @return 最大重试次数
     */
    public int getMaxRetries() { return maxRetries; }
    
    /**
     * 设置最大重试次数。
     * @param maxRetries 最大重试次数
     */
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    
    /**
     * 获取重试初始延迟时间（毫秒）。
     * @return 重试延迟时间
     */
    public long getRetryDelayMs() { return retryDelayMs; }
    
    /**
     * 设置重试初始延迟时间（毫秒）。
     * @param retryDelayMs 重试延迟时间
     */
    public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }
    
    /**
     * 获取重试退避因子。
     * @return 退避因子
     */
    public double getRetryBackoffFactor() { return retryBackoffFactor; }
    
    /**
     * 设置重试退避因子。
     * @param retryBackoffFactor 退避因子
     */
    public void setRetryBackoffFactor(double retryBackoffFactor) { this.retryBackoffFactor = retryBackoffFactor; }
    
    /**
     * 获取是否启用请求日志。
     * @return 请求日志启用状态
     */
    public boolean isLogRequestEnabled() { return logRequestEnabled; }
    
    /**
     * 设置是否启用请求日志。
     * @param logRequestEnabled 请求日志启用状态
     */
    public void setLogRequestEnabled(boolean logRequestEnabled) { this.logRequestEnabled = logRequestEnabled; }
    
    /**
     * 获取是否启用响应日志。
     * @return 响应日志启用状态
     */
    public boolean isLogResponseEnabled() { return logResponseEnabled; }
    
    /**
     * 设置是否启用响应日志。
     * @param logResponseEnabled 响应日志启用状态
     */
    public void setLogResponseEnabled(boolean logResponseEnabled) { this.logResponseEnabled = logResponseEnabled; }
    
    /**
     * 获取是否启用请求/响应头日志。
     * @return 头日志启用状态
     */
    public boolean isLogHeadersEnabled() { return logHeadersEnabled; }
    
    /**
     * 设置是否启用请求/响应头日志。
     * @param logHeadersEnabled 头日志启用状态
     */
    public void setLogHeadersEnabled(boolean logHeadersEnabled) { this.logHeadersEnabled = logHeadersEnabled; }
    
    /**
     * 获取日志输出最大请求/响应体长度。
     * @return 最大body长度
     */
    public int getMaxLogBodyLength() { return maxLogBodyLength; }
    
    /**
     * 设置日志输出最大请求/响应体长度。
     * @param maxLogBodyLength 最大body长度
     */
    public void setMaxLogBodyLength(int maxLogBodyLength) { this.maxLogBodyLength = maxLogBodyLength; }
    
    /**
     * 获取默认请求头映射。
     * @return 默认请求头Map
     */
    public Map<String, String> getDefaultHeaders() { return defaultHeaders; }
    
    /**
     * 设置默认请求头映射。
     * @param defaultHeaders 默认请求头Map
     */
    public void setDefaultHeaders(Map<String, String> defaultHeaders) { this.defaultHeaders = defaultHeaders; }
    
    /**
     * 获取需要自动注入Token的HTTP方法列表。
     * @return HTTP方法列表
     */
    public List<String> getMethodsRequireToken() { return methodsRequireToken; }
    
    /**
     * 设置需要自动注入Token的HTTP方法列表。
     * @param methodsRequireToken HTTP方法列表
     */
    public void setMethodsRequireToken(List<String> methodsRequireToken) { this.methodsRequireToken = methodsRequireToken; }
    
    /**
     * 获取是否启用熔断器。
     * @return 熔断器启用状态
     */
    public boolean isEnableCircuitBreaker() { return enableCircuitBreaker; }
    
    /**
     * 设置是否启用熔断器。
     * @param enableCircuitBreaker 熔断器启用状态
     */
    public void setEnableCircuitBreaker(boolean enableCircuitBreaker) { this.enableCircuitBreaker = enableCircuitBreaker; }
    
    /**
     * 获取熔断器触发阈值。
     * @return 连续失败次数阈值
     */
    public int getCircuitBreakerThreshold() { return circuitBreakerThreshold; }
    
    /**
     * 设置熔断器触发阈值。
     * @param circuitBreakerThreshold 连续失败次数阈值
     */
    public void setCircuitBreakerThreshold(int circuitBreakerThreshold) { this.circuitBreakerThreshold = circuitBreakerThreshold; }
    
    /**
     * 获取熔断器重置时间。
     * @return 重置时间
     */
    public Duration getCircuitBreakerResetTime() { return circuitBreakerResetTime; }
    
    /**
     * 设置熔断器重置时间。
     * @param circuitBreakerResetTime 重置时间
     */
    public void setCircuitBreakerResetTime(Duration circuitBreakerResetTime) { this.circuitBreakerResetTime = circuitBreakerResetTime; }
    
    /**
     * 获取端点特定配置映射。
     * @return 端点配置Map
     */
    public Map<String, EndpointConfig> getEndpoints() { return endpoints; }
    
    /**
     * 设置端点特定配置映射。
     * @param endpoints 端点配置Map
     */
    public void setEndpoints(Map<String, EndpointConfig> endpoints) { this.endpoints = endpoints; }
    
    /**
     * 端点特定配置类。
     * 
     * <p>用于为特定URL模式配置独立的连接参数，优先级高于全局配置。
     * 当请求URL匹配到某个端点配置时，将使用该端点的配置覆盖全局配置。
     * 
     * <h2>配置示例</h2>
     * <pre>{@code
     * endpoints:
     *   "https://slow-api.example.com":
     *     base-url: "https://slow-api.example.com"
     *     timeout: 120s
     *     max-retries: 5
     *     token: "special-token-for-this-endpoint"
     *     headers:
     *       X-Custom-Header: "custom-value"
     * }</pre>
     * 
     * @since 1.0.0
     */
    public static class EndpointConfig {
        
        /**
         * 端点基础URL。
         * 
         * <p>该端点配置适用的基础URL，用于URL匹配和请求构建。
         * 
         * <br><b>YAML配置键：</b>{@code endpoints.{pattern}.base-url}
         * <br><b>默认值：</b>null
         * <br><b>取值范围：</b>有效的URL字符串
         */
        private String baseUrl;
        
        /**
         * 该端点的请求超时时间。
         * 
         * <p>覆盖全局的{@link RestClientProperties#defaultTimeout}配置。
         * 
         * <br><b>YAML配置键：</b>{@code endpoints.{pattern}.timeout}
         * <br><b>默认值：</b>null（使用全局配置）
         * <br><b>取值范围：</b>正数Duration值
         */
        private Duration timeout;
        
        /**
         * 该端点的特定请求头。
         * 
         * <p>这些请求头会与全局默认请求头合并，端点配置的请求头优先级更高。
         * 
         * <br><b>YAML配置键：</b>{@code endpoints.{pattern}.headers}
         * <br><b>默认值：</b>空Map
         * <br><b>取值范围：</b>键值对形式
         */
        private Map<String, String> headers = new LinkedHashMap<>();
        
        /**
         * 该端点的特定Token。
         * 
         * <p>覆盖全局Token配置，用于需要不同认证的端点。
         * 
         * <br><b>YAML配置键：</b>{@code endpoints.{pattern}.token}
         * <br><b>默认值：</b>null（使用全局Token配置）
         * <br><b>取值范围：</b>Token字符串
         */
        private String token;
        
        /**
         * 该端点的最大重试次数。
         * 
         * <p>覆盖全局的{@link RestClientProperties#maxRetries}配置。
         * 
         * <br><b>YAML配置键：</b>{@code endpoints.{pattern}.max-retries}
         * <br><b>默认值：</b>0（使用全局配置）
         * <br><b>取值范围：</b>非负整数
         */
        private int maxRetries;
        
        /**
         * 获取端点基础URL。
         * @return 基础URL
         */
        public String getBaseUrl() { return baseUrl; }
        
        /**
         * 设置端点基础URL。
         * @param baseUrl 基础URL
         */
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        
        /**
         * 获取该端点的请求超时时间。
         * @return 超时时间
         */
        public Duration getTimeout() { return timeout; }
        
        /**
         * 设置该端点的请求超时时间。
         * @param timeout 超时时间
         */
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        
        /**
         * 获取该端点的特定请求头。
         * @return 请求头Map
         */
        public Map<String, String> getHeaders() { return headers; }
        
        /**
         * 设置该端点的特定请求头。
         * @param headers 请求头Map
         */
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
        
        /**
         * 获取该端点的特定Token。
         * @return Token值
         */
        public String getToken() { return token; }
        
        /**
         * 设置该端点的特定Token。
         * @param token Token值
         */
        public void setToken(String token) { this.token = token; }
        
        /**
         * 获取该端点的最大重试次数。
         * @return 最大重试次数
         */
        public int getMaxRetries() { return maxRetries; }
        
        /**
         * 设置该端点的最大重试次数。
         * @param maxRetries 最大重试次数
         */
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    }
}
