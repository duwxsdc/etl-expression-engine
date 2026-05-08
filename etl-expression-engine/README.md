# ETL表达式引擎

ETL流程编排专用表达式参数引擎，支持MVEL2表达式计算、安全SQL查询、会话隔离等功能。

## 架构演进

✅ **当前架构：HTTP POST + Redis会话管理**
- 后端：SpringBoot 3.4.6 + JDK21 + 虚拟线程 + ScopedValue + Record DTO
- 前端：原生HTML/CSS/JavaScript + Fetch API
- 会话管理：Redis分布式存储 + 自动过期
- 安全机制：MVEL沙箱 + SQL黑名单 + 表达式超时

❌ **已移除：WebSocket长连接架构**

## 快速启动

### 环境要求
- JDK 21+
- Maven 3.8+
- Redis 6.0+（可选，用于分布式部署）

### 启动步骤
1. **本地启动（无Redis）**：
   ```bash
   cd etl-expression-engine
   mvn spring-boot:run
   ```
   访问 `http://localhost:8080`

2. **Redis模式启动**：
   - 启动Redis服务（默认配置：localhost:6379）
   - 修改 `application.yml` 中的Redis配置（如需要）
   - 启动应用

## API文档

### HTTP POST接口

**URL**: `POST /etl/expression/execute`

**请求头**:
- `X-Session-Id`: 会话ID（可选，自动创建）
- `Content-Type`: `text/plain`

**请求体**: 多行表达式字符串

**响应格式**: JSON
```json
{
  "sessionId": "ETL-1234567890ABCDEF",
  "originExpr": "a=100; b=a+1",
  "success": true,
  "finalResult": 101,
  "errorMsg": null,
  "contextVars": {
    "a": 100,
    "b": 101
  }
}
```

## 核心功能

### 1. 表达式计算
- ✅ 多行表达式顺序执行
- ✅ 变量赋值：`a=100`, `name="etl"`
- ✅ 算术运算：`a+b`, `a*2`
- ✅ 逻辑运算：`a>50 ? "big" : "small"`
- ✅ 对象方法调用：`list.size()`

### 2. 安全SQL查询
- ✅ 只读SELECT执行：`SQL: SELECT * FROM users WHERE id=1`
- ✅ SQL注入防护：自动检测恶意模式
- ✅ DML/DDL拦截：禁止INSERT/UPDATE/DELETE/DROP等

### 3. 会话管理
- ✅ 分布式Redis存储
- ✅ 自动过期：30分钟无操作自动清理
- ✅ 页面刷新保持：localStorage持久化SessionId
- ✅ 跨请求隔离：每个SessionId独立变量上下文

### 4. 安全机制
- ✅ MVEL沙箱：禁用Runtime/System/Process等危险API
- ✅ 执行超时：默认5秒，可配置
- ✅ 表达式长度限制：默认10000字符
- ✅ 输入验证：严格语法检查

## 技术栈

| 层级 | 技术 |
|------|------|
| **后端** | SpringBoot 3.4.6, JDK21, MVEL2, JDBC, Redis, H2 |
| **前端** | 原生HTML/CSS/JavaScript, Fetch API |
| **架构** | 虚拟线程, ScopedValue替代ThreadLocal, Record DTO |
| **安全** | 三层防护：沙箱+超时+输入验证 |

## 迁移注意事项

### WebSocket → HTTP迁移要点
- **通信方式**：WebSocket长连接 → HTTP短连接
- **会话管理**：内存存储 → Redis分布式存储
- **线程模型**：传统线程 → 虚拟线程 + ScopedValue
- **前端适配**：WebSocket API → Fetch API
- **错误处理**：连接异常 → HTTP状态码处理

### 兼容性保证
- ✅ UI界面完全一致（样式、布局、配色）
- ✅ 交互体验完全一致（回车提交、变量面板、日志滚动）
- ✅ 功能特性完全一致（多行表达式、SQL查询、变量赋值）
- ✅ 安全能力完全一致（沙箱、超时、黑名单）

## 开发指南

### 添加新依赖
```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>example-library</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 配置Redis集群
```yaml
# application.yml
spring:
  redis:
    cluster:
      nodes: redis1:6379,redis2:6379,redis3:6379
```

### 自定义表达式超时
```yaml
# application.yml
etl:
  engine:
    expression-timeout: 10000 # 10秒
```

## 贡献指南

欢迎提交Issue和Pull Request！

- Bug报告：请提供复现步骤和错误日志
- 功能建议：请描述使用场景和预期效果
- 代码贡献：请遵循Java开发规范，添加单元测试

---
© 2026 ETL Expression Engine. All rights reserved.