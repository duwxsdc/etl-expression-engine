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

/**
 * 基于Java标准库HttpClient的HTTP客户端适配器实现。
 * <p>
 * 该类实现了{@link HttpClientAdapter}接口，使用JDK 21内置的HttpClient进行HTTP请求。
 * 支持HTTP/2协议，使用虚拟线程执行器实现高并发请求处理，自动跟随重定向。
 * </p>
 *
 * @author ETL Engine
 * @version 1.0
 * @since 1.0
 */
public final class JavaHttpClientAdapter implements HttpClientAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(JavaHttpClientAdapter.class);
    
    /**
     * JDK HttpClient实例
     */
    private final HttpClient httpClient;
    
    /**
     * 虚拟线程执行器
     */
    private final ExecutorService executorService;
    
    /**
     * 默认构造方法，初始化HttpClient适配器。
     * <p>
     * 使用虚拟线程执行器和HTTP/2协议，自动跟随重定向。
     * </p>
     */
    public JavaHttpClientAdapter() {
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.httpClient = HttpClient.newBuilder()
                .executor(executorService)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_2)
                .build();
        logger.info("Java HttpClient适配器初始化完成, HTTP/2, 虚拟线程执行器");
    }
    
    /**
     * 带最大连接数参数的构造方法。
     * <p>
     * 当前实现中最大连接数参数仅用于日志记录，实际并发控制由虚拟线程管理。
     * </p>
     *
     * @param maxConnections 最大连接数
     */
    public JavaHttpClientAdapter(int maxConnections) {
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.httpClient = HttpClient.newBuilder()
                .executor(executorService)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_2)
                .build();
        logger.info("Java HttpClient适配器初始化完成, HTTP/2, 最大连接数: {}", maxConnections);
    }
    
    /**
     * 执行HTTP请求并返回响应结果。
     * <p>
     * 根据请求方法构建对应的HTTP请求，支持GET、POST、PUT、DELETE、PATCH等方法。
     * 自动处理超时设置和请求头配置。
     * </p>
     *
     * @param method HTTP请求方法
     * @param url 请求URL地址
     * @param headers 请求头映射
     * @param body 请求体字节数组
     * @param timeoutMillis 请求超时时间（毫秒）
     * @return HTTP响应对象
     * @throws HttpTimeoutException 当请求超时时抛出
     * @throws HttpException 当连接失败、请求被中断或其他错误时抛出
     */
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
    
    /**
     * 关闭HTTP客户端适配器，释放执行器资源。
     */
    @Override
    public void shutdown() {
        executorService.shutdown();
        logger.info("Java HttpClient适配器已关闭");
    }
    
    /**
     * 获取适配器名称和描述信息。
     *
     * @return 适配器名称描述字符串
     */
    @Override
    public String getName() {
        return "Java HttpClient (JDK 21, HTTP/2)";
    }
}
