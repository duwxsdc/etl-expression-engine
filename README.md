# ETL Expression Engine

ETL流程编排专用表达式参数引擎，提供安全的MVEL表达式执行环境，支持SQL查询和会话管理功能。

## 项目简介

ETL Expression Engine是一个面向ETL数据集成场景的表达式执行服务，具备以下特点：

- **安全可控**：三层安全防护机制（沙箱关键字过滤+超时控制+SQL只读限制）
- **高性能**：基于JDK 21虚拟线程实现轻量级并发
- **会话隔离**：每个用户会话拥有独立的变量上下文
- **弹性部署**：支持Redis分布式存储和本地内存降级

## 快速入门

### 环境要求

- JDK 21+
- Maven 3.8+
- Redis 6.0+（可选，用于分布式部署）

### 启动方式

```bash
# 进入项目目录
cd etl-expression-engine

# 编译打包
mvn clean package -DskipTests

# 启动应用
java --enable-preview -jar target/etl-expression-engine-1.0.0.jar
```

### 访问地址

启动后访问：`http://localhost:8080`

## 核心功能

- **MVEL表达式计算**：支持多行表达式顺序执行、变量赋值、算术/逻辑运算
- **安全SQL查询**：通过`sql()`和`sqlValue()`函数执行只读查询
- **会话管理**：Redis分布式存储 + 自动过期 + 本地内存降级
- **安全机制**：MVEL沙箱、执行超时、表达式长度限制

## 项目结构

```
20260428/
├── etl-expression-engine/          # 主应用模块
│   ├── src/main/java/com/etl/engine/  # Java源代码
│   ├── src/main/resources/         # 配置文件
│   ├── src/test/                   # 测试代码
│   ├── docs/                       # 项目文档
│   │   ├── architecture.md         # 系统架构设计文档
│   │   ├── api.md                  # API接口文档
│   │   ├── deployment.md           # 安装部署指南
│   │   ├── user-guide.md           # 用户操作手册
│   │   └── dev-guide.md            # 开发维护文档
│   └── README.md                   # 项目说明
└── README.md                       # 根目录说明
```

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端 | Spring Boot | 3.4.6 |
| JDK | JDK | 21 |
| 表达式引擎 | MVEL2 | 2.5.2.Final |
| 数据库 | H2 | 2.2.224 |
| 缓存 | Redis | 6.0+ |
| 构建工具 | Maven | 3.8+ |

## 文档目录

- [系统架构设计文档](etl-expression-engine/docs/architecture.md)
- [API接口文档](etl-expression-engine/docs/api.md)
- [安装部署指南](etl-expression-engine/docs/deployment.md)
- [用户操作手册](etl-expression-engine/docs/user-guide.md)
- [开发维护文档](etl-expression-engine/docs/dev-guide.md)

## 许可证

© 2026 ETL Expression Engine. All rights reserved.