# MVEL表达式使用指南

## 目录
1. [基础语法规则](#1-基础语法规则)
2. [典型使用场景](#2-典型使用场景)
3. [完整示例代码](#3-完整示例代码)
4. [性能优化建议](#4-性能优化建议)
5. [安全注意事项](#5-安全注意事项)

---

## 1. 基础语法规则

### 1.1 变量

MVEL支持多种变量定义和使用方式：

```java
// 变量声明
var x = 10;
var name = "Hello";

// 使用变量
x + 5           // 返回 15
name + " World" // 返回 "Hello World"

// 类型推断
var list = [1, 2, 3, 4, 5];  // List
var map = {"key": "value"};   // Map
```

### 1.2 运算符

```java
// 算术运算符
a + b   // 加法
a - b   // 减法
a * b   // 乘法
a / b   // 除法
a % b   // 取模

// 比较运算符
a == b  // 等于
a != b  // 不等于
a > b   // 大于
a < b   // 小于
a >= b  // 大于等于
a <= b  // 小于等于

// 逻辑运算符
a && b  // 逻辑与
a || b  // 逻辑或
!a      // 逻辑非

// 三元运算符
condition ? valueIfTrue : valueIfFalse
```

### 1.3 函数调用

```java
// 字符串方法
name.toUpperCase()
name.substring(0, 5)
name.length()

// 数学函数
Math.max(a, b)
Math.min(a, b)
Math.abs(a)

// 自定义函数（已注册）
httpRequest("https://api.example.com").get()
```

### 1.4 集合操作

```java
// List操作
list[0]             // 索引访问
list.size()         // 获取大小
list.contains(x)    // 包含检查

// Map操作
map["key"]          // 键访问
map.get("key")      // get方法
map.keySet()        // 获取所有键
```

---

## 2. 典型使用场景

### 2.1 HTTP请求处理

```java
// GET请求
httpRequest("https://api.example.com/users")
    .header("Authorization", "Bearer " + token)
    .get()
    .extract("data")

// POST请求
httpRequest("https://api.example.com/users")
    .header("Content-Type", "application/json")
    .bodyJson({"name": "John", "age": 30})
    .post()
    .extract("id")

// 带路径变量
httpRequest("https://api.example.com/users/{id}")
    .pathVariable("id", userId)
    .get()
    .extract("name")
```

### 2.2 条件判断

```java
// 简单条件
age >= 18 ? "成年" : "未成年"

// 复杂条件
(status == "active" && balance > 0) ? "可用" : "不可用"

// 多条件判断
score >= 90 ? "优秀" : 
score >= 80 ? "良好" : 
score >= 60 ? "及格" : "不及格"
```

### 2.3 数据转换

```java
// 类型转换
Integer.parseInt(strValue)
Double.parseDouble(strValue)

// 提取并转换
response.extract("price", Double.class)

// 列表转换
response.extract("items", java.util.List.class)
```

### 2.4 嵌套对象访问

```java
// 链式访问
user.address.city
response.data.items[0].name

// 安全访问（使用extract）
response.extract("user.profile.avatar")
```

---

## 3. 完整示例代码

### 3.1 正确用法示例

```java
// 示例1: 简单API调用
String expression = """
    httpRequest("https://api.example.com/users")
        .header("Authorization", "Bearer token123")
        .get()
        .extract("data")
    """;
Object result = mvelEngine.execute(expression, context);

// 示例2: 带参数的API调用
String expression = """
    httpRequest("https://api.example.com/users/{id}")
        .pathVariable("id", userId)
        .queryVariable("fields", "name,email")
        .get()
        .extract("user.name")
    """;
Map<String, Object> context = Map.of("userId", "12345");
Object result = mvelEngine.execute(expression, context);

// 示例3: 条件处理
String expression = """
    var response = httpRequest("https://api.example.com/check")
        .get();
    response.extract("status") == "ok" 
        ? response.extract("data") 
        : null
    """;

// 示例4: 类型安全提取
String expression = """
    httpRequest("https://api.example.com/price")
        .get()
        .extract("price", java.lang.Double.class)
    """;

// 示例5: List泛型转换
String expression = """
    httpRequest("https://api.example.com/items")
        .get()
        .asJava("java.util.List<com.example.ItemVO>")
    """;
```

### 3.2 错误用法对比

```java
// ❌ 错误: 直接使用未定义变量
String badExpr = "undefinedVar + 1";  // 会抛出异常

// ✅ 正确: 先定义或传入变量
String goodExpr = "definedVar + 1";
context.put("definedVar", 10);

// ❌ 错误: 使用禁止的关键字
String badExpr = "import java.io.*;";  // 安全沙箱会拦截

// ✅ 正确: 使用已注册的函数
String goodExpr = "httpRequest(url).get()";

// ❌ 错误: 复杂表达式无缓存
for (int i = 0; i < 1000; i++) {
    engine.execute("复杂表达式...", context);  // 每次都解析
}

// ✅ 正确: 预编译表达式
var compiled = MVEL.compileExpression("复杂表达式...");
for (int i = 0; i < 1000; i++) {
    MVEL.executeExpression(compiled, context);
}

// ❌ 错误: 不处理异常
String badExpr = "Integer.parseInt(invalidNumber)";  // 可能抛异常

// ✅ 正确: 使用条件判断避免异常
String goodExpr = """
    str == null || str.isEmpty() ? 0 : Integer.parseInt(str)
    """;
```

---

## 4. 性能优化建议

### 4.1 避免复杂表达式

```java
// ❌ 不推荐: 过于复杂的单行表达式
"((a + b) * c - d) / e > f ? (g < h ? i : j) : (k != l ? m : n)"

// ✅ 推荐: 拆分为多步骤
"""
var temp1 = (a + b) * c - d;
var temp2 = temp1 / e;
temp2 > f ? (g < h ? i : j) : (k != l ? m : n)
"""
```

### 4.2 使用缓存机制

```java
// 预编译常用表达式
private static final Map<String, Serializable> EXPR_CACHE = new ConcurrentHashMap<>();

public Object executeCached(String expr, Map<String, Object> context) {
    Serializable compiled = EXPR_CACHE.computeIfAbsent(expr, 
        e -> MVEL.compileExpression(e));
    return MVEL.executeExpression(compiled, context);
}
```

### 4.3 减少HTTP请求次数

```java
// ❌ 不推荐: 多次请求同一接口
"""
var user = httpRequest("/api/user").get().extract("data");
var profile = httpRequest("/api/user").get().extract("profile");
"""

// ✅ 推荐: 一次请求获取所有数据
"""
var response = httpRequest("/api/user").get();
var user = response.extract("data");
var profile = response.extract("profile");
"""
```

### 4.4 合理使用异步

```java
// 对于耗时操作使用异步
var asyncResult = httpRequest("https://slow-api.example.com")
    .async()
    .asyncGet();

// 继续执行其他逻辑...

// 需要结果时等待
var response = asyncResult.get();
```

---

## 5. 安全注意事项

### 5.1 防止注入攻击

```java
// ❌ 危险: 直接拼接用户输入
String expr = "name == '" + userInput + "'";  // SQL注入风险

// ✅ 安全: 使用参数化
String expr = "name == inputName";
context.put("inputName", userInput);
```

### 5.2 敏感数据处理

```java
// ❌ 危险: 日志中输出敏感信息
logger.info("执行表达式: " + expression);  // 可能包含密码等

// ✅ 安全: 过滤敏感信息
logger.info("执行表达式: " + sanitize(expression));

// ❌ 危险: 返回完整响应
return response.body();  // 可能包含敏感字段

// ✅ 安全: 只提取需要的字段
return response.extract("publicField");
```

### 5.3 安全沙箱限制

系统已实现安全沙箱，禁止以下操作：

| 禁止项 | 说明 |
|--------|------|
| import | 禁止导入包 |
| package | 禁止定义包 |
| interface/enum | 禁止定义接口/枚举 |
| try/catch/finally | 禁止异常处理块 |
| throw/throws | 禁止抛出异常 |
| synchronized | 禁止同步块 |
| while(true) | 禁止无限循环 |
| Runtime.exec | 禁止执行系统命令 |
| ProcessBuilder | 禁止创建进程 |
| System.exit | 禁止退出JVM |

### 5.4 访问控制

```java
// 使用安全沙箱验证表达式
MvelSecuritySandbox sandbox = new MvelSecuritySandbox();

if (!sandbox.isExpressionSafe(expression)) {
    throw new SecurityException("表达式包含不安全操作");
}

// 执行验证通过的表达式
Object result = mvelEngine.execute(expression, context);
```

---

## 附录: 常用表达式模板

### HTTP请求模板

```java
// GET请求提取单个字段
"httpRequest(url).header('Authorization', token).get().extract(field)"

// POST请求创建资源
"httpRequest(url).bodyJson(data).post().extract('id')"

// 分页查询
"httpRequest(url).queryVariable('page', page).queryVariable('size', size).get()"
```

### 数据处理模板

```java
// 条件赋值
"condition ? trueValue : falseValue"

// 空值处理
"value != null ? value : defaultValue"

// 类型转换
"Integer.parseInt(stringValue)"
```

### 集合操作模板

```java
// 列表过滤
"list.stream().filter(item -> item.status == 'active').toList()"

// 列表映射
"list.stream().map(item -> item.name).toList()"

// 列表求和
"list.stream().mapToInt(item -> item.value).sum()"
```
