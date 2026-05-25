package com.etl.engine.rest;

import com.etl.engine.callback.LocalEventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 内部回调控制器，处理节点内部的回调请求。
 * 
 * <p>该控制器提供内部节点间通信的回调接口，用于接收从{@link PublicCallbackController}
 * 转发过来的回调请求，以及直接调用的内部回调。</p>
 * 
 * <p>主要功能：</p>
 * <ul>
 *   <li>接收转发过来的回调数据并完成对应事件</li>
 *   <li>提供直接回调接口，通过URL参数指定事件ID</li>
 *   <li>查询事件状态和统计信息</li>
 * </ul>
 * 
 * <p>接口路径前缀：{@code /api/internal}</p>
 * 
 * <p>该控制器不对外暴露，仅供集群内部节点间通信使用。</p>
 * 
 * @author ETL Engine
 * @version 1.0
 * @since 1.0
 * @see LocalEventManager
 * @see PublicCallbackController
 */
@RestController
@RequestMapping("/api/internal")
public class InternalCallbackController {
    
    private static final Logger logger = LoggerFactory.getLogger(InternalCallbackController.class);
    
    private final LocalEventManager eventManager;
    
    /**
     * 构造函数，初始化内部回调控制器。
     * 
     * @param eventManager 本地事件管理器，用于完成等待中的事件
     */
    public InternalCallbackController(LocalEventManager eventManager) {
        this.eventManager = eventManager;
    }
    
    /**
     * 处理内部回调请求。
     * 
     * <p>该接口接收从公共回调控制器转发过来的回调数据，
     * 提取事件ID并调用{@link LocalEventManager}完成对应的事件，
     * 从而唤醒等待该事件的线程。</p>
     * 
     * <p>回调数据中的eventId支持以下字段名称：</p>
     * <ul>
     *   <li>eventId（驼峰格式）</li>
     *   <li>event_id（下划线格式）</li>
     *   <li>x-callback-eventid（HTTP头格式）</li>
     * </ul>
     * 
     * @param callbackData 回调数据Map，必须包含eventId字段
     * @return 处理结果响应：
     *         <ul>
     *           <li>成功：包含success=true、eventId、message="事件唤醒成功"</li>
     *           <li>事件不存在或已处理：包含success=false、eventId、message="事件不存在或已处理"</li>
     *           <li>参数缺失：HTTP 400，包含success=false、error="缺少eventId"</li>
     *         </ul>
     */
    @PostMapping("/callback")
    public ResponseEntity<?> handleInternalCallback(@RequestBody Map<String, Object> callbackData) {
        String eventId = extractEventId(callbackData);
        
        logger.info("收到内部回调: eventId={}, data={}", eventId, callbackData);
        
        if (eventId == null || eventId.isBlank()) {
            logger.warn("内部回调缺少eventId");
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "缺少eventId"
            ));
        }
        
        boolean completed = eventManager.completeEvent(eventId, callbackData);
        
        if (completed) {
            logger.info("事件唤醒成功: eventId={}", eventId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "eventId", eventId,
                "message", "事件唤醒成功"
            ));
        } else {
            logger.warn("事件唤醒失败，事件不存在或已处理: eventId={}", eventId);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "eventId", eventId,
                "message", "事件不存在或已处理"
            ));
        }
    }
    
    /**
     * 处理直接回调请求。
     * 
     * <p>该接口提供一种简化的内部回调方式，事件ID通过URL路径参数指定，
     * 业务载荷通过请求体传递。适用于明确知道事件ID的场景。</p>
     * 
     * @param eventId 事件唯一标识，必填参数，通过URL路径传递
     * @param payload 业务载荷数据，可以是任意JSON对象
     * @return 处理结果响应：
     *         <ul>
     *           <li>成功：包含success=true、eventId、message="事件唤醒成功"</li>
     *           <li>失败：包含success=false、eventId、message="事件不存在或已处理"</li>
     *         </ul>
     */
    @PostMapping("/callback/direct")
    public ResponseEntity<?> handleDirectCallback(
            @RequestParam String eventId,
            @RequestBody Object payload) {
        
        logger.info("收到直接回调: eventId={}, payload={}", eventId, payload);
        
        boolean completed = eventManager.completeEvent(eventId, payload);
        
        return ResponseEntity.ok(Map.of(
            "success", completed,
            "eventId", eventId,
            "message", completed ? "事件唤醒成功" : "事件不存在或已处理"
        ));
    }
    
    /**
     * 查询指定事件的状态。
     * 
     * <p>该接口用于查询某个事件是否存在、是否已完成、是否仍在等待中。</p>
     * 
     * @param eventId 事件唯一标识，通过URL路径传递
     * @return 事件状态响应，包含以下字段：
     *         <ul>
     *           <li>eventId：事件ID</li>
     *           <li>exists：事件是否存在</li>
     *           <li>completed：事件是否已完成</li>
     *           <li>pending：事件是否仍在等待中（exists且未completed）</li>
     *         </ul>
     */
    @GetMapping("/event/{eventId}/status")
    public ResponseEntity<?> getEventStatus(@PathVariable String eventId) {
        boolean exists = eventManager.hasEvent(eventId);
        boolean completed = eventManager.isCompleted(eventId);
        
        return ResponseEntity.ok(Map.of(
            "eventId", eventId,
            "exists", exists,
            "completed", completed,
            "pending", exists && !completed
        ));
    }
    
    /**
     * 获取事件统计信息。
     * 
     * <p>该接口返回当前事件管理器的统计信息，包括等待中的事件数量等。</p>
     * 
     * @return 统计信息响应，包含以下字段：
     *         <ul>
     *           <li>pendingCount：当前等待中的事件数量</li>
     *         </ul>
     */
    @GetMapping("/events/stats")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(Map.of(
            "pendingCount", eventManager.getPendingCount()
        ));
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
}
