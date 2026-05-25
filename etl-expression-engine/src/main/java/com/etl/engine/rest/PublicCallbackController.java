package com.etl.engine.rest;

import com.etl.engine.callback.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 公共回调控制器，处理外部系统的回调请求。
 * 
 * <p>该控制器提供对外暴露的回调接口，用于接收外部系统的异步回调通知。
 * 当收到回调时，会根据目标节点信息判断是本地处理还是转发到其他节点。</p>
 * 
 * <p>主要功能：</p>
 * <ul>
 *   <li>接收外部系统的回调数据</li>
 *   <li>判断回调目标是本机还是远程节点</li>
 *   <li>本机回调直接处理，远程回调转发到目标节点</li>
 * </ul>
 * 
 * <p>接口路径前缀：{@code /api/public}</p>
 * 
 * @author ETL Engine
 * @version 1.0
 * @since 1.0
 */
@RestController
@RequestMapping("/api/public")
public class PublicCallbackController {
    
    private static final Logger logger = LoggerFactory.getLogger(PublicCallbackController.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private final RestClient internalRestClient;
    private final LocalNodeInfo localNodeInfo;
    
    /**
     * 构造函数，初始化公共回调控制器。
     * 
     * @param restClient 用于内部节点间通信的REST客户端
     * @param localNodeInfo 本地节点信息，用于判断目标是否为本机
     */
    public PublicCallbackController(RestClient restClient, LocalNodeInfo localNodeInfo) {
        this.internalRestClient = restClient;
        this.localNodeInfo = localNodeInfo;
    }
    
    /**
     * 处理公共回调请求。
     * 
     * <p>该接口接收外部系统的回调数据，根据回调数据中的目标信息进行路由：</p>
     * <ul>
     *   <li>如果目标节点是本机，直接调用{@link LocalEventManager}完成事件</li>
     *   <li>如果目标节点是远程节点，转发回调数据到目标节点的内部回调接口</li>
     * </ul>
     * 
     * <p>回调数据必须包含以下字段：</p>
     * <ul>
     *   <li>eventId（或event_id、x-callback-eventid）：事件唯一标识</li>
     *   <li>targetIp（或target_ip、x-callback-targetip）：目标节点IP</li>
     *   <li>targetPort（或target_port、x-callback-targetport）：目标节点端口</li>
     * </ul>
     * 
     * @param callbackData 回调数据Map，包含eventId、targetIp、targetPort及其他业务数据
     * @return 处理结果响应：
     *         <ul>
     *           <li>成功处理：包含success=true、eventId、processed=true</li>
     *           <li>成功转发：包含success=true、eventId、forwarded=true、目标节点信息</li>
     *           <li>参数缺失：HTTP 400，包含success=false、error信息</li>
     *           <li>转发失败：HTTP 502，包含success=false、error信息</li>
     *         </ul>
     */
    @PostMapping("/callback")
    public ResponseEntity<?> handleCallback(@RequestBody Map<String, Object> callbackData) {
        logger.info("收到公共回调: {}", callbackData);
        
        String eventId = extractEventId(callbackData);
        String targetIp = extractTargetIp(callbackData);
        Integer targetPort = extractTargetPort(callbackData);
        
        if (eventId == null || eventId.isBlank()) {
            logger.warn("回调数据缺少eventId");
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "缺少eventId"
            ));
        }
        
        if (targetIp == null || targetPort == null) {
            logger.warn("回调数据缺少targetIp或targetPort: eventId={}", eventId);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "缺少targetIp或targetPort"
            ));
        }
        
        if (localNodeInfo.isLocalNode(targetIp, targetPort)) {
            logger.info("回调目标就是本机，直接处理: eventId={}, target={}:{}", 
                eventId, targetIp, targetPort);
            
            boolean success = LocalEventManagerHolder.eventManager.completeEvent(eventId, callbackData);
            
            return ResponseEntity.ok(Map.of(
                "success", success,
                "eventId", eventId,
                "processed", true,
                "message", success ? "回调处理成功" : "事件不存在或已处理"
            ));
        }
        
        logger.info("转发回调到目标节点: eventId={}, target={}:{}", eventId, targetIp, targetPort);
        
        try {
            String internalUrl = localNodeInfo.buildInternalCallbackUrl(targetIp, targetPort);
            
            String response = internalRestClient.post()
                .uri(internalUrl)
                .header("Content-Type", "application/json")
                .body(callbackData)
                .retrieve()
                .body(String.class);
            
            logger.info("转发成功: eventId={}, response={}", eventId, response);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "eventId", eventId,
                "forwarded", true,
                "targetIp", targetIp,
                "targetPort", targetPort,
                "response", response
            ));
            
        } catch (Exception e) {
            logger.error("转发失败: eventId={}, target={}:{}, error={}", 
                eventId, targetIp, targetPort, e.getMessage());
            
            return ResponseEntity.status(502).body(Map.of(
                "success", false,
                "eventId", eventId,
                "forwarded", false,
                "error", "转发失败: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 处理简化格式的回调请求。
     * 
     * <p>该接口提供一种简化的回调方式，通过URL参数传递元数据，
     * 请求体传递业务载荷。内部会构造标准格式的回调数据并调用{@link #handleCallback(Map)}。</p>
     * 
     * @param eventId 事件唯一标识，可选参数
     * @param targetIp 目标节点IP，可选参数
     * @param targetPort 目标节点端口，可选参数
     * @param payload 业务载荷数据，可选参数
     * @return 处理结果响应，与{@link #handleCallback(Map)}返回格式一致
     */
    @PostMapping("/callback/simple")
    public ResponseEntity<?> handleSimpleCallback(
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) String targetIp,
            @RequestParam(required = false) Integer targetPort,
            @RequestBody(required = false) Object payload) {
        
        Map<String, Object> callbackData = new java.util.LinkedHashMap<>();
        callbackData.put("eventId", eventId);
        callbackData.put("targetIp", targetIp);
        callbackData.put("targetPort", targetPort);
        callbackData.put("payload", payload);
        
        return handleCallback(callbackData);
    }
    
    /**
     * 从回调数据中提取事件ID。
     * 
     * <p>支持多种字段名称格式：</p>
     * <ul>
     *   <li>eventId（驼峰格式）</li>
     *   <li>event_id（下划线格式）</li>
     *   <li>x-callback-eventid（HTTP头格式）</li>
     * </ul>
     * 
     * @param data 回调数据Map
     * @return 事件ID字符串，如果不存在则返回null
     */
    private String extractEventId(Map<String, Object> data) {
        Object eventId = data.get("eventId");
        if (eventId == null) {
            eventId = data.get("event_id");
        }
        if (eventId == null) {
            eventId = data.get("x-callback-eventid");
        }
        return eventId != null ? String.valueOf(eventId) : null;
    }
    
    /**
     * 从回调数据中提取目标节点IP。
     * 
     * <p>支持多种字段名称格式：</p>
     * <ul>
     *   <li>targetIp（驼峰格式）</li>
     *   <li>target_ip（下划线格式）</li>
     *   <li>x-callback-targetip（HTTP头格式）</li>
     * </ul>
     * 
     * @param data 回调数据Map
     * @return 目标节点IP字符串，如果不存在则返回null
     */
    private String extractTargetIp(Map<String, Object> data) {
        Object targetIp = data.get("targetIp");
        if (targetIp == null) {
            targetIp = data.get("target_ip");
        }
        if (targetIp == null) {
            targetIp = data.get("x-callback-targetip");
        }
        return targetIp != null ? String.valueOf(targetIp) : null;
    }
    
    /**
     * 从回调数据中提取目标节点端口。
     * 
     * <p>支持多种字段名称格式：</p>
     * <ul>
     *   <li>targetPort（驼峰格式）</li>
     *   <li>target_port（下划线格式）</li>
     *   <li>x-callback-targetport（HTTP头格式）</li>
     * </ul>
     * 
     * @param data 回调数据Map
     * @return 目标节点端口号，如果不存在或格式无效则返回null
     */
    private Integer extractTargetPort(Map<String, Object> data) {
        Object targetPort = data.get("targetPort");
        if (targetPort == null) {
            targetPort = data.get("target_port");
        }
        if (targetPort == null) {
            targetPort = data.get("x-callback-targetport");
        }
        if (targetPort == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(targetPort));
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 本地事件管理器持有者。
     * 
     * <p>该内部类用于在静态上下文中持有{@link LocalEventManager}的引用，
     * 使得公共回调控制器能够访问本地事件管理器来完成事件。</p>
     * 
     * <p>使用静态持有者模式的原因是为了解决循环依赖或延迟初始化的问题。</p>
     * 
     * @author ETL Engine
     * @version 1.0
     * @since 1.0
     */
    public static class LocalEventManagerHolder {
        
        /** 本地事件管理器实例 */
        static LocalEventManager eventManager;
        
        /**
         * 设置本地事件管理器实例。
         * 
         * <p>该方法应在应用启动时调用，用于初始化静态持有的事件管理器引用。</p>
         * 
         * @param manager 本地事件管理器实例
         */
        public static void setEventManager(LocalEventManager manager) {
            eventManager = manager;
        }
    }
}
