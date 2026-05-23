# ETL Expression Engine - API接口文档

---

## 目录

- [接口概览](#接口概览)
- [执行表达式](#执行表达式)
- [会话管理接口](#会话管理接口)
- [内置函数](#内置函数)
- [表达式语法](#表达式语法)
- [安全限制](#安全限制)
- [示例请求和响应](#示例请求和响应)
- [错误码参考](#错误码参考)

---

## 1. 接口概览

系统提供完整的HTTP接口用于表达式执行和会话管理。

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 执行表达式 | POST | `/etl/expression/execute` | 接收表达式字符串，返回执行结果 |
| 销毁会话 | DELETE | `/etl/expression/session/{sessionId}` | 销毁指定会话 |
| 获取会话信息 | GET | `/etl/expression/session/{sessionId}` | 获取会话详情和变量 |
| 创建新会话 | POST | `/etl/expression/session/new` | 创建新会话并返回SessionId |
| 查询Redis会话列表 | GET | `/etl/expression/redis/sessions` | 获取Redis中所有会话 |
| 清理Redis会话 | DELETE | `/etl/expression/redis/sessions` | 删除Redis中所有会话数据 |

---

## 2. 执行表达式

### 2.1 接口信息

```
POST /etl/expression/execute
```

### 2.2 请求头

| 请求头 | 必填 | 类型 | 说明 |
|--------|------|------|------|
| Content-Type | 是 | String | 固定值：`text/plain` |
| X-Session-Id | 否 | String | 会话ID。不传时系统自动创建新会话；传入已有会话ID时复用该会话的变量上下文 |

### 2.3 请求体

请求体为纯文本格式（`text/plain`），内容为要执行的表达式字符串。

**多行表达式**：使用分号（`;`）分隔多个表达式，系统按顺序依次执行，最后一行的结果作为最终返回值。中间行的变量赋值会保存到会话上下文中。

### 2.4 响应体

响应为JSON格式，结构如下：

| 字段 | 类型 | 说明 |
|------|------|------|
| sessionId | String | 会话ID。首次请求时由系统生成，后续请求应通过请求头传回 |
| originExpr | String | 原始表达式字符串 |
| success | boolean | 执行是否成功 |
| finalResult | Object | 最终执行结果。成功时为表达式返回值，失败时为null |
| errorMsg | String | 错误信息。成功时为null，失败时包含具体错误描述 |
| contextVars | Object | 当前会话的所有变量键值对 |

### 2.5 响应状态码

| 状态码 | 说明 |
|--------|------|
| 200 | 请求处理完成（不区分表达式执行成功或失败，均返回200） |
| 500 | 服务器内部错误 |

**注意**：表达式执行失败（如语法错误、安全拦截、超时等）不会导致HTTP错误状态码，而是通过响应体中的`success`字段和`errorMsg`字段来标识。

---

## 3. 会话管理接口

### 3.1 销毁会话

```
DELETE /etl/expression/session/{sessionId}
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| sessionId | String | 要销毁的会话ID |

**成功响应**：
```json
{
  "success": true,
  "message": "Session destroyed successfully",
  "sessionId": "ETL-A1B2C3D4E5F67890"
}
```

**失败响应**：
```json
{
  "success": false,
  "message": "Session not found: ETL-A1B2C3D4E5F67890"
}
```

### 3.2 获取会话信息

```
GET /etl/expression/session/{sessionId}
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| sessionId | String | 会话ID |

**成功响应**：
```json
{
  "success": true,
  "sessionId": "ETL-A1B2C3D4E5F67890",
  "variableCount": 2,
  "createTime": 1716408000000,
  "lastAccessTime": 1716408060000,
  "variables": {
    "a": 100,
    "b": 200
  }
}
```

### 3.3 创建新会话

```
POST /etl/expression/session/new
```

**成功响应**：
```json
{
  "success": true,
  "sessionId": "ETL-B2C3D4E5F67890A1",
  "message": "New session created successfully"
}
```

### 3.4 查询Redis会话列表

```
GET /etl/expression/redis/sessions
```

**成功响应（Redis模式）**：
```json
{
  "success": true,
  "mode": "redis",
  "totalSessions": 3,
  "sessions": [
    {
      "key": "etl:context:ETL-A1B2C3D4E5F67890",
      "sessionId": "ETL-A1B2C3D4E5F67890",
      "ttlSeconds": 1790,
      "data": {...}
    }
  ]
}
```

**成功响应（本地模式）**：
```json
{
  "success": false,
  "message": "Redis not connected - running in local memory mode",
  "mode": "local"
}
```

### 3.5 清理Redis会话

```
DELETE /etl/expression/redis/sessions
```

**成功响应**：
```json
{
  "success": true,
  "message": "Deleted 5 session keys",
  "deletedCount": 5
}
```

---

## 4. 内置函数

系统在MVEL表达式上下文中注册了以下内置函数，可直接在表达式中调用。

### 4.1 sql() - 执行SQL查询

**函数签名**：

```
List<Map<String, Object>> sql(String sqlExpression)
```

**功能说明**：

执行指定的SQL SELECT查询语句，返回完整的结果集。结果集为`List<Map<String, Object>>`类型，每个Map代表一行数据，键为列名，值为对应数据。

**参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sqlExpression | String | 是 | SQL SELECT查询语句 |

**返回值**：

查询结果列表，每行数据为一个Map。

**安全限制**：

- SQL语句必须以`SELECT`开头
- 禁止包含INSERT、UPDATE、DELETE、DROP、ALTER、CREATE等DML/DDL关键字
- 禁止包含SQL注入特征（单引号、注释符、UNION等）

**示例**：

```
sql("SELECT * FROM etl_config")
```

返回：

```json
[
  {"ID": 1, "CONFIG_KEY": "batch.size", "CONFIG_VALUE": "1000", "DESCRIPTION": "批处理大小"},
  {"ID": 2, "CONFIG_KEY": "timeout.seconds", "CONFIG_VALUE": "300", "DESCRIPTION": "超时时间(秒)"}
]
```

### 4.2 sqlValue() - 执行SQL单值查询

**函数签名**：

```
Object sqlValue(String sqlExpression)
```

**功能说明**：

执行指定的SQL SELECT查询语句，返回结果集中第一行第一列的值。适用于聚合查询（如COUNT、SUM、MAX等）或只需要单个值的场景。

**参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sqlExpression | String | 是 | SQL SELECT查询语句 |

**返回值**：

结果集第一行第一列的值。如果结果集为空或第一行无数据，返回null。

**安全限制**：

与`sql()`函数相同。

**示例**：

```
sqlValue("SELECT count(*) FROM etl_config")
```

返回：

```json
4
```

---

## 5. 表达式语法

系统使用MVEL2作为表达式引擎，支持以下语法特性。

### 5.1 算术运算

支持加、减、乘、除、取模等基本算术运算。

| 运算符 | 说明 | 示例 |
|--------|------|------|
| + | 加法 | 1 + 2 = 3 |
| - | 减法 | 10 - 3 = 7 |
| * | 乘法 | 4 * 5 = 20 |
| / | 除法 | 10 / 3 = 3 |
| % | 取模 | 10 % 3 = 1 |

### 5.2 变量赋值

使用等号（`=`）进行变量赋值，赋值后的变量在同一会话的后续表达式中可用。

**语法**：

```
变量名 = 表达式
```

**变量命名规则**：

- 以字母或下划线开头
- 只能包含字母、数字和下划线
- 区分大小写

**示例**：

```
a = 100
b = a + 50
name = "etl"
flag = true
```

### 5.3 三元运算

支持Java风格的条件运算符。

**语法**：

```
条件 ? 值1 : 值2
```

**示例**：

```
score = 85
score >= 60 ? "pass" : "fail"
```

### 5.4 多行表达式

使用分号（`;`）分隔多个表达式，系统按顺序依次执行。

**语法**：

```
表达式1; 表达式2; 表达式3
```

**执行规则**：

- 按顺序从左到右执行
- 变量赋值立即生效，后续表达式可引用
- 最后一行的结果作为整个表达式的最终返回值

**示例**：

```
a = 100; b = 200; a + b
```

最终返回值为300，变量a和b保存到会话上下文。

### 5.5 比较运算

| 运算符 | 说明 | 示例 |
|--------|------|------|
| == | 等于 | a == 100 |
| != | 不等于 | a != 0 |
| > | 大于 | score > 60 |
| < | 小于 | count < 10 |
| >= | 大于等于 | score >= 60 |
| <= | 小于等于 | count <= 100 |

### 5.6 逻辑运算

| 运算符 | 说明 | 示例 |
|--------|------|------|
| && | 逻辑与 | a > 0 && b > 0 |
| \|\| | 逻辑或 | a > 0 \|\| b > 0 |
| ! | 逻辑非 | !flag |

### 5.7 字符串操作

支持字符串字面量（双引号）和基本的字符串拼接。

```
name = "ETL"
greeting = "Hello, " + name
```

---

## 6. 安全限制

### 6.1 禁止的关键字

以下Java关键字在表达式中被禁止使用：

| 类别 | 关键字 |
|------|--------|
| 包/类定义 | import, package, class, interface, enum |
| 异常处理 | try, catch, finally, throw, throws |
| 并发/修饰 | synchronized, volatile, transient, native, strictfp |

### 6.2 禁止的类名

以下Java类名在表达式中被禁止使用：

| 类别 | 类名 |
|------|------|
| 进程控制 | Runtime, ProcessBuilder, Process, getRuntime, exit, halt |
| 文件操作 | FileInputStream, FileOutputStream, FileReader, FileWriter, java.io, java.nio.file |
| 网络访问 | HttpURLConnection, Socket, ServerSocket, java.net |
| 反射机制 | ClassLoader, java.lang.reflect, Method#invoke, Constructor#newInstance |
| 脚本引擎 | ScriptEngine, javax.script |
| 数据库直连 | java.sql.DriverManager |
| 系统类 | System |

### 6.3 SQL安全限制

| 限制项 | 说明 |
|--------|------|
| 语句类型 | 仅允许SELECT语句 |
| DML操作 | 禁止INSERT、UPDATE、DELETE |
| DDL操作 | 禁止CREATE、DROP、ALTER、TRUNCATE |
| 权限操作 | 禁止GRANT、REVOKE |
| 存储过程 | 禁止EXEC、EXECUTE、CALL |
| SQL注入 | 拦截单引号、注释符、UNION、OUTFILE等注入特征 |

### 6.4 其他限制

| 限制项 | 默认值 | 说明 |
|--------|--------|------|
| 表达式最大长度 | 10000字符 | 超过限制返回错误 |
| 表达式执行超时 | 5000毫秒 | 超时后强制中断执行 |

---

## 7. 示例请求和响应

### 7.1 基本算术运算

**请求**：

```
POST /etl/expression/execute
Content-Type: text/plain

1 + 2 * 3
```

**响应**：

```json
{
  "sessionId": "ETL-A1B2C3D4E5F67890",
  "originExpr": "1 + 2 * 3",
  "success": true,
  "finalResult": 7,
  "errorMsg": null,
  "contextVars": {}
}
```

### 7.2 变量赋值与引用

**请求**：

```
POST /etl/expression/execute
Content-Type: text/plain
X-Session-Id: ETL-A1B2C3D4E5F67890

a = 100; b = a + 50
```

**响应**：

```json
{
  "sessionId": "ETL-A1B2C3D4E5F67890",
  "originExpr": "a = 100; b = a + 50",
  "success": true,
  "finalResult": 150,
  "errorMsg": null,
  "contextVars": {
    "a": 100,
    "b": 150
  }
}
```

### 7.3 SQL查询

**请求**：

```
POST /etl/expression/execute
Content-Type: text/plain
X-Session-Id: ETL-A1B2C3D4E5F67890

sql("SELECT * FROM etl_config")
```

**响应**：

```json
{
  "sessionId": "ETL-A1B2C3D4E5F67890",
  "originExpr": "sql(\"SELECT * FROM etl_config\")",
  "success": true,
  "finalResult": [
    {"ID": 1, "CONFIG_KEY": "batch.size", "CONFIG_VALUE": "1000", "DESCRIPTION": "批处理大小"},
    {"ID": 2, "CONFIG_KEY": "timeout.seconds", "CONFIG_VALUE": "300", "DESCRIPTION": "超时时间(秒)"},
    {"ID": 3, "CONFIG_KEY": "retry.count", "CONFIG_VALUE": "3", "DESCRIPTION": "重试次数"},
    {"ID": 4, "CONFIG_KEY": "parallel.threads", "CONFIG_VALUE": "4", "DESCRIPTION": "并行线程数"}
  ],
  "errorMsg": null,
  "contextVars": {
    "a": 100,
    "b": 150
  }
}
```

### 7.4 SQL单值查询

**请求**：

```
POST /etl/expression/execute
Content-Type: text/plain
X-Session-Id: ETL-A1B2C3D4E5F67890

sqlValue("SELECT count(*) FROM etl_config")
```

**响应**：

```json
{
  "sessionId": "ETL-A1B2C3D4E5F67890",
  "originExpr": "sqlValue(\"SELECT count(*) FROM etl_config\")",
  "success": true,
  "finalResult": 4,
  "errorMsg": null,
  "contextVars": {
    "a": 100,
    "b": 150
  }
}
```

### 7.5 条件运算

**请求**：

```
POST /etl/expression/execute
Content-Type: text/plain
X-Session-Id: ETL-A1B2C3D4E5F67890

score = 85; score >= 60 ? "pass" : "fail"
```

**响应**：

```json
{
  "sessionId": "ETL-A1B2C3D4E5F67890",
  "originExpr": "score = 85; score >= 60 ? \"pass\" : \"fail\"",
  "success": true,
  "finalResult": "pass",
  "errorMsg": null,
  "contextVars": {
    "a": 100,
    "b": 150,
    "score": 85
  }
}
```

### 7.6 安全拦截 - 禁止关键字

**请求**：

```
POST /etl/expression/execute
Content-Type: text/plain

import java.lang.Runtime
```

**响应**：

```json
{
  "sessionId": "ETL-B2C3D4E5F6G7H8I9",
  "originExpr": "import java.lang.Runtime",
  "success": false,
  "finalResult": null,
  "errorMsg": "表达式包含禁止的内容",
  "contextVars": {}
}
```

### 7.7 安全拦截 - SQL写操作

**请求**：

```
POST /etl/expression/execute
Content-Type: text/plain

sql("INSERT INTO etl_config VALUES (5, 'test', 'test', 'test')")
```

**响应**：

```json
{
  "sessionId": "ETL-B2C3D4E5F6G7H8I9",
  "originExpr": "sql(\"INSERT INTO etl_config VALUES (5, 'test', 'test', 'test')\")",
  "success": false,
  "finalResult": null,
  "errorMsg": "执行错误: java.lang.SecurityException: SQL语句验证失败: 包含禁止的操作",
  "contextVars": {}
}
```

### 7.8 执行超时

**请求**：

```
POST /etl/expression/execute
Content-Type: text/plain

while(true) {}
```

**响应**：

```json
{
  "sessionId": "ETL-B2C3D4E5F6G7H8I9",
  "originExpr": "while(true) {}",
  "success": false,
  "finalResult": null,
  "errorMsg": "表达式执行超时, 超过 5000ms",
  "contextVars": {}
}
```

### 7.9 首次请求（无Session-Id）

**请求**：

```
POST /etl/expression/execute
Content-Type: text/plain

1 + 1
```

**响应**：

```json
{
  "sessionId": "ETL-C3D4E5F6G7H8I9J0",
  "originExpr": "1 + 1",
  "success": true,
  "finalResult": 2,
  "errorMsg": null,
  "contextVars": {}
}
```

**说明**：未提供`X-Session-Id`时，系统自动创建新会话并返回生成的`sessionId`。客户端应在后续请求中通过`X-Session-Id`请求头传回该值，以保持会话连续性。

---

## 8. 错误码参考

| 错误场景 | success | errorMsg示例 | HTTP状态码 |
|----------|---------|-------------|------------|
| 空表达式 | false | 表达式不能为空 | 200 |
| 表达式过长 | false | 表达式长度超过限制: 10000 | 200 |
| 安全拦截 | false | 表达式包含禁止的内容 | 200 |
| 语法错误 | false | 执行错误: 表达式执行错误: [MVEL错误详情] | 200 |
| 执行超时 | false | 表达式执行超时, 超过 5000ms | 200 |
| SQL验证失败 | false | 执行错误: java.lang.SecurityException: SQL语句验证失败: 包含禁止的操作 | 200 |
| SQL执行错误 | false | 执行错误: SQL执行错误: [数据库错误详情] | 200 |
| 无效变量名 | false | 执行错误: 无效的变量名: 123abc | 200 |
| 会话不存在 | false | Session not found: ETL-XXX | 200 |
| 服务器内部错误 | false | 服务器内部错误: [异常信息] | 500 |
| Redis连接失败 | - | Redis写入失败，降级为本地存储 | -（日志级别） |

---

## 附录：HTTP状态码汇总

| 状态码 | 含义 | 说明 |
|--------|------|------|
| 200 | 请求成功 | 正常响应，包括表达式执行成功和失败 |
| 500 | 服务器错误 | 系统内部异常，如数据库连接失败等 |