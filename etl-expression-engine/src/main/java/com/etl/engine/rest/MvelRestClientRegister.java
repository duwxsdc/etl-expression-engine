package com.etl.engine.rest;

import com.etl.engine.mvel.MvelSecuritySandbox;
import org.mvel2.ParserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;

/**
 * MvelRestClient MVEL上下文注册组件。
 * 
 * <p>该组件负责将{@link MvelRestClient}及其常用方法注册到MVEL表达式的执行上下文中，
 * 使得ETL表达式能够直接使用REST客户端功能进行HTTP调用。</p>
 * 
 * <h2>注册内容</h2>
 * <p>该组件在MVEL上下文中注册以下内容：</p>
 * <ul>
 *   <li><b>类导入</b>：{@code RestClient}类本身，可通过{@code RestClient.get(url)}等方式调用</li>
 *   <li><b>变量输入</b>：{@code RestClient}作为上下文变量，便于表达式直接引用</li>
 *   <li><b>方法别名</b>：
 *     <ul>
 *       <li>{@code restGet} - 对应{@link MvelRestClient#get(String)}方法</li>
 *       <li>{@code restPost} - 对应{@link MvelRestClient#post(String)}方法</li>
 *       <li>{@code restPut} - 对应{@link MvelRestClient#put(String)}方法</li>
 *       <li>{@code restDelete} - 对应{@link MvelRestClient#delete(String)}方法</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <h2>使用示例</h2>
 * <p>注册完成后，可在MVEL表达式中使用以下方式发起HTTP请求：</p>
 * <pre>{@code
 * // 方式一：通过类调用
 * RestClient.get("http://api.example.com/data")
 * RestClient.post("http://api.example.com/data")
 * 
 * // 方式二：通过方法别名
 * restGet("http://api.example.com/data")
 * restPost("http://api.example.com/data")
 * }</pre>
 * 
 * <h2>安全性说明</h2>
 * <p>该组件通过{@link MvelSecuritySandbox}创建安全的解析上下文，
 * 确保REST客户端功能在受控的安全沙箱环境中运行，防止恶意表达式滥用HTTP能力。</p>
 * 
 * <h2>初始化时机</h2>
 * <p>该组件使用{@link PostConstruct}注解，在Spring容器完成依赖注入后自动执行注册。
 * 注册顺序晚于{@link RestClientCallbackAutoConfig}中的初始化逻辑，
 * 确保MvelRestClient已完全初始化后再注册到MVEL上下文。</p>
 * 
 * @author ETL Engine Team
 * @see MvelRestClient
 * @see MvelSecuritySandbox
 * @see RestClientCallbackAutoConfig
 * @since 1.0.0
 */
@Component
public class MvelRestClientRegister {
    
    private static final Logger logger = LoggerFactory.getLogger(MvelRestClientRegister.class);
    
    private final MvelSecuritySandbox mvelSecuritySandbox;
    
    /**
     * 构造注册器实例。
     * 
     * <p>通过依赖注入获取{@link MvelSecuritySandbox}实例，用于创建安全的MVEL解析上下文。</p>
     * 
     * @param mvelSecuritySandbox MVEL安全沙箱实例
     */
    public MvelRestClientRegister(MvelSecuritySandbox mvelSecuritySandbox) {
        this.mvelSecuritySandbox = mvelSecuritySandbox;
    }
    
    /**
     * 执行MvelRestClient到MVEL上下文的注册。
     * 
     * <p>该方法在Spring容器完成依赖注入后自动调用（{@link PostConstruct}），
     * 执行以下注册步骤：</p>
     * <ol>
     *   <li>通过{@link MvelSecuritySandbox}创建安全的{@link ParserContext}</li>
     *   <li>导入{@link MvelRestClient}类，使其可在表达式中直接使用类名调用</li>
     *   <li>将{@link MvelRestClient}添加为上下文输入变量</li>
     *   <li>通过反射获取get/post/put/delete方法，并注册为方法别名</li>
     *   <li>输出注册成功的日志信息，列出所有可用的调用方式</li>
     * </ol>
     * 
     * <h3>异常处理</h3>
     * <p>如果方法注册失败（例如方法签名变更），会抛出{@link IllegalStateException}，
     * 阻止应用启动，确保运行时一致性。</p>
     * 
     * <h3>日志输出</h3>
     * <p>注册成功后会输出详细的日志信息，包括所有可用的调用方式，
     * 便于开发者在编写表达式时参考：</p>
     * <pre>
     * MvelRestClient注册成功，可在MVEL表达式中使用:
     *   - RestClient.get(url)
     *   - RestClient.post(url)
     *   - RestClient.put(url)
     *   - RestClient.delete(url)
     * </pre>
     * 
     * @throws IllegalStateException 当方法注册失败时抛出，阻止应用启动
     */
    @PostConstruct
    public void registerRestClient() {
        logger.info("注册MvelRestClient到MVEL上下文...");
        
        ParserContext parserContext = mvelSecuritySandbox.createSafeParserContext();
        
        try {
            parserContext.addImport("RestClient", MvelRestClient.class);
            
            parserContext.addInput("RestClient", MvelRestClient.class);
            
            Method getMethod = MvelRestClient.class.getMethod("get", String.class);
            parserContext.addImport("restGet", getMethod);
            
            Method postMethod = MvelRestClient.class.getMethod("post", String.class);
            parserContext.addImport("restPost", postMethod);
            
            Method putMethod = MvelRestClient.class.getMethod("put", String.class);
            parserContext.addImport("restPut", putMethod);
            
            Method deleteMethod = MvelRestClient.class.getMethod("delete", String.class);
            parserContext.addImport("restDelete", deleteMethod);
            
            logger.info("MvelRestClient注册成功，可在MVEL表达式中使用:");
            logger.info("  - RestClient.get(url)");
            logger.info("  - RestClient.post(url)");
            logger.info("  - RestClient.put(url)");
            logger.info("  - RestClient.delete(url)");
            
        } catch (NoSuchMethodException e) {
            logger.error("MvelRestClient方法注册失败", e);
            throw new IllegalStateException("MvelRestClient方法注册失败", e);
        }
    }
}
