# ETL Expression Engine API 文档

## 目录
1. [概述](#1-概述)
2. [快速开始](#2-快速开始)
3. [MVEL表达式使用指南](#3-mvel表达式使用指南)
4. [API接口详解](#4-api接口详解)
5. [安全机制](#5-安全机制)
6. [性能优化建议](#6-性能优化建议)
7. [错误处理](#7-错误处理)
8. [最佳实践](#8-最佳实践)

---

## 1. 概述

ETL Expression Engine 是一个基于MVEL的表达式执行引擎，支持：
- HTTP请求处理
- SQL查询执行
- 数据转换与处理
- 异步操作支持
- 安全沙箱保护

### 1.1 核心特性

| 特性 | 说明 |
|------|------|
| 表达式执行 | 支持复杂MVEL表达式 |
| HTTP客户端 | 同步/异步HTTP请求 |
| 安全沙箱 | 表达式安全检查 |
| 类型转换 | asJava类型转换 |
| 回调机制 | 异步回调支持 |
| 加密服务 | AES-256/RSA-2048 |
| 令牌管理 | OAuth2/API Key认证 |

---

## 2. 快速开始

### 2.1 基础表达式执行

```bash
# 简单计算
POST /api/etl/execute
Content-Type: text/plain

1 + 2 * 3

# 响应: 7
```

### 2.2 HTTP请求

```bash
# GET请求
POST /api/etl/execute
Content-Type: text/plain

httpRequest("https://api.example.com/users").get().extract("data")
```

### 2.3 数据处理

```bash
# 数据转换
POST /api/etl/execute
Content-Type: text/plain

list = [1, 2, 3, 4, 5];
result = new java.util.ArrayList();
for (item : list) {
    result.add(item * 2);
}
result
```

---

## 3. MVEL表达式使用指南

### 3.1 基础语法规则

#### 3.1.1 变量定义

```java
// 变量声明（推荐使用全限定名创建对象）
name = "ETL Engine";
count = 100;
list = new java.util.ArrayList();
map = new java.util.HashMap();
```

#### 3.1.2 运算符

```java
// 算术运算
result = (a + b) * c - d / e;

// 比较运算
isValid = age >= 18 && status == "active";

// 三元运算
level = score >= 90 ? "优秀" : score >= 60 ? "及格" : "不及格";
```

#### 3.1.3 集合操作

```java
// List操作
list = [1, 2, 3, 4, 5];
first = list[0];
size = list.size();

// Map操作
map = {"name": "ETL", "version": "1.0"};
name = map["name"];
map.put("newKey", "value");
```

#### 3.1.4 循环语句

```java
// for循环
sum = 0;
for (i : {1..10}) {
    sum = sum + i;
}

// 遍历集合
result = new java.util.ArrayList();
for (item : dataList) {
    if (item.value > 100) {
        result.add(item);
    }
}
```

### 3.2 典型使用场景

#### 3.2.1 HTTP请求处理

```java
// GET请求提取数据
httpRequest("https://api.example.com/users")
    .header("Authorization", "Bearer " + token)
    .get()
    .extract("data.items")

// POST请求创建资源
httpRequest("https://api.example.com/orders")
    .contentType("application/json")
    .bodyJson({"productId": 123, "quantity": 2})
    .post()
    .extract("orderId", java.lang.Long.class)

// 带重试的请求
httpRequest("https://api.example.com/data")
    .retry(3, 1000)
    .get()
```

#### 3.2.2 数据转换管道

```java
// ETL数据转换
sourceData = inputRecords;

transformedList = new java.util.ArrayList();
for (record : sourceData) {
    transformed = new java.util.HashMap();
    transformed.put("id", record.get("id"));
    transformed.put("processedName", record.get("name").toString().toUpperCase());
    
    amount = ((java.lang.Number) record.get("amount")).doubleValue();
    transformed.put("totalWithTax", amount * 1.15);
    
    transformedList.add(transformed);
}

transformedList
```

#### 3.2.3 条件判断与分类

```java
// 订单优先级分类
orders = orderList;

urgentOrders = new java.util.ArrayList();
normalOrders = new java.util.ArrayList();

for (order : orders) {
    amount = ((java.lang.Number) order.get("amount")).doubleValue();
    days = ((java.lang.Number) order.get("daysSinceCreated")).intValue();
    
    if (order.get("status") == "PENDING" && days > 7) {
        order.put("priority", "URGENT");
        urgentOrders.add(order);
    } else if (amount > 10000) {
        order.put("priority", "HIGH");
        normalOrders.add(order);
    } else {
        order.put("priority", "NORMAL");
        normalOrders.add(order);
    }
}

{"urgent": urgentOrders, "normal": normalOrders}
```

#### 3.2.4 动态聚合计算

```java
// 多维度聚合
byRegion = new java.util.HashMap();

for (sale : salesData) {
    region = sale.get("region");
    amount = ((java.lang.Number) sale.get("amount")).doubleValue();
    
    if (!byRegion.containsKey(region)) {
        byRegion.put(region, new java.util.HashMap());
        byRegion.get(region).put("totalAmount", 0.0);
        byRegion.get(region).put("count", 0);
    }
    
    regionData = byRegion.get(region);
    regionData.put("totalAmount", 
        ((java.lang.Number) regionData.get("totalAmount")).doubleValue() + amount);
    regionData.put("count", 
        ((java.lang.Number) regionData.get("count")).intValue() + 1);
}

byRegion
```

### 3.3 正确用法与错误用法对比

#### ❌ 错误用法

```java
// 错误1: 使用import语句（被安全沙箱禁止）
import java.util.ArrayList;
list = new ArrayList();

// 正确做法: 使用全限定名
list = new java.util.ArrayList();

// 错误2: 使用try-catch（被安全沙箱禁止）
try {
    result = operation();
} catch (e) {
    result = null;
}

// 正确做法: 使用条件判断
result = operation();
if (result == null) {
    result = defaultValue;
}

// 错误3: 使用未定义变量
result = undefinedVar + 1;

// 正确做法: 确保变量已定义
var = 0;
result = var + 1;

// 错误4: 复杂表达式无缓存
// 每次请求都解析表达式，性能差

// 正确做法: 对于频繁使用的表达式，在调用端缓存
```

#### ✅ 正确用法

```java
// 1. HTTP请求提取数据
response = httpRequest("https://api.example.com/data")
    .header("Authorization", "Bearer token")
    .get();

// 提取单个字段
name = response.extract("user.name");

// 提取并转换类型
age = response.extract("user.age", java.lang.Integer.class);

// 提取嵌套对象
address = response.extract("user.address.city");

// 2. 类型安全转换
price = response.extract("price", java.lang.Double.class);
items = response.extract("items", java.util.List.class);

// 3. 使用全限定名创建对象
list = new java.util.ArrayList();
map = new java.util.HashMap();
set = new java.util.HashSet();

// 4. 链式调用避免中间变量
httpRequest("https://api.example.com/users")
    .get()
    .extract("data")
    .asJava("java.util.List<com.example.UserVO>")
```

### 3.4 安全注意事项

#### 3.4.1 禁止的操作

以下操作被安全沙箱拦截：

| 类别 | 禁止内容 | 原因 |
|------|---------|------|
| 关键字 | import, package, interface, enum | 防止定义新类型 |
| 关键字 | try, catch, finally, throw | 防止异常处理绕过 |
| 关键字 | synchronized, volatile | 防止并发问题 |
| 类名 | Runtime, ProcessBuilder | 防止执行系统命令 |
| 类名 | System.exit | 防止终止JVM |
| 类名 | Class, Method, Field | 防止反射调用 |
| 模式 | while(true), for(;;true) | 防止无限循环 |

#### 3.4.2 安全最佳实践

```java
// 1. 不要在表达式中处理敏感信息
// ❌ 错误
password = "secret123";

// ✅ 正确: 从上下文获取
password = context.get("password");

// 2. 使用提取而非返回完整响应
// ❌ 可能暴露敏感字段
response.body()

// ✅ 只提取需要的字段
response.extract("publicField")

// 3. 验证输入数据
amount = data.get("amount");
if (amount == null || !(amount instanceof java.lang.Number)) {
    return 0;
}
((java.lang.Number) amount).doubleValue()
```

---

## 4. API接口详解

### 4.1 表达式执行接口

```http
POST /api/etl/execute
Content-Type: text/plain

<expression>
```

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/etl/execute \
  -H "Content-Type: text/plain" \
  -d 'httpRequest("https://api.example.com/data").get().extract("items")'
```

**响应示例**:
```json
{
  "success": true,
  "result": [...],
  "executionTime": 125
}
```

### 4.2 令牌管理接口

```http
# 注册令牌配置
POST /api/token/register
Content-Type: application/json

{
  "tokenId": "oauth-api",
  "config": {
    "tokenEndpoint": "https://auth.example.com/token",
    "grantType": "client_credentials",
    "clientId": "your-client-id",
    "clientSecret": "your-client-secret"
  }
}

# 获取令牌
GET /api/token/{tokenId}

# 刷新令牌
POST /api/token/{tokenId}/refresh

# 令牌状态
GET /api/token/{tokenId}/status
```

### 4.3 加密服务接口

```http
# 生成AES密钥
POST /api/crypto/aes/generate?keyId=encryption-key

# 加密数据
POST /api/crypto/encrypt
Content-Type: application/json

{
  "keyId": "encryption-key",
  "data": "sensitive data"
}

# 解密数据
POST /api/crypto/decrypt
Content-Type: application/json

{
  "keyId": "encryption-key",
  "encryptedData": "encrypted-base64-string"
}

# 性能基准测试
GET /api/crypto/benchmark
```

### 4.4 回调管理接口

```http
# 注册回调
POST /api/callback/register
Content-Type: application/json

{
  "callbackUrl": "https://your-server/callback",
  "triggerEvent": "DATA_PROCESSED",
  "data": {"result": "success"}
}

# 查询回调状态
GET /api/callback/{callbackId}/status

# 获取回调日志
GET /api/callback/{callbackId}/logs

# 统计信息
GET /api/callback/statistics
```

---

## 5. 安全机制

### 5.1 表达式安全沙箱

安全沙箱执行以下检查：

```
=== 开始安全沙箱检查 ===
1. 表达式长度检查 (最大50000字符)
2. 禁止关键字检查
3. 禁止类名检查
4. 危险模式检查
5. 循环复杂度检查 (最大10层)
6. 嵌套深度检查 (最大20层)
7. 资源使用检查
[检查结果] 表达式安全检查通过 ✅
```

### 5.2 密钥安全存储

```
密钥生命周期:
1. 生成: AES-256/RSA-2048密钥
2. 加密: 使用主密钥加密存储
3. 使用: 运行时解密到内存
4. 清除: 使用后立即清零
5. 轮换: 支持定期轮换策略
```

### 5.3 令牌安全

```
令牌管理特性:
1. 内存安全存储 (char[])
2. 自动过期检测
3. 自动刷新机制
4. 使用后安全清除
```

---

## 6. 性能优化建议

### 6.1 表达式优化

```java
// ❌ 避免复杂单行表达式
result = ((a + b) * c > d ? e : f) + (g < h ? i : j);

// ✅ 拆分为多步骤
temp = (a + b) * c;
result = temp > d ? e : f;
result = result + (g < h ? i : j);
```

### 6.2 HTTP请求优化

```java
// ❌ 多次请求同一接口
user = httpRequest("/api/user").get().extract("data");
profile = httpRequest("/api/user").get().extract("profile");

// ✅ 一次请求获取所有数据
response = httpRequest("/api/user").get();
user = response.extract("data");
profile = response.extract("profile");
```

### 6.3 性能基准

```
安全沙箱检查:
- 简单表达式(5字符): 0.84ms
- 中等表达式(89字符): 0.48ms
- 复杂表达式(391字符): 11.90ms
- 吞吐量: 815 checks/sec

加密服务:
- AES-256加密: 0.22ms
- AES-256解密: 0.12ms
- RSA-2048加密: 0.20ms
- RSA-2048解密: 1.63ms
```

---

## 7. 错误处理

### 7.1 常见错误

| 错误码 | 说明 | 解决方案 |
|--------|------|---------|
| EXPR_001 | 表达式为空 | 检查请求体 |
| EXPR_002 | 表达式过长 | 简化表达式或分段执行 |
| EXPR_003 | 包含禁止关键字 | 使用全限定名替代import |
| EXPR_004 | 包含禁止类名 | 避免使用危险类 |
| EXPR_005 | 循环复杂度过高 | 简化循环嵌套 |
| HTTP_001 | HTTP请求超时 | 增加超时时间或检查网络 |
| HTTP_002 | HTTP请求失败 | 检查URL和认证信息 |
| CRYPTO_001 | 密钥不存在 | 先调用生成密钥接口 |
| CRYPTO_002 | 加解密失败 | 检查密钥和数据格式 |

### 7.2 错误响应格式

```json
{
  "success": false,
  "error": {
    "code": "EXPR_003",
    "message": "表达式包含禁止的关键字: import",
    "details": "请使用全限定名，如 new java.util.ArrayList()"
  }
}
```

---

## 8. 最佳实践

### 8.1 表达式编写规范

1. **使用全限定名**: `new java.util.ArrayList()` 而非 `new ArrayList()`
2. **避免深层嵌套**: 保持嵌套层级不超过5层
3. **类型安全**: 使用 `instanceof` 检查类型
4. **空值处理**: 检查null值避免NPE

### 8.2 HTTP请求规范

1. **设置合理超时**: 根据接口响应时间设置
2. **使用重试机制**: 对不稳定接口启用重试
3. **提取必要字段**: 只提取需要的数据

### 8.3 安全规范

1. **敏感数据处理**: 从上下文获取，不要硬编码
2. **定期密钥轮换**: 设置30-90天轮换周期
3. **监控回调状态**: 定期检查失败回调

---

## 附录: 配置参数

```yaml
etl:
  engine:
    expression-timeout: 5000      # 表达式执行超时(ms)
    max-expression-length: 10000  # 最大表达式长度
    
    security:
      sandbox:
        enabled: true
        max-expression-size: 50000
        max-loop-count: 10
        max-nesting-depth: 20
    
    token:
      default-retries: 3
      refresh-buffer-ms: 60000
    
    crypto:
      aes-key-size: 256
      rsa-key-size: 2048
    
    callback:
      max-retries: 3
      log-retention-days: 30
```

---

**文档版本**: v2.0.0  
**更新日期**: 2026-05-24  
**作者**: ETL Engine Team
