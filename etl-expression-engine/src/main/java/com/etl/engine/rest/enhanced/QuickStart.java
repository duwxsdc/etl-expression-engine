package com.etl.engine.rest.enhanced;

import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class QuickStart {

    public static void main(String[] args) throws Exception {
        EnhancedRestClient client = EnhancedRestClient.create(RestClient.create());
        
        ExtensionRegistry registry = client.getExtensionRegistry();
        registry.register(DefaultTokenExtension.withBearerToken("your-token"));
        registry.register(new CallbackExtension());
        
        System.out.println("=== Example 1: Simple GET ===");
        String users = client.get()
            .uri("https://jsonplaceholder.typicode.com/users/1")
            .retrieve()
            .body(String.class);
        System.out.println("Users: " + users);
        
        System.out.println("\n=== Example 2: POST with Token ===");
        String created = client.post()
            .uri("https://jsonplaceholder.typicode.com/posts")
            .defaultToken()
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(Map.of("title", "Test", "body", "Content", "userId", 1))
            .retrieve()
            .body(String.class);
        System.out.println("Created: " + created);
        
        System.out.println("\n=== Example 3: Callback Demo ===");
        System.out.println("To test callback, start a separate thread to call:");
        System.out.println("POST http://localhost:8080/api/callback/complete/{eventId}");
        System.out.println("with your callback data.");
        
        String eventId = CallbackManager.getInstance().generateEventId();
        System.out.println("Generated eventId: " + eventId);
        
        CompletableFuture<Object> future = CallbackManager.getInstance().register(eventId, 5000);
        
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                CallbackManager.getInstance().complete(eventId, Map.of(
                    "status", "SUCCESS",
                    "data", "Callback received!",
                    "timestamp", System.currentTimeMillis()
                ));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        try {
            Object callbackResult = future.get();
            System.out.println("Callback result: " + callbackResult);
        } catch (Exception e) {
            System.out.println("Callback error: " + e.getMessage());
        }
        
        System.out.println("\n=== Stats ===");
        System.out.println("Pending callbacks: " + CallbackManager.getInstance().getPendingCount());
    }
}