# ETL Expression Engine - 开发维护文档

## 1. 项目结构说明

### 1.1 目录结构

```
etl-expression-engine/
|-- pom.xml                              # Maven项目配置
|-- etl-engine.bat                       # Windows启动/停止脚本
|-- start.bat                            # 快速启动脚本
|-- README.md                            # 项目说明
|-- docs/                                # 项目文档
|   |-- architecture.md                  # 系统架构设计文档
|   |-- api.md                           # API接口文档
|   |-- deployment.md                    # 安装部署指南
|   |-- user-guide.md                    # 用户操作手册
|   |-- dev-guide.md                     # 开发维护文档
|-- src/
    |-- main/
    |   |-- java/com/etl/engine/
    |   |   |-- EtlExpressionEngineApplication.java   # Spring Boot启动类
    |   |   |-- config/                               # 配置包
    |   |   |   |-- DataSourceConfig.java             # 数据源配置
    |   |   |   |-- EngineProperties.java             # 引擎配置属性
    |   |   |   |-- RedisConfig.java                  # Redis配置
    |   |   |-- context/                              # 上下文包
    |   |   |   |-- ContextHolder.java                # ThreadLocal上下文持有者
    |   |   |   |-- EtlContext.java                    # 会话上下文（ScopedValue版）
    |   |   |   |-- EtlContextManager.java             # 会话管理器（Redis+本地双模式）
    |   |   |   |-- EtlContextScope.java               # ScopedValue作用域工具
    |   |   |   |-- SessionContext.java                # 会话变量上下文（ThreadLocal版）
    |   |   |   |-- SessionContextManager.java         # 会话上下文管理器（本地版）
    |   |   |-- controller/                           # 控制器包
    |   |   |   |-- EtlExpressionController.java      # 表达式执行REST控制器
    |   |   |-- engine/                               # 引擎包（旧版，ThreadLocal实现）
    |   |   |   |-- MvelSandboxEngine.java            # MVEL沙箱引擎（旧版）
    |   |   |   |-- SqlExecutionEngine.java           # SQL执行引擎（旧版）
    |   |   |-- model/                                # 数据模型包
    |   |   |   |-- EtlTaskVo.java                    # ETL任务值对象
    |   |   |   |-- ExecuteResult.java                # HTTP执行结果（Record）
    |   |   |   |-- ExpressionResult.java             # 表达式执行结果（Record）
    |   |   |   |-- SqlQueryResult.java               # SQL查询结果（Record）
    |   |   |-- mvel/                                 # MVEL核心包（当前版本，ScopedValue实现）
    |   |   |   |-- MvelExpressionEngine.java         # 表达式执行引擎
    |   |   |   |-- MvelSecuritySandbox.java          # 安全沙箱
    |   |   |   |-- SqlFunction.java                  # SQL函数桥接
    |   |   |   |-- HttpFunction.java                 # HTTP请求函数桥接
    |   |   |-- http/                                 # HTTP请求包
    |   |   |   |-- HttpRequestBuilder.java           # HTTP请求构建器接口
    |   |   |   |-- HttpRequestBuilderImpl.java       # HTTP请求构建器实现
    |   |   |   |-- HttpResponse.java                 # HTTP响应接口
    |   |   |   |-- HttpResponseImpl.java             # HTTP响应实现
    |   |   |   |-- HttpClientAdapter.java            # HTTP客户端适配器接口
    |   |   |   |-- JavaHttpClientAdapter.java        # JDK HttpClient适配器
    |   |   |   |-- AsyncHttpRequest.java             # 异步请求接口
    |   |   |   |-- HttpInterceptor.java              # HTTP拦截器
    |   |   |   |-- HttpException.java                # HTTP异常类
    |   |-- sql/                                  # SQL执行包（当前版本）
    |   |   |   |-- SqlExecuteEngine.java             # SQL执行引擎
    |   |   |-- util/                                 # 工具类包
    |   |       |-- DateTimeUtils.java                # 日期时间工具
    |   |       |-- StringUtils.java                  # 字符串工具
    |   |-- resources/
    |       |-- application.yml                       # 主配置文件
    |       |-- data.sql                              # H2初始化数据脚本
    |       |-- static/
    |           |-- index.html                        # Web控制台页面
    |-- test/
        |-- java/com/etl/engine/
        |   |-- ContextHolderTest.java                # ContextHolder单元测试
        |   |-- EtlExpressionControllerTest.java      # 控制器集成测试
        |   |-- MvelExpressionEngineIntegrationTest.java  # 表达式引擎集成测试
        |   |-- MvelSandboxEngineTest.java            # 沙箱引擎单元测试
        |   |-- RedisSerializationTest.java           # Redis序列化测试
        |   |-- SessionContextManagerTest.java        # 会话管理器单元测试
        |   |-- SqlExecutionEngineTest.java           # SQL执行引擎单元测试
        |   |-- SqlFunctionTest.java                  # SQL函数单元测试
        |   |-- TestConfig.java                       # 测试配置类
        |-- resources/
            |-- application-test.yml                  # 测试环境配置
```

### 1.2 包职责说明

| 包名 | 职责 | 说明 |
|------|------|------|
| config | 配置管理 | 数据源、Redis、引擎属性的配置类 |
| context | 上下文管理 | 会话上下文的创建、存储、传递和销毁 |
| controller | 请求处理 | HTTP接口的接收和响应 |
| engine | 旧版引擎 | 基于ThreadLocal的实现，保留作为参考 |
| model | 数据模型 | 不可变Record类和值对象 |
| mvel | MVEL核心 | 当前版本的表达式引擎、安全沙箱、SQL函数桥接和HTTP函数桥接 |
| http | HTTP请求 | MVEL表达式内的HTTP请求功能，链式API设计 |
| sql | SQL执行 | 当前版本的SQL执行引擎 |
| util | 工具类 | 通用工具方法 |

### 1.3 双版本说明

项目中存在两套并行的引擎实现：

| 版本 | 包 | 上下文传递 | 会话管理 | 状态 |
|------|-----|-----------|----------|------|
| 当前版本 | mvel + sql | ScopedValue | EtlContextManager（Redis+本地） | 活跃 |
| 旧版 | engine | ThreadLocal | SessionContextManager（本地） | 保留 |

当前版本使用JDK 21的`ScopedValue`替代`ThreadLocal`，使用虚拟线程替代线程池，是推荐使用的版本。Controller层当前使用的是`mvel`包中的`MvelExpressionEngine`。

## 2. 核心类说明

### 2.1 MvelExpressionEngine

**文件**: `com.etl.engine.mvel.MvelExpressionEngine`

表达式执行的核心引擎，负责整个表达式执行的生命周期管理。

**核心方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| execute | `ExecuteResult execute(String expression, EtlContext context)` | 表达式执行入口，完成安全检查、超时控制和结果返回 |
| executeWithTimeout | `ExecuteResult executeWithTimeout(String expression, EtlContext context)` | 通过虚拟线程+Future实现超时控制 |
| executeInternal | `ExecuteResult executeInternal(String expression, EtlContext context)` | 多行表达式解析和逐行执行 |
| executeSingleExpression | `Object executeSingleExpression(String expression, EtlContext context)` | 单行表达式执行，区分赋值语句和普通表达式 |
| evaluateExpression | `Object evaluateExpression(String expression, EtlContext context)` | MVEL编译执行，注册内置函数和注入上下文变量 |

**依赖关系**：

- EngineProperties：读取超时时间和表达式长度限制
- MvelSecuritySandbox：表达式安全校验
- SqlExecuteEngine：SQL执行能力（通过SqlFunction桥接）

### 2.2 MvelSecuritySandbox

**文件**: `com.etl.engine.mvel.MvelSecuritySandbox`

安全沙箱，负责表达式内容的安全校验。

**核心方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| isExpressionSafe | `boolean isExpressionSafe(String expression)` | 检查表达式是否安全，返回false表示包含禁止内容 |
| createSafeParserContext | `ParserContext createSafeParserContext()` | 创建安全的MVEL解析上下文 |

**安全规则**：

- FORBIDDEN_KEYWORDS：15项Java关键字黑名单
- FORBIDDEN_CLASSES：25项危险类名黑名单
- 关键字使用正则单词边界匹配（`\b`），避免误判
- 类名使用大小写不敏感的包含匹配

### 2.3 SqlFunction

**文件**: `com.etl.engine.mvel.SqlFunction`

SQL函数桥接类，将SqlExecuteEngine的查询能力暴露给MVEL表达式。

**核心方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| init | `void init(SqlExecuteEngine engine)` | 注入SQL执行引擎实例 |
| sql | `List<Map<String, Object>> sql(String sqlExpression)` | 执行SQL查询，返回完整结果集 |
| sqlValue | `Object sqlValue(String sqlExpression)` | 执行SQL查询，返回第一行第一列 |

**设计要点**：

- 私有构造函数，仅暴露静态方法
- 通过`init()`方法注入依赖，避免Spring循环依赖
- 静态持有SqlExecuteEngine实例，在MVEL表达式执行时可直接调用

### 2.4 HttpFunction

**文件**: `com.etl.engine.mvel.HttpFunction`

HTTP请求函数桥接类，将HTTP客户端能力暴露给MVEL表达式。

**核心方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| init | `void init()` | 初始化HTTP客户端 |
| init | `void init(HttpClientAdapter adapter)` | 使用自定义客户端适配器初始化 |
| httpRequest | `HttpRequestBuilder httpRequest(String url)` | 创建HTTP请求构建器 |
| http | `HttpRequestBuilder http(String url)` | httpRequest的别名 |
| shutdown | `void shutdown()` | 关闭HTTP客户端 |

**使用示例**：

```java
// MVEL表达式中的HTTP请求
httpRequest("https://api.example.com/users")
    .header("Authorization", "Bearer token")
    .queryVariable("page", 1)
    .timeout(5000)
    .get()
    .asJson()

// POST JSON数据
httpRequest("https://api.example.com/users")
    .bodyJson(Map.of("name", "test", "age", 25))
    .post()
    .asMap()

// 异步请求
httpRequest("https://api.example.com/data")
    .asyncGet()
    .thenAccept(response -> {
        // 处理响应
    })
```

### 2.5 HttpRequestBuilder

**文件**: `com.etl.engine.http.HttpRequestBuilder`

HTTP请求构建器接口，提供链式API配置请求参数。

**核心方法**：

| 方法 | 说明 |
|------|------|
| header(String key, String value) | 设置请求头 |
| headers(Map<String, String> headers) | 批量设置请求头 |
| body(Object data) | 设置请求体（自动JSON序列化） |
| bodyJson(Object data) | 设置JSON请求体 |
| bodyForm(Map<String, String> formData) | 设置表单请求体 |
| pathVariables(Map<String, Object> variables) | 设置路径参数 |
| queryVariables(Map<String, Object> variables) | 设置查询参数 |
| timeout(int millis) | 设置超时时间 |
| basicAuth(String username, String password) | 设置Basic认证 |
| bearerAuth(String token) | 设置Bearer认证 |
| retry(int maxRetries) | 设置重试次数 |
| interceptor(HttpInterceptor interceptor) | 添加拦截器 |
| get() / post() / put() / delete() / patch() | 执行请求 |
| asyncGet() / asyncPost() | 异步执行请求 |

### 2.6 HttpResponse

**文件**: `com.etl.engine.http.HttpResponse`

HTTP响应接口，提供多种响应解析方式。

**核心方法**：

| 方法 | 说明 |
|------|------|
| statusCode() | 获取状态码 |
| isSuccess() | 是否成功(2xx) |
| headers() | 获取响应头 |
| asString() | 获取原始字符串 |
| asJson() | 解析为JsonNode |
| asMap() | 解析为Map |
| asXml() | 解析为XML Document |
| asBean(Class<T> clazz) | 解析为JavaBean |
| custom(Function<String, T> parser) | 自定义解析 |

### 2.7 SqlExecuteEngine

**文件**: `com.etl.engine.sql.SqlExecuteEngine`

SQL执行引擎，提供安全的只读查询能力。

**核心方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| executeQuery | `Object executeQuery(String sql)` | 执行SQL查询，返回List<Map<String, Object>> |
| validateSql | `boolean validateSql(String sql)` | SQL安全校验（私有方法） |

**安全机制**：

- 强制SELECT开头
- 16项DML/DDL关键字黑名单
- SQL注入正则模式检测

### 2.5 EtlContextManager

**文件**: `com.etl.engine.context.EtlContextManager`

会话管理器，支持Redis分布式存储和本地内存降级。

**核心方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| createSession | `EtlContext createSession()` | 创建新会话 |
| getSession | `EtlContext getSession(String sessionId)` | 获取会话 |
| updateSession | `void updateSession(EtlContext context)` | 更新会话 |
| destroySession | `void destroySession(String sessionId)` | 销毁会话 |
| hasSession | `boolean hasSession(String sessionId)` | 检查会话是否存在 |
| getActiveSessionCount | `int getActiveSessionCount()` | 获取活跃会话数 |
| shutdown | `void shutdown()` | 关闭管理器 |

**Redis Key格式**：`etl:context:{sessionId}`

**TTL**：30分钟，每次访问自动续期

### 2.6 EtlContext

**文件**: `com.etl.engine.context.EtlContext`

会话上下文，实现`Serializable`接口以支持Redis序列化。

**核心方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| setVariable | `void setVariable(String name, Object value)` | 设置变量 |
| getVariable | `Object getVariable(String name)` | 获取变量 |
| getAllVariables | `Map<String, Object> getAllVariables()` | 获取所有变量（不可变视图） |
| clearVariables | `void clearVariables()` | 清空所有变量 |

### 2.7 EtlContextScope

**文件**: `com.etl.engine.context.EtlContextScope`

ScopedValue作用域工具，实现虚拟线程间的上下文传递。

**核心字段**：

```java
public static final ScopedValue<EtlContext> CURRENT_CONTEXT = ScopedValue.newInstance();
```

**核心方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| getCurrentContext | `EtlContext getCurrentContext()` | 获取当前上下文 |
| hasCurrentContext | `boolean hasCurrentContext()` | 检查上下文是否存在 |
| getCurrentSessionId | `String getCurrentSessionId()` | 获取当前会话ID |
| getVariable | `Object getVariable(String name)` | 获取当前上下文变量 |
| setVariable | `void setVariable(String name, Object value)` | 设置当前上下文变量 |

### 2.8 ExecuteResult

**文件**: `com.etl.engine.model.ExecuteResult`

HTTP执行结果，使用JDK 21 Record实现不可变结构。

**字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| sessionId | String | 会话ID |
| originExpr | String | 原始表达式 |
| success | boolean | 是否成功 |
| finalResult | Object | 最终结果 |
| errorMsg | String | 错误信息 |
| contextVars | Map<String, Object> | 上下文变量 |

**工厂方法**：

- `ExecuteResult.success(sessionId, originExpr, finalResult, contextVars)`
- `ExecuteResult.failure(sessionId, originExpr, errorMsg, contextVars)`

### 2.9 EngineProperties

**文件**: `com.etl.engine.config.EngineProperties`

引擎配置属性，前缀`etl.engine`。

**属性**：

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| expressionTimeout | long | 5000 | 表达式超时时间（毫秒） |
| maxExpressionLength | int | 10000 | 表达式最大长度 |
| enableSqlExecution | boolean | true | 是否启用SQL执行 |
| sqlReadonly | boolean | true | SQL只读模式 |

### 2.10 EtlExpressionController

**文件**: `com.etl.engine.controller.EtlExpressionController`

REST控制器，提供表达式执行的HTTP入口。

**接口**：

- `POST /etl/expression/execute`：执行表达式
  - 请求头：`X-Session-Id`（可选）
  - 请求体：`text/plain` 表达式字符串
  - 响应：`ExecuteResult` JSON

## 3. 扩展开发指南

### 3.1 添加新的MVEL内置函数

如需在表达式中添加新的内置函数，按以下步骤操作：

#### 步骤一：创建函数类

在`com.etl.engine.mvel`包下创建新的函数类，参考SqlFunction的实现模式：

```java
package com.etl.engine.mvel;

public class CustomFunction {

    private static CustomDependency dependency;

    private CustomFunction() {
    }

    public static void init(CustomDependency dep) {
        dependency = dep;
    }

    public static String myFunction(String input) {
        if (dependency == null) {
            throw new IllegalStateException("依赖未初始化");
        }
        return dependency.process(input);
    }
}
```

**设计要点**：

- 私有构造函数，仅暴露静态方法
- 通过`init()`方法注入Spring管理的依赖
- 方法必须是public static，且参数和返回值类型可序列化

#### 步骤二：注册函数到MVEL上下文

在`MvelExpressionEngine.evaluateExpression()`方法中添加函数注册：

```java
private Object evaluateExpression(String expression, EtlContext context) {
    ParserContext parserContext = mvelSecuritySandbox.createSafeParserContext();

    try {
        parserContext.addImport("sql", SqlFunction.class.getMethod("sql", String.class));
        parserContext.addImport("sqlValue", SqlFunction.class.getMethod("sqlValue", String.class));
        // 注册新函数
        parserContext.addImport("myFunction", CustomFunction.class.getMethod("myFunction", String.class));
    } catch (NoSuchMethodException e) {
        throw new IllegalStateException("函数注册失败", e);
    }

    // ... 后续代码不变
}
```

#### 步骤三：初始化依赖

在`MvelExpressionEngine`构造函数中初始化新函数的依赖：

```java
public MvelExpressionEngine(EngineProperties properties,
                           MvelSecuritySandbox mvelSecuritySandbox,
                           SqlExecuteEngine sqlExecuteEngine,
                           CustomDependency customDependency) {
    this.properties = properties;
    this.mvelSecuritySandbox = mvelSecuritySandbox;
    this.sqlExecuteEngine = sqlExecuteEngine;
    SqlFunction.init(sqlExecuteEngine);
    CustomFunction.init(customDependency);
}
```

#### 步骤四：更新安全沙箱

如果新函数的名称与某些安全规则冲突，需要在`MvelSecuritySandbox`中调整规则。

#### 步骤五：编写测试

参考`SqlFunctionTest.java`编写单元测试。

### 3.2 扩展安全规则

#### 添加新的禁止关键字

在`MvelSecuritySandbox.FORBIDDEN_KEYWORDS`列表中添加新的关键字：

```java
private static final List<String> FORBIDDEN_KEYWORDS = List.of(
    "import", "package", "class", "interface", "enum",
    "try", "catch", "finally", "throw", "throws",
    "synchronized", "volatile", "transient", "native", "strictfp",
    // 新增关键字
    "assert", "break", "continue", "return"
);
```

#### 添加新的禁止类名

在`MvelSecuritySandbox.FORBIDDEN_CLASSES`列表中添加新的类名：

```java
private static final List<String> FORBIDDEN_CLASSES = List.of(
    // ... 现有类名
    // 新增类名
    "java.lang.Thread", "java.util.Timer"
);
```

#### 添加SQL安全规则

在`SqlExecuteEngine.FORBIDDEN_KEYWORDS`集合中添加新的SQL关键字：

```java
private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
    // ... 现有关键字
    // 新增关键字
    "LOAD", "BACKUP", "RESTORE"
);
```

或修改`SQL_INJECTION_PATTERNS`正则表达式，添加新的注入检测模式。

### 3.3 扩展会话管理

#### 修改会话超时时间

在`EtlContextManager`中修改以下常量：

```java
private static final long SESSION_TIMEOUT_SECONDS = 30 * 60;  // 修改为需要的超时时间
private static final long CLEANUP_INTERVAL_SECONDS = 5 * 60;  // 修改为需要的清理间隔
```

或将其提取到`EngineProperties`中，通过配置文件控制。

#### 添加新的会话存储后端

实现类似`EtlContextManager`的管理器，支持其他存储后端（如MySQL、MongoDB等），需要：

1. 实现会话的CRUD操作
2. 处理序列化/反序列化
3. 实现超时清理机制
4. 在Controller中替换注入的管理器

## 4. 测试指南

### 4.1 运行全部测试

```bash
mvn test
```

### 4.2 运行指定测试类

```bash
mvn test -Dtest=MvelExpressionEngineIntegrationTest
```

### 4.3 运行指定测试方法

```bash
mvn test -Dtest=MvelExpressionEngineIntegrationTest#testArithmeticExpression
```

### 4.4 测试分类说明

| 测试类 | 类型 | 测试内容 |
|--------|------|----------|
| ContextHolderTest | 单元测试 | ThreadLocal上下文持有者的基本功能 |
| EtlExpressionControllerTest | 集成测试 | HTTP接口的请求和响应验证 |
| MvelExpressionEngineIntegrationTest | 集成测试 | 表达式引擎的端到端执行流程 |
| MvelSandboxEngineTest | 单元测试 | 旧版沙箱引擎的安全校验和执行 |
| RedisSerializationTest | 集成测试 | EtlContext的Redis序列化和反序列化 |
| SessionContextManagerTest | 单元测试 | 旧版会话管理器的生命周期管理 |
| SqlExecutionEngineTest | 单元测试 | SQL执行引擎的查询和安全校验 |
| SqlFunctionTest | 单元测试 | SQL函数的桥接功能 |

### 4.5 测试配置

测试环境使用`application-test.yml`配置，主要特点：

- 排除Redis自动配置，使用本地内存模式
- 使用H2内存数据库
- 不依赖外部服务

### 4.6 编写测试的建议

**单元测试**：

- 使用Mockito模拟依赖
- 测试边界条件和异常场景
- 每个测试方法只验证一个行为

**集成测试**：

- 使用`@SpringBootTest`加载完整上下文
- 使用`@Transactional`确保测试数据隔离
- 测试完整的请求-响应流程

**安全测试**：

- 测试所有禁止关键字的拦截
- 测试所有禁止类名的拦截
- 测试SQL注入防护
- 测试超时控制

## 5. 日志配置说明

### 5.1 日志级别

| 包/类 | 级别 | 说明 |
|-------|------|------|
| root | INFO | 全局默认级别 |
| com.etl.engine | DEBUG | 项目代码详细日志 |

### 5.2 日志输出格式

```
2026-05-22 14:30:00 [http-nio-8080-exec-1] INFO  c.e.engine.controller.EtlExpressionController - 表达式执行完成: sessionId=ETL-A1B2C3D4, success=true
```

格式说明：

| 字段 | 说明 |
|------|------|
| 2026-05-22 14:30:00 | 时间戳 |
| http-nio-8080-exec-1 | 线程名 |
| INFO | 日志级别 |
| c.e.engine.controller.EtlExpressionController | 类名缩写 |
| 表达式执行完成... | 日志消息 |

### 5.3 关键日志说明

| 日志内容 | 级别 | 含义 |
|----------|------|------|
| ETL上下文管理器已启动, 模式: Redis | INFO | 系统启动，Redis模式 |
| ETL上下文管理器已启动, 模式: 本地内存 | INFO | 系统启动，本地内存模式 |
| 创建新会话: ETL-XXX | INFO | 新会话创建 |
| 销毁会话: ETL-XXX | INFO | 会话销毁 |
| 表达式包含禁止的关键字: XXX | WARN | 安全拦截 |
| 表达式包含禁止的类名: XXX | WARN | 安全拦截 |
| 表达式执行超时 | WARN | 执行超时 |
| Redis写入失败，降级为本地存储 | WARN | Redis降级 |
| 本地会话超时自动清理: ETL-XXX | WARN | 会话超时清理 |
| 表达式执行异常 | ERROR | 执行异常 |
| SQL执行错误 | ERROR | SQL执行失败 |

### 5.4 日志文件

启动脚本模式下，日志输出到以下文件：

| 文件 | 说明 |
|------|------|
| app.log | 标准输出日志 |
| app-error.log | 错误输出日志 |

### 5.5 生产环境日志建议

```yaml
logging:
  level:
    root: WARN
    com.etl.engine: INFO
```

- 将全局日志级别设为WARN，减少日志量
- 项目代码保持INFO级别，记录关键操作
- 需要排查问题时，临时调整为DEBUG

## 6. 性能调优建议

### 6.1 JVM调优

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| -Xms | 256m | 初始堆内存，与-Xmx相同可避免动态扩容开销 |
| -Xmx | 512m | 最大堆内存，根据并发量调整 |
| -XX:+UseZGC | - | 使用ZGC垃圾收集器，降低GC停顿时间 |
| --enable-preview | - | 必须参数，启用ScopedValue等预览特性 |

### 6.2 虚拟线程调优

系统使用`Executors.newVirtualThreadPerTaskExecutor()`创建虚拟线程，无需手动配置线程池大小。JVM自动管理虚拟线程的调度和挂起。

**注意事项**：

- 虚拟线程适合I/O密集型操作（如SQL查询）
- CPU密集型操作（如复杂计算）不会从虚拟线程中获益
- 虚拟线程不适用于需要synchronized的代码，应使用ReentrantLock替代

### 6.3 Redis连接池调优

```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 8    # 根据并发量调整，建议为CPU核心数的2-4倍
        max-idle: 8      # 与max-active保持一致
        min-idle: 2      # 保持一定数量的空闲连接，减少创建开销
        max-wait: 10000  # 获取连接超时时间
```

### 6.4 表达式执行超时

```yaml
etl:
  engine:
    expression-timeout: 5000  # 根据SQL查询复杂度调整
```

- 简单查询场景：3000-5000ms
- 复杂查询场景：10000-30000ms
- 不建议设置过大，避免资源长时间被占用

### 6.5 会话管理优化

- **会话超时时间**：默认30分钟，可根据业务场景调整
- **清理间隔**：默认5分钟，会话量大时可缩短到1-2分钟
- **Redis vs 本地内存**：单实例部署使用本地内存即可，多实例部署必须使用Redis

### 6.6 H2数据库优化

当前H2使用内存模式，数据量受JVM堆内存限制。如需支持更大数据量，可切换为H2文件模式：

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/etl;DB_CLOSE_DELAY=-1
```

或切换为外部数据库（MySQL、PostgreSQL等），需同步修改驱动依赖和连接配置。

## 7. 版本更新日志

### v1.0.0 (2026-05-22)

**初始版本发布**

核心功能：

- MVEL表达式编译执行引擎
- 三层安全防护机制（沙箱关键字过滤+超时控制+SQL只读限制）
- 内置SQL查询函数（sql、sqlValue）
- 内置HTTP请求函数（httpRequest、http）
  - 链式API设计，支持多种HTTP方法
  - 支持同步和异步请求
  - 多种响应解析方式（JSON、XML、Map、Bean）
  - 请求重试机制
  - HTTP拦截器支持
  - Bearer/Basic认证支持
- Redis分布式会话存储+本地内存降级
- JDK 21虚拟线程并发支持
- ScopedValue上下文传递
- Web控制台界面
- H2内存数据库及初始化数据
- Windows启动/停止脚本

技术栈：

- JDK 21 + Spring Boot 3.4.6
- MVEL2 2.5.2.Final
- H2 2.2.224
- Redis + Lettuce
- Jackson (JSON/XML)
