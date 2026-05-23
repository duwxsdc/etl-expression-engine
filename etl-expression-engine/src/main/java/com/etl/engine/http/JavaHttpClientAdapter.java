package com.etl.engine.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class JavaHttpClientAdapter implements HttpClientAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(JavaHttpClientAdapter.class);
    
    private final HttpClient httpClient;
    private final ExecutorService executorService;
    
    public JavaHttpClientAdapter() {
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.httpClient = HttpClient.newBuilder()
                .executor(executorService)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_2)
                .build();
        logger.info("Java HttpClient适配器初始化完成, HTTP/2, 虚拟线程执行器");
    }
    
    public JavaHttpClientAdapter(int maxConnections) {
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.httpClient = HttpClient.newBuilder()
                .executor(executorService)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_2)
                .build();
        logger.info("Java HttpClient适配器初始化完成, HTTP/2, 最大连接数: {}", maxConnections);
    }
    
    @Override
    public HttpResponse execute(String method, String url, Map<String, String> headers, byte[] body, int timeoutMillis) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url));
            
            if (timeoutMillis > 0) {
                requestBuilder.timeout(Duration.ofMillis(timeoutMillis));
            }
            
            if (headers != null) {
                headers.forEach(requestBuilder::header);
            }
            
            HttpRequest.BodyPublisher bodyPublisher = body != null && body.length > 0
                    ? HttpRequest.BodyPublishers.ofByteArray(body)
                    : HttpRequest.BodyPublishers.noBody();
            
            switch (method.toUpperCase()) {
                case "GET" -> requestBuilder.GET();
                case "POST" -> requestBuilder.POST(bodyPublisher);
                case "PUT" -> requestBuilder.PUT(bodyPublisher);
                case "DELETE" -> requestBuilder.DELETE();
                case "PATCH" -> requestBuilder.method("PATCH", bodyPublisher);
                default -> requestBuilder.method(method, bodyPublisher);
            }
            
            HttpRequest httpRequest = requestBuilder.build();
            java.net.http.HttpResponse<byte[]> rawResponse = httpClient.send(
                    httpRequest, java.net.http.HttpResponse.BodyHandlers.ofByteArray()
            );
            
            Map<String, String> responseHeaders = new LinkedHashMap<>();
            rawResponse.headers().map().forEach((key, values) -> {
                if (!values.isEmpty()) {
                    responseHeaders.put(key, values.getFirst());
                }
            });
            
            String contentType = responseHeaders.getOrDefault("content-type", "");
            
            return new HttpResponseImpl(
                    rawResponse.statusCode(),
                    "",
                    responseHeaders,
                    rawResponse.body(),
                    contentType
            );
            
        } catch (java.net.http.HttpTimeoutException e) {
            throw new HttpTimeoutException("请求超时: " + url + ", 超时: " + timeoutMillis + "ms", timeoutMillis, e);
        } catch (java.net.ConnectException e) {
            throw new HttpException("连接失败: " + url, -1, url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpException("请求被中断: " + url, -1, url, e);
        } catch (Exception e) {
            throw new HttpException("HTTP请求失败: " + url + ", 错误: " + e.getMessage(), -1, url, e);
        }
    }
    
    @Override
    public void shutdown() {
        executorService.shutdown();
        logger.info("Java HttpClient适配器已关闭");
    }
    
    @Override
    public String getName() {
        return "Java HttpClient (JDK 21, HTTP/2)";
    }
}
