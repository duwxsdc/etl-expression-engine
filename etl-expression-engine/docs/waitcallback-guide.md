# waitCallback 回调等待机制完整指南

## 概述

`waitCallback()` 是 MvelRestClient 提供的异步回调等待方法，用于在发起异步HTTP请求后阻塞当前线程，等待第三方服务回调并返回结果。

## 核心流程

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  MVEL表达式     │────▶│  第三方异步服务 │────▶│  集群任意节点  │
│  bindCallback() │     │  (延迟处理)     │     │  公共回调接口  │
└─────────────────┘     └─────────────────┘     └────────┬────────┘
        │                                                  │
        │                                                  │ 转发
        │                                                  ▼
        │                                         ┌─────────────────┐
        │                                         │  原始发起节点  │
        │                                         │  内部回调接口  │
        │                                         └────────┬────────┘
        │                                                  │
        │◀─────────────────事件唤醒─────────────────────────┘
        │
        ▼
   waitCallback()返回结果
```

---

## 语法结构

### 基本语法

```java
var result = RestClient.post("https://third-party.com/api")
    .bindCallback(timeoutMs)    // 绑定回调，设置超时时间
    .execute()                  // 执行请求
    .waitCallback();            // 阻塞等待回调
```

### 完整语法

```java
var result = RestClient.post("https://third-party.com/api")
    .header("Authorization", "Bearer token")    // 设置请求头
    .header("X-Request-Id", "req-001")
    .bodyJson({                                 // 设置请求体
        "action": "process",
        "data": "待处理数据"
    })
    .bindCallback(30000)                        // 绑定回调，30秒超时
    .execute()                                  // 执行请求，返回AsyncResult
    .waitCallback();                            // 阻塞等待回调唤醒
```

---

## 方法说明

### bindCallback(timeoutMs)

绑定异步回调，自动注入以下信息：

| 注入项 | 说明 | 请求头 |
|--------|------|--------|
| eventId | 全局唯一事件ID | X-Callback-EventId |
| targetIp | 本机IP地址 | X-Callback-TargetIp |
| targetPort | 本机端口 | X-Callback-TargetPort |

**参数**:
- `timeoutMs`: 超时时间（毫秒），超过此时间未收到回调将抛出异常

**返回**: MvelRestClientBuilder（支持链式调用）

### execute()

执行HTTP请求，返回 AsyncResult 对象。

**返回**: AsyncResult record，包含：
- `eventId`: 事件ID
- `future`: CompletableFuture 对象
- `initialResponse`: 初始响应（同步请求的响应）

### waitCallback()

阻塞当前线程，等待回调唤醒。

**返回**: Object - 回调数据（通常是Map类型）

**异常**:
- `CallbackTimeoutException`: 等待超时
- `RuntimeException`: 回调失败或其他错误

### waitCallback(timeoutMs)

带自定义超时的等待。

**参数**:
- `timeoutMs`: 自定义超时时间（毫秒）

**返回**: Object - 回调数据

---

## 使用示例

### 示例1: 基本异步回调

```java
var result = RestClient.post("https://api.example.com/process")
    .bodyJson({"data": "test"})
    .bindCallback(30000)
    .execute()
    .waitCallback();

// result 包含回调数据
if (result.status == "SUCCESS") {
    return result.payload;
} else {
    throw new Error(result.message);
}
```

### 示例2: 带认证的异步请求

```java
var result = RestClient.post("https://api.example.com/async")
    .header("Authorization", "Bearer " + token)
    .header("X-Api-Key", "your-api-key")
    .bodyJson({
        "action": "process",
        "dataset": "sales_2024"
    })
    .bindCallback(60000)  // 60秒超时
    .execute()
    .waitCallback();
```

### 示例3: 链式异步请求

```java
// 第一步：处理数据
var processResult = RestClient.post("https://data-service.com/process")
    .bodyJson({"dataset": "sales"})
    .bindCallback(30000)
    .execute()
    .waitCallback();

// 第二步：发送通知（基于第一步结果）
if (processResult.status == "SUCCESS") {
    var notifyResult = RestClient.post("https://notify-service.com/send")
        .bodyJson({
            "to": "user@example.com",
            "subject": "处理完成",
            "data": processResult.payload
        })
        .bindCallback(15000)
        .execute()
        .waitCallback();
    
    return {
        "success": true,
        "processResult": processResult,
        "notifyResult": notifyResult
    };
}
```

### 示例4: 错误处理

```java
var result = RestClient.post("https://api.example.com/process")
    .bodyJson({"data": "test"})
    .bindCallback(30000)
    .execute()
    .waitCallback();

// 检查回调状态
if (result instanceof Map) {
    var status = result.get("status");
    if ("SUCCESS".equals(status)) {
        return result.get("payload");
    } else if ("ERROR".equals(status)) {
        throw new RuntimeException("处理失败: " + result.get("message"));
    }
}
return result;
```

---

## 回调数据格式

第三方服务回调时，需要发送以下格式的数据：

### 公共回调接口

**URL**: `POST /api/public/callback`

**请求体**:
```json
{
    "eventId": "abc123def456",
    "targetIp": "192.168.1.100",
    "targetPort": 8080,
    "payload": {
        "result": "processed",
        "data": { ... }
    },
    "status": "SUCCESS",
    "message": "处理完成",
    "timestamp": "2026-05-25T10:30:00Z"
}
```

### 必需字段

| 字段 | 类型 | 说明 |
|------|------|------|
| eventId | String | 事件ID，由bindCallback自动生成 |
| targetIp | String | 目标节点IP，原始发起请求的节点 |
| targetPort | Integer | 目标节点端口 |
| payload | Object | 回调数据载荷 |
| status | String | 状态：SUCCESS / ERROR |

---

## 场景说明

### 场景1: 成功回调

```
请求 → 等待 → 第三方回调(SUCCESS) → 唤醒 → 返回结果
```

**预期结果**: waitCallback() 返回回调数据

### 场景2: 错误回调

```
请求 → 等待 → 第三方回调(ERROR) → 唤醒 → 返回错误信息
```

**预期结果**: waitCallback() 返回包含错误信息的Map

### 场景3: 超时

```
请求 → 等待 → 超时时间到 → 抛出CallbackTimeoutException
```

**预期结果**: 抛出 CallbackTimeoutException

### 场景4: 重复回调

```
请求 → 回调1 → 唤醒 → 回调2 → 忽略
```

**预期结果**: 第二次回调被忽略，保证幂等性

### 场景5: 延迟回调

```
请求 → 等待N秒 → 第三方回调 → 唤醒
```

**预期结果**: 正常返回结果，等待时间取决于第三方处理时间

---

## 注意事项

### 1. 超时设置

- 建议根据第三方服务的处理时间设置合理的超时
- 一般设置为预期时间的 2-3 倍
- 默认超时: 30秒，最大可设置数分钟

### 2. 线程阻塞

- `waitCallback()` 会阻塞当前线程
- 在虚拟线程中执行，不会阻塞操作系统线程
- 建议在MVEL表达式中使用，由引擎管理线程

### 3. 事件管理

- 事件存储在内存中，节点重启会丢失
- 最大等待事件数: 10000（可配置）
- 超时事件会自动清理

### 4. 幂等性

- 同一eventId的多次回调只处理第一次
- 后续回调会被忽略并记录日志

### 5. 集群部署

- 第三方可回调到集群任意节点
- 公共接口会自动转发到原始发起节点
- 确保节点间网络互通

---

## 配置项

```yaml
etl:
  engine:
    callback-rest:
      public-path: /api/public/callback     # 公共回调路径
      internal-path: /api/internal/callback  # 内部回调路径
      default-timeout-ms: 30000              # 默认超时时间
      max-pending-events: 10000              # 最大等待事件数
```

---

## API接口

### 查询事件状态

```
GET /api/internal/event/{eventId}/status

Response:
{
    "eventId": "abc123",
    "exists": false,
    "completed": true,
    "pending": false
}
```

### 查询统计信息

```
GET /api/internal/events/stats

Response:
{
    "pendingCount": 5
}
```

---

## 测试页面

访问测试页面进行交互测试：

```
http://localhost:8080/waitcallback-test.html
```

测试页面提供以下功能：
- 快速测试场景（成功、错误、超时、重复、延迟）
- 自定义测试参数
- 实时查看执行结果和日志
- 服务状态监控

---

## 常见问题

### Q1: 为什么回调没有触发？

**可能原因**:
1. 第三方未正确传递 eventId、targetIp、targetPort
2. 网络不通，转发失败
3. 事件已超时被清理

**排查方法**:
```bash
# 检查事件状态
curl http://localhost:8080/api/internal/event/{eventId}/status
```

### Q2: 如何处理超时？

```java
try {
    var result = builder.waitCallback();
} catch (Exception e) {
    if (e.getMessage().contains("超时")) {
        // 处理超时逻辑
    }
}
```

### Q3: 回调数据格式是什么？

回调数据是Object类型，通常是Map：
```java
if (result instanceof Map) {
    Map resultMap = (Map) result;
    String status = resultMap.get("status");
    Object payload = resultMap.get("payload");
}
```

---

## 版本历史

- v1.0.0: 初始实现
- v1.0.1: 支持集群转发
- v1.0.2: 添加拦截器支持、完善文档
