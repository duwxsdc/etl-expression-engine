package com.etl.engine.rest.enhanced;

import org.springframework.web.client.RestClient;

import java.util.Map;

public class ExtensionDemo {

    public static void main(String[] args) {
        EnhancedRestClient client = EnhancedRestClient.create(RestClient.create());
        
        ExtensionRegistry registry = client.getExtensionRegistry();
        
        registry.register(new TracingExtension());
        registry.register(new LoggingExtension());
        registry.register(DefaultTokenExtension.withBearerToken("demo-token"));
        registry.register(new SigningExtension("my-secret-key"));
        registry.register(new RetryExtension(3, 1000));
        registry.register(new CallbackExtension());
        
        System.out.println("=== Registered Extensions ===");
        registry.getExtensions().forEach(ext -> 
            System.out.println("- " + ext.name() + " (order: " + ext.order() + ")")
        );
        
        System.out.println("\n=== Request with All Extensions ===");
        
        try {
            String result = client.post()
                .uri("https://api.example.com/process")
                .enable("tracing", "logging", "signing")
                .defaultToken()
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of(
                    "action", "process",
                    "data", "sample-data"
                ))
                .retrieve()
                .body(String.class);
            
            System.out.println("Result: " + result);
        } catch (Exception e) {
            System.out.println("Error (expected for demo): " + e.getMessage());
        }
        
        System.out.println("\n=== Selective Extensions ===");
        
        try {
            String result = client.get()
                .uri("https://api.example.com/data")
                .enable("tracing", "logging")
                .retrieve()
                .body(String.class);
            
            System.out.println("Result: " + result);
        } catch (Exception e) {
            System.out.println("Error (expected for demo): " + e.getMessage());
        }
        
        System.out.println("\n=== Callback with Extensions ===");
        
        try {
            Object callbackResult = client.post()
                .uri("https://third-party.com/async")
                .enable("tracing", "logging")
                .defaultToken()
                .callback(5000)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of("task", "async-process"))
                .retrieve()
                .body(Object.class);
            
            System.out.println("Callback result: " + callbackResult);
        } catch (CallbackTimeoutException e) {
            System.out.println("Callback timeout (expected for demo): " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}