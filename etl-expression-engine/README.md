# ETL流程编排专用表达式参数引擎

## 项目概述

ETL表达式参数引擎是一个基于SpringBoot 3.2.0和JDK 17构建的高性能表达式计算引擎，专为ETL数据编排场景设计。支持动态参数配置、变量定义、表达式计算以及SQL查询功能，通过WebSocket实现实时交互。

## 技术栈

- **JDK 17** - 核心运行环境
- **SpringBoot 3.2.0** - 核心框架
- **MVEL 2.5.2** - 表达式引擎
- **WebSocket** - 实时通信
- **H2 Database** - 嵌入式数据库（演示用）

## 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                      Frontend Console                        │
│                  (HTML + CSS + JavaScript)                   │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ WebSocket
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                  WebSocket Handler Layer                     │
│              ExpressionWebSocketHandler.java                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Context Management                        │
│   ┌─────────────────────┐  ┌─────────────────────┐         │
│   │ SessionContextManager│  │   ContextHolder     │         │
│   │   (Session管理)       │  │   (ThreadLocal)     │         │
│   └─────────────────────┘  └─────────────────────┘         │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     Engine Layer                             │
│   ┌─────────────────────┐  ┌─────────────────────┐         │
│   │  MvelSandboxEngine  │  │ SqlExecutionEngine  │         │
│   │   (表达式计算)        │  │    (SQL查询)        │         │
│   └─────────────────────┘  └─────────────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

## 快速开始

### 1. 环境要求
- JDK 17+
- Maven 3.8+

### 2. 编译打包
```bash
cd etl-expression-engine
mvn clean package -DskipTests
```

### 3. 启动服务
```bash
java -jar target/etl-expression-engine-1.0.0.jar
```

或者双击 `start.bat` 文件启动

### 4. 访问控制台
打开浏览器访问: http://localhost:8080

## 核心功能

### 1. 表达式计算
支持MVEL2语法，包括：
- 变量赋值: `a=100`
- 算术运算: `a+b`, `a*b`
- 逻辑运算: `a>b`, `flag1&&flag2`
- 三元运算: `score>=60?"pass":"fail"`
- 多行执行: `a=10; b=20; a+b`

### 2. 会话隔离
- 每个WebSocket连接独立会话
- 变量上下文完全隔离
- 会话超时自动清理

### 3. SQL查询
- 支持SELECT只读查询
- 拦截DML/DDL危险语句
- SQL注入防护

### 4. 安全沙箱
- 禁止危险类和API
- 表达式超时保护
- 表达式长度限制

## 表达式语法手册

### 变量赋值
```
a = 100
name = "etl"
flag = true
```

### 算术运算
```
a + b
a - b
a * b
a / b
a % b
```

### 逻辑运算
```
a > b
a >= b
a < b
a <= b
a == b
a != b
flag1 && flag2
flag1 || flag2
!flag
```

### 三元运算
```
score >= 60 ? "pass" : "fail"
a > b ? a : b
```

### 对象操作
```
user['name']
list[0]
```

### 多行表达式
```
a = 10; b = 20; c = a + b; c * 2
```

### SQL查询
```
SQL: SELECT * FROM etl_config
SQL: SELECT task_name, status FROM etl_task WHERE priority = 1
```

## 使用示例

### 示例1: 基本计算
```
> a = 100
Result: 100

> b = 200
Result: 200

> a + b
Result: 300
```

### 示例2: 三元运算
```
> score = 85
Result: 85

> score >= 60 ? "及格" : "不及格"
Result: "及格"
```

### 示例3: 多行执行
```
> x = 10; y = 20; z = x + y; z * 2
Result: 60
Assigned: x=10, y=20, z=30
```

### 示例4: SQL查询
```
> SQL: SELECT * FROM etl_config
Result: [{"ID":1,"CONFIG_KEY":"batch.size",...}]
```

## WebSocket接口

### 连接地址
```
ws://localhost:8080/ws/expression
```

### 消息格式

#### 执行请求
```json
{
  "type": "EXECUTE",
  "content": "a=100; a*2"
}
```

#### 执行响应
```json
{
  "type": "RESULT",
  "sessionId": "SESSION-ABC123",
  "data": {
    "success": true,
    "result": 200,
    "assignedVariables": {"a": 100}
  }
}
```

## 配置说明

```yaml
etl:
  engine:
    expression-timeout: 5000    # 表达式执行超时(毫秒)
    max-expression-length: 10000 # 最大表达式长度
    enable-sql-execution: true   # 是否启用SQL执行
    sql-readonly: true           # SQL只读模式
```

## 测试覆盖

运行测试:
```bash
mvn test
```

项目包含完整的单元测试:
- `MvelSandboxEngineTest` - 表达式引擎测试
- `SqlExecutionEngineTest` - SQL引擎测试
- `SessionContextManagerTest` - 会话管理测试
- `ContextHolderTest` - ThreadLocal上下文测试

## 安全机制

### MVEL沙箱安全
- 禁止危险关键字: import, package, new, class等
- 禁止危险类: Runtime, System, ProcessBuilder, File等
- 表达式超时保护(默认5秒)
- 表达式长度限制(默认10000字符)

### SQL安全
- 只允许SELECT查询
- 拦截INSERT/UPDATE/DELETE/DROP等语句
- SQL注入防护
- 查询超时限制

## 性能优化

1. 线程池处理高并发请求
2. ConcurrentHashMap保证线程安全
3. 会话超时自动清理(默认30分钟)
4. 表达式编译缓存优化

## 注意事项

1. 会话隔离，不同浏览器连接互不影响
2. SQL仅支持只读查询，不允许修改操作
3. 表达式执行有超时限制，防止死循环
4. H2数据库控制台: http://localhost:8080/h2-console (JDBC URL: jdbc:h2:mem:testdb)

## License

MIT License
