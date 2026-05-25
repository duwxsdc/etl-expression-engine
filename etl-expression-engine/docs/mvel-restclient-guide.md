# MVEL RestClient 异步回调使用指南

## 概述

本模块实现了基于 Spring 官方 RestClient 的分布式异步回调机制，支持：
- MVEL 表达式中发起异步 HTTP 请求
- 自动携带本机 IP、端口、eventId
- 第三方回调到集群任意节点
- 集群内 HTTP 二次转发到原始发起机器
- 事件唤醒继续执行

## 核心组件

| 组件 | 说明 |
|------|------|
| `MvelRestClient` | RestClient 包装类，MVEL 表达式入口 |
| `MvelRestClientBuilder` | 构建器模式，支持链式调用 |
| `LocalNodeInfo` | 本地节点信息（IP、端口） |
| `LocalEventManager` | 本机事件管理（等待/唤醒） |
| `PublicCallbackController` | 公共回调接口 `/api/public/callback` |
| `InternalCallbackController` | 内部回调接口 `/api/internal/callback` |

## MVEL 表达式示例

### 1. 基础 GET 请求

```java
var result = RestClient.get("https://api.example.com/users")
    .queryParam("page", 1)
    .queryParam("size", 10)
    .execute()
    .asString();
```

### 2. POST 请求带 JSON Body

```java
var body = {
    "name": "张三",
    "age": 25,
    "email": "zhangsan@example.com"
};

var result = RestClient.post("https://api.example.com/users")
    .header("Authorization", "Bearer token123")
    .header("X-Request-Id", "req-001")
    .bodyJson(body)
    .execute()
    .asString();
```

### 3. 异步请求 + 回调等待（核心功能）

```java
var body = {
    "action": "process",
    "data": "待处理数据"
};

var result = RestClient.post("https://third-party.com/async-api")
    .header("token", "xxx")
    .bodyJson(body)
    .bindCallback(30000)  // 自动注入本机IP、端口、eventId，超时30秒
    .execute()
    .waitCallback();      // 阻塞等待回调唤醒，返回回调结果
```

### 4. 带路径参数的请求

```java
var result = RestClient.get("https://api.example.com/users/{userId}/orders/{orderId}")
    .pathVariable("userId", 123)
    .pathVariable("orderId", 456)
    .execute()
    .asMap();
```

### 5. 完整业务场景示例

```java
// 1. 发起异步请求处理数据
var processDataResult = RestClient.post("https://data-service.com/process")
    .header("X-Api-Key", "your-api-key")
    .bodyJson({
        "dataset": "sales_2024",
        "operation": "aggregate"
    })
    .bindCallback(60000)  // 60秒超时
    .execute()
    .waitCallback();

// 2. 根据回调结果继续处理
if (processDataResult.status == "SUCCESS") {
    // 3. 发起另一个异步请求
    var notifyResult = RestClient.post("https://notification-service.com/send")
        .header("Authorization", "Bearer token")
        .bodyJson({
            "to": "user@example.com",
            "subject": "处理完成",
            "data": processDataResult.data
        })
        .bindCallback(30000)
        .execute()
        .waitCallback();
    
    return {
        "success": true,
        "processResult": processDataResult,
        "notifyResult": notifyResult
    };
} else {
    return {
        "success": false,
        "error": processDataResult.message
    };
}
```

## 回调数据格式

第三方服务回调时，需要包含以下字段：

```json
{
    "eventId": "abc123def456",
    "targetIp": "192.168.1.100",
    "targetPort": 8080,
    "payload": {
        "status": "SUCCESS",
        "data": "处理结果数据"
    },
    "status": "SUCCESS",
    "message": "处理完成"
}
```

### 必需字段说明

| 字段 | 说明 |
|------|------|
| `eventId` | 事件ID，由 `bindCallback()` 自动生成并通过请求头传递 |
| `targetIp` | 目标IP，原始发起机器的IP |
| `targetPort` | 目标端口，原始发起机器的端口 |

## API 接口

### 公共回调接口

```
POST /api/public/callback
Content-Type: application/json

{
    "eventId": "xxx",
    "targetIp": "192.168.1.100",
    "targetPort": 8080,
    "payload": {...}
}
```

**处理流程**：
1. 收到回调后，检查 targetIp 和 targetPort
2. 如果是本机，直接唤醒事件
3. 如果不是本机，转发到目标机器的 `/api/internal/callback`

### 内部回调接口

```
POST /api/internal/callback
Content-Type: application/json

{
    "eventId": "xxx",
    "payload": {...}
}
```

**处理流程**：
1. 根据 eventId 查找等待的事件
2. 唤醒对应的 CompletableFuture
3. MVEL 表达式继续执行

### 事件状态查询

```
GET /api/internal/event/{eventId}/status

Response:
{
    "eventId": "xxx",
    "exists": true,
    "completed": false,
    "pending": true
}
```

### 统计信息

```
GET /api/internal/events/stats

Response:
{
    "pendingCount": 5
}
```

## 配置项

```yaml
etl:
  engine:
    node:
      ip: ""  # 可选，手动指定节点IP，为空则自动检测
    
    callback-rest:
      public-path: /api/public/callback
      internal-path: /api/internal/callback
      default-timeout-ms: 30000
      max-pending-events: 10000
```

## 技术特性

1. **虚拟线程支持**：使用 JDK 21 虚拟线程执行阻塞等待
2. **CompletableFuture**：事件等待基于 CompletableFuture 实现
3. **超时自动清理**：过期事件自动清理，防止内存泄漏
4. **重复回调忽略**：已处理的事件忽略重复回调
5. **无中间件依赖**：纯 HTTP 转发，无需 Redis、MQ

## 注意事项

1. `bindCallback()` 必须在 `execute()` 之前调用
2. `waitCallback()` 会阻塞当前线程直到收到回调或超时
3. 超时后会抛出 `CallbackTimeoutException`
4. 建议超时时间设置合理（如 30-60 秒）
5. 集群节点间需要网络互通
