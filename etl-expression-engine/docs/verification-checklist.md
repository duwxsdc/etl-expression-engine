# ETL Expression Engine - 验证检查清单

## 版本信息
- 版本号: 1.0.0
- 更新日期: 2026-05-23
- 更新内容: 结构化日志、MVEL HTTP请求功能

---

## 一、编译打包验证

### 1.1 编译检查
| 检查项 | 预期结果 | 实际结果 | 状态 |
|--------|----------|----------|------|
| Maven编译无错误 | BUILD SUCCESS | | [ ] |
| 无编译警告（除preview警告） | 仅JDK preview警告 | | [ ] |
| 所有源文件编译通过 | 44个源文件 | | [ ] |
| 测试代码编译通过 | 13个测试文件 | | [ ] |

### 1.2 打包检查
| 检查项 | 预期结果 | 实际结果 | 状态 |
|--------|----------|----------|------|
| JAR包生成成功 | etl-expression-engine-1.0.0.jar | | [ ] |
| Spring Boot重新打包成功 | .jar.original文件存在 | | [ ] |
| JAR包可执行 | java --enable-preview -jar启动成功 | | [ ] |

---

## 二、应用启动验证

### 2.1 启动检查
| 检查项 | 预期结果 | 实际结果 | 状态 |
|--------|----------|----------|------|
| Spring Boot启动成功 | Started EtlExpressionEngineApplication | | [ ] |
| Tomcat端口监听 | 8080端口 | | [ ] |
| H2数据库初始化 | HikariPool-1 Start completed | | [ ] |
| MVEL引擎初始化 | MVEL表达式引擎初始化完成 | | [ ] |
| HTTP函数注册 | HTTP函数初始化完成 | | [ ] |
| SQL函数注册 | SQL函数和HTTP函数已注册 | | [ ] |

### 2.2 组件加载检查
| 组件 | 预期状态 | 实际状态 | 状态 |
|------|----------|----------|------|
| EtlContextManager | 已启动 | | [ ] |
| SessionContextManager | 已启动 | | [ ] |
| MvelExpressionEngine | 已初始化 | | [ ] |
| MvelSecuritySandbox | 已加载 | | [ ] |

---

## 三、结构化日志验证

### 3.1 日志格式检查
| 检查项 | 预期结果 | 实际结果 | 状态 |
|--------|----------|----------|------|
| 日志为JSON格式 | 可被JSON解析器解析 | | [ ] |
| 包含timestamp字段 | ISO-8601格式，精确到毫秒 | | [ ] |
| 包含requestId字段 | REQ-XXXXXXXX格式 | | [ ] |
| 包含stage字段 | 处理阶段标识 | | [ ] |
| 包含status字段 | STARTED/SUCCESS/ERROR | | [ ] |

### 3.2 请求生命周期追踪检查
| 阶段 | 日志stage值 | 是否记录 | 状态 |
|------|-------------|----------|------|
| 请求接收 | REQUEST_RECEIVED | | [ ] |
| 会话创建 | SESSION_CREATED | | [ ] |
| 会话解析 | SESSION_RESOLVED | | [ ] |
| 参数解析 | PARAMS_PARSED | | [ ] |
| 执行开始 | EXECUTION_START | | [ ] |
| 会话更新 | SESSION_UPDATED | | [ ] |
| 请求完成 | REQUEST_COMPLETED | | [ ] |
| 错误处理 | EXECUTION_ERROR | | [ ] |

### 3.3 测试用例
```
测试步骤：
1. 发送POST请求到 /etl/expression/execute
2. 检查日志输出是否包含完整的请求生命周期
3. 验证requestId在整个生命周期中保持一致
4. 验证durationMs在REQUEST_COMPLETED阶段正确记录
```

---

## 四、MVEL表达式执行验证

### 4.1 基础表达式测试
| 测试表达式 | 预期结果 | 实际结果 | 状态 |
|------------|----------|----------|------|
| `1 + 2 + 3` | 6 | | [ ] |
| `10 * 5 - 3` | 47 | | [ ] |
| `"Hello" + " World"` | "Hello World" | | [ ] |
| `a = 10; a * 2` | 20 | | [ ] |

### 4.2 变量赋值测试
| 测试表达式 | 预期结果 | 实际结果 | 状态 |
|------------|----------|----------|------|
| `x = 100; y = 200; x + y` | 300 | | [ ] |
| `name = "ETL"; name` | "ETL" | | [ ] |

### 4.3 复杂表达式测试
| 测试表达式 | 预期结果 | 实际结果 | 状态 |
|------------|----------|----------|------|
| `Math.max(10, 20)` | 20 | | [ ] |
| `Math.sqrt(16)` | 4.0 | | [ ] |
| `new java.util.ArrayList().add("test")` | true | | [ ] |

---

## 五、HTTP请求功能验证

### 5.1 GET请求测试
| 测试表达式 | 预期结果 | 实际结果 | 状态 |
|------------|----------|----------|------|
| `http('https://httpbin.org/get').get().statusCode()` | 200 | | [ ] |
| `http('https://httpbin.org/get').queryVariable('name', 'test').get().body().asString()` | 包含args.name=test | | [ ] |

### 5.2 POST请求测试
| 测试表达式 | 预期结果 | 实际结果 | 状态 |
|------------|----------|----------|------|
| `http('https://httpbin.org/post').bodyJson({'key':'value'}).post().statusCode()` | 200 | | [ ] |
| `http('https://httpbin.org/post').bodyForm({'name':'ETL'}).post().body().asMap()` | 包含form.name=ETL | | [ ] |

### 5.3 请求头测试
| 测试表达式 | 预期结果 | 实际结果 | 状态 |
|------------|----------|----------|------|
| `http('https://httpbin.org/headers').header('X-Custom', 'TestValue').get().body().asString()` | 包含X-Custom | | [ ] |

### 5.4 认证测试
| 测试表达式 | 预期结果 | 实际结果 | 状态 |
|------------|----------|----------|------|
| `http('https://httpbin.org/basic-auth/user/pass').basicAuth('user', 'pass').get().statusCode()` | 200 | | [ ] |

### 5.5 响应解析测试
| 方法 | 测试表达式 | 预期结果 | 状态 |
|------|------------|----------|------|
| asString() | `.get().body().asString()` | String类型 | [ ] |
| asMap() | `.get().body().asMap()` | Map类型 | [ ] |
| asJson() | `.get().body().asJson()` | JsonNode类型 | [ ] |

---

## 六、会话管理验证

### 6.1 会话创建测试
| 测试接口 | 预期结果 | 实际结果 | 状态 |
|----------|----------|----------|------|
| POST /etl/expression/session/new | 返回sessionId | | [ ] |
| POST /etl/expression/execute（无sessionId） | 自动创建会话 | | [ ] |

### 6.2 会话查询测试
| 测试接口 | 预期结果 | 实际结果 | 状态 |
|----------|----------|----------|------|
| GET /etl/expression/session/{sessionId} | 返回会话信息 | | [ ] |
| GET /etl/expression/session/invalid | 返回not found | | [ ] |

### 6.3 会话销毁测试
| 测试接口 | 预期结果 | 实际结果 | 状态 |
|----------|----------|----------|------|
| DELETE /etl/expression/session/{sessionId} | success=true | | [ ] |

---

## 七、错误处理验证

### 7.1 表达式错误测试
| 测试场景 | 预期结果 | 实际结果 | 状态 |
|----------|----------|----------|------|
| 空表达式 | success=false | | [ ] |
| 超长表达式 | success=false | | [ ] |
| 不安全表达式 | success=false | | [ ] |
| 语法错误表达式 | success=false | | [ ] |

### 7.2 HTTP错误测试
| 测试场景 | 预期结果 | 实际结果 | 状态 |
|----------|----------|----------|------|
| 无效URL | 异常捕获 | | [ ] |
| 连接超时 | 超时处理 | | [ ] |
| 404响应 | statusCode=404 | | [ ] |

---

## 八、性能验证

### 8.1 响应时间测试
| 测试场景 | 预期响应时间 | 实际响应时间 | 状态 |
|----------|--------------|--------------|------|
| 简单表达式执行 | < 100ms | | [ ] |
| HTTP GET请求 | < 3000ms | | [ ] |
| 会话创建 | < 50ms | | [ ] |

### 8.2 并发测试
| 测试场景 | 预期结果 | 实际结果 | 状态 |
|----------|----------|----------|------|
| 10并发请求 | 全部成功 | | [ ] |
| 50并发请求 | 全部成功 | | [ ] |

---

## 九、验证结果汇总

| 验证模块 | 总用例数 | 通过数 | 失败数 | 通过率 |
|----------|----------|--------|--------|--------|
| 编译打包验证 | 7 | | | |
| 应用启动验证 | 10 | | | |
| 结构化日志验证 | 11 | | | |
| MVEL表达式验证 | 9 | | | |
| HTTP请求验证 | 12 | | | |
| 会话管理验证 | 5 | | | |
| 错误处理验证 | 7 | | | |
| 性能验证 | 5 | | | |
| **总计** | **66** | | | |

---

## 十、验证执行记录

### 执行日期：2026-05-23

#### 编译打包验证结果
```
[INFO] BUILD SUCCESS
[INFO] Total time:  19.470 s
```

#### 应用启动验证结果
```
Started EtlExpressionEngineApplication in 4.197 seconds
Tomcat started on port 8080 (http)
```

#### 结构化日志验证结果
```
请求ID: REQ-26CCB8D7
生命周期: REQUEST_RECEIVED → SESSION_CREATED → PARAMS_PARSED → EXECUTION_START → SESSION_UPDATED → REQUEST_COMPLETED
日志格式: JSON结构化
```

#### MVEL表达式验证结果
```
表达式: 1 + 2 + 3
结果: 6
状态: success=true
```

#### HTTP请求验证结果
```
GET请求: https://httpbin.org/get?name=test
响应状态: 200
响应内容: 包含args.name=test
```

---

## 十一、问题记录

| 序号 | 问题描述 | 严重程度 | 解决方案 | 状态 |
|------|----------|----------|----------|------|
| 1 | | | | |

---

**验证人员签名：** ________________

**验证日期：** ________________

**审核人员签名：** ________________

**审核日期：** ________________
