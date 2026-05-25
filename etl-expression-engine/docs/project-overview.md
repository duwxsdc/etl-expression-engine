# ETL 表达式引擎 - 项目概览

> **最新更新**: 2026-05-25
> **版本**: v1.0.2
> **状态**: ✅ 所有组件已添加完整注释，测试全部通过

## 目录结构

```
etl-expression-engine/
├── docs/                           # 文档目录
│   ├── api-documentation.md        # API接口文档
│   ├── mvel-restclient-guide.md    # RestClient基础使用指南
│   ├── mvel-restclient-complete-guide.md # RestClient完整使用指南
│   ├── mvel-restclient-extension.md      # RestClient扩展功能文档
│   ├── deployment.md               # 部署文档
│   └── push-comparison-report.md   # 版本变更报告
├── src/
│   ├── main/
│   │   ├── java/com/etl/engine/
│   │   │   ├── callback/           # 回调核心组件
│   │   │   │   ├── LocalNodeInfo.java     # 本地节点信息
│   │   │   │   ├── LocalEventManager.java # 事件管理器
│   │   │   │   ├── CallbackRecord.java    # 回调数据记录
│   │   │   │   └── *Exception.java        # 异常类
│   │   │   ├── rest/               # RestClient组件
│   │   │   │   ├── MvelRestClient.java        # RestClient入口
│   │   │   │   ├── MvelRestClientBuilder.java # 构建器
│   │   │   │   ├── RestClientProperties.java  # 配置属性
│   │   │   │   ├── RequestInterceptor.java    # 请求拦截器
│   │   │   │   ├── ResponseInterceptor.java   # 响应拦截器
│   │   │   │   ├── PublicCallbackController.java # 公共回调接口
│   │   │   │   ├── InternalCallbackController.java # 内部回调接口
│   │   │   │   └── RestClientCallbackAutoConfig.java # 自动配置
│   │   │   ├── controller/         # 控制器
│   │   │   │   ├── MvelTestController.java # 测试控制器
│   │   │   │   └ MvelTestRunnerController.java # MVEL执行控制器
│   │   │   ├── mvel/               # MVEL引擎核心
│   │   │   │   ├── MvelExpressionEngine.java # 表达式引擎
│   │   │   │   ├── MvelSecuritySandbox.java  # 安全沙箱
│   │   │   │   └ HttpFunction.java           # HTTP函数
│   │   │   ├── security/           # 安全组件
│   │   │   │   ├── TokenManager.java # Token管理
│   │   │   ├── crypto/             # 加密组件
│   │   │   │   ├── CryptoService.java # 加密服务
│   │   │   ├── mock/               # 模拟服务
│   │   │   │   ├── MockThirdPartyController.java # 模拟第三方服务
│   │   │   ├── config/             # 配置类
│   │   │   ├── context/            # 上下文管理
│   │   │   ├── http/               # HTTP组件
│   │   │   └ model/               # 数据模型
│   │   └ resources/
│   │   │   ├── application.yml     # 主配置文件
│   │   │   └ META-INF/spring/...  # Spring自动配置
│   │   └ test/
│   │   │   ├── java/com/etl/engine/
│   │   │   │   ├── rest/           # RestClient测试
│   │   │   │   ├── callback/       # 回调测试
│   │   │   │   ├── mvel/           # MVEL测试
│   │   │   │   └ controller/      # 控制器测试
├── pom.xml                         # Maven配置
```

## 核心组件说明

### 1. 回调组件 (com.etl.engine.callback)

| 组件 | 说明 | 关键特性 |
|------|------|----------|
| LocalNodeInfo | 本地节点信息管理 | 自动IP检测、端口获取、节点ID生成 |
| LocalEventManager | 事件等待/唤醒管理 | CompletableFuture、超时清理、重复回调防护 |
| CallbackRecord | 回调数据记录 | Record格式、JSON序列化 |
| CallbackTimeoutException | 超时异常 | 事件等待超时时抛出 |
| DuplicateCallbackException | 重复回调异常 | 防止重复处理 |

### 2. RestClient组件 (com.etl.engine.rest)

| 组件 | 说明 | 关键特性 |
|------|------|----------|
| MvelRestClient | RestClient入口类 | 静态方法、全局拦截器 |
| MvelRestClientBuilder | 构建器模式 | 链式调用、bindCallback、waitCallback |
| RestClientProperties | 配置属性 | 自动Token、超时、重试、日志 |
| RequestInterceptor | 请求拦截器 | 自定义Header、URL重写 |
| ResponseInterceptor | 响应拦截器 | 日志记录、性能监控 |
| PublicCallbackController | 公共回调接口 | 第三方回调入口、转发逻辑 |
| InternalCallbackController | 内部回调接口 | 事件唤醒、状态查询 |

### 3. MVEL引擎 (com.etl.engine.mvel)

| 组件 | 说明 | 关键特性 |
|------|------|----------|
| MvelExpressionEngine | 表达式执行引擎 | 虚拟线程、超时控制、ScopedValue |
| MvelSecuritySandbox | 安全沙箱 | 禁止类/关键字、危险模式检测 |
| HttpFunction | HTTP函数入口 | httpRequest、get、post别名 |

### 4. 安全组件 (com.etl.engine.security)

| 组件 | 说明 | 关键特性 |
|------|------|----------|
| TokenManager | Token动态管理 | OAuth2支持、自动刷新、指数退避 |
| TokenInfo | Token信息存储 | 过期时间、刷新令牌 |
| TokenException | Token异常 | 认证失败、过期异常 |

### 5. 加密组件 (com.etl.engine.crypto)

| 组件 | 说明 | 关键特性 |
|------|------|----------|
| CryptoService | 加密服务 | AES-256-GCM、RSA-2048 |
| KeyInfo | 密钥信息 | 密钥ID、创建时间 |
| KeyRotationPolicy | 密钥轮换策略 | 自动轮换、过期检测 |

## 配置项说明

### 基础配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| server.port | 服务端口 | 8080 |
| etl.engine.expression-timeout | 表达式超时(ms) | 5000 |
| etl.engine.max-expression-length | 表达式最大长度 | 10000 |

### RestClient配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| etl.engine.rest-client.auto-token-enabled | 自动Token注入 | false |
| etl.engine.rest-client.default-timeout | 默认超时 | 30s |
| etl.engine.rest-client.max-retries | 最大重试次数 | 0 |
| etl.engine.rest-client.log-request-enabled | 请求日志 | true |

### 回调配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| etl.engine.callback-rest.public-path | 公共回调路径 | /api/public/callback |
| etl.engine.callback-rest.default-timeout-ms | 默认超时 | 30000 |
| etl.engine.callback-rest.max-pending-events | 最大等待事件 | 10000 |

### 安全沙箱配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| etl.engine.security.sandbox.enabled | 启用沙箱 | true |
| etl.engine.security.sandbox.max-loop-count | 最大循环次数 | 10 |
| etl.engine.security.sandbox.max-nesting-depth | 最大嵌套深度 | 20 |

## API接口说明

### MVEL表达式执行

```
POST /test/run-mvel
Body: { "expression": "...", "variables": {...} }
```

### RestClient状态

```
GET /test/restclient/status
Response: { "pendingEvents": 0, "nodeIp": "...", "nodePort": 8080 }
```

### 公共回调接口

```
POST /api/public/callback
Body: { "eventId": "...", "targetIp": "...", "targetPort": 8080, "payload": {...} }
```

### 内部回调接口

```
POST /api/internal/callback
Body: { "eventId": "...", "payload": {...} }
```

### 事件状态查询

```
GET /api/internal/event/{eventId}/status
Response: { "eventId": "...", "exists": true, "completed": false }
```

### 模拟第三方服务

```
POST /mock/third/async
Body: { "eventId": "...", "targetIp": "...", "targetPort": 8080, "delayMs": 3000 }
```

## 测试用例清单

### RestClient回调测试 (RestClientCallbackIntegrationTest)

- 场景1: 正常流程 - 请求→阻塞→回调→唤醒→成功
- 场景2: 超时场景 - 第三方不回调→超时异常
- 场景3: 重复回调 - 第三方多次回调→只处理一次
- 场景4: 回调乱序 - 第三方回调慢→依然正常唤醒
- 场景5: 快速回调 - 第三方立刻回调→正常处理
- 场景6: 并发请求 - 多个并发请求同时处理
- 场景7: 公共回调接口 - 转发到目标节点
- 场景8: 异常回调 - 回调携带错误状态
- 场景9: RestClient链式构建
- 场景10: 事件管理器状态查询

### RestClient扩展测试 (MvelRestClientExtensionTest)

- 扩展1: 自动Token注入配置
- 扩展2: skipAutoToken跳过自动注入
- 扩展3: bearerAuth手动认证
- 扩展4: basicAuth基础认证
- 扩展5: 请求拦截器添加Header
- 扩展6: 响应拦截器日志记录
- 扩展7: 端点级别配置
- 扩展8: 自定义TokenProvider
- 扩展9: 默认请求头配置
- 扩展10: withRetry重试配置
- 扩展11: 多种Content-Type支持
- 扩展12: Token来源优先级
- 扩展13: 日志配置验证
- 扩展14: 链式构建完整性
- 扩展15: 拦截器链执行顺序

## 技术栈

- **框架**: Spring Boot 3.4.6
- **JDK**: Java 21 (虚拟线程、ScopedValue)
- **表达式引擎**: MVEL2 2.5.2.Final
- **HTTP客户端**: Spring RestClient 6.1+
- **数据库**: H2 (内存数据库)
- **缓存**: Redis (可选)

## 构建与部署

### 构建

```bash
mvn clean package -DskipTests
```

### 运行

```bash
java --enable-preview -jar target/etl-expression-engine-1.0.0.jar
```

### 测试

```bash
mvn test
```

## 文档索引

- [API接口文档](./api-documentation.md)
- [RestClient使用指南](./mvel-restclient-guide.md)
- [RestClient完整指南](./mvel-restclient-complete-guide.md)
- [RestClient扩展功能](./mvel-restclient-extension.md)
- [部署文档](./deployment.md)

## 版本历史

- v1.0.0 - 初始版本，支持MVEL表达式执行、HTTP请求、SQL查询
- v1.0.1 - 新增分布式异步回调机制
- v1.0.2 - 新增RestClient扩展功能（自动Token、拦截器、重试）