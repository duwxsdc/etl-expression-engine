# MVEL + RestClient 异步回调完整使用指南

## 目录

1. [概述](#概述)
2. [核心组件](#核心组件)
3. [API接口说明](#api接口说明)
4. [MVEL表达式写法](#mvel表达式写法)
5. [测试验证](#测试验证)
6. [边界场景](#边界场景)
7. [分布式部署](#分布式部署)
8. [常见问题](#常见问题)

---

## 概述

本模块实现了基于 Spring 官方 RestClient 的分布式异步回调机制，完整流程如下：

```
┌─────────────┐      ┌─────────────────┐      ┌──────────────────┐
│ MVEL表达式  │─────▶│ 第三方异步服务  │─────▶│ 集群任意节点    │
│ RestClient  │      │ (延迟处理)      │      │ /api/public/     │
│ bindCallback│      │                 │      │ callback         │
└─────────────┘      └─────────────────┘      └────────┬─────────┘
       │                                                │
       │                                                │ HTTP转发
       │                                                ▼
       │                                       ┌──────────────────┐
       │                                       │ 原始发起节点    │
       │                                       │ /api/internal/   │
       │                                       │ callback         │
       │                                       └────────┬─────────┘
       │                                                │
       │◀─────────────事件唤醒──────────────────────────┘
       │
       ▼
  继续执行MVEL
```

### 技术栈

- **Spring Boot**: 3.4.6
- **JDK**: 21 (虚拟线程支持)
- **MVEL2**: 2.5.2.Final
- **RestClient**: Spring 6.1+ 官方客户端

---

## 核心组件

### 1. MvelRestClient

MVEL 表达式入口类，提供静态方法发起 HTTP 请求。

```java
// 可用方法
RestClient.get(url)      // GET 请求
RestClient.post(url)     // POST 请求
RestClient.put(url)      // PUT 请求
RestClient.delete(url)   // DELETE 请求
RestClient.patch(url)    // PATCH 请求
```

### 2. MvelRestClientBuilder

构建器模式，支持链式调用：

| 方法 | 说明 |
|------|------|
| `header(name, value)` | 添加请求头 |
| `headers(map)` | 批量添加请求头 |
| `bodyJson(object)` | 设置 JSON 请求体 |
| `bodyForm(map)` | 设置表单请求体 |
| `queryParam(name, value)` | 添加查询参数 |
| `pathVariable(name, value)` | 添加路径变量 |
| `timeout(duration)` | 设置请求超时 |
| `bindCallback(timeoutMs)` | 绑定回调，自动注入本机信息 |
| `execute()` | 执行请求 |
| `waitCallback()` | 阻塞等待回调 |

### 3. LocalNodeInfo

本地节点信息，自动获取本机 IP 和端口。

### 4. LocalEventManager

本机事件管理器，管理 eventId → CompletableFuture 映射。

---

## API接口说明

### 公共回调接口

**接口**: `POST /api/public/callback`

第三方服务回调此接口，可回调到集群任意节点。

**请求体**:
```json
{
    "eventId": "abc123def456",
    "targetIp": "192.168.1.100",
    "targetPort": 8080,
    "payload": { "result": "success" },
    "status": "SUCCESS",
    "message": "处理完成"
}
```

**处理逻辑**:
1. 检查 targetIp 和 targetPort 是否为本机
2. 如果是本机，直接唤醒事件
3. 如果不是本机，转发到目标节点的内部回调接口

### 内部回调接口

**接口**: `POST /api/internal/callback`

集群内部转发接口，仅处理事件唤醒。

**请求体**:
```json
{
    "eventId": "abc123def456",
    "payload": { "result": "success" },
    "status": "SUCCESS"
}
```

### 事件状态查询

**接口**: `GET /api/internal/event/{eventId}/status`

**响应**:
```json
{
    "eventId": "abc123def456",
    "exists": false,
    "completed": true,
    "pending": false
}
```

### 统计信息

**接口**: `GET /api/internal/events/stats`

**响应**:
```json
{
    "pendingCount": 5
}
```

### MVEL 测试执行接口

**接口**: `POST /test/run-mvel`

**请求体**:
```json
{
    "expression": "RestClient.post(\"http://example.com\").execute().asString()",
    "variables": {
        "key": "value"
    }
}
```

---

## MVEL表达式写法

### 1. 基础 GET 请求

```java
var result = RestClient.get("https://api.example.com/users")
    .queryParam("page", 1)
    .queryParam("size", 10)
    .header("Authorization", "Bearer token")
    .execute()
    .asString();
```

### 2. POST 请求带 JSON Body

```java
var body = {
    "name": "张三",
    "age": 25
};

var result = RestClient.post("https://api.example.com/users")
    .header("Authorization", "Bearer token")
    .bodyJson(body)
    .execute()
    .asMap();
```

### 3. 异步请求 + 回调等待（核心功能）

```java
var result = RestClient.post("https://third-party.com/async-api")
    .header("token", "xxx")
    .bodyJson({
        "action": "process",
        "data": "待处理数据"
    })
    .bindCallback(30000)  // 自动注入本机IP、端口、eventId，超时30秒
    .execute()
    .waitCallback();     // 阻塞等待回调唤醒
```

### 4. 带路径参数

```java
var result = RestClient.get("https://api.example.com/users/{userId}/orders/{orderId}")
    .pathVariable("userId", 123)
    .pathVariable("orderId", 456)
    .execute()
    .asString();
```

### 5. 完整业务场景

```java
// 1. 发起异步请求
var processDataResult = RestClient.post("https://data-service.com/process")
    .header("X-Api-Key", "your-api-key")
    .bodyJson({
        "dataset": "sales_2024",
        "operation": "aggregate"
    })
    .bindCallback(60000)
    .execute()
    .waitCallback();

// 2. 根据回调结果继续处理
if (processDataResult.status == "SUCCESS") {
    // 3. 发起另一个异步请求
    var notifyResult = RestClient.post("https://notify-service.com/send")
        .header("Authorization", "Bearer token")
        .bodyJson({
            "to": "user@example.com",
            "subject": "处理完成",
            "data": processDataResult.payload
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

---

## 测试验证

### 启动服务

```bash
# 启动 ETL 引擎 (端口 8080)
java --enable-preview -jar etl-expression-engine-1.0.0.jar

# 或使用 Maven
mvn spring-boot:run
```

### 测试接口调用

#### 1. 测试 MVEL 脚本执行

```bash
curl -X POST http://localhost:8080/test/run-mvel \
  -H "Content-Type: application/json" \
  -d '{
    "expression": "RestClient.get(\"https://httpbin.org/get\").queryParam(\"test\", \"value\").execute().asString()"
  }'
```

#### 2. 测试异步回调流程

```bash
# 发起异步请求
curl -X POST http://localhost:8080/test/run-mvel \
  -H "Content-Type: application/json" \
  -d '{
    "expression": "RestClient.post(\"http://localhost:8080/mock/third/async\").bodyJson({\"delayMs\": 3000}).bindCallback(30000).execute().waitCallback()"
  }'
```

#### 3. 测试模拟第三方服务

```bash
# 发起异步请求（3秒后回调）
curl -X POST http://localhost:8080/mock/third/async \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "test-event-001",
    "targetIp": "127.0.0.1",
    "targetPort": 8080,
    "delayMs": 3000
  }'

# 可配置的异步请求
curl -X POST "http://localhost:8080/mock/third/async/config?delayMs=2000&success=true&duplicate=false" \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "test-event-002",
    "targetIp": "127.0.0.1",
    "targetPort": 8080
  }'
```

#### 4. 立即回调测试

```bash
curl -X POST http://localhost:8080/mock/third/immediate-callback \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "test-event-003",
    "targetIp": "127.0.0.1",
    "targetPort": 8080,
    "payload": {"result": "immediate"}
  }'
```

#### 5. 查看状态

```bash
# RestClient 状态
curl http://localhost:8080/test/restclient/status

# 事件状态
curl http://localhost:8080/api/internal/event/{eventId}/status

# 统计信息
curl http://localhost:8080/api/internal/events/stats

# 模拟服务任务列表
curl http://localhost:8080/mock/third/tasks
```

---

## 边界场景

### 场景1: 正常流程

```
请求 → 阻塞等待 → 第三方回调 → 唤醒 → 返回结果
```

### 场景2: 超时场景

```
请求 → 阻塞等待 → 第三方不回调 → 超时 → 抛出 CallbackTimeoutException
```

**测试命令**:
```bash
curl -X POST "http://localhost:8080/mock/third/async/config?timeout=true" \
  -H "Content-Type: application/json" \
  -d '{"eventId": "timeout-test", "targetIp": "127.0.0.1", "targetPort": 8080}'
```

### 场景3: 重复回调

```
请求 → 回调一次 → 再次回调 → 忽略第二次
```

**测试命令**:
```bash
curl -X POST "http://localhost:8080/mock/third/async/config?duplicate=true" \
  -H "Content-Type: application/json" \
  -d '{"eventId": "dup-test", "targetIp": "127.0.0.1", "targetPort": 8080}'
```

### 场景4: 回调失败

```
请求 → 第三方处理失败 → 回调携带 ERROR 状态
```

**测试命令**:
```bash
curl -X POST "http://localhost:8080/mock/third/async/config?success=false" \
  -H "Content-Type: application/json" \
  -d '{"eventId": "fail-test", "targetIp": "127.0.0.1", "targetPort": 8080}'
```

### 场景5: 并发请求

```java
// 同时发起多个异步请求
for (i : {0..9}) {
    RestClient.post(url).bindCallback(30000).execute().waitCallback();
}
```

---

## 分布式部署

### 集群配置

每个节点需要配置唯一的节点 IP（如果自动检测不准确）：

```yaml
etl:
  engine:
    node:
      ip: "192.168.1.100"  # 手动指定节点IP
```

### 部署架构

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│    Node A       │     │    Node B       │     │    Node C       │
│  192.168.1.100  │     │  192.168.1.101  │     │  192.168.1.102  │
│     :8080       │     │     :8080       │     │     :8080       │
└─────────────────┘     └─────────────────┘     └─────────────────┘
        │                       │                       │
        └───────────────────────┴───────────────────────┘
                                │
                        第三方服务回调
                       (可回调到任意节点)
```

### 转发流程

1. 第三方回调到 Node B 的 `/api/public/callback`
2. Node B 检查 targetIp/targetPort，发现目标是 Node A
3. Node B 转发请求到 Node A 的 `/api/internal/callback`
4. Node A 唤醒事件，MVEL 继续执行

### 注意事项

1. **网络互通**: 集群节点间必须网络可达
2. **端口一致**: 建议所有节点使用相同端口
3. **时间同步**: 节点间时间需要同步（影响超时判断）
4. **无状态**: 事件存储在内存中，节点重启会丢失未完成事件

---

## 常见问题

### Q1: 为什么回调没有触发？

**可能原因**:
1. 第三方服务未正确传递 eventId、targetIp、targetPort
2. 网络不通，转发失败
3. 事件已超时被清理

**排查方法**:
```bash
# 检查事件状态
curl http://localhost:8080/api/internal/event/{eventId}/status

# 查看日志
grep "eventId" logs/application.log
```

### Q2: 超时时间如何设置？

**建议**:
- 根据第三方服务的处理时间设置
- 一般设置为预期时间的 2-3 倍
- 默认 30 秒，最大可设置数分钟

```java
.bindCallback(60000)  // 60秒超时
```

### Q3: 如何处理回调失败？

**方案**:
```java
var result = RestClient.post(url)
    .bindCallback(30000)
    .execute()
    .waitCallback();

if (result.status == "ERROR") {
    // 处理失败情况
    log.error("回调失败: " + result.message);
} else {
    // 处理成功情况
}
```

### Q4: 节点重启后事件会丢失吗？

**回答**: 是的，事件存储在内存中。如果需要持久化，可以扩展 LocalEventManager 将事件存储到 Redis 或数据库。

### Q5: 如何监控等待事件数量？

**方法**:
```bash
# API 查询
curl http://localhost:8080/api/internal/events/stats

# MVEL 中查询
var count = MvelRestClient.getPendingEventCount();
```

### Q6: 支持多少并发请求？

**回答**:
- 默认最大等待事件数: 10000
- 可通过配置调整: `etl.engine.callback-rest.max-pending-events`
- 使用虚拟线程，理论上支持大量并发

---

## 配置参考

```yaml
server:
  port: 8080

etl:
  engine:
    node:
      ip: ""  # 留空自动检测，或手动指定
    
    callback-rest:
      public-path: /api/public/callback
      internal-path: /api/internal/callback
      default-timeout-ms: 30000
      max-pending-events: 10000

logging:
  level:
    com.etl.engine.callback: DEBUG
    com.etl.engine.rest: DEBUG
```

---

## 完整测试示例

### 测试脚本

```bash
#!/bin/bash

echo "=== 1. 测试基础 GET 请求 ==="
curl -X POST http://localhost:8080/test/run-mvel \
  -H "Content-Type: application/json" \
  -d '{"expression": "RestClient.get(\"https://httpbin.org/get\").queryParam(\"test\", \"value\").execute().asString()"}'

echo -e "\n\n=== 2. 测试异步回调流程 ==="
curl -X POST http://localhost:8080/test/run-mvel \
  -H "Content-Type: application/json" \
  -d '{"expression": "RestClient.post(\"http://localhost:8080/mock/third/async\").bodyJson({\"delayMs\": 2000, \"eventId\": \"test-001\", \"targetIp\": \"127.0.0.1\", \"targetPort\": 8080}).bindCallback(30000).execute().waitCallback()"}'

echo -e "\n\n=== 3. 查看状态 ==="
curl http://localhost:8080/test/restclient/status

echo -e "\n\n=== 测试完成 ==="
```

### 预期结果

```json
{
  "success": true,
  "result": {
    "eventId": "test-001",
    "payload": { "result": "processed" },
    "status": "SUCCESS"
  },
  "duration": 2345
}
```
