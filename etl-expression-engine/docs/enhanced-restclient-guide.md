# Enhanced RestClient 装饰器扩展

## 架构说明

```
┌─────────────────────────────────────────────────────────────┐
│                    EnhancedRestClient                        │
│  (包装器 - 100%保持原生RestClient方法签名)                    │
├─────────────────────────────────────────────────────────────┤
│  get() / post() / put() / delete() / patch() / ...         │
│  → EnhancedRequestSpec                                      │
├─────────────────────────────────────────────────────────────┤
│  ExtensionRegistry (全局单例)                               │
│  ├── TracingExtension (order: 5)                           │
│  ├── LoggingExtension (order: 10)                          │
│  ├── DefaultTokenExtension (order: 100)                    │
│  ├── CallbackExtension (order: 200)                        │
│  ├── RetryExtension (order: 300)                           │
│  └── SigningExtension (order: 400)                         │
├─────────────────────────────────────────────────────────────┤
│  RestClientExtension (接口)                                 │
│  - name(): 扩展名称                                         │
│  - order(): 执行顺序                                        │
│  - apply(ExtensionContext): 应用扩展                        │
└─────────────────────────────────────────────────────────────┘
```

## 使用示例

### 1. 基础使用

```java
EnhancedRestClient client = EnhancedRestClient.create(RestClient.create());

String result = client.get()
    .uri("https://api.example.com/users")
    .retrieve()
    .body(String.class);
```

### 2. 默认Token注入

```java
ExtensionRegistry registry = client.getExtensionRegistry();
registry.register(DefaultTokenExtension.withBearerToken("your-token"));

String result = client.post()
    .uri("https://api.example.com/data")
    .defaultToken()
    .contentType(MediaType.APPLICATION_JSON)
    .body("{\"name\":\"test\"}")
    .retrieve()
    .body(String.class);
```

### 3. 异步回调等待

```java
registry.register(new CallbackExtension("192.168.1.100", 8080));

Object result = client.post()
    .uri("https://third-party.com/async-api")
    .callback(30000)
    .contentType(MediaType.APPLICATION_JSON)
    .body("{\"action\":\"process\"}")
    .retrieve()
    .body(Object.class);
```

### 4. 启用多个扩展

```java
String result = client.post()
    .uri("https://api.example.com/process")
    .enable("tracing", "logging", "signing")
    .defaultToken()
    .contentType(MediaType.APPLICATION_JSON)
    .body(data)
    .retrieve()
    .body(String.class);
```

### 5. 自定义扩展

```java
RestClientExtension customExtension = new RestClientExtension() {
    @Override
    public String name() { return "custom"; }
    
    @Override
    public int order() { return 500; }
    
    @Override
    public void apply(ExtensionContext context) {
        context.addHeader("X-Custom", "value");
    }
};

registry.register(customExtension);
```

## 扩展列表

| 扩展名 | 说明 | Order |
|--------|------|-------|
| tracing | 自动注入TraceId/SpanId | 5 |
| logging | 请求日志记录 | 10 |
| defaultToken | 动态Token注入 | 100 |
| callback | 异步回调等待 | 200 |
| retry | 重试机制 | 300 |
| signing | 请求签名 | 400 |

## 配置项

```yaml
enhanced:
  rest-client:
    default-token-enabled: true
    default-token: your-token
    callback-enabled: true
    callback-target-ip: 192.168.1.100
    callback-target-port: 8080
```

## 回调接口

```
POST /api/callback/complete
Content-Type: application/json

{
    "eventId": "xxx",
    "status": "SUCCESS",
    "data": {...}
}
```

## 新增扩展步骤

1. 创建实现 `RestClientExtension` 接口的类
2. 实现 `name()`, `order()`, `apply()` 方法
3. 注册到 `ExtensionRegistry`
4. 使用 `.enable("extensionName")` 或专用方法启用
