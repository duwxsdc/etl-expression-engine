package com.etl.engine.rest.enhanced;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.function.Function;

public class UsageExample {

    public static void main(String[] args) {
        example1_BasicUsage();
        example2_WithDefaultToken();
        example3_WithCallback();
        example4_CustomExtension();
        example5_MixedUsage();
    }
    
    static void example1_BasicUsage() {
        RestClient nativeClient = RestClient.create();
        EnhancedRestClient client = EnhancedRestClient.create(nativeClient);
        
        String result = client.get()
            .uri("https://api.example.com/users")
            .header("Accept", "application/json")
            .retrieve()
            .body(String.class);
        
        System.out.println("Result: " + result);
    }
    
    static void example2_WithDefaultToken() {
        RestClient nativeClient = RestClient.create();
        EnhancedRestClient client = EnhancedRestClient.create(nativeClient);
        
        ExtensionRegistry registry = client.getExtensionRegistry();
        registry.register(DefaultTokenExtension.withBearerToken("my-access-token"));
        
        String result = client.post()
            .uri("https://api.example.com/data")
            .defaultToken()
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"name\":\"test\"}")
            .retrieve()
            .body(String.class);
        
        System.out.println("Result: " + result);
    }
    
    static void example3_WithCallback() {
        RestClient nativeClient = RestClient.create();
        EnhancedRestClient client = EnhancedRestClient.create(nativeClient);
        
        ExtensionRegistry registry = client.getExtensionRegistry();
        registry.register(new CallbackExtension("192.168.1.100", 8080));
        
        try {
            Object callbackResult = client.post()
                .uri("https://third-party.com/async-api")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"action\":\"process\"}")
                .callback(30000)
                .retrieve()
                .body(Object.class);
            
            System.out.println("Callback result: " + callbackResult);
        } catch (CallbackTimeoutException e) {
            System.err.println("Callback timeout: " + e.getMessage());
        }
    }
    
    static void example4_CustomExtension() {
        RestClient nativeClient = RestClient.create();
        EnhancedRestClient client = EnhancedRestClient.create(nativeClient);
        
        RestClientExtension loggingExtension = new RestClientExtension() {
            @Override
            public String name() {
                return "logging";
            }
            
            @Override
            public int order() {
                return 50;
            }
            
            @Override
            public void apply(ExtensionContext context) {
                System.out.println("[LOG] " + context.getMethod() + " " + context.getUrl());
            }
        };
        
        RestClientExtension signingExtension = new RestClientExtension() {
            @Override
            public String name() {
                return "signing";
            }
            
            @Override
            public int order() {
                return 150;
            }
            
            @Override
            public void apply(ExtensionContext context) {
                String url = context.getUrl();
                String sign = "signature-" + url.hashCode();
                context.addHeader("X-Signature", sign);
            }
        };
        
        ExtensionRegistry registry = client.getExtensionRegistry();
        registry.register(loggingExtension);
        registry.register(signingExtension);
        
        String result = client.get()
            .uri("https://api.example.com/data")
            .enable("logging", "signing")
            .retrieve()
            .body(String.class);
        
        System.out.println("Result: " + result);
    }
    
    static void example5_MixedUsage() {
        RestClient nativeClient = RestClient.create();
        EnhancedRestClient client = EnhancedRestClient.create(nativeClient);
        
        Function<String, String> dynamicTokenProvider = url -> {
            if (url.contains("/admin/")) {
                return "admin-token";
            } else if (url.contains("/user/")) {
                return "user-token";
            }
            return "default-token";
        };
        
        ExtensionRegistry registry = client.getExtensionRegistry();
        registry.register(new DefaultTokenExtension(dynamicTokenProvider));
        registry.register(new CallbackExtension());
        
        String result = client.post()
            .uri("https://api.example.com/user/process")
            .defaultToken()
            .callback(60000)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("userId", 123, "action", "sync"))
            .retrieve()
            .body(String.class);
        
        System.out.println("Result: " + result);
    }
}