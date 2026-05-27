package com.etl.engine.rest.enhanced;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/callback")
public class CallbackController {
    
    private final CallbackManager callbackManager = CallbackManager.getInstance();
    
    @PostMapping("/complete")
    public ResponseEntity<?> completeCallback(@RequestBody Map<String, Object> callbackData) {
        String eventId = extractEventId(callbackData);
        
        if (eventId == null || eventId.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Missing eventId"
            ));
        }
        
        boolean completed = callbackManager.complete(eventId, callbackData);
        
        if (completed) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "eventId", eventId,
                "message", "Callback completed"
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "eventId", eventId,
                "message", "Event not found or already completed"
            ));
        }
    }
    
    @PostMapping("/complete/{eventId}")
    public ResponseEntity<?> completeCallbackById(
            @PathVariable String eventId,
            @RequestBody(required = false) Object result) {
        
        boolean completed = callbackManager.complete(eventId, result);
        
        if (completed) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "eventId", eventId,
                "message", "Callback completed"
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "eventId", eventId,
                "message", "Event not found or already completed"
            ));
        }
    }
    
    @GetMapping("/status/{eventId}")
    public ResponseEntity<?> getStatus(@PathVariable String eventId) {
        boolean exists = callbackManager.exists(eventId);
        CallbackManager.PendingCallback pending = callbackManager.getPendingCallback(eventId);
        
        return ResponseEntity.ok(Map.of(
            "eventId", eventId,
            "exists", exists,
            "pending", exists,
            "completed", !exists,
            "createTime", pending != null ? pending.getCreateTime() : null,
            "timeoutMs", pending != null ? pending.getTimeoutMs() : null
        ));
    }
    
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(Map.of(
            "pendingCount", callbackManager.getPendingCount()
        ));
    }
    
    @DeleteMapping("/cleanup/{eventId}")
    public ResponseEntity<?> cleanup(@PathVariable String eventId) {
        callbackManager.cleanup(eventId);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "eventId", eventId,
            "message", "Event cleaned up"
        ));
    }
    
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