# ETL Expression Engine - 系统架构设计文档

## 1. 系统概述

ETL Expression Engine（ETL流程编排专用表达式参数引擎）是一个面向ETL数据集成场景的表达式执行服务。系统提供安全的MVEL表达式运行环境，支持在表达式内直接调用SQL查询函数，实现数据转换逻辑的动态编排与参数化配置。

核心设计目标：

- **安全可控**：三层安全防护机制，确保表达式执行不会对系统造成危害
- **高性能**：基于JDK 21虚拟线程实现轻量级并发，避免传统线程池的资源开销
- **会话隔离**：每个用户会话拥有独立的变量上下文，互不干扰
- **弹性部署**：支持Redis分布式存储和本地内存降级两种会话管理模式

## 2. 技术栈

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 运行时 | JDK | 21 | 使用虚拟线程、ScopedValue、Record等新特性 |
| 框架 | Spring Boot | 3.4.6 | Web服务、依赖注入、自动配置 |
| 表达式引擎 | MVEL2 | 2.5.2.Final | 表达式编译与执行 |
| 数据库 | H2 | 2.2.224 | 嵌入式内存数据库，用于SQL查询演示 |
| 缓存 | Redis | 6.0+ | 分布式会话存储（可选） |
| Redis客户端 | Lettuce | - | 基于Netty的异步Redis客户端 |
| 构建工具 | Maven | 3.8+ | 项目构建与依赖管理 |

## 3. 整体架构图

```
+------------------------------------------------------------------+
|                        ETL Expression Engine                      |
|                                                                   |
|  +------------------------------------------------------------+  |
|  |                    Presentation Layer                       |  |
|  |  +-------------------+    +-----------------------------+  |  |
|  |  |   Web Console     |    |    REST API                  |  |  |
|  |  |   (index.html)    |    |  POST /etl/expression/execute|  |  |
|  |  +--------+----------+    +--------------+----------------+  |  |
|  +-----------|------------------------------|-----------------+  |
|              |                              |                    |
|  +-----------v------------------------------v-----------------+  |
|  |                    Controller Layer                         |  |
|  |  +-------------------------------------------------------+ |  |
|  |  |  EtlExpressionController                              | |  |
|  |  |  - 接收表达式请求                                       | |  |
|  |  |  - 会话管理(X-Session-Id)                              | |  |
|  |  |  - 返回ExecuteResult                                   | |  |
|  |  +-----+-----------------------------+-------------------+ |  |
|  +--------|-----------------------------|---------------------+  |
|           |                             |                        |
|  +--------v-----------------------------v---------------------+  |
|  |                    Engine Layer                              |  |
|  |                                                             |  |
|  |  +-----------------------+    +--------------------------+ |  |
|  |  | MvelExpressionEngine  |    | MvelSecuritySandbox      | |  |
|  |  | - 表达式编译执行       |    | - 关键字黑名单过滤        | |  |
|  |  | - 虚拟线程调度         |    | - 危险类名拦截            | |  |
|  |  | - ScopedValue上下文    |    | - 安全ParserContext       | |  |
|  |  | - 多行表达式解析       |    +--------------------------+ |  |
|  |  | - 变量赋值追踪         |                                 |  |
|  |  +-----+-----------+------+                                 |  |
|  |        |           |                                        |  |
|  |  +-----v-----+ +---v-----------+                            |  |
|  |  | SqlFunction| |SqlExecuteEngine|                          |  |
|  |  | - sql()    | |- SELECT只读   |                          |  |
|  |  | - sqlValue()| |- SQL注入防护  |                          |  |
|  |  +-----+------+ +-+--+---------+                           |  |
|  +--------|-----------|--|------------------------------------+  |
|           |           |  |                                       |
|  +--------v-----------v--v------------------------------------+  |
|  |                 Infrastructure Layer                        |  |
|  |                                                             |  |
|  |  +----------------+  +---------------+  +---------------+ |  |
|  |  |  EtlContext    |  | EtlContext    |  |  H2 Database  | |  |
|  |  |  Manager       |  | (ScopedValue) |  |  (JdbcTemplate)| |  |
|  |  | - Redis存储    |  | - 变量容器    |  | - 内存模式     | |  |
|  |  | - 本地降级     |  | - 会话隔离    |  | - 初始数据     | |  |
|  |  +-------+--------+  +-------+-------+  +-------+-------+ |  |
|  |          |                     |                  |         |  |
|  |  +-------v--------+           |          +-------v-------+ |  |
|  |  |     Redis      |           |          |  DataSource    | |  |
|  |  |  (Lettuce)     |           |          |    Config      | |  |
|  |  +----------------+           |          +---------------+ |  |
|  +-------------------------------|-----------------------------+  |
|                                  |                                |
+----------------------------------|--------------------------------+
                                   |
                          +--------v--------+
                          |   EngineProperties|
                          |   (配置属性)      |
                          +-----------------+
```

## 4. 核心组件说明

### 4.1 MvelExpressionEngine - 表达式执行核心

**所在包**: `com.etl.engine.mvel`

MvelExpressionEngine是整个系统的核心执行引擎，负责接收表达式字符串，完成安全校验、编译执行和结果返回的完整流程。

**关键设计**：

- **虚拟线程调度**：使用`Executors.newVirtualThreadPerTaskExecutor()`创建虚拟线程执行表达式，每次请求分配一个虚拟线程，避免平台线程的资源消耗。虚拟线程由JVM调度，挂起时不占用OS线程，适合I/O密集型的SQL查询场景。
- **ScopedValue上下文传递**：使用JDK 21引入的`ScopedValue`替代传统的`ThreadLocal`，实现会话上下文在虚拟线程间的安全传递。`ScopedValue`是不可变的、有界的作用域值，避免了`ThreadLocal`在虚拟线程池中的内存泄漏风险。

**执行流程**：

```
execute(expression, context)
    |
    +-- 1. 空表达式检查
    +-- 2. 表达式长度检查 (maxExpressionLength)
    +-- 3. 安全沙箱检查 (MvelSecuritySandbox.isExpressionSafe)
    +-- 4. executeWithTimeout()
            |
            +-- 提交虚拟线程任务
            +-- ScopedValue.where(CURRENT_CONTEXT, context).call()
            +-- executeInternal()
            |       |
            |       +-- 按";"分割多行表达式
            |       +-- 逐行执行 executeSingleExpression()
            |       +-- 识别赋值语句，更新上下文变量
            |       +-- 返回最后一行结果
            |
            +-- future.get(timeout) 超时控制
```

### 4.2 MvelSecuritySandbox - 安全沙箱

**所在包**: `com.etl.engine.mvel`

MvelSecuritySandbox负责表达式安全校验，是系统安全防护的第一道屏障。

**防护策略**：

- **关键字黑名单**：拦截Java语言级别的危险关键字，包括`import`、`package`、`new`、`class`、`try`、`catch`、`throw`等，防止用户通过表达式执行任意Java代码。
- **危险类名拦截**：阻止访问系统级类，包括`Runtime`、`System`、`ProcessBuilder`、`FileInputStream`、`Socket`、`ClassLoader`、`ScriptEngine`等，防止文件操作、网络访问、进程执行等危险行为。
- **安全ParserContext**：创建MVEL解析上下文时启用严格类型检查（`setStrictTypeEnforcement(true)`），关闭强类型推断（`setStrongTyping(false)`），在灵活性与安全性之间取得平衡。

**校验规则**：

| 检查类型 | 检查内容 | 匹配方式 |
|----------|----------|----------|
| 关键字 | import, package, class, interface, enum, try, catch, finally, throw, throws, synchronized, volatile, transient, native, strictfp | 正则单词边界匹配 |
| 类名 | Runtime, System, ProcessBuilder, Process, FileInputStream, FileOutputStream, FileReader, FileWriter, HttpURLConnection, Socket, ServerSocket, ClassLoader, getRuntime, exit, halt, java.lang.reflect, Method#invoke, Constructor#newInstance, ScriptEngine, javax.script, java.sql.DriverManager, java.io, java.net, java.nio.file | 大小写不敏感包含匹配 |

### 4.3 SqlFunction - SQL函数注册

**所在包**: `com.etl.engine.mvel`

SqlFunction是MVEL表达式与SQL执行引擎之间的桥梁，通过静态方法将SQL查询能力暴露给表达式上下文。

**注册机制**：

在MvelExpressionEngine的`evaluateExpression()`方法中，通过`ParserContext.addImport()`将SqlFunction的静态方法注册为MVEL内置函数：

```java
parserContext.addImport("sql", SqlFunction.class.getMethod("sql", String.class));
parserContext.addImport("sqlValue", SqlFunction.class.getMethod("sqlValue", String.class));
```

**函数说明**：

| 函数 | 签名 | 返回值 | 说明 |
|------|------|--------|------|
| sql | `sql(String sqlExpression)` | `List<Map<String, Object>>` | 执行SQL查询，返回完整结果集 |
| sqlValue | `sqlValue(String sqlExpression)` | `Object` | 执行SQL查询，返回第一行第一列的单值 |

**初始化**：SqlFunction通过`init(SqlExecuteEngine)`方法注入SQL执行引擎实例，采用静态持有模式，确保在MVEL表达式执行时可以直接调用。

### 4.4 SqlExecuteEngine - SQL执行引擎

**所在包**: `com.etl.engine.sql`

SqlExecuteEngine提供安全的只读SQL查询能力，是系统安全防护的第二道屏障。

**安全机制**：

- **SELECT白名单**：强制要求所有SQL语句必须以`SELECT`开头，拒绝任何DML/DDL操作。
- **关键字黑名单**：拦截INSERT、UPDATE、DELETE、DROP、ALTER、CREATE、TRUNCATE、REPLACE、MERGE、GRANT、REVOKE、EXEC、EXECUTE、CALL、INTO、SET等危险关键字。
- **SQL注入防护**：通过正则表达式检测单引号、双横线注释、分号、管道符、注释符号、UNION、OUTFILE等SQL注入特征。

**校验流程**：

```
executeQuery(sql)
    |
    +-- 1. 空值检查
    +-- 2. validateSql()
    |       +-- 必须以SELECT开头
    |       +-- 关键字黑名单检查 (正则单词边界匹配)
    |       +-- SQL注入模式检测
    +-- 3. JdbcTemplate.queryForList() 执行查询
    +-- 4. 返回 List<Map<String, Object>>
```

### 4.5 EtlContextManager - 会话管理器

**所在包**: `com.etl.engine.context`

EtlContextManager负责会话的完整生命周期管理，支持Redis分布式存储和本地内存降级两种模式。

**双模式架构**：

```
+-------------------+     Redis可用      +-------------------+
|                   | -----------------> |      Redis        |
| EtlContextManager |                    |  etl:context:     |
|                   | <----------------- |  {sessionId}      |
+-------------------+     Redis不可用    +-------------------+
        |
        | 降级
        v
+-------------------+
|  localSessions    |
|  ConcurrentHashMap|
+-------------------+
```

**关键特性**：

- **Redis优先策略**：当Redis可用时，所有会话操作优先通过Redis执行，实现分布式会话共享。
- **自动降级**：当Redis操作失败时，自动降级为本地ConcurrentHashMap存储，确保服务可用性。
- **会话超时清理**：每5分钟执行一次本地会话清理，超过30分钟未访问的会话自动销毁。
- **Redis TTL**：Redis中的会话键设置30分钟过期时间，每次访问自动续期。
- **会话ID格式**：`ETL-{16位大写十六进制}`，例如`ETL-A1B2C3D4E5F6G7H8`。

### 4.6 EtlContext - 会话上下文

**所在包**: `com.etl.engine.context`

EtlContext是会话级别的变量容器，实现`Serializable`接口以支持Redis序列化存储。

**数据结构**：

| 字段 | 类型 | 说明 |
|------|------|------|
| sessionId | String | 会话唯一标识 |
| variables | ConcurrentHashMap<String, Object> | 变量存储容器 |
| createTime | long | 会话创建时间戳 |
| lastAccessTime | volatile long | 最后访问时间戳（用于超时判断） |

**线程安全**：使用`ConcurrentHashMap`存储变量，`volatile`修饰`lastAccessTime`，确保多线程环境下的可见性和原子性。所有读写操作自动更新`lastAccessTime`。

### 4.7 EtlContextScope - 作用域工具

**所在包**: `com.etl.engine.context`

EtlContextScope使用JDK 21的`ScopedValue`实现跨组件的上下文透传，是替代`ThreadLocal`的现代化方案。

**设计优势**：

- **不可变性**：ScopedValue绑定后不可修改，避免了ThreadLocal的随意set导致的状态不一致问题。
- **自动清理**：ScopedValue在作用域结束后自动解绑，无需手动remove，避免内存泄漏。
- **虚拟线程兼容**：ScopedValue与虚拟线程完美配合，在线程挂起和恢复时正确传递上下文。

**使用方式**：

```java
ScopedValue.where(EtlContextScope.CURRENT_CONTEXT, context)
    .call(() -> executeInternal(expression, context));
```

在表达式执行的整个调用链中，任何组件都可以通过`EtlContextScope.getCurrentContext()`获取当前会话上下文。

## 5. 数据流图

### 5.1 请求处理主流程

```
浏览器/客户端
    |
    | POST /etl/expression/execute
    | Content-Type: text/plain
    | X-Session-Id: ETL-XXXXXXXXXXXXXXXX
    |
    v
EtlExpressionController
    |
    +-- 获取/创建 EtlContext (通过EtlContextManager)
    |
    v
MvelExpressionEngine.execute(expression, context)
    |
    +-- 安全校验 (MvelSecuritySandbox)
    |
    +-- 虚拟线程提交
    |       |
    |       +-- ScopedValue绑定上下文
    |       |
    |       v
    |   executeInternal()
    |       |
    |       +-- 分号分割多行表达式
    |       |
    |       +-- 逐行执行
    |       |       |
    |       |       +-- 赋值语句: 解析变量名 -> 执行右值 -> 存入EtlContext
    |       |       |
    |       |       +-- 普通表达式: MVEL编译执行
    |       |               |
    |       |               +-- 注册sql/sqlValue函数到ParserContext
    |       |               +-- 注入上下文变量到执行环境
    |       |               +-- MVEL.compileExpression() + executeExpression()
    |       |               |
    |       |               +-- [如果调用sql函数]
    |       |                       |
    |       |                       v
    |       |                   SqlFunction.sql/sqlValue()
    |       |                       |
    |       |                       v
    |       |                   SqlExecuteEngine.executeQuery()
    |       |                       |
    |       |                       +-- SQL安全校验
    |       |                       +-- JdbcTemplate.queryForList()
    |       |                       +-- 返回查询结果
    |       |
    |       +-- 收集赋值变量
    |       +-- 返回ExecuteResult
    |
    +-- 超时控制 (Future.get with timeout)
    |
    +-- 更新会话到Redis (EtlContextManager.updateSession)
    |
    v
ExecuteResult (Record)
    {
        sessionId, originExpr, success,
        finalResult, errorMsg, contextVars
    }
```

### 5.2 会话管理数据流

```
请求到达 (携带X-Session-Id)
    |
    v
SessionId为空?
    |
    +-- 是: EtlContextManager.createSession()
    |       |
    |       +-- 生成SessionId
    |       +-- 创建EtlContext
    |       +-- Redis可用? -> Redis SET (TTL 30min)
    |       +-- Redis不可用? -> localSessions.put()
    |
    +-- 否: EtlContextManager.getSession(sessionId)
            |
            +-- Redis可用? -> Redis GET + 续期TTL
            +-- Redis失败/不可用? -> localSessions.get()
            +-- 会话不存在? -> 创建新会话

表达式执行完成
    |
    v
EtlContextManager.updateSession(context)
    |
    +-- Redis可用? -> Redis SET (TTL 30min)
    +-- Redis失败/不可用? -> localSessions.put()
```

## 6. 安全架构

系统采用三层纵深防护体系，确保表达式执行的安全性：

### 6.1 第一层：MVEL沙箱关键字过滤

```
表达式输入
    |
    v
MvelSecuritySandbox.isExpressionSafe()
    |
    +-- 关键字黑名单 (15项)
    |   import, package, class, interface, enum,
    |   try, catch, finally, throw, throws,
    |   synchronized, volatile, transient, native, strictfp
    |
    +-- 危险类名黑名单 (25项)
    |   Runtime, System, ProcessBuilder, Process,
    |   FileInputStream, FileOutputStream, ...
    |   java.io, java.net, java.nio.file, ...
    |
    +-- 通过 -> 进入下一层
    +-- 拦截 -> 返回"表达式包含禁止的内容"
```

### 6.2 第二层：执行超时控制

```
通过安全检查的表达式
    |
    v
虚拟线程提交执行
    |
    v
Future.get(expressionTimeout)
    |
    +-- 正常完成 -> 返回结果
    +-- TimeoutException -> 返回超时错误
    +-- ExecutionException -> 返回执行异常
    +-- InterruptedException -> 返回中断错误
```

超时时间通过`etl.engine.expression-timeout`配置，默认5000ms。

### 6.3 第三层：SQL只读限制

```
表达式调用sql/sqlValue函数
    |
    v
SqlExecuteEngine.executeQuery()
    |
    +-- 必须以SELECT开头
    +-- DML/DDL关键字黑名单 (16项)
    +-- SQL注入模式检测
    +-- JdbcTemplate只读查询
    |
    +-- 通过 -> 执行查询
    +-- 拦截 -> 抛出SecurityException
```

### 6.4 安全防护总结

| 防护层 | 防护目标 | 实现方式 | 配置项 |
|--------|----------|----------|--------|
| 第一层 | 防止恶意代码执行 | 关键字+类名黑名单 | - |
| 第二层 | 防止资源耗尽攻击 | 虚拟线程+超时控制 | expression-timeout |
| 第三层 | 防止数据篡改 | SQL只读+注入防护 | sql-readonly, enable-sql-execution |
| 附加 | 防止超大表达式 | 表达式长度限制 | max-expression-length |

## 7. 会话管理架构

### 7.1 Redis分布式模式

```
+----------+     +----------+     +----------+
| 实例 A   |     | 实例 B   |     | 实例 C   |
| EtlContext|    | EtlContext|    | EtlContext|
| Manager  |     | Manager  |     | Manager  |
+-----+----+     +-----+----+     +-----+----+
      |                |                |
      +----------------+----------------+
                       |
                       v
              +------------------+
              |     Redis        |
              | etl:context:     |
              | {sessionId}      |
              | TTL: 30min       |
              +------------------+
```

**优势**：
- 多实例间会话共享，支持负载均衡
- Redis持久化保障会话数据安全
- 自动TTL过期，无需手动清理

### 7.2 本地内存降级模式

```
+----------+
| 单实例   |
| EtlContext|
| Manager  |
+-----+----+
      |
      v
+------------------+
| localSessions    |
| ConcurrentHashMap|
| 定时清理(5min)   |
| 超时销毁(30min)  |
+------------------+
```

**触发条件**：
- Redis连接失败
- RedisTemplate未配置
- Redis操作异常

**降级策略**：
- 所有Redis操作自动切换为本地ConcurrentHashMap操作
- 通过定时任务（5分钟间隔）清理超时会话
- 会话数据仅在当前JVM内可见，不支持分布式

### 7.3 会话生命周期

```
创建 -> 使用中 -> 超时/销毁
 |        |          |
 v        v          v
createSession   getSession   destroySession
  |              |  |            |
  v              v  v            v
Redis SET    Redis GET    Redis DELETE
  + TTL      + 续期TTL    + localRemove
  + localPut  + localGet
```

## 8. 配置属性

系统通过`EngineProperties`类统一管理引擎配置，前缀为`etl.engine`：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| expression-timeout | 5000 | 表达式执行超时时间（毫秒） |
| max-expression-length | 10000 | 表达式最大长度（字符数） |
| enable-sql-execution | true | 是否启用SQL执行功能 |
| sql-readonly | true | SQL是否只读模式 |
