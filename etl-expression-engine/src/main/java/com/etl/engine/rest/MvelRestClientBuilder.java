package com.etl.engine.rest;

import com.etl.engine.callback.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class MvelRestClientBuilder {
    
    private static final Logger logger = LoggerFactory.getLogger(MvelRestClientBuilder.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private final RestClient restClient;
    private final LocalNodeInfo localNodeInfo;
    private final LocalEventManager eventManager;
    private final RestClientProperties properties;
    private final List<RequestInterceptor> requestInterceptors;
    private final List<ResponseInterceptor> responseInterceptors;
    
    private final String url;
    private final String method;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final Map<String, Object> queryParams = new LinkedHashMap<>();
    private final Map<String, Object> pathVariables = new LinkedHashMap<>();
    private Object body;
    private MediaType contentType = MediaType.APPLICATION_JSON;
    private Duration timeout;
    
    private boolean callbackBound = false;
    private long callbackTimeoutMs = 30000;
    private String eventId;
    
    private boolean skipAutoToken = false;
    private int retryCount = 0;
    
    MvelRestClientBuilder(String url, String method, RestClient restClient, 
                          LocalNodeInfo localNodeInfo, LocalEventManager eventManager,
                          RestClientProperties properties,
                          List<RequestInterceptor> requestInterceptors,
                          List<ResponseInterceptor> responseInterceptors) {
        this.url = url;
        this.method = method;
        this.restClient = restClient;
        this.localNodeInfo = localNodeInfo;
        this.eventManager = eventManager;
        this.properties = properties;
        this.requestInterceptors = requestInterceptors != null ? requestInterceptors : new ArrayList<>();
        this.responseInterceptors = responseInterceptors != null ? responseInterceptors : new ArrayList<>();
        
        if (properties != null) {
            this.timeout = properties.getDefaultTimeout();
            
            if (properties.getDefaultHeaders() != null) {
                this.headers.putAll(properties.getDefaultHeaders());
            }
            
            RestClientProperties.EndpointConfig endpointConfig = properties.getEndpointConfig(url);
            if (endpointConfig != null) {
                if (endpointConfig.getHeaders() != null) {
                    this.headers.putAll(endpointConfig.getHeaders());
                }
                if (endpointConfig.getTimeout() != null) {
                    this.timeout = endpointConfig.getTimeout();
                }
                if (endpointConfig.getMaxRetries() > 0) {
                    this.retryCount = endpointConfig.getMaxRetries();
                }
            }
        }
    }
    
    public MvelRestClientBuilder header(String name, String value) {
        this.headers.put(name, value);
        return this;
    }
    
    public MvelRestClientBuilder headers(Map<String, String> headers) {
        this.headers.putAll(headers);
        return this;
    }
    
    public MvelRestClientBuilder body(Object body) {
        this.body = body;
        return this;
    }
    
    public MvelRestClientBuilder bodyJson(Object body) {
        this.body = body;
        this.contentType = MediaType.APPLICATION_JSON;
        return this;
    }
    
    public MvelRestClientBuilder bodyForm(Map<String, String> formData) {
        this.body = formData;
        this.contentType = MediaType.APPLICATION_FORM_URLENCODED;
        return this;
    }
    
    public MvelRestClientBuilder bodyText(String text) {
        this.body = text;
        this.contentType = MediaType.TEXT_PLAIN;
        return this;
    }
    
    public MvelRestClientBuilder bodyXml(String xml) {
        this.body = xml;
        this.contentType = MediaType.APPLICATION_XML;
        return this;
    }
    
    public MvelRestClientBuilder queryParam(String name, Object value) {
        this.queryParams.put(name, value);
        return this;
    }
    
    public MvelRestClientBuilder queryParams(Map<String, Object> params) {
        this.queryParams.putAll(params);
        return this;
    }
    
    public MvelRestClientBuilder pathVariable(String name, Object value) {
        this.pathVariables.put(name, value);
        return this;
    }
    
    public MvelRestClientBuilder pathVariables(Map<String, Object> variables) {
        this.pathVariables.putAll(variables);
        return this;
    }
    
    public MvelRestClientBuilder timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }
    
    public MvelRestClientBuilder timeoutMs(long timeoutMs) {
        this.timeout = Duration.ofMillis(timeoutMs);
        return this;
    }
    
    public MvelRestClientBuilder contentType(MediaType contentType) {
        this.contentType = contentType;
        return this;
    }
    
    public MvelRestClientBuilder accept(MediaType... acceptTypes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < acceptTypes.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(acceptTypes[i].toString());
        }
        this.headers.put("Accept", sb.toString());
        return this;
    }
    
    public MvelRestClientBuilder basicAuth(String username, String password) {
        String credentials = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
        this.headers.put("Authorization", "Basic " + credentials);
        return this;
    }
    
    public MvelRestClientBuilder bearerAuth(String token) {
        this.headers.put("Authorization", "Bearer " + token);
        return this;
    }
    
    public MvelRestClientBuilder skipAutoToken() {
        this.skipAutoToken = true;
        return this;
    }
    
    public MvelRestClientBuilder withRetry(int count) {
        this.retryCount = count;
        return this;
    }
    
    public MvelRestClientBuilder withInterceptor(RequestInterceptor interceptor) {
        this.requestInterceptors.add(interceptor);
        return this;
    }
    
    public MvelRestClientBuilder withResponseInterceptor(ResponseInterceptor interceptor) {
        this.responseInterceptors.add(interceptor);
        return this;
    }
    
    public MvelRestClientBuilder bindCallback(long timeoutMs) {
        this.callbackBound = true;
        this.callbackTimeoutMs = timeoutMs;
        this.eventId = eventManager.generateEventId();
        
        headers.put("X-Callback-EventId", eventId);
        headers.put("X-Callback-TargetIp", localNodeInfo.getNodeIp());
        headers.put("X-Callback-TargetPort", String.valueOf(localNodeInfo.getServerPort()));
        
        logger.debug("绑定回调: eventId={}, targetIp={}, targetPort={}, timeout={}ms",
            eventId, localNodeInfo.getNodeIp(), localNodeInfo.getServerPort(), timeoutMs);
        
        return this;
    }
    
    public MvelRestClientBuilder bindCallback(String eventId, long timeoutMs) {
        this.callbackBound = true;
        this.callbackTimeoutMs = timeoutMs;
        this.eventId = eventId;
        
        headers.put("X-Callback-EventId", eventId);
        headers.put("X-Callback-TargetIp", localNodeInfo.getNodeIp());
        headers.put("X-Callback-TargetPort", String.valueOf(localNodeInfo.getServerPort()));
        
        return this;
    }
    
    public AsyncResult execute() {
        String processedUrl = processUrl();
        
        Map<String, String> finalHeaders = new LinkedHashMap<>(headers);
        
        if (properties != null && !skipAutoToken && properties.shouldAddToken(method)) {
            String autoToken = properties.resolveToken();
            if (autoToken != null && !autoToken.isBlank() && !finalHeaders.containsKey(properties.getAutoTokenHeader())) {
                finalHeaders.put(properties.getAutoTokenHeader(), autoToken);
                logger.debug("自动注入Token: header={}", properties.getAutoTokenHeader());
            }
        }
        
        RequestInterceptor.InterceptedRequest interceptedRequest = RequestInterceptor.InterceptedRequest.of(
            processedUrl, method, finalHeaders, body
        );
        
        for (RequestInterceptor interceptor : requestInterceptors) {
            interceptedRequest = interceptor.intercept(interceptedRequest);
        }
        
        final String finalUrl = interceptedRequest.url();
        final Map<String, String> finalHeadersAfterInterceptors = interceptedRequest.headers();
        final Object finalBody = interceptedRequest.body();
        
        if (properties != null && properties.isLogRequestEnabled()) {
            logRequest(finalUrl, method, finalHeadersAfterInterceptors, finalBody);
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            String responseBody = doExecute(finalUrl, method, finalHeadersAfterInterceptors, finalBody);
            long duration = System.currentTimeMillis() - startTime;
            
            if (properties != null && properties.isLogResponseEnabled()) {
                logResponse(finalUrl, method, responseBody, duration);
            }
            
            ResponseInterceptor.InterceptedResponse interceptedResponse = ResponseInterceptor.InterceptedResponse.of(
                200, Map.of(), responseBody, duration
            );
            
            for (ResponseInterceptor interceptor : responseInterceptors) {
                interceptedResponse = interceptor.intercept(interceptedResponse);
            }
            
            String finalResponseBody = interceptedResponse.body();
            
            if (callbackBound) {
                LocalEventManager.PendingEvent event = eventManager.registerEvent(eventId, callbackTimeoutMs);
                return new AsyncResult(eventId, event.future(), finalResponseBody);
            }
            
            return new AsyncResult(null, null, finalResponseBody);
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("HTTP请求失败: {} {} - {} ({}ms)", method, finalUrl, e.getMessage(), duration);
            
            if (retryCount > 0 && shouldRetry(e)) {
                logger.info("尝试重试: 剩余次数={}, delay={}ms", retryCount, 
                    properties != null ? properties.getRetryDelayMs() : 1000);
                retryCount--;
                try {
                    Thread.sleep(properties != null ? properties.getRetryDelayMs() : 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return execute();
            }
            
            if (callbackBound) {
                LocalEventManager.PendingEvent event = eventManager.registerEvent(eventId, callbackTimeoutMs);
                event.future().completeExceptionally(e);
                return new AsyncResult(eventId, event.future(), null);
            }
            throw new RuntimeException("HTTP请求失败: " + e.getMessage(), e);
        }
    }
    
    private String doExecute(String processedUrl, String method, Map<String, String> execHeaders, Object body) {
        try {
            RestClient.RequestBodySpec requestSpec = buildRequest(processedUrl, method);
            
            for (Map.Entry<String, String> h : execHeaders.entrySet()) {
                requestSpec.header(h.getKey(), h.getValue());
            }
            
            if (body != null && needsBody(method)) {
                if (contentType == MediaType.APPLICATION_JSON) {
                    String jsonBody = OBJECT_MAPPER.writeValueAsString(body);
                    return requestSpec
                        .contentType(contentType)
                        .body(jsonBody)
                        .retrieve()
                        .body(String.class);
                } else {
                    return requestSpec
                        .contentType(contentType)
                        .body(body)
                        .retrieve()
                        .body(String.class);
                }
            } else {
                return requestSpec.retrieve().body(String.class);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private boolean needsBody(String method) {
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || 
               "PATCH".equalsIgnoreCase(method);
    }
    
    private boolean shouldRetry(Exception e) {
        String message = e.getMessage();
        return message != null && (
            message.contains("timeout") ||
            message.contains("Timeout") ||
            message.contains("Connection refused") ||
            message.contains("502") ||
            message.contains("503") ||
            message.contains("504")
        );
    }
    
    private void logRequest(String url, String method, Map<String, String> headers, Object body) {
        StringBuilder log = new StringBuilder();
        log.append("HTTP请求: {} {}");
        
        if (properties != null && properties.isLogHeadersEnabled() && !headers.isEmpty()) {
            log.append(", headers={}");
            logger.debug(log.toString(), method, url, headers);
        } else {
            logger.debug(log.toString(), method, url);
        }
        
        if (body != null && properties != null) {
            try {
                String bodyStr = contentType == MediaType.APPLICATION_JSON 
                    ? OBJECT_MAPPER.writeValueAsString(body) 
                    : String.valueOf(body);
                int maxLen = properties.getMaxLogBodyLength();
                if (bodyStr.length() > maxLen) {
                    bodyStr = bodyStr.substring(0, maxLen) + "...";
                }
                logger.debug("请求体: {}", bodyStr);
            } catch (Exception e) {
                logger.debug("请求体: [无法序列化]");
            }
        }
    }
    
    private void logResponse(String url, String method, String response, long duration) {
        StringBuilder log = new StringBuilder();
        log.append("HTTP响应: {} {} -> {}ms");
        
        if (response != null && properties != null) {
            int maxLen = properties.getMaxLogBodyLength();
            String truncatedResponse = response.length() > maxLen 
                ? response.substring(0, maxLen) + "..." 
                : response;
            logger.debug(log.toString(), method, url, duration);
            logger.debug("响应体: {}", truncatedResponse);
        } else {
            logger.debug(log.toString(), method, url, duration);
        }
    }
    
    private String processUrl() {
        String processedUrl = url;
        
        for (Map.Entry<String, Object> entry : pathVariables.entrySet()) {
            processedUrl = processedUrl.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        
        if (!queryParams.isEmpty()) {
            StringBuilder sb = new StringBuilder(processedUrl);
            if (!processedUrl.contains("?")) {
                sb.append("?");
            } else if (!processedUrl.endsWith("?") && !processedUrl.endsWith("&")) {
                sb.append("&");
            }
            
            boolean first = !processedUrl.contains("?");
            for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
                if (!first) {
                    sb.append("&");
                }
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
            processedUrl = sb.toString();
        }
        
        return processedUrl;
    }
    
    private RestClient.RequestBodySpec buildRequest(String processedUrl, String method) {
        RestClient.RequestBodySpec spec;
        
        String methodUpper = method.toUpperCase();
        if ("GET".equals(methodUpper)) {
            spec = (RestClient.RequestBodySpec) restClient.get().uri(processedUrl);
        } else if ("POST".equals(methodUpper)) {
            spec = restClient.post().uri(processedUrl);
        } else if ("PUT".equals(methodUpper)) {
            spec = restClient.put().uri(processedUrl);
        } else if ("DELETE".equals(methodUpper)) {
            spec = (RestClient.RequestBodySpec) restClient.delete().uri(processedUrl);
        } else if ("PATCH".equals(methodUpper)) {
            spec = restClient.patch().uri(processedUrl);
        } else if ("HEAD".equals(methodUpper)) {
            spec = (RestClient.RequestBodySpec) restClient.head().uri(processedUrl);
        } else if ("OPTIONS".equals(methodUpper)) {
            spec = (RestClient.RequestBodySpec) restClient.options().uri(processedUrl);
        } else {
            throw new IllegalArgumentException("不支持的HTTP方法: " + method);
        }
        
        return spec;
    }
    
    public Object waitCallback() {
        AsyncResult result = execute();
        
        if (!callbackBound || result.eventId() == null) {
            return result.initialResponse();
        }
        
        try {
            logger.debug("等待回调: eventId={}", eventId);
            
            Object callbackResult = result.future().get();
            
            logger.debug("回调完成: eventId={}, result={}", eventId, callbackResult);
            return callbackResult;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CallbackTimeoutException("等待回调被中断: eventId=" + eventId, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                throw new CallbackTimeoutException("等待回调超时: eventId=" + eventId + ", timeout=" + callbackTimeoutMs + "ms", cause);
            }
            throw new RuntimeException("等待回调失败: " + cause.getMessage(), cause);
        }
    }
    
    public Object waitCallback(long timeoutMs) {
        AsyncResult result = execute();
        
        if (!callbackBound || result.eventId() == null) {
            return result.initialResponse();
        }
        
        try {
            logger.debug("等待回调: eventId={}, timeout={}ms", eventId, timeoutMs);
            
            Object callbackResult = result.future().get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            
            logger.debug("回调完成: eventId={}, result={}", eventId, callbackResult);
            return callbackResult;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CallbackTimeoutException("等待回调被中断: eventId=" + eventId, e);
        } catch (TimeoutException e) {
            throw new CallbackTimeoutException("等待回调超时: eventId=" + eventId + ", timeout=" + timeoutMs + "ms", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                throw new CallbackTimeoutException("等待回调超时: eventId=" + eventId, cause);
            }
            throw new RuntimeException("等待回调失败: " + cause.getMessage(), cause);
        }
    }
    
    public String getEventId() {
        return eventId;
    }
    
    public String getUrl() {
        return url;
    }
    
    public String getMethod() {
        return method;
    }
    
    public record AsyncResult(
        String eventId,
        CompletableFuture<Object> future,
        String initialResponse
    ) {
        private static final Logger ASYNC_LOGGER = LoggerFactory.getLogger(AsyncResult.class);

        public boolean hasCallback() {
            return eventId != null;
        }
        
        public Map<String, Object> asMap() {
            if (initialResponse == null) return null;
            try {
                return OBJECT_MAPPER.readValue(initialResponse, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                return Map.of("raw", initialResponse);
            }
        }
        
        public String asString() {
            return initialResponse;
        }
        
        public <T> T asObject(Class<T> clazz) {
            if (initialResponse == null) return null;
            try {
                return OBJECT_MAPPER.readValue(initialResponse, clazz);
            } catch (Exception e) {
                throw new RuntimeException("JSON解析失败: " + e.getMessage(), e);
            }
        }

        public Object waitCallback() {
            if (!hasCallback() || future == null) {
                return initialResponse;
            }
            try {
                ASYNC_LOGGER.debug("等待回调: eventId={}", eventId);
                Object callbackResult = future.get();
                ASYNC_LOGGER.debug("回调完成: eventId={}, result={}", eventId, callbackResult);
                return callbackResult;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CallbackTimeoutException("等待回调被中断: eventId=" + eventId, e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof TimeoutException) {
                    throw new CallbackTimeoutException("等待回调超时: eventId=" + eventId, cause);
                }
                throw new RuntimeException("等待回调失败: " + cause.getMessage(), cause);
            }
        }

        public Object waitCallback(long timeoutMs) {
            if (!hasCallback() || future == null) {
                return initialResponse;
            }
            try {
                ASYNC_LOGGER.debug("等待回调: eventId={}, timeout={}ms", eventId, timeoutMs);
                Object callbackResult = future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                ASYNC_LOGGER.debug("回调完成: eventId={}, result={}", eventId, callbackResult);
                return callbackResult;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CallbackTimeoutException("等待回调被中断: eventId=" + eventId, e);
            } catch (TimeoutException e) {
                throw new CallbackTimeoutException("等待回调超时: eventId=" + eventId + ", timeout=" + timeoutMs + "ms", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof TimeoutException) {
                    throw new CallbackTimeoutException("等待回调超时: eventId=" + eventId, cause);
                }
                throw new RuntimeException("等待回调失败: " + cause.getMessage(), cause);
            }
        }
    }
}
