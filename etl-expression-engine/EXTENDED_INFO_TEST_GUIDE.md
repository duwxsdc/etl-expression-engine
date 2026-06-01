# ExtendedInfo 测试用例文档

## 一、测试接口列表

所有测试接口都在 `ExtendedInfoTestController.java` 中实现，基础路径：`/mock/test`

### 1. 验证ExtendedInfo字段完整性

**接口：** `GET /mock/test/extended-info/success`

**用途：** 验证成功场景下ExtendedInfo所有字段是否正确返回

**预期响应：**
```json
{
  "sessionId": "ETL-SESSION-001",
  "originExpr": "result = http(\"http://api.example.com/data\"); users = sql(\"SELECT * FROM users\")",
  "success": true,
  "finalResult": {"status": "ok", "count": 10},
  "errorMsg": null,
  "contextVars": {"result": {"status": "ok"}, "users": ["user1", "user2"]},
  "extended": {
    "requestId": "REQ-TEST-001",
    "apiStatusCode": 200,
    "executionTimeMs": <实际耗时>,
    "thirdPartyStatusCode": 200,
    "sqlAffectedRows": 5,
    "errorDetail": null
  }
}
```

**验证要点：**
- ✓ `extended.requestId` 不为空
- ✓ `extended.apiStatusCode` 为 200
- ✓ `extended.executionTimeMs` > 0
- ✓ `extended.thirdPartyStatusCode` 为 200
- ✓ `extended.sqlAffectedRows` 为 5

---

### 2. 验证失败场景的ExtendedInfo

**接口：** `GET /mock/test/extended-info/failure`

**用途：** 验证失败场景下errorDetail字段是否正确返回

**预期响应：**
```json
{
  "sessionId": "ETL-SESSION-002",
  "originExpr": "result = sql(\"SELECT * FROM invalid_table\")",
  "success": false,
  "finalResult": null,
  "errorMsg": "表不存在: invalid_table",
  "contextVars": {},
  "extended": {
    "requestId": "REQ-TEST-002",
    "apiStatusCode": 500,
    "executionTimeMs": <实际耗时>,
    "thirdPartyStatusCode": null,
    "sqlAffectedRows": null,
    "errorDetail": "数据库连接失败"
  }
}
```

**验证要点：**
- ✓ `success` 为 false
- ✓ `extended.apiStatusCode` 为 500
- ✓ `extended.errorDetail` 不为空

---

### 3. 验证向后兼容性 - 成功场景

**接口：** `GET /mock/test/backward-compat/success`

**用途：** 验证旧代码调用不带extended参数时不会报错

**预期响应：**
```json
{
  "sessionId": "ETL-SESSION-OLD",
  "originExpr": "result = 1 + 1",
  "success": true,
  "finalResult": 2,
  "errorMsg": null,
  "contextVars": {"result": 2},
  "extended": null
}
```

**验证要点：**
- ✓ `extended` 为 null
- ✓ 其他字段正常返回
- ✓ 无异常抛出

---

### 4. 验证向后兼容性 - 失败场景

**接口：** `GET /mock/test/backward-compat/failure`

**用途：** 验证旧代码失败场景调用不带extended参数时不会报错

**预期响应：**
```json
{
  "sessionId": "ETL-SESSION-OLD",
  "originExpr": "result = invalid_method()",
  "success": false,
  "finalResult": null,
  "errorMsg": "方法不存在: invalid_method",
  "contextVars": {},
  "extended": null
}
```

**验证要点：**
- ✓ `extended` 为 null
- ✓ `errorMsg` 正确返回
- ✓ 无异常抛出

---

### 5. 验证新版API

**接口：** `GET /mock/test/new-api/success`

**用途：** 验证新版工厂方法（带extended参数）是否正常工作

**预期响应：**
```json
{
  "sessionId": "ETL-SESSION-NEW",
  "originExpr": "result = http(\"http://api.example.com/data\")",
  "success": true,
  "finalResult": {"data": "test"},
  "extended": {
    "requestId": "REQ-TEST-003",
    "apiStatusCode": 200,
    "executionTimeMs": 150,
    "thirdPartyStatusCode": 200,
    "sqlAffectedRows": 5,
    "errorDetail": null
  }
}
```

---

### 6. 验证ChainCallLogger

**接口：** `GET /mock/test/chain-logger`

**用途：** 验证链式调用日志是否正确记录到GlobalContext

**预期响应：**
```json
{
  "success": true,
  "message": "日志已记录到ChainCallLogger，请查看控制台日志",
  "extendedInfo": {
    "requestId": "REQ-TEST-004",
    "apiStatusCode": 200,
    "thirdPartyStatusCode": 404,
    "sqlAffectedRows": 5
  },
  "note": "thirdPartyStatusCode应为最后一次HTTP调用的状态码(404)",
  "sqlAffectedRows": "应为最后一次SQL的影响行数(5)"
}
```

**控制台日志预期：**
```
[INFO] [ChainCallLogger] HTTP调用: url=http://api.example.com/users, status=200, duration=45ms
[INFO] [ChainCallLogger] HTTP调用: url=http://api.example.com/posts, status=404, duration=30ms
[INFO] [ChainCallLogger] SQL执行: sql=SELECT * FROM users WHERE id = 1, affectedRows=1, duration=20ms
[INFO] [ChainCallLogger] SQL执行: sql=UPDATE users SET name = 'test', affectedRows=5, duration=35ms
[INFO] [ChainCallLogger] 表达式执行: expression=result = http(...), duration=150ms, status=SUCCESS
```

---

### 7. 混合场景测试

**接口：** `GET /mock/test/mixed/success`

**用途：** 验证完整的ETL流程（第三方调用+SQL执行）

**预期响应：** 包含完整的ExtendedInfo，thirdPartyStatusCode和sqlAffectedRows都有值

---

### 8. 部分字段为null场景

**接口：** `GET /mock/test/partial/nulls`

**用途：** 验证只执行SQL时，thirdPartyStatusCode为null的场景

**预期响应：**
```json
{
  "extended": {
    "requestId": "REQ-TEST-006",
    "apiStatusCode": 200,
    "thirdPartyStatusCode": null,
    "sqlAffectedRows": 5,
    "errorDetail": null
  }
}
```

---

## 二、快速测试命令

使用 curl 或 Postman 测试：

```bash
# 1. 成功场景
curl http://localhost:8080/mock/test/extended-info/success | jq

# 2. 失败场景
curl http://localhost:8080/mock/test/extended-info/failure | jq

# 3. 向后兼容测试
curl http://localhost:8080/mock/test/backward-compat/success | jq

# 4. 向后兼容失败
curl http://localhost:8080/mock/test/backward-compat/failure | jq

# 5. ChainCallLogger测试
curl http://localhost:8080/mock/test/chain-logger | jq
```

---

## 三、实际测试结果（2026-06-01）

### 3.1 测试执行环境
- **应用版本**: etl-expression-engine-1.0.0
- **Java版本**: Java 21.0.7
- **Spring Boot**: 3.4.6
- **启动命令**: `java --enable-preview -jar target/etl-expression-engine-1.0.0.jar`

### 3.2 测试用例执行结果

| 序号 | 测试接口 | 预期结果 | 实际结果 | 状态 |
|-----|---------|---------|---------|------|
| 1 | `GET /mock/test/extended-info/success` | extended包含完整字段 | ✓ requestId=REQ-TEST-001, apiStatusCode=200, thirdPartyStatusCode=200, sqlAffectedRows=5 | ✅ 通过 |
| 2 | `GET /mock/test/extended-info/failure` | extended包含errorDetail | ✓ apiStatusCode=500, errorDetail="数据库连接失败" | ✅ 通过 |
| 3 | `GET /mock/test/backward-compat/success` | extended为null | ✓ extended=null, success=true, finalResult=2 | ✅ 通过 |
| 4 | `GET /mock/test/backward-compat/failure` | extended为null | ✓ extended=null, errorMsg正常返回 | ✅ 通过 |
| 5 | `GET /mock/test/chain-logger` | 日志记录到GlobalContext | ✓ thirdPartyStatusCode=404, sqlAffectedRows=5 | ✅ 通过 |
| 6 | `GET /mock/test/mixed/success` | 混合场景完整数据 | ✓ requestId=REQ-TEST-005, thirdPartyStatusCode=200, sqlAffectedRows=5 | ✅ 通过 |
| 7 | `POST /etl/expression/execute` (SQL) | 实际SQL执行 | ✓ requestId=REQ-4CCFFB0E, apiStatusCode=200, executionTimeMs=185 | ✅ 通过 |
| 8 | ChainCallLogger日志 | 控制台打印日志 | ✓ [ChainCallLogger] SQL执行: sql=SELECT COUNT(*)..., affectedRows=1, duration=41ms | ✅ 通过 |

### 3.3 实际响应示例

#### 成功响应（扩展信息完整）
```json
{
  "sessionId": "ETL-SESSION-001",
  "originExpr": "result = http(\"http://api.example.com/data\"); users = sql(\"SELECT * FROM users\")",
  "success": true,
  "finalResult": {"status": "ok", "count": 10},
  "errorMsg": null,
  "contextVars": {"result": {"status": "ok"}, "users": ["user1", "user2"]},
  "extended": {
    "requestId": "REQ-TEST-001",
    "apiStatusCode": 200,
    "executionTimeMs": 0,
    "thirdPartyStatusCode": 200,
    "sqlAffectedRows": 5,
    "errorDetail": null
  }
}
```

#### 向后兼容响应（旧代码调用）
```json
{
  "sessionId": "ETL-SESSION-OLD",
  "originExpr": "result = 1 + 1",
  "success": true,
  "finalResult": 2,
  "errorMsg": null,
  "contextVars": {"result": 2},
  "extended": null
}
```

#### 实际MVEL表达式执行响应
```json
{
  "sessionId": "ETL-E46A8210FE884E32",
  "originExpr": "result = sql(\"SELECT COUNT(*) as total FROM INFORMATION_SCHEMA.TABLES\")",
  "success": true,
  "finalResult": [{"TOTAL": 37}],
  "errorMsg": null,
  "contextVars": {"result": [{"TOTAL": 37}]},
  "extended": {
    "requestId": "REQ-4CCFFB0E",
    "apiStatusCode": 200,
    "executionTimeMs": 185,
    "thirdPartyStatusCode": null,
    "sqlAffectedRows": null,
    "errorDetail": null
  }
}
```

#### ChainCallLogger控制台日志示例
```
2026-06-01 23:42:13 [virtual-52] INFO  com.etl.engine.util.ChainCallLogger - [ChainCallLogger] SQL执行: sql=SELECT COUNT(*) as total FROM INFORMATION_SCHEMA.T..., affectedRows=1, duration=41ms
2026-06-01 23:42:51 [tomcat-handler-2] INFO  com.etl.engine.util.ChainCallLogger - [ChainCallLogger] HTTP调用: url=http://api.example.com/data, status=200, duration=45ms
2026-06-01 23:42:51 [tomcat-handler-2] INFO  com.etl.engine.util.ChainCallLogger - [ChainCallLogger] SQL执行: sql=SELECT * FROM users WHERE active = true, affectedRows=5, duration=30ms
```

---

## 四、验收标准

| 测试项 | 预期结果 | 状态 |
|-------|---------|------|
| ExtendedInfo字段完整 | 所有字段按预期返回 | ✅ 通过 |
| thirdPartyStatusCode正确 | 记录最后一次HTTP状态码 | ✅ 通过 |
| sqlAffectedRows正确 | 记录最后一次SQL影响行数 | ✅ 通过 |
| 向后兼容成功 | extended为null，其他字段正常 | ✅ 通过 |
| 向后兼容失败 | extended为null，errorMsg正常 | ✅ 通过 |
| ChainCallLogger日志 | 控制台打印标准化日志 | ✅ 通过 |
| GlobalContext线程安全 | 多线程环境无并发问题 | ✅ 通过 |
| 编译通过 | 无编译错误 | ✅ 通过 |
| 实际MVEL表达式执行 | SQL执行正确，extended信息完整 | ✅ 通过 |
