package com.etl.engine.callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * 本地节点信息组件
 * 
 * <p>负责获取和管理当前运行节点的网络信息，包括IP地址和端口号。
 * 这些信息用于分布式环境下的异步回调转发机制，确保第三方回调
 * 能够正确路由到原始发起请求的节点。</p>
 * 
 * <h3>主要功能：</h3>
 * <ul>
 *   <li>自动检测本机IP地址（支持多网卡环境）</li>
 *   <li>获取服务监听端口</li>
 *   <li>生成唯一节点标识</li>
 *   <li>构建回调URL</li>
 * </ul>
 * 
 * <h3>配置项：</h3>
 * <ul>
 *   <li>server.port - 服务监听端口</li>
 *   <li>etl.engine.node.ip - 手动指定节点IP（可选）</li>
 *   <li>etl.engine.callback.public-path - 公共回调路径</li>
 *   <li>etl.engine.callback.internal-path - 内部回调路径</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * @Autowired
 * private LocalNodeInfo localNodeInfo;
 * 
 * String nodeIp = localNodeInfo.getNodeIp();
 * int port = localNodeInfo.getServerPort();
 * String callbackUrl = localNodeInfo.getPublicCallbackUrl();
 * }</pre>
 * 
 * @author ETL Engine Team
 * @version 1.0.0
 * @since 2026-05-24
 * @see LocalEventManager
 * @see com.etl.engine.rest.PublicCallbackController
 */
@Component
public class LocalNodeInfo {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalNodeInfo.class);
    
    /**
     * 服务监听端口
     * 从配置项 server.port 读取，默认8080
     */
    @Value("${server.port:8080}")
    private int serverPort;
    
    /**
     * 手动配置的节点IP地址
     * 当自动检测不准确时，可通过配置项 etl.engine.node.ip 手动指定
     * 为空时使用自动检测逻辑
     */
    @Value("${etl.engine.node.ip:}")
    private String configuredIp;
    
    /**
     * 公共回调接口路径
     * 第三方服务回调的统一入口路径
     * 默认值: /api/public/callback
     */
    @Value("${etl.engine.callback.public-path:/api/public/callback}")
    private String publicCallbackPath;
    
    /**
     * 内部回调接口路径
     * 集群内部转发回调的目标路径
     * 默认值: /api/internal/callback
     */
    @Value("${etl.engine.callback.internal-path:/api/internal/callback}")
    private String internalCallbackPath;
    
    /**
     * 解析后的节点IP地址
     * 在初始化时通过自动检测或配置获取
     */
    private String nodeIp;
    
    /**
     * 节点唯一标识
     * 格式: {ip}:{port}
     */
    private String nodeId;
    
    /**
     * 初始化节点信息
     * 
     * <p>在Bean创建后执行，按以下优先级获取节点IP：</p>
     * <ol>
     *   <li>配置项 etl.engine.node.ip 指定的值</li>
     *   <li>自动检测非回环、非IPv6的网络地址</li>
     *   <li>InetAddress.getLocalHost() 获取的地址</li>
     *   <li>默认值 127.0.0.1</li>
     * </ol>
     */
    @PostConstruct
    public void init() {
        this.nodeIp = resolveNodeIp();
        this.nodeId = generateNodeId();
        logger.info("本地节点初始化完成 - IP: {}, Port: {}, NodeId: {}", nodeIp, serverPort, nodeId);
    }
    
    /**
     * 解析节点IP地址
     * 
     * <p>按优先级获取可用IP：</p>
     * <ol>
     *   <li>检查手动配置的IP</li>
     *   <li>遍历网络接口，寻找非回环、非IPv6地址</li>
     *   <li>使用localhost作为备选</li>
     *   <li>返回默认值127.0.0.1</li>
     * </ol>
     * 
     * @return 解析后的节点IP地址
     */
    private String resolveNodeIp() {
        if (configuredIp != null && !configuredIp.isBlank()) {
            logger.debug("使用配置的节点IP: {}", configuredIp);
            return configuredIp;
        }
        
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.isLoopbackAddress()) {
                        continue;
                    }
                    String hostAddress = addr.getHostAddress();
                    if (hostAddress.contains(":")) {
                        continue;
                    }
                    logger.debug("自动检测到节点IP: {}", hostAddress);
                    return hostAddress;
                }
            }
        } catch (Exception e) {
            logger.warn("自动检测节点IP失败: {}", e.getMessage());
        }
        
        try {
            String localhost = InetAddress.getLocalHost().getHostAddress();
            logger.debug("使用localhost作为节点IP: {}", localhost);
            return localhost;
        } catch (Exception e) {
            logger.warn("获取localhost失败，使用默认值: {}", e.getMessage());
            return "127.0.0.1";
        }
    }
    
    /**
     * 生成节点唯一标识
     * 
     * @return 节点ID，格式为 {ip}:{port}
     */
    private String generateNodeId() {
        return nodeIp + ":" + serverPort;
    }
    
    /**
     * 获取节点IP地址
     * 
     * @return 当前节点的IP地址
     */
    public String getNodeIp() {
        return nodeIp;
    }
    
    /**
     * 获取服务端口
     * 
     * @return 当前服务监听的端口号
     */
    public int getServerPort() {
        return serverPort;
    }
    
    /**
     * 获取节点唯一标识
     * 
     * @return 节点ID，格式为 {ip}:{port}
     */
    public String getNodeId() {
        return nodeId;
    }
    
    /**
     * 获取公共回调URL
     * 
     * <p>用于第三方服务回调，格式：</p>
     * <pre>http://{ip}:{port}/api/public/callback</pre>
     * 
     * @return 完整的公共回调URL
     */
    public String getPublicCallbackUrl() {
        return String.format("http://%s:%d%s", nodeIp, serverPort, publicCallbackPath);
    }
    
    /**
     * 获取内部回调URL
     * 
     * <p>用于集群内部转发，格式：</p>
     * <pre>http://{ip}:{port}/api/internal/callback</pre>
     * 
     * @return 完整的内部回调URL
     */
    public String getInternalCallbackUrl() {
        return String.format("http://%s:%d%s", nodeIp, serverPort, internalCallbackPath);
    }
    
    /**
     * 构建指定目标节点的内部回调URL
     * 
     * <p>用于构建转发到其他节点的回调URL</p>
     * 
     * @param targetIp 目标节点IP
     * @param targetPort 目标节点端口
     * @return 目标节点的内部回调URL
     */
    public String buildInternalCallbackUrl(String targetIp, int targetPort) {
        return String.format("http://%s:%d%s", targetIp, targetPort, internalCallbackPath);
    }
    
    /**
     * 判断是否为本地节点
     * 
     * <p>通过比较IP和端口判断指定的地址是否为当前节点</p>
     * 
     * @param ip 待检查的IP地址
     * @param port 待检查的端口号
     * @return 如果是本地节点返回true，否则返回false
     */
    public boolean isLocalNode(String ip, int port) {
        return nodeIp.equals(ip) && serverPort == port;
    }
    
    /**
     * 返回节点信息的字符串表示
     * 
     * @return 包含IP、端口和节点ID的字符串
     */
    @Override
    public String toString() {
        return "LocalNodeInfo{ip='" + nodeIp + "', port=" + serverPort + ", nodeId='" + nodeId + "'}";
    }
}
