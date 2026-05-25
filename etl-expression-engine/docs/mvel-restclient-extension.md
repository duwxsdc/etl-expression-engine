# MvelRestClient 扩展功能文档

## 概述

MvelRestClient 是基于 Spring 官方 RestClient 的增强封装，提供以下扩展功能：

- 自动 Token 注入
- 请求/响应拦截器机制
- 端点级别配置
- 重试机制
- 多种 Content-Type 支持
- 完善的日志记录

---

## 配置说明

### 完整配置项

```yaml
etl:
  engine:
    rest-client:
      # 自动Token注入配置
      auto-token-enabled: false
      auto-token-header: Authorization
      auto-token-prefix: "Bearer "
      auto-token-value: ""
      auto-token-from-env: false
      auto-token-env-name: ETL_API_TOKEN
      
      # 超时配置
      default-timeout: 30s
      connect-timeout: 10s
      read-timeout: 60s
      
      # 重试配置
      max-retries: 0
      retry-delay-ms: 1000
      retry-backoff-factor: 2.0
      
      # 日志配置
      log-request-enabled: true
      log-response-enabled: true
      log-headers-enabled: false
      max-log-body-length: 1000
      
      # 默认请求头
      default-headers:
        X-App-Name: ETL-Engine
        X-App-Version: 1.0.0
      
      # 需要自动注入Token的HTTP方法
      methods-require-token:
        - GET
        - POST
        - PUT
        - DELETE
        - PATCH
```

---

## 自动 Token 注入

### 配置方式

#### 方式一：配置文件固定值

```yaml
etl:
  engine:
    rest-client:
      auto-token-enabled: true
      auto-token-value: "your-static-token"
```

#### 方式二：环境变量

```yaml
etl:
  engine:
    rest-client:
      auto-token-enabled: true
      auto-token-from-env: true
      auto-token-env-name: MY_API_TOKEN
```

```bash
export MY_API_TOKEN=your-dynamic-token
```

#### 方式三：自定义 TokenProvider（推荐）

```java
MvelRestClient.setTokenProvider(envName -> {
    // 从数据库、缓存或其他来源获取Token
    return tokenService.getLatestToken();
});
```

### Token 来源优先级

1. 自定义 TokenProvider
2. 环境变量
3. 配置文件固定值

### 跳过自动注入

```java
var result = RestClient.get("https://example.com/api")
    .skipAutoToken()  // 跳过自动Token注入
    .execute()
    .asString();
```

---

## 拦截器机制

### 请求拦截器

```java
// 添加全局请求拦截器
MvelRestClient.addGlobalRequestInterceptor(request -> {
    // 添加自定义Header
    return request.withHeader("X-Custom-Header", "value")
                  .withHeader("X-Timestamp", String.valueOf(System.currentTimeMillis()));
});

// 单次请求使用拦截器
var result = RestClient.post("https://example.com/api")
    .withInterceptor(request -> request.withHeader("X-Request-Id", UUID.randomUUID().toString()))
    .bodyJson(data)
    .execute()
    .asString();
```

### 响应拦截器

```java
// 添加全局响应拦截器
MvelRestClient.addGlobalResponseInterceptor(response -> {
    log.info("响应状态: {}, 耗时: {}ms", response.statusCode(), response.durationMs());
    return response;
});

// 响应处理示例
MvelRestClient.addGlobalResponseInterceptor(response -> {
    if (!response.isSuccess()) {
        log.error("请求失败: status={}, body={}", response.statusCode(), response.body());
    }
    return response;
});
```

### 拦截器管理

```java
// 查看当前拦截器
List<RequestInterceptor> requestInterceptors = MvelRestClient.getGlobalRequestInterceptors();
List<ResponseInterceptor> responseInterceptors = MvelRestClient.getGlobalResponseInterceptors();

// 移除拦截器
MvelRestClient.removeGlobalRequestInterceptor(interceptor);

// 清空所有拦截器
MvelRestClient.clearGlobalInterceptors();
```

---

## 端点级别配置

为不同的 API 端点配置不同的行为：

```yaml
etl:
  engine:
    rest-client:
      endpoints:
        "api.service-a.com":
          timeout: 60s
          headers:
            X-Api-Key: service-a-key
          max-retries: 3
        "api.service-b.com":
          timeout: 30s
          token: "service-b-token"
```

---

## 认证方式

### Bearer Token

```java
var result = RestClient.get("https://api.example.com/users")
    .bearerAuth("your-token")  // 自动添加 Authorization: Bearer your-token
    .execute()
    .asString();
```

### Basic Auth

```java
var result = RestClient.get("https://api.example.com/users")
    .basicAuth("username", "password")  // 自动Base64编码
    .execute()
    .asString();
```

### 自定义认证头

```java
var result = RestClient.get("https://api.example.com/users")
    .header("X-API-Key", "your-api-key")
    .execute()
    .asString();
```

---

## 重试机制

### 配置重试

```yaml
etl:
  engine:
    rest-client:
      max-retries: 3
      retry-delay-ms: 1000
      retry-backoff-factor: 2.0
```

### 单次请求配置

```java
var result = RestClient.post("https://api.example.com/data")
    .bodyJson(data)
    .withRetry(3)  // 失败后最多重试3次
    .execute()
    .asString();
```

### 重试条件

以下情况会触发重试：
- 连接超时
- 502 Bad Gateway
- 503 Service Unavailable
- 504 Gateway Timeout
- Connection refused

---

## Content-Type 支持

### JSON (默认)

```java
var result = RestClient.post(url)
    .bodyJson(Map.of("key", "value"))
    .execute()
    .asString();
```

### 表单

```java
var result = RestClient.post(url)
    .bodyForm(Map.of("username", "admin", "password", "123456"))
    .execute()
    .asString();
```

### 纯文本

```java
var result = RestClient.post(url)
    .bodyText("plain text content")
    .execute()
    .asString();
```

### XML

```java
var result = RestClient.post(url)
    .bodyXml("<root><item>value</item></root>")
    .execute()
    .asString();
```

### 设置 Accept 头

```java
var result = RestClient.get(url)
    .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML)
    .execute()
    .asString();
```

---

## 日志配置

```yaml
etl:
  engine:
    rest-client:
      log-request-enabled: true    # 记录请求日志
      log-response-enabled: true   # 记录响应日志
      log-headers-enabled: true    # 记录请求头
      max-log-body-length: 1000    # 日志最大Body长度
```

---

## 完整示例

### 异步请求 + 自动Token + 拦截器

```java
// 1. 配置自动Token
MvelRestClient.setTokenProvider(envName -> tokenService.getLatestToken());

// 2. 添加全局拦截器
MvelRestClient.addGlobalRequestInterceptor(request -> {
    return request.withHeader("X-Trace-Id", traceIdGenerator.generate());
});

MvelRestClient.addGlobalResponseInterceptor(response -> {
    metricsService.record(response.durationMs());
    return response;
});

// 3. 发起请求
var result = RestClient.post("https://third-party.com/api/process")
    .header("X-Request-Id", "req-001")
    .bodyJson(Map.of(
        "action", "process",
        "data", largeDataSet
    ))
    .bindCallback(60000)  // 绑定回调，60秒超时
    .execute()
    .waitCallback();      // 阻塞等待回调

// 4. 处理结果
if (result instanceof Map resultMap && "SUCCESS".equals(resultMap.get("status"))) {
    return resultMap.get("data");
} else {
    throw new RuntimeException("处理失败");
}
```

---

## API 速查表

| 方法 | 说明 |
|------|------|
| `RestClient.get(url)` | GET 请求 |
| `RestClient.post(url)` | POST 请求 |
| `RestClient.put(url)` | PUT 请求 |
| `RestClient.delete(url)` | DELETE 请求 |
| `RestClient.patch(url)` | PATCH 请求 |
| `.header(name, value)` | 添加请求头 |
| `.headers(map)` | 批量添加请求头 |
| `.bodyJson(object)` | JSON 请求体 |
| `.bodyForm(map)` | 表单请求体 |
| `.bodyText(text)` | 纯文本请求体 |
| `.bodyXml(xml)` | XML 请求体 |
| `.queryParam(name, value)` | 查询参数 |
| `.pathVariable(name, value)` | 路径变量 |
| `.timeout(duration)` | 设置超时 |
| `.bearerAuth(token)` | Bearer 认证 |
| `.basicAuth(user, pass)` | Basic 认证 |
| `.skipAutoToken()` | 跳过自动Token |
| `.withRetry(count)` | 设置重试次数 |
| `.withInterceptor(i)` | 添加请求拦截器 |
| `.withResponseInterceptor(i)` | 添加响应拦截器 |
| `.bindCallback(timeout)` | 绑定异步回调 |
| `.execute()` | 执行请求 |
| `.waitCallback()` | 等待回调 |

---

## 注意事项

1. **Token 优先级**：自定义 Provider > 环境变量 > 配置文件
2. **拦截器执行顺序**：按添加顺序依次执行
3. **重试延迟**：支持指数退避，delay = baseDelay * factor^retryCount
4. **线程安全**：所有组件都是线程安全的
5. **内存管理**：全局拦截器会一直保留，注意及时清理
