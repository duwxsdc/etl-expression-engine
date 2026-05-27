# ExtRestClient 装饰器模式扩展计划 (增强版)

## 一、架构概览

```
com.etl.engine.rest.ext/
├── ExtRestClient.java              # 核心装饰器入口
├── ExtRequestSpec.java             # 链式请求构建器（扩展点）
├── ExtResponseSpec.java            # 响应处理器（扩展点）
├── ExtRequestBodySpec.java         # 请求体构建器
├── callback/
│   └── CallbackRegistry.java       # 回调注册器
├── token/
│   └── TokenProvider.java          # @FunctionalInterface动态Token
├── interceptor/                    # 【新增】拦截器扩展点
│   ├── RequestInterceptor.java     # 请求拦截器接口
│   └── ResponseInterceptor.java    # 响应拦截器接口
├── validator/                      # 【新增】响应校验扩展点
│   └── ResponseValidator.java      # 响应校验器接口
└── support/
    └── ExtAutoConfig.java          # Spring Boot自动配置
```

## 二、扩展点设计

### 2.1 请求阶段扩展点 - ExtRequestSpec

**位置**: 所有请求前的扩展方法都在 `ExtRequestSpec` 中添加

```java
public class ExtRequestSpec {
    
    // ========== 已有核心方法 ==========
    public ExtRequestSpec uri(String uri) { ... }
    public ExtRequestSpec header(String name, String value) { ... }
    public ExtRequestSpec body(Object body) { ... }
    
    // ========== 已有扩展方法 ==========
    public ExtRequestSpec defaultToken() { ... }
    public ExtRequestSpec callback(long timeoutMs) { ... }
    
    // ========== 新增扩展方法示例 ==========
    
    /**
     * 设置应用ID，用于后续Token计算
     * @param appId 应用标识
     */
    public ExtRequestSpec appId(String appId) {
        this.attributes.put("appId", appId);
        return this;
    }
    
    /**
     * 根据appId动态获取Token
     * 需先调用 appId() 设置应用标识
     */
    public ExtRequestSpec tokenByAppId() {
        String appId = this.attributes.get("appId");
        // 根据 appId 从缓存/配置获取对应 token
        String token = TokenCache.getToken(appId);
        this.headers.put("Authorization", "Bearer " + token);
        return this;
    }
    
    /**
     * 添加请求拦截器
     */
    public ExtRequestSpec intercept(RequestInterceptor interceptor) {
        this.requestInterceptors.add(interceptor);
        return this;
    }
    
    /**
     * 重试机制
     */
    public ExtRequestSpec retry(int maxRetries, long delayMs) {
        this.attributes.put("maxRetries", maxRetries);
        this.attributes.put("retryDelayMs", delayMs);
        return this;
    }
    
    /**
     * 请求签名
     */
    public ExtRequestSpec sign(String secretKey) {
        this.attributes.put("signSecretKey", secretKey);
        return this;
    }
    
    /**
     * 链路追踪
     */
    public ExtRequestSpec trace() {
        this.headers.put("X-Trace-Id", UUID.randomUUID().toString());
        return this;
    }
}
```

### 2.2 响应阶段扩展点 - ExtResponseSpec

**位置**: 所有响应后的扩展方法都在 `ExtResponseSpec` 中添加

```java
public class ExtResponseSpec {
    
    // ========== 已有核心方法 ==========
    public <T> T body(Class<T> type) { ... }
    
    // ========== 新增扩展方法示例 ==========
    
    /**
     * 校验响应中是否包含指定key
     * @param key 期望存在的key
     */
    public ExtResponseSpec assertContainsKey(String key) {
        Object result = delegate.body(Object.class);
        if (result instanceof Map map) {
            if (!map.containsKey(key)) {
                throw new ResponseValidationException("响应缺少必要字段: " + key);
            }
        }
        return this;
    }
    
    /**
     * 校验响应中指定key的值
     * @param key 字段名
     * @param expectedValue 期望值
     */
    public ExtResponseSpec assertKeyValue(String key, Object expectedValue) {
        Object result = delegate.body(Object.class);
        if (result instanceof Map map) {
            Object actual = map.get(key);
            if (!Objects.equals(actual, expectedValue)) {
                throw new ResponseValidationException(
                    "字段值不匹配: " + key + ", 期望=" + expectedValue + ", 实际=" + actual);
            }
        }
        return this;
    }
    
    /**
     * 校验响应状态码
     */
    public ExtResponseSpec assertStatus(int expectedStatus) {
        // 实现状态码校验
        return this;
    }
    
    /**
     * 添加响应拦截器
     */
    public ExtResponseSpec intercept(ResponseInterceptor interceptor) {
        interceptor.intercept(lastResponse);
        return this;
    }
    
    /**
     * 获取原始响应后继续链式处理
     */
    public ExtResponseSpec peek(Consumer<Object> consumer) {
        consumer.accept(lastResponse);
        return this;
    }
}
```

### 2.3 拦截器接口设计

```java
// com.etl.engine.rest.ext.interceptor.RequestInterceptor
@FunctionalInterface
public interface RequestInterceptor {
    /**
     * 拦截请求，可修改headers/body等
     * @param context 请求上下文
     */
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

// com.etl.engine.rest.ext.interceptor.ResponseInterceptor
@FunctionalInterface
public interface ResponseInterceptor {
    /**
     * 拦截响应，可用于日志、校验、转换等
     * @param response 响应对象
     */
    void intercept(Object response);
}
```

### 2.4 响应校验器接口设计

```java
// com.etl.engine.rest.ext.validator.ResponseValidator
@FunctionalInterface
public interface ResponseValidator {
    /**
     * 校验响应
     * @param response 响应对象
     * @throws ResponseValidationException 校验失败时抛出
     */
    void validate(Object response);
}
```

## 三、扩展方法分类

| 扩展点位置 | 方法类型 | 示例方法 | 说明 |
|-----------|---------|---------|------|
| ExtRequestSpec | 请求预处理 | `appId()`, `defaultToken()`, `tokenByAppId()` | 设置请求属性、注入Header |
| ExtRequestSpec | 请求增强 | `retry()`, `sign()`, `trace()`, `intercept()` | 重试、签名、追踪、拦截 |
| ExtRequestSpec | 回调机制 | `callback()`, `callback(timeoutMs)` | 异步回调等待 |
| ExtResponseSpec | 响应校验 | `assertContainsKey()`, `assertKeyValue()`, `assertStatus()` | 字段存在性、值匹配、状态码 |
| ExtResponseSpec | 响应处理 | `peek()`, `intercept()` | 日志、转换、后处理 |

## 四、使用示例

### 4.1 响应校验示例

```java
// 校验响应包含必要字段
Map<String, Object> result = extClient.get()
    .uri("http://api.example.com/user/123")
    .defaultToken()
    .retrieve()
    .assertContainsKey("id")
    .assertContainsKey("name")
    .assertKeyValue("status", "active")
    .body(Map.class);
```

### 4.2 appId + token 链式逻辑

```java
// 先设置appId，再根据appId获取token
String result = extClient.post()
    .uri("http://api.example.com/order")
    .appId("order-service")        // 设置应用ID
    .tokenByAppId()                 // 根据appId动态获取Token
    .contentType(MediaType.APPLICATION_JSON)
    .body(orderData)
    .retrieve()
    .assertContainsKey("orderId")
    .body(String.class);
```

### 4.3 多扩展组合

```java
// 重试 + 签名 + 追踪 + 校验
OrderResult result = extClient.post()
    .uri("http://api.example.com/order")
    .appId("order-service")
    .tokenByAppId()
    .retry(3, 1000)                 // 失败重试3次，间隔1秒
    .sign("my-secret-key")          // 请求签名
    .trace()                        // 链路追踪
    .contentType(MediaType.APPLICATION_JSON)
    .body(orderData)
    .retrieve()
    .assertStatus(200)              // 校验状态码
    .assertContainsKey("orderId")   // 校验字段
    .body(OrderResult.class);
```

### 4.4 拦截器使用

```java
// 自定义请求拦截器
RequestInterceptor authInterceptor = ctx -> {
    ctx.getHeaders().put("X-Request-Id", UUID.randomUUID().toString());
    ctx.getHeaders().put("X-Client-Version", "1.0.0");
};

// 自定义响应拦截器（日志）
ResponseInterceptor logInterceptor = resp -> {
    log.info("响应结果: {}", resp);
};

extClient.post()
    .uri("http://api.example.com/data")
    .intercept(authInterceptor)
    .body(data)
    .retrieve()
    .intercept(logInterceptor)
    .body(String.class);
```

## 五、实现步骤

### Step 1: 创建拦截器接口
- `RequestInterceptor.java` - 请求拦截器
- `ResponseInterceptor.java` - 响应拦截器

### Step 2: 创建校验器接口
- `ResponseValidator.java` - 响应校验器
- `ResponseValidationException.java` - 校验异常

### Step 3: 扩展 ExtRequestSpec
- 添加 `appId()`, `tokenByAppId()`, `retry()`, `sign()`, `trace()`, `intercept()` 方法
- 在 `applyExtensions()` 中统一执行扩展逻辑

### Step 4: 扩展 ExtResponseSpec
- 添加 `assertContainsKey()`, `assertKeyValue()`, `assertStatus()`, `peek()`, `intercept()` 方法
- 支持链式校验

### Step 5: 更新 ExtAutoConfig
- 注册默认拦截器（如有）

## 六、类图关系

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
    │                                               ├── assertKeyValue()     [校验扩展]
    │                                               └── peek()/intercept()   [后处理扩展]
    │
    └── unwrap() → RestClient (获取原生实例)
```

## 七、零侵入保证

1. **新增方法只在 ExtRequestSpec/ExtResponseSpec 中添加**
2. **不修改 ExtRestClient 核心逻辑**
3. **拦截器/校验器通过接口定义，独立实现**
4. **所有扩展通过 attributes Map 传递参数，不新增字段**

## 八、文件清单 (共11个)

| 文件 | 职责 | 状态 |
|------|------|------|
| TokenProvider.java | Token计算接口 | 新建 |
| CallbackRegistry.java | 回调注册器 | 新建 |
| ExtRestClient.java | 装饰器入口 | 新建 |
| ExtRequestSpec.java | 请求构建器+扩展点 | 新建 |
| ExtRequestBodySpec.java | 请求体构建器 | 新建 |
| ExtResponseSpec.java | 响应处理器+扩展点 | 新建 |
| RequestInterceptor.java | 请求拦截器接口 | 新建 |
| ResponseInterceptor.java | 响应拦截器接口 | 新建 |
| ResponseValidator.java | 响应校验器接口 | 新建 |
| ResponseValidationException.java | 校验异常 | 新建 |
| ExtAutoConfig.java | 自动配置 | 新建 |
