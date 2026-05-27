# ExtRestClient 扩展框架 API 文档

## 一、概述

ExtRestClient 是基于装饰器模式的 Spring RestClient 扩展框架，提供：
- 100% 保持原生 RestClient API 兼容
- 动态 Token 注入
- 异步回调等待
- 请求/响应拦截器
- 响应校验
- 可无限扩展

## 二、快速开始

### 2.1 注入使用

```java
@Autowired
private ExtRestClient extClient;
```

### 2.2 基础请求

```java
String result = extClient.get()
    .uri("http://api.example.com/data")
    .header("Accept", "application/json")
    .retrieve()
    .body(String.class);
```

## 三、扩展方法

### 3.1 动态 Token

```java
// 方式1: 默认Token
String result = extClient.post()
    .uri("http://api.example.com/data")
    .defaultToken()
    .body(data)
    .retrieve()
    .body(String.class);

// 方式2: appId + tokenByAppId 链式逻辑
String result = extClient.post()
    .uri("http://api.example.com/order")
    .appId("order-service")
    .tokenByAppId()
    .body(orderData)
    .retrieve()
    .body(String.class);
```

### 3.2 异步回调等待

```java
Object result = extClient.post()
    .uri("http://third-party.com/async-api")
    .callback(30000)  // 30秒超时
    .body(requestData)
    .retrieve()
    .body(Object.class);
```

### 3.3 增强扩展

```java
// 重试机制
extClient.post()
    .uri("http://api.example.com/data")
    .retry(3, 1000)  // 最多重试3次，间隔1秒
    .body(data)
    .retrieve();

// 请求签名
extClient.post()
    .uri("http://api.example.com/data")
    .sign("my-secret-key")
    .body(data)
    .retrieve();

// 链路追踪
extClient.post()
    .uri("http://api.example.com/data")
    .trace()  // 自动注入 X-Trace-Id, X-Span-Id
    .body(data)
    .retrieve();
```

### 3.4 请求拦截器

```java
RequestInterceptor interceptor = ctx -> {
    ctx.getHeaders().put("X-Request-Id", UUID.randomUUID().toString());
};

extClient.post()
    .uri("http://api.example.com/data")
    .intercept(interceptor)
    .body(data)
    .retrieve();
```

### 3.5 响应校验

```java
Map<String, Object> result = extClient.get()
    .uri("http://api.example.com/user/123")
    .defaultToken()
    .retrieve()
    .assertContainsKey("id")
    .assertContainsKey("name")
    .assertKeyValue("status", "active")
    .body(Map.class);
```

### 3.6 响应拦截器

```java
ResponseInterceptor logInterceptor = resp -> {
    log.info("响应结果: {}", resp);
};

extClient.post()
    .uri("http://api.example.com/data")
    .body(data)
    .retrieve()
    .intercept(logInterceptor)
    .body(String.class);
```

## 四、完整链式调用示例

```java
OrderResult result = extClient.post()
    .uri("http://api.example.com/order")
    .appId("order-service")          // 设置AppId
    .tokenByAppId()                   // 根据AppId获取Token
    .retry(3, 1000)                   // 重试3次
    .sign("my-secret-key")            // 请求签名
    .trace()                          // 链路追踪
    .contentType(MediaType.APPLICATION_JSON)
    .body(orderData)
    .retrieve()
    .assertStatus(200)                // 校验状态码
    .assertContainsKey("orderId")     // 校验字段
    .assertKeyValue("status", "SUCCESS")
    .peek(resp -> log.info("响应: {}", resp))  // 查看响应
    .body(OrderResult.class);
```

## 五、API 参考

### ExtRestClient 方法

| 方法 | 说明 |
|------|------|
| `get()` | GET 请求 |
| `post()` | POST 请求 |
| `put()` | PUT 请求 |
| `delete()` | DELETE 请求 |
| `patch()` | PATCH 请求 |
| `method(HttpMethod)` | 自定义方法 |
| `unwrap()` | 获取原生 RestClient |

### ExtRequestSpec 扩展方法

| 方法 | 说明 |
|------|------|
| `defaultToken()` | 启用动态Token注入 |
| `appId(String)` | 设置应用ID |
| `tokenByAppId()` | 根据AppId获取Token |
| `callback()` | 启用异步回调等待 |
| `callback(long)` | 启用异步回调等待（自定义超时） |
| `retry(int, long)` | 重试机制 |
| `sign(String)` | 请求签名 |
| `trace()` | 链路追踪 |
| `intercept(RequestInterceptor)` | 添加请求拦截器 |

### ExtResponseSpec 扩展方法

| 方法 | 说明 |
|------|------|
| `assertContainsKey(String)` | 校验字段存在 |
| `assertKeyValue(String, Object)` | 校验字段值 |
| `assertStatus(int)` | 校验状态码 |
| `peek(Consumer)` | 查看响应内容 |
| `intercept(ResponseInterceptor)` | 添加响应拦截器 |

## 六、接口定义

### TokenProvider

```java
@FunctionalInterface
public interface TokenProvider {
    String compute(String url, String method, Map<String, String> headers, Object body);
    
    static TokenProvider bearer(String token) { ... }
}
```

### RequestInterceptor

```java
@FunctionalInterface
public interface RequestInterceptor {
    void intercept(RequestContext context);
    
    @Data
    class RequestContext {
        private String url;
        private String method;
        private Map<String, String> headers;
        private Object body;
        private Map<String, Object> attributes;
    }
}
```

### ResponseInterceptor

```java
@FunctionalInterface
public interface ResponseInterceptor {
    void intercept(Object response);
}
```

## 七、扩展开发

新增功能只需在 `ExtRequestSpec` 或 `ExtResponseSpec` 中添加方法：

```java
// 在 ExtRequestSpec 中添加新扩展
public ExtRequestSpec myExtension(String param) {
    this.attributes.put("myExtension", param);
    return this;
}

// 在 applyExtensions() 中执行扩展逻辑
private void applyExtensions() {
    // ... 已有扩展
    
    // 自定义扩展
    String myParam = (String) attributes.get("myExtension");
    if (myParam != null) {
        // 扩展逻辑
    }
}
```

## 八、配置项

```yaml
# application.yml
# ExtRestClient 使用 Spring Boot 自动配置
# 依赖 LocalEventManager 和 LocalNodeInfo
```

## 九、架构图

```
ExtRestClient (装饰器入口)
    │
    ├── get()/post()/put()/delete()...
    │       │
    │       └──→ ExtRequestSpec (请求构建器 + 扩展点)
    │                   │
    │                   ├── uri()/header()/body()     [原生方法]
    │                   ├── appId()/tokenByAppId()   [Token扩展]
    │                   ├── retry()/sign()/trace()   [增强扩展]
    │                   ├── callback()               [回调扩展]
    │                   └── intercept()              [拦截器扩展]
    │                           │
    │                           └──→ retrieve()
    │                                   │
    │                                   └──→ ExtResponseSpec (响应处理器 + 扩展点)
    │                                               │
    │                                               ├── body()/toEntity()    [原生方法]
    │                                               ├── assertContainsKey()  [校验扩展]
    │                                               └── peek()/intercept()   [后处理扩展]
    │
    └── unwrap() → RestClient (获取原生实例)
```
