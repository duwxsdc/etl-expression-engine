# MVEL表达式API调用指南

## 概述

本文档提供在ETL表达式引擎中使用MVEL表达式进行API调用的完整指南，涵盖HTTP请求、响应处理、类型转换、资源管理和安全机制。

## 目录

1. [MVEL表达式基础语法](#1-mvel表达式基础语法)
2. [HTTP请求功能](#2-http请求功能)
3. [响应处理机制](#3-响应处理机制)
4. [类型转换功能](#4-类型转换功能)
5. [资源管理机制](#5-资源管理机制)
6. [异步操作](#6-异步操作)
7. [安全注意事项](#7-安全注意事项)
8. [性能优化建议](#8-性能优化建议)
9. [完整示例代码](#9-完整示例代码)

---

## 1. MVEL表达式基础语法

### 1.1 变量赋值

```java
// 基本赋值
name = "张三";
age = 30;
active = true;

// 计算赋值
total = 100 + 200;
average = total / 2;
```

### 1.2 对象创建

```java
// 创建Map
map = new java.util.LinkedHashMap();
map.put("key", "value");

// 创建List
list = new java.util.ArrayList();
list.add("item1");
list.add("item2");

// 使用Arrays工具类
items = java.util.Arrays.asList("a", "b", "c");
```

### 1.3 条件表达式

```java
// if-else条件
result = if (score > 60) { "pass" } else { "fail" };

// 三元运算符
status = age >= 18 ? "adult" : "minor";
```

### 1.4 循环表达式

```java
// for循环（安全的迭代）
sum = 0;
for (i : items) { sum = sum + i; }

// while循环（有限制）
count = 0;
while (count < 10) { count = count + 1; }
```

---

## 2. HTTP请求功能

### 2.1 基本HTTP请求

```java
// GET请求
response = httpRequest("http://localhost:8080/api/users").get();

// POST请求
response = httpRequest("http://localhost:8080/api/users")
    .bodyJson(Map.of("name", "张三", "age", 30))
    .post();

// 简写形式
response = http("http://localhost:8080/api/users").get();
```

### 2.2 请求头设置

```java
// 单个请求头
response = httpRequest("http://localhost:8080/api/users")
    .header("Authorization", "Bearer token123")
    .get();

// 多个请求头
response = httpRequest("http://localhost:8080/api/users")
    .header("Authorization", "Bearer token123")
    .header("Content-Type", "application/json")
    .header("X-Custom-Header", "custom-value")
    .get();

// 使用Consumer设置请求头
response = httpRequest("http://localhost:8080/api/users")
    .header(h -> {
        h.add("Authorization", "Bearer token123");
        h.add("Content-Type", "application/json");
    })
    .get();
```

### 2.3 认证配置

```java
// Basic认证
response = httpRequest("http://localhost:8080/api/users")
    .basicAuth("username", "password")
    .get();

// Bearer Token认证
response = httpRequest("http://localhost:8080/api/users")
    .bearerAuth("your-access-token")
    .get();
```

### 2.4 查询参数

```java
// 单个查询参数
response = httpRequest("http://localhost:8080/api/users")
    .queryVariable("page", 1)
    .queryVariable("size", 20)
    .get();

// 多个查询参数
params = new java.util.LinkedHashMap();
params.put("name", "张三");
params.put("department", "研发部");

response = httpRequest("http://localhost:8080/api/users")
    .queryVariables(params)
    .get();
```

### 2.5 路径参数

```java
// 路径参数替换
response = httpRequest("http://localhost:8080/api/users/{id}/orders/{orderId}")
    .pathVariable("id", 1001)
    .pathVariable("orderId", "ORD-001")
    .get();

// 批量路径参数
pathVars = new java.util.LinkedHashMap();
pathVars.put("id", 1001);
pathVars.put("orderId", "ORD-001");

response = httpRequest("http://localhost:8080/api/users/{id}/orders/{orderId}")
    .pathVariables(pathVars)
    .get();
```

### 2.6 请求体设置

```java
// JSON请求体
data = new java.util.LinkedHashMap();
data.put("name", "张三");
data.put("age", 30);

response = httpRequest("http://localhost:8080/api/users")
    .bodyJson(data)
    .post();

// 表单数据
formData = new java.util.LinkedHashMap();
formData.put("username", "admin");
formData.put("password", "secret");

response = httpRequest("http://localhost:8080/api/login")
    .bodyForm(formData)
    .post();

// XML请求体
response = httpRequest("http://localhost:8080/api/data")
    .bodyXml("<root><name>张三</name></root>")
    .post();
```

### 2.7 超时和重试

```java
// 设置超时时间（毫秒）
response = httpRequest("http://localhost:8080/api/users")
    .timeout(5000)  // 5秒超时
    .get();

// 设置重试次数
response = httpRequest("http://localhost:8080/api/users")
    .retry(3)  // 最多重试3次
    .get();

// 重试次数和延迟
response = httpRequest("http://localhost:8080/api/users")
    .retry(3, 1000)  // 重试3次，每次间隔1秒
    .get();
```

---

## 3. 响应处理机制

### 3.1 基本响应获取

```java
response = httpRequest("http://localhost:8080/api/users/1").get();

// 获取状态码
statusCode = response.statusCode();  // 200

// 获取状态消息
statusMessage = response.statusMessage();  // "OK"

// 判断响应状态
isSuccess = response.isSuccess();      // true
isClientError = response.isClientError(); // false
isServerError = response.isServerError(); // false
```

### 3.2 响应体获取

```java
// 字符串格式
bodyString = response.asString();

// JSON格式
jsonNode = response.asJson();
name = jsonNode.get("name").asText();

// Map格式
map = response.asMap();

// 字节数组
bytes = response.asBytes();
```

### 3.3 JSON路径提取

```java
response = httpRequest("http://localhost:8080/api/user/profile").get();

// 提取简单字段
name = response.extract("name");  // 字符串类型
age = response.extract("age");    // 自动推断类型

// 提取嵌套字段
city = response.extract("address.city");
province = response.extract("address.province");

// 提取指定类型
age = response.extract("age", Integer.class);
price = response.extract("price", Double.class);
active = response.extract("active", Boolean.class);

// 数组元素提取
firstItemId = response.extract("items[0].id");
secondItemName = response.extract("items[1].name");
```

### 3.4 Consumer响应处理

```java
// 使用Consumer消费响应
response = httpRequest("http://localhost:8080/api/users/1").get();

response.response(resp -> {
    // 在Consumer中处理响应
    userName = resp.extract("name");
    userAge = resp.extract("age");
    System.out.println("用户: " + userName + ", 年龄: " + userAge);
});

// 链式Consumer调用
response.response(resp -> { name = resp.extract("name"); })
        .response(resp -> { age = resp.extract("age"); });
```

### 3.5 Function响应处理

```java
// 使用Function处理响应并返回结果
result = response.response(resp -> {
    processed = new java.util.LinkedHashMap();
    processed.put("name", resp.extract("name"));
    processed.put("age", resp.extract("age"));
    processed.put("isAdult", resp.extract("age", Integer.class) >= 18);
    return processed;
});

// 复杂数据处理
summary = response.response(resp -> {
    items = resp.extract("items");
    total = 0;
    for (item : items) {
        total = total + item.price;
    }
    return Map.of("total", total, "count", items.size());
});
```

---

## 4. 类型转换功能

### 4.1 基本类型转换

```java
response = httpRequest("http://localhost:8080/api/data").get();

// 转换为Map
map = response.asJava("java.util.Map");

// 转换为指定类
user = response.asJava("com.example.User");

// 使用Class对象
userMap = response.asJava(Map.class);
```

### 4.2 泛型类型转换

```java
// List泛型
users = response.asJava("java.util.List<com.example.User>");

// Map泛型
scoreMap = response.asJava("java.util.Map<java.lang.String, java.lang.Integer>");

// 简写形式
userList = response.asJava("List<User>");
stringIntMap = response.asJava("Map<String, Integer>");
```

### 4.3 复杂嵌套类型

```java
// 嵌套泛型
data = response.asJava("java.util.List<java.util.Map<java.lang.String, java.lang.Integer>>");

// 自定义VO类型
result = response.asJava("com.huawei.VO1");
```

### 4.4 类型转换示例

```java
// 场景1: 获取用户列表
response = httpRequest("http://localhost:8080/api/users").get();
users = response.asJava("java.util.List<java.util.Map>");
for (user : users) {
    System.out.println(user.get("name"));
}

// 场景2: 获取嵌套数据
response = httpRequest("http://localhost:8080/api/order/1").get();
order = response.asJava("java.util.Map");
customerName = order.get("customer").get("name");

// 场景3: 提取并转换
response = httpRequest("http://localhost:8080/api/config").get();
config = response.asJava("java.util.Map<java.lang.String, java.lang.Object>");
```

---

## 5. 资源管理机制

### 5.1 自动资源管理

框架内置资源自动管理机制，防止资源泄漏：

```java
// 资源自动跟踪
// 系统会自动跟踪HTTP连接等资源，无需手动管理

// 资源监控
// 后台线程定期检测长时间持有的资源，发出警告
```

### 5.2 手动资源管理

```java
// 如需手动控制，可使用ResourceManager
// 注意：普通MVEL表达式中不需要手动调用

// 查看活跃资源
resources = ResourceManager.getActiveResources();
count = ResourceManager.getActiveResourceCount();

// 检查资源泄漏
ResourceManager.checkResourceLeaks();
```

---

## 6. 异步操作

### 6.1 异步请求基础

```java
// 创建异步请求
future = httpRequest("http://localhost:8080/api/data")
    .header("Authorization", "Bearer token")
    .async()
    .get();

// 获取异步结果（带超时）
response = future.get(5000);  // 5秒超时

// 获取异步结果（无限等待）
response = future.get();
```

### 6.2 异步回调

```java
// 使用Consumer回调
future = httpRequest("http://localhost:8080/api/data")
    .async()
    .get();

future.thenAccept(resp -> {
    body = resp.asJson();
    name = body.get("name").asText();
    System.out.println("获取到数据: " + name);
});
```

### 6.3 异步请求管理

```java
// 检查异步请求状态
isDone = future.isDone();
isCancelled = future.isCancelled();

// 取消异步请求
cancelled = future.cancel();

// 设置超时
futureWithTimeout = future.withTimeout(3000);  // 3秒超时
```

---

## 7. 安全注意事项

### 7.1 表达式安全规则

以下内容在MVEL表达式中被禁止：

```java
// 禁止使用的关键字
import, package, class, interface, enum,
try, catch, finally, throw, throws,
synchronized, volatile, transient, native

// 禁止使用的类
Runtime, System, ProcessBuilder, Process,
FileInputStream, FileOutputStream, Socket,
ClassLoader, Unsafe

// 禁止使用的模式
while(true) {}      // 无限循环
for(;;) {}          // 无限循环
Runtime.exec()      // 命令执行
System.exit()       // 系统退出
```

### 7.2 安全表达式示例

```java
// 安全的表达式
result = httpRequest("http://localhost:8080/api/data").get();
name = result.extract("name");

// 安全的循环
sum = 0;
items = result.extract("items");
for (item : items) {
    sum = sum + item.value;
}

// 安全的条件判断
status = if (score > 60) { "pass" } else { "fail" };
```

### 7.3 危险表达式示例（会被拒绝）

```java
// 危险：无限循环
while (true) { }
for (;;) { }

// 危险：系统调用
Runtime.getRuntime().exec("cmd")

// 危险：文件操作
new FileInputStream("/etc/passwd")

// 危险：导入语句
import java.io.File
```

---

## 8. 性能优化建议

### 8.1 请求优化

```java
// 推荐：设置合理的超时时间
response = httpRequest("http://localhost:8080/api/data")
    .timeout(5000)  // 防止长时间阻塞
    .get();

// 推荐：对不稳定的服务启用重试
response = httpRequest("http://localhost:8080/api/data")
    .retry(3, 1000)  // 重试3次，间隔1秒
    .get();

// 推荐：批量操作使用异步
future1 = httpRequest("http://localhost:8080/api/data1").async().get();
future2 = httpRequest("http://localhost:8080/api/data2").async().get();
// 并行处理
result1 = future1.get();
result2 = future2.get();
```

### 8.2 响应处理优化

```java
// 推荐：使用extract直接提取字段
name = response.extract("name");  // 高效

// 避免：先转换整个对象再获取字段
// user = response.asJava("com.example.User");  // 可能更慢
// name = user.getName();

// 推荐：使用指定类型避免类型推断
age = response.extract("age", Integer.class);  // 明确类型
```

### 8.3 资源管理优化

```java
// 推荐：及时释放不用的异步请求
future = httpRequest("http://localhost:8080/api/data").async().get();
if (future.isDone()) {
    response = future.get();
} else {
    future.cancel();  // 取消不需要的请求
}

// 推荐：使用带超时的异步请求
response = future.get(3000);  // 3秒超时，避免无限等待
```

---

## 9. 完整示例代码

### 9.1 用户信息获取与处理

```java
// 获取用户信息
response = httpRequest("http://localhost:8080/api/users/{id}")
    .pathVariable("id", 1001)
    .header("Authorization", "Bearer " + token)
    .timeout(5000)
    .get();

// 判断请求是否成功
if (response.isSuccess()) {
    // 提取用户信息
    user = new java.util.LinkedHashMap();
    user.put("id", response.extract("id"));
    user.put("name", response.extract("name"));
    user.put("email", response.extract("email"));
    user.put("department", response.extract("department.name"));
    
    // 返回处理结果
    user;
} else {
    // 返回错误信息
    Map.of("error", true, "message", response.extract("message"));
}
```

### 9.2 批量数据处理

```java
// 获取批量数据
response = httpRequest("http://localhost:8080/api/products")
    .queryVariable("category", "electronics")
    .queryVariable("page", 1)
    .queryVariable("size", 100)
    .get();

// 处理数据列表
products = response.extract("items");
processedItems = new java.util.ArrayList();

for (product : products) {
    item = new java.util.LinkedHashMap();
    item.put("id", product.id);
    item.put("name", product.name);
    item.put("price", product.price * 1.1);  // 加价10%
    item.put("inStock", product.stock > 0);
    processedItems.add(item);
}

// 返回处理结果
Map.of(
    "total", response.extract("total"),
    "processed", processedItems.size(),
    "items", processedItems
);
```

### 9.3 并发请求处理

```java
// 创建多个异步请求
future1 = httpRequest("http://localhost:8080/api/users")
    .async()
    .get();

future2 = httpRequest("http://localhost:8080/api/orders")
    .async()
    .get();

future3 = httpRequest("http://localhost:8080/api/products")
    .async()
    .get();

// 等待所有请求完成
resp1 = future1.get(5000);
resp2 = future2.get(5000);
resp3 = future3.get(5000);

// 合并结果
result = new java.util.LinkedHashMap();
result.put("users", resp1.extract("items"));
result.put("orders", resp2.extract("items"));
result.put("products", resp3.extract("items"));
result;
```

### 9.4 复杂数据转换

```java
// 获取订单详情
response = httpRequest("http://localhost:8080/api/orders/{orderId}")
    .pathVariable("orderId", "ORD-001")
    .bearerAuth(token)
    .get();

// 使用Function进行复杂数据处理
summary = response.response(resp -> {
    order = resp.extract("order");
    items = order.items;
    
    // 计算统计信息
    totalAmount = 0;
    itemCount = 0;
    
    for (item : items) {
        totalAmount = totalAmount + item.price * item.quantity;
        itemCount = itemCount + item.quantity;
    }
    
    // 构建返回结果
    result = new java.util.LinkedHashMap();
    result.put("orderId", order.id);
    result.put("customerName", order.customer.name);
    result.put("totalAmount", totalAmount);
    result.put("itemCount", itemCount);
    result.put("avgPrice", totalAmount / itemCount);
    result.put("status", order.status);
    
    return result;
});

summary;
```

---

## 附录

### A. 支持的HTTP方法

| 方法 | 描述 | 示例 |
|------|------|------|
| GET | 获取资源 | `.get()` |
| POST | 创建资源 | `.post()` |
| PUT | 更新资源 | `.put()` |
| DELETE | 删除资源 | `.delete()` |
| PATCH | 部分更新 | `.patch()` |

### B. 响应状态判断方法

| 方法 | 描述 | 状态码范围 |
|------|------|-----------|
| `isSuccess()` | 成功响应 | 200-299 |
| `isRedirect()` | 重定向 | 300-399 |
| `isClientError()` | 客户端错误 | 400-499 |
| `isServerError()` | 服务器错误 | 500-599 |

### C. 常见错误处理

```java
// 错误响应处理
response = httpRequest("http://localhost:8080/api/data").get();

if (response.isClientError()) {
    error = response.extract("error");
    message = response.extract("message");
    "客户端错误: " + message;
} else if (response.isServerError()) {
    "服务器错误，请稍后重试";
} else {
    response.extract("data");
}
```

### D. 测试接口列表

| 接口路径 | 方法 | 功能 |
|---------|------|------|
| /etl/expression/test/asjava/simple | GET | 简单对象测试 |
| /etl/expression/test/asjava/nested | GET | 嵌套对象测试 |
| /etl/expression/test/asjava/list | GET | 列表对象测试 |
| /etl/expression/test/response/simple | GET | 简单响应测试 |
| /etl/expression/test/resource/create | GET | 资源创建测试 |
| /etl/expression/test/async/concurrent | GET | 并发测试 |
