# ETL Expression Engine

ETL流程编排专用表达式参数引擎，支持MVEL2表达式计算、安全SQL查询、会话隔离等功能。

---

## 目录

- [架构演进](#架构演进)
- [快速启动](#快速启动)
- [核心功能](#核心功能)
- [技术栈](#技术栈)
- [API接口](#api接口)
- [配置说明](#配置说明)
- [部署方案](#部署方案)
- [开发指南](#开发指南)
- [安全机制](#安全机制)
- [文档目录](#文档目录)

---

## 架构演进

✅ **当前架构：HTTP POST + Redis会话管理**
- 后端：SpringBoot 3.4.6 + JDK21 + 虚拟线程 + ScopedValue + Record DTO
- 前端：原生HTML/CSS/JavaScript + Fetch API
- 会话管理：Redis分布式存储 + 自动过期 + 本地内存降级
- 安全机制：MVEL沙箱 + SQL黑名单 + 表达式超时

❌ **已移除：WebSocket长连接架构**

---

## 快速启动

### 环境要求
- **JDK 21+**：必需，依赖虚拟线程、ScopedValue、Record等特性
- **Maven 3.8+**：项目构建工具
- **Redis 6.0+**：可选，用于分布式会话存储

### 启动步骤

**方式一：Maven直接启动**
```bash
cd etl-expression-engine
mvn spring-boot:run
```

**方式二：编译打包后启动**
```bash
mvn clean package -DskipTests
java --enable-preview -jar target/etl-expression-engine-1.0.0.jar
```

**方式三：Windows脚本启动**
```bash
etl-engine.bat start
```

**访问地址**：`http://localhost:8080`

---

## 核心功能

### 1. 表达式计算
- ✅ 多行表达式顺序执行（分号分隔）
- ✅ 变量赋值：`a=100`, `name="etl"`
- ✅ 算术运算：`a+b`, `a*2`, `10%3`
- ✅ 逻辑运算：`a>50 ? "big" : "small"`
- ✅ 对象方法调用：`list.size()`

### 2. 安全SQL查询
- ✅ SQL函数调用：`sql("SELECT * FROM users")`
- ✅ SQL单值查询：`sqlValue("SELECT count(*) FROM users")`
- ✅ SQL注入防护：自动检测恶意模式
- ✅ DML/DDL拦截：禁止INSERT/UPDATE/DELETE/DROP等

### 3. 会话管理
- ✅ 分布式Redis存储
- ✅ 自动过期：30分钟无操作自动清理
- ✅ 本地内存降级：Redis不可用时自动切换
- ✅ 页面刷新保持：localStorage持久化SessionId
- ✅ 跨请求隔离：每个SessionId独立变量上下文

### 4. 安全机制
- ✅ MVEL沙箱：禁用Runtime/System/Process等危险API
- ✅ 执行超时：默认5秒，可配置
- ✅ 表达式长度限制：默认10000字符
- ✅ 输入验证：严格语法检查

---

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| **后端框架** | Spring Boot | 3.4.6 |
| **JDK** | OpenJDK | 21 |
| **表达式引擎** | MVEL2 | 2.5.2.Final |
| **数据库** | H2 | 2.2.224 |
| **缓存** | Redis | 6.0+ |
| **Redis客户端** | Lettuce | 6.5+ |
| **构建工具** | Maven | 3.8+ |

---

## API接口

### 执行表达式

**POST** `/etl/expression/execute`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| X-Session-Id | String | 否 | 会话ID，不传则自动创建 |
| 请求体 | String | 是 | 表达式字符串（text/plain） |

**响应示例**：
```json
{
  "sessionId": "ETL-1234567890ABCDEF",
  "originExpr": "a=100; b=a+1",
  "success": true,
  "finalResult": 101,
  "errorMsg": null,
  "contextVars": {"a": 100, "b": 101}
}
```

### 会话管理API

| 方法 | 路径 | 说明 |
|------|------|------|
| DELETE | `/etl/expression/session/{sessionId}` | 销毁指定会话 |
| GET | `/etl/expression/session/{sessionId}` | 获取会话信息 |
| POST | `/etl/expression/session/new` | 创建新会话 |
| GET | `/etl/expression/redis/sessions` | 查询Redis会话列表 |
| DELETE | `/etl/expression/redis/sessions` | 清理所有Redis会话 |

---

## 配置说明

### 核心配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| server.port | 8080 | 服务端口 |
| etl.engine.expression-timeout | 5000 | 表达式超时(ms) |
| etl.engine.max-expression-length | 10000 | 表达式最大长度 |
| etl.engine.enable-sql-execution | true | 启用SQL执行 |
| etl.engine.sql-readonly | true | SQL只读模式 |
| spring.data.redis.host | 127.0.0.1 | Redis地址 |
| spring.data.redis.port | 6379 | Redis端口 |

### 配置文件位置

- 开发环境：`src/main/resources/application.yml`
- 生产环境：JAR包同级目录`config/application.yml`

---

## 部署方案

### 本地开发
```bash
mvn spring-boot:run
```

### 单机部署
```bash
java --enable-preview -Xms256m -Xmx512m -jar etl-expression-engine-1.0.0.jar
```

### Docker部署
```bash
docker build -t etl-expression-engine:1.0.0 .
docker run -p 8080:8080 etl-expression-engine:1.0.0
```

### Docker Compose
```bash
docker-compose up -d
```

---

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
spring:
  data:
    redis:
      cluster:
        nodes: redis1:6379,redis2:6379,redis3:6379
```

### 自定义表达式超时
```yaml
etl:
  engine:
    expression-timeout: 10000 # 10秒
```

---

## 安全机制

### 禁止的关键字
- `import`, `package`, `class`, `interface`, `enum`
- `try`, `catch`, `finally`, `throw`, `throws`
- `synchronized`, `volatile`, `transient`, `native`, `strictfp`

### 禁止的类名
- `Runtime`, `System`, `ProcessBuilder`, `Process`
- `FileInputStream`, `FileOutputStream`, `FileReader`, `FileWriter`
- `HttpURLConnection`, `Socket`, `ServerSocket`
- `ClassLoader`, `ScriptEngine`, `java.sql.DriverManager`

### SQL安全限制
- 仅允许SELECT语句
- 禁止INSERT/UPDATE/DELETE/DROP/CREATE/ALTER等
- 拦截SQL注入特征（单引号、注释符、UNION等）

---

## 文档目录

| 文档 | 路径 | 说明 |
|------|------|------|
| 系统架构设计 | `docs/architecture.md` | 系统架构、组件说明、数据流 |
| API接口文档 | `docs/api.md` | 接口定义、参数、响应示例 |
| 安装部署指南 | `docs/deployment.md` | 环境要求、编译部署、配置说明 |
| 用户操作手册 | `docs/user-guide.md` | 使用说明、表达式语法、最佳实践 |
| 开发维护文档 | `docs/dev-guide.md` | 项目结构、扩展开发、测试指南 |

---

## 贡献指南

欢迎提交Issue和Pull Request！

- **Bug报告**：请提供复现步骤和错误日志
- **功能建议**：请描述使用场景和预期效果
- **代码贡献**：请遵循Java开发规范，添加单元测试

---

© 2026 ETL Expression Engine. All rights reserved.