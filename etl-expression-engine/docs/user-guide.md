# ETL Expression Engine - 用户操作手册

## 1. 系统访问

### 1.1 访问地址

启动ETL Expression Engine服务后，在浏览器中访问以下地址：

```
http://localhost:8080
```

如果服务部署在远程服务器或使用了非默认端口，请将`localhost`替换为服务器地址，`8080`替换为实际端口。

### 1.2 系统要求

- 现代浏览器：Chrome 90+、Firefox 88+、Edge 90+、Safari 14+
- 启用JavaScript

### 1.3 首次访问

首次访问时，系统自动创建一个新的会话（Session），并在页面右上角显示会话ID。会话ID格式为`ETL-XXXXXXXXXXXXXXXX`。

## 2. 界面说明

系统提供基于Web的控制台界面，采用深色主题设计，模拟终端操作体验。

### 2.1 界面布局

```
+--------------------------------------------------------------+
| ETL Expression Engine Console    [状态] Session: ETL-... [变量]|
+--------------------------------------------------------------+
|                                                              |
|  [时间] ETL Expression Engine Console Ready                  |
|  [时间] Session ID: ETL-A1B2C3D4E5F67890                    |
|  [时间] > 1+1                                                |
|  [时间] Result: 2                                            |
|  [时间] > a=100                                              |
|  [时间] Result: 100                                          |
|  [时间] Assigned: a=100                                      |
|                                                              |
+--------------------------------------------------------------+
| [a=100] [a+b] [flag=true] [name="etl"] [三元运算] [SQL查询] [SQL单值] [Clear]|
| > [____________________________] [Execute]                    |
+--------------------------------------------------------------+
```

### 2.2 区域说明

#### 顶部导航栏

| 元素 | 说明 |
|------|------|
| 标题 | 显示"ETL Expression Engine Console" |
| 状态指示灯 | 绿色表示已连接，红色表示连接断开 |
| 状态文字 | 显示当前连接状态（Connected/Disconnected） |
| Session ID | 显示当前会话ID |
| Variables按钮 | 点击打开/关闭变量面板 |

#### 控制台输出区

显示表达式执行的历史记录，包括：

- 系统消息（蓝色）：系统初始化、会话信息等
- 输入记录（浅蓝色）：用户输入的表达式，以`>`前缀标识
- 执行结果（橙色）：表达式的返回值，以`Result:`前缀标识
- 变量赋值（黄色）：赋值操作记录，以`Assigned:`前缀标识
- 错误信息（红色）：执行失败的错误描述

每条记录左侧显示时间戳。

#### 快捷操作按钮

提供常用表达式的快速插入功能：

| 按钮 | 插入内容 | 说明 |
|------|----------|------|
| a=100 | `a=100` | 变量赋值示例 |
| a+b | `a+b` | 变量引用示例 |
| flag=true | `flag=true` | 布尔变量赋值 |
| name="etl" | `name="etl"` | 字符串变量赋值 |
| 三元运算 | `a>50 ? "big" : "small"` | 三元条件运算示例 |
| SQL查询 | `sql("SELECT 1 as test")` | SQL查询函数调用 |
| SQL单值 | `sqlValue("SELECT count(*) FROM etl_config")` | SQL单值查询函数调用 |

点击按钮后，表达式自动填入输入框，按Enter或点击Execute执行。

#### 表达式输入区

- 输入框：输入要执行的表达式
- Execute按钮：点击执行输入框中的表达式
- 支持按Enter键直接执行

#### 变量面板

点击右上角"Variables"按钮打开变量面板，显示当前会话中的所有变量：

- 变量名（浅蓝色）
- 变量值（橙色）
- 面板从右侧滑入，再次点击按钮关闭

#### Clear按钮

清空控制台输出区的所有历史记录。注意：清空输出不会影响会话变量。

## 3. 表达式编写指南

### 3.1 基本算术

支持加、减、乘、除、取模运算，遵循标准数学运算优先级。

```
1 + 2 * 3
```

结果：7（先乘后加）

```
(1 + 2) * 3
```

结果：9（括号改变优先级）

```
10 / 3
```

结果：3（整数除法）

```
10 % 3
```

结果：1（取模）

### 3.2 变量赋值

使用等号进行变量赋值，赋值后的变量在同一会话中持续可用。

**单变量赋值**：

```
a = 100
```

结果：a = 100

**多变量赋值**：

使用分号分隔多个表达式：

```
a = 100; b = 200; c = a + b
```

结果：a = 100, b = 200, c = 300

**变量引用**：

赋值后的变量可在后续表达式中直接引用：

```
a = 100
```

```
a + 50
```

结果：150

**字符串变量**：

```
name = "ETL"
```

```
"Hello, " + name
```

结果："Hello, ETL"

**布尔变量**：

```
flag = true
```

```
!flag
```

结果：false

### 3.3 SQL查询

使用`sql()`函数执行SQL查询，返回完整的结果集。

**查询所有配置**：

```
sql("SELECT * FROM etl_config")
```

返回包含所有配置项的列表，每项包含ID、CONFIG_KEY、CONFIG_VALUE、DESCRIPTION等字段。

**条件查询**：

```
sql("SELECT * FROM etl_task WHERE status = 'RUNNING'")
```

返回状态为RUNNING的任务列表。

**排序查询**：

```
sql("SELECT * FROM etl_task ORDER BY priority ASC")
```

按优先级升序返回任务列表。

**将查询结果赋值给变量**：

```
tasks = sql("SELECT * FROM etl_task")
```

查询结果保存到变量tasks中，可在后续表达式中引用。

### 3.4 SQL单值查询

使用`sqlValue()`函数执行SQL查询，返回结果集中第一行第一列的单个值。

**统计记录数**：

```
sqlValue("SELECT count(*) FROM etl_config")
```

返回：4

**获取最大优先级**：

```
sqlValue("SELECT max(priority) FROM etl_task")
```

返回：3

**获取特定配置值**：

```
sqlValue("SELECT config_value FROM etl_config WHERE config_key = 'batch.size'")
```

返回："1000"

**将单值赋给变量**：

```
total = sqlValue("SELECT count(*) FROM etl_task")
```

### 3.5 条件运算

使用三元运算符进行条件判断。

**基本条件**：

```
score = 85; score >= 60 ? "pass" : "fail"
```

结果："pass"

**数值比较**：

```
count = sqlValue("SELECT count(*) FROM etl_task"); count > 0 ? "has tasks" : "no tasks"
```

**布尔逻辑**：

```
a = 10; b = 20; a > 0 && b > 0 ? "both positive" : "not both positive"
```

结果："both positive"

### 3.6 比较运算

| 运算符 | 示例 | 说明 |
|--------|------|------|
| == | a == 100 | 判断相等 |
| != | a != 0 | 判断不等 |
| > | score > 60 | 大于 |
| < | count < 10 | 小于 |
| >= | score >= 60 | 大于等于 |
| <= | count <= 100 | 小于等于 |

### 3.7 逻辑运算

| 运算符 | 示例 | 说明 |
|--------|------|------|
| && | a > 0 && b > 0 | 逻辑与 |
| \|\| | a > 0 \|\| b > 0 | 逻辑或 |
| ! | !flag | 逻辑非 |

## 4. 会话管理说明

### 4.1 会话概念

会话（Session）是系统管理用户状态的机制。每个会话拥有独立的变量空间，不同会话之间的变量互不影响。

### 4.2 会话创建

- 首次访问系统时，自动创建新会话
- 会话ID由系统自动生成，格式为`ETL-XXXXXXXXXXXXXXXX`
- 会话ID保存在浏览器的localStorage中，刷新页面不会丢失

### 4.3 会话保持

- 同一浏览器标签页中，所有表达式执行共享同一会话
- 页面刷新后会话自动恢复（通过localStorage保存的Session ID）
- 新开浏览器标签页将使用同一会话ID（共享localStorage）

### 4.4 会话超时

- 会话超过30分钟未活动将自动过期
- 过期后再次执行表达式时，系统自动创建新会话
- 新会话的变量空间为空，之前的变量不再可用

### 4.5 会话变量

- 变量在会话内全局有效，所有表达式共享
- 变量类型自动推断，无需声明类型
- 变量可被后续赋值覆盖
- 变量面板实时显示当前会话的所有变量

### 4.6 多会话场景

如需使用独立的变量空间（例如同时测试不同的数据场景），可以通过以下方式创建新会话：

1. 清除浏览器localStorage中的`etl-session-id`
2. 刷新页面，系统将创建新会话

## 5. 内置数据表

系统内置以下H2内存数据表，供SQL查询练习使用。

### 5.1 etl_config - 配置表

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | INT | 主键，自增 |
| CONFIG_KEY | VARCHAR(100) | 配置键名 |
| CONFIG_VALUE | VARCHAR(500) | 配置值 |
| DESCRIPTION | VARCHAR(200) | 配置描述 |
| CREATED_AT | TIMESTAMP | 创建时间 |

**初始数据**：

| CONFIG_KEY | CONFIG_VALUE | DESCRIPTION |
|------------|--------------|-------------|
| batch.size | 1000 | 批处理大小 |
| timeout.seconds | 300 | 超时时间(秒) |
| retry.count | 3 | 重试次数 |
| parallel.threads | 4 | 并行线程数 |

### 5.2 etl_task - 任务表

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | INT | 主键，自增 |
| TASK_NAME | VARCHAR(100) | 任务名称 |
| TASK_TYPE | VARCHAR(50) | 任务类型 |
| STATUS | VARCHAR(20) | 任务状态 |
| PRIORITY | INT | 优先级 |
| CREATED_AT | TIMESTAMP | 创建时间 |

**初始数据**：

| TASK_NAME | TASK_TYPE | STATUS | PRIORITY |
|-----------|-----------|--------|----------|
| 数据抽取任务A | EXTRACT | COMPLETED | 1 |
| 数据转换任务B | TRANSFORM | RUNNING | 2 |
| 数据加载任务C | LOAD | PENDING | 3 |
| 数据校验任务D | VALIDATE | FAILED | 1 |

## 6. 使用技巧和最佳实践

### 6.1 表达式编写技巧

**分步调试**：对于复杂逻辑，建议分步编写和执行，每步确认结果后再继续。

```
步骤1: count = sqlValue("SELECT count(*) FROM etl_task")
步骤2: count > 0 ? "has data" : "empty"
```

**变量命名规范**：使用有意义的变量名，提高可读性。

```
推荐: batchSize = sqlValue("SELECT config_value FROM etl_config WHERE config_key = 'batch.size'")
不推荐: x = sqlValue("SELECT config_value FROM etl_config WHERE config_key = 'batch.size'")
```

**多行表达式组织**：使用分号将相关操作组织在一起。

```
total = sqlValue("SELECT count(*) FROM etl_task"); running = sqlValue("SELECT count(*) FROM etl_task WHERE status = 'RUNNING'"); running * 100 / total
```

### 6.2 SQL查询最佳实践

**使用sqlValue获取单值**：当只需要一个值时，使用`sqlValue()`而非`sql()`，返回值更简洁。

```
推荐: sqlValue("SELECT count(*) FROM etl_task")
不推荐: sql("SELECT count(*) FROM etl_task")
```

**添加WHERE条件**：查询时尽量添加WHERE条件，缩小结果集，提高查询效率。

```
推荐: sql("SELECT * FROM etl_task WHERE status = 'RUNNING'")
不推荐: sql("SELECT * FROM etl_task")
```

**指定查询列**：避免使用`SELECT *`，明确指定需要的列名。

```
推荐: sql("SELECT task_name, status FROM etl_task")
不推荐: sql("SELECT * FROM etl_task")
```

### 6.3 安全限制注意事项

**避免使用Java关键字**：以下关键字在表达式中被禁止，请使用替代方案。

| 禁止用法 | 替代方案 |
|----------|----------|
| import xxx | 使用内置函数 |
| new Object() | 使用字面量或内置函数 |
| try { } catch { } | 使用三元运算符处理条件 |

**避免使用危险类名**：表达式中不能包含`System`、`Runtime`、`File`等类名。

**SQL只支持SELECT**：只能执行查询语句，不能执行INSERT、UPDATE、DELETE等写操作。

### 6.4 性能建议

**控制结果集大小**：SQL查询返回大量数据时，表达式执行时间会变长。建议通过WHERE条件和LIMIT子句控制结果集大小。

**避免复杂嵌套**：过于复杂的嵌套表达式可能影响可读性和执行效率，建议拆分为多步执行。

**合理使用变量**：将中间结果保存到变量中，避免重复计算。

```
推荐: total = sqlValue("SELECT count(*) FROM etl_task"); ratio = total / 10
不推荐: sqlValue("SELECT count(*) FROM etl_task") / 10
```

### 6.5 常见错误及解决

| 错误信息 | 原因 | 解决方案 |
|----------|------|----------|
| 表达式不能为空 | 输入了空字符串 | 输入有效的表达式 |
| 表达式长度超过限制 | 表达式超过10000字符 | 简化表达式或拆分执行 |
| 表达式包含禁止的内容 | 使用了禁止的关键字或类名 | 参考安全限制章节，使用替代方案 |
| 表达式执行超时 | 表达式执行时间超过5秒 | 简化SQL查询或优化表达式逻辑 |
| SQL语句验证失败 | SQL包含非SELECT操作或注入特征 | 仅使用SELECT查询语句 |
| 无效的变量名 | 变量名不符合命名规则 | 使用字母或下划线开头，只包含字母、数字和下划线 |
| 表达式执行错误 | MVEL语法错误 | 检查表达式语法是否正确 |

## 7. 快捷键

| 快捷键 | 功能 |
|--------|------|
| Enter | 执行输入框中的表达式 |
| Ctrl + Enter | 换行（不执行） |
| Ctrl + L | 清空控制台输出 |
| Esc | 清空输入框 |

## 8. HTTP请求功能

系统支持在表达式中直接发起HTTP请求，用于调用外部API获取数据或提交数据。

### 8.1 基本用法

使用`http()`函数创建HTTP请求构建器，支持链式调用：

```
http('https://api.example.com/data')
    .header('Authorization', 'Bearer token')
    .get()
    .body()
    .asMap()
```

### 8.2 常用HTTP方法

**GET请求**：
```
result = http('https://httpbin.org/get').get().body().asMap()
```

**POST请求**：
```
result = http('https://httpbin.org/post')
    .bodyJson({'name': 'ETL', 'version': '1.0'})
    .post()
    .body()
    .asMap()
```

**PUT请求**：
```
result = http('https://api.example.com/resource/1')
    .bodyJson({'status': 'updated'})
    .put()
    .statusCode()
```

**DELETE请求**：
```
result = http('https://api.example.com/resource/1')
    .delete()
    .statusCode()
```

### 8.3 请求配置

**添加请求头**：
```
http('https://api.example.com/data')
    .header('X-API-Key', 'your-api-key')
    .header('Accept', 'application/json')
    .get()
```

**添加查询参数**：
```
http('https://api.example.com/users')
    .queryVariable('page', 1)
    .queryVariable('size', 20)
    .get()
```

**设置请求体**：
```
http('https://api.example.com/orders')
    .contentType('application/json')
    .bodyJson({'orderId': 'ORD-001', 'amount': 100})
    .post()
```

**设置认证**：
```
http('https://api.example.com/protected')
    .bearerAuth('your-jwt-token')
    .get()
```

```
http('https://api.example.com/protected')
    .basicAuth('username', 'password')
    .get()
```

**设置超时**：
```
http('https://api.example.com/slow')
    .timeout(30000)
    .get()
```

**设置重试**：
```
http('https://api.example.com/unstable')
    .retry(3, 1000)
    .get()
```

### 8.4 响应处理

**获取状态码**：
```
response = http('https://api.example.com/data').get()
response.statusCode()
```

**获取响应头**：
```
response = http('https://api.example.com/data').get()
response.headers()
```

**解析响应体**：
- `asString()` - 获取字符串格式
- `asMap()` - 解析为Map（适用于JSON对象）
- `asJson()` - 解析为JsonNode
- `asXml()` - 解析为XML文档

```
response = http('https://api.example.com/users').get()
data = response.body().asMap()
data.users
```

### 8.5 实际示例

**调用天气API**：
```
weather = http('https://api.openweathermap.org/data/2.5/weather')
    .queryVariable('q', 'Beijing')
    .queryVariable('appid', 'your-api-key')
    .get()
    .body()
    .asMap()
    
weather.weather[0].description
```

**提交数据到API**：
```
result = http('https://api.example.com/tasks')
    .contentType('application/json')
    .bodyJson({
        'name': '新任务',
        'priority': 1,
        'status': 'PENDING'
    })
    .post()
    .body()
    .asMap()
    
result.taskId
```

**处理分页数据**：
```
allUsers = [];
page = 1;
while (page <= 5) {
    resp = http('https://api.example.com/users')
        .queryVariable('page', page)
        .queryVariable('size', 100)
        .get()
        .body()
        .asMap();
    allUsers.addAll(resp.data);
    page = page + 1;
}
allUsers.size()
```

### 8.6 注意事项

1. **检查状态码**：HTTP错误状态码不会导致表达式执行失败，需要手动检查
   ```
   response = http('https://api.example.com/data').get()
   response.statusCode() == 200 ? response.body().asMap() : '请求失败'
   ```

2. **超时设置**：对于响应较慢的API，建议设置合理的超时时间
   ```
   http('url').timeout(30000).get()
   ```

3. **重试机制**：对于不稳定的服务，使用重试机制提高成功率
   ```
   http('url').retry(3, 2000).get()
   ```

4. **敏感信息**：不要在表达式中硬编码API密钥或密码，可通过变量传递

## 9. 问题反馈

如遇到问题或有建议，请联系开发团队并提供以下信息：

- 浏览器类型和版本
- 操作系统
- 错误信息截图
- 复现步骤
- 会话ID（如适用）
