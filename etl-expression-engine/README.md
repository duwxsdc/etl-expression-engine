# ETL流程编排专用表达式参数引擎

## 项目概述

ETL表达式参数引擎是一个基于SpringBoot 3.2.0和JDK 17构建的高性能分布式表达式计算引擎，专为ETL数据编排场景设计。采用"本地优先 + MQ广播兜底"策略实现分布式部署，支持动态参数配置、变量定义、表达式计算以及SQL查询功能，通过WebSocket实现实时交互。

## 技术栈

- **JDK 17** - 核心运行环境
- **SpringBoot 3.2.0** - 核心框架
- **MVEL 2.5.2** - 表达式引擎
- **RocketMQ** - 分布式消息广播
- **WebSocket** - 实时通信
- **H2 Database** - 嵌入式数据库（演示用）

## 分布式架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Load Balancer                                   │
│                    (WebSocket消息也会经过负载均衡)                            │
└─────────────────────────────────────────────────────────────────────────────┘
                    │                    │                    │
                    ▼                    ▼                    ▼
┌──────────────────────┐ ┌──────────────────────┐ ┌──────────────────────┐
│     Node-1           │ │     Node-2           │ │     Node-3           │
│  ┌────────────────┐  │ │  ┌────────────────┐  │ │  ┌────────────────┐  │
│  │ WebSocket      │  │ │  │ WebSocket      │  │ │  │ WebSocket      │  │
│  │ + Session      │  │ │  │ (无此Session)  │  │ │  │ (无此Session)  │  │
│  │ SESSION-XXX    │  │ │  │                │  │ │  │                │  │
│  └────────────────┘  │ │  └────────────────┘  │ │  └────────────────┘  │
│  ┌────────────────┐  │ │  ┌────────────────┐  │ │  ┌────────────────┐  │
│  │ SessionContext │  │ │  │                │  │ │  │                │  │
│  │ (变量存储)      │  │ │  │                │  │ │  │                │  │
│  └────────────────┘  │ │  └────────────────┘  │ │  └────────────────┘  │
│  ┌────────────────┐  │ │  ┌────────────────┐  │ │  ┌────────────────┐  │
│  │ MvelSandbox    │  │ │  │ MvelSandbox    │  │ │  │ MvelSandbox    │  │
│  │ Engine         │  │ │  │ Engine         │  │ │  │ Engine         │  │
│  └────────────────┘  │ │  └────────────────┘  │ │  └────────────────┘  │
└──────────────────────┘ └──────────────────────┘ └──────────────────────┘
           │                       │                       │
           └───────────────────────┼───────────────────────┘
                                   │
                                   ▼
                    ┌──────────────────────────────┐
                    │       RocketMQ Cluster       │
                    │    Topic: mvel-broadcast     │
                    │    Mode: BROADCASTING        │
                    └──────────────────────────────┘
```

### 核心策略：本地检查 + MQ广播兜底

**场景说明**：
- 用户WebSocket连接建立时到达Node-1，Session保存在Node-1
- 用户发送表达式消息时，可能通过负载均衡到达Node-2
- Node-2没有该Session，需要广播找到拥有Session的节点

**请求处理流程**：

```
用户WebSocket消息 → 负载均衡 → 到达某个节点
                              ↓
                    检查本地是否有该Session
                              ↓
           ┌──────────────────┴──────────────────┐
           ↓                                      ↓
      Session在本地                          Session不在本地
           ↓                                      ↓
      直接执行表达式                          RocketMQ广播
           ↓                                      ↓
      通过WebSocket返回                    所有节点收到广播
                                                  ↓
                                  ┌───────────────┼───────────────┐
                                  ↓               ↓               ↓
                              Node-1          Node-2          Node-3
                              有Session        无Session        无Session
                                  ↓               ↓               ↓
                              执行并返回        丢弃             丢弃
                                  ↓
                          通过WebSocket返回给前端
```

**关键点**：
1. 前端请求必须携带sessionId（连接建立时服务端返回）
2. 执行节点直接通过其WebSocket连接返回结果
3. 不需要Redis共享SessionContext

## 核心模块

### 1. WebSocket层
- **DistributedWebSocketHandler** - 分布式WebSocket处理器
- **WebSocketSessionManager** - 本地WebSocket会话管理

### 2. MQ层
- **BroadcastMessageProducer** - RocketMQ消息生产者
- **BroadcastMessageConsumer** - RocketMQ消息消费者（广播模式）

### 3. 引擎层
- **MvelSandboxEngine** - MVEL安全沙箱引擎
- **SqlExecutionEngine** - SQL执行引擎

### 4. 控制层
- **IdempotentController** - 幂等控制器（基于requestId）
- **SessionContextManager** - 会话上下文管理

## 快速开始

### 1. 环境要求
- JDK 17+
- Maven 3.8+
- RocketMQ 4.9+（分布式部署需要）

### 2. 安装RocketMQ（可选，用于分布式部署）

```bash
# 下载RocketMQ
wget https://archive.apache.org/dist/rocketmq/4.9.4/rocketmq-all-4.9.4-bin-release.zip

# 解压并启动NameServer
nohup sh bin/mqnamesrv &

# 启动Broker
nohup sh bin/mqbroker -n localhost:9876 &
```

### 3. 编译打包
```bash
cd etl-expression-engine
mvn clean package -DskipTests
```

### 4. 启动服务

单机模式（无RocketMQ）：
```bash
java -jar target/etl-expression-engine-1.0.0.jar
```

分布式模式：
```bash
# 节点1
java -Detl.engine.node-id=node-1 -jar target/etl-expression-engine-1.0.0.jar --server.port=8080

# 节点2
java -Detl.engine.node-id=node-2 -jar target/etl-expression-engine-1.0.0.jar --server.port=8081

# 节点3
java -Detl.engine.node-id=node-3 -jar target/etl-expression-engine-1.0.0.jar --server.port=8082
```

### 5. 访问控制台
打开浏览器访问: http://localhost:8080

## 配置说明

```yaml
server:
  port: 8080

spring:
  application:
    name: etl-expression-engine

rocketmq:
  name-server: localhost:9876
  producer:
    group: etl-expression-producer
    send-message-timeout: 3000
    retry-times-when-send-failed: 2

etl:
  engine:
    node-id: node-1                           # 节点ID（分布式环境必须唯一）
    expression-timeout: 5000                  # 表达式执行超时(毫秒)
    max-expression-length: 10000              # 最大表达式长度
    enable-sql-execution: true                # 是否启用SQL执行
    sql-readonly: true                        # SQL只读模式
    broadcast-topic: mvel-broadcast           # 广播Topic
    idempotent-window-seconds: 30             # 幂等时间窗口(秒)
```

## 核心功能

### 1. 表达式计算
支持MVEL2语法，包括：
- 变量赋值: `a=100`
- 算术运算: `a+b`, `a*b`
- 逻辑运算: `a>b`, `flag1&&flag2`
- 三元运算: `score>=60?"pass":"fail"`
- 多行执行: `a=10; b=20; a+b`

### 2. 会话隔离
- 每个WebSocket连接独立会话
- 变量上下文完全隔离
- 会话超时自动清理

### 3. SQL查询
- 支持SELECT只读查询
- 拦截DML/DDL危险语句
- SQL注入防护

### 4. 安全沙箱
- 禁止危险类和API（Runtime、ProcessBuilder、File、Socket等）
- 表达式超时保护
- 表达式长度限制

### 5. 分布式支持
- 广播模式确保消息到达所有节点
- 幂等控制防止重复执行
- 自过滤避免处理自己的请求

## WebSocket接口

### 连接地址
```
ws://localhost:8080/ws/expression
```

### 消息格式

#### 执行请求
```json
{
  "type": "EXECUTE",
  "requestId": "req-12345",
  "content": "a=100; a*2"
}
```

#### 执行响应
```json
{
  "type": "RESULT",
  "requestId": "req-12345",
  "sessionId": "SESSION-ABC123",
  "data": {
    "success": true,
    "result": 200,
    "assignedVariables": {"a": 100}
  }
}
```

#### 错误响应
```json
{
  "type": "ERROR",
  "requestId": "req-12345",
  "sessionId": "SESSION-ABC123",
  "content": "表达式执行超时"
}
```

## 广播消息格式

```json
{
  "requestId": "req-12345",
  "sessionId": "SESSION-ABC123",
  "expression": "a=100; a*2",
  "vars": {"b": 50},
  "timeout": 5000,
  "sourceNodeId": "node-1"
}
```

## 幂等控制

- 基于requestId实现幂等
- 时间窗口30秒内去重
- 重复请求返回DUPLICATE标记

## 安全机制

### MVEL沙箱安全
- 禁止危险关键字: import, package, new, class等
- 禁止危险类: Runtime, System, ProcessBuilder, File, Socket等
- 表达式超时保护(默认5秒)
- 表达式长度限制(默认10000字符)

### SQL安全
- 只允许SELECT查询
- 拦截INSERT/UPDATE/DELETE/DROP等语句
- SQL注入防护
- 查询超时限制

## 测试覆盖

运行测试:
```bash
mvn test
```

项目包含完整的单元测试:
- `MvelSandboxEngineTest` - 表达式引擎测试
- `SqlExecutionEngineTest` - SQL引擎测试
- `SessionContextManagerTest` - 会话管理测试
- `ContextHolderTest` - ThreadLocal上下文测试
- `IdempotentControllerTest` - 幂等控制测试

## 性能优化

1. 线程池处理高并发请求
2. ConcurrentHashMap保证线程安全
3. 会话超时自动清理(默认30分钟)
4. 表达式编译缓存优化
5. RocketMQ广播模式减少网络开销

## 注意事项

1. 分布式部署时，每个节点必须配置唯一的node-id
2. RocketMQ必须使用BROADCASTING模式
3. 会话隔离，不同浏览器连接互不影响
4. SQL仅支持只读查询，不允许修改操作
5. 表达式执行有超时限制，防止死循环
6. H2数据库控制台: http://localhost:8080/h2-console (JDBC URL: jdbc:h2:mem:testdb)

## License

MIT License
