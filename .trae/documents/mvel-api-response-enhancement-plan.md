# MVEL表达式请求处理模块优化实施计划（极简版）

## 一、核心设计

### 1.1 ExecuteResult增加扩展字段

```java
// ExecuteResult.java
public record ExecuteResult(
    String sessionId,
    String originExpr,
    boolean success,
    Object finalResult,
    String errorMsg,
    Map<String, Object> contextVars,
    ExtendedInfo extended  // 新增，默认null
) {
    // 原有方法不变，extended默认null
    public static ExecuteResult success(String sessionId, String originExpr, 
                                        Object finalResult, Map<String, Object> contextVars) {
        return new ExecuteResult(sessionId, originExpr, true, finalResult, null, 
                                 contextVars != null ? contextVars : Collections.emptyMap(), null);
    }
    
    public static ExecuteResult failure(String sessionId, String originExpr, 
                                        String errorMsg, Map<String, Object> contextVars) {
        return new ExecuteResult(sessionId, originExpr, false, null, errorMsg,
                                 contextVars != null ? contextVars : Collections.emptyMap(), null);
    }
}
```

### 1.2 ExtendedInfo极简版（只保留6个核心字段）

```java
// ExtendedInfo.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtendedInfo implements Serializable {
    private String requestId;                  // 请求ID
    private Integer apiStatusCode;             // API状态码：200/400/500
    private Long executionTimeMs;              // 执行耗时
    private Integer thirdPartyStatusCode;      // 第三方状态码（可选）
    private Integer sqlAffectedRows;           // SQL影响行数（可选）
    private String errorDetail;                // 错误详情（可选）
}
```

### 1.3 GlobalContext极简版

```java
// GlobalContext.java
public final class GlobalContext {
    private static final ThreadLocal<GlobalContext> HOLDER = new ThreadLocal<>();
    
    private final String requestId;
    private final long startTime;
    private Integer thirdPartyStatusCode;
    private Integer sqlAffectedRows;
    
    private GlobalContext(String requestId) {
        this.requestId = requestId;
        this.startTime = System.currentTimeMillis();
    }
    
    public static void init(String requestId) {
        HOLDER.set(new GlobalContext(requestId));
    }
    
    public static GlobalContext current() {
        return HOLDER.get();
    }
    
    public static void clear() {
        HOLDER.remove();
    }
    
    public ExtendedInfo buildExtendedInfo() {
        return ExtendedInfo.builder()
            .requestId(requestId)
            .apiStatusCode(200)
            .executionTimeMs(System.currentTimeMillis() - startTime)
            .thirdPartyStatusCode(thirdPartyStatusCode)
            .sqlAffectedRows(sqlAffectedRows)
            .build();
    }
    
    public ExtendedInfo buildExtendedInfo(String errorDetail) {
        return ExtendedInfo.builder()
            .requestId(requestId)
            .apiStatusCode(500)
            .executionTimeMs(System.currentTimeMillis() - startTime)
            .errorDetail(errorDetail)
            .build();
    }
    
    public String getRequestId() { return requestId; }
    public void setThirdPartyStatusCode(int code) { this.thirdPartyStatusCode = code; }
    public void setSqlAffectedRows(int rows) { this.sqlAffectedRows = rows; }
}
```

### 1.4 异常类极简版

```java
// MvelExecutionException.java
@Getter
public class MvelExecutionException extends RuntimeException {
    private final String requestId;
    
    public MvelExecutionException(String message, Throwable cause) {
        super(message, cause);
        GlobalContext ctx = GlobalContext.current();
        this.requestId = ctx != null ? ctx.getRequestId() : null;
    }
}
```

### 1.5 日志器极简版

```java
// ChainCallLogger.java
public final class ChainCallLogger {
    private ChainCallLogger() {}
    
    public static void logHttpCall(String url, int statusCode, long durationMs) {
        GlobalContext ctx = GlobalContext.current();
        if (ctx != null) ctx.setThirdPartyStatusCode(statusCode);
        log.info("[ChainCallLogger] HTTP调用: url={}, status={}, duration={}ms", url, statusCode, durationMs);
    }
    
    public static void logSqlExecution(String sql, int affectedRows, long durationMs) {
        GlobalContext ctx = GlobalContext.current();
        if (ctx != null) ctx.setSqlAffectedRows(affectedRows);
        log.info("[ChainCallLogger] SQL执行: affectedRows={}, duration={}ms", affectedRows, durationMs);
    }
}
```

---

## 二、实施步骤

### 步骤1：创建基础类
- `model/ExtendedInfo.java`
- `model/ExecuteResult.java`（修改，增加extended字段）

### 步骤2：创建全局上下文
- `context/GlobalContext.java`

### 步骤3：创建异常类
- `exception/MvelExecutionException.java`
- `exception/GlobalExceptionHandler.java`

### 步骤4：创建日志器
- `util/ChainCallLogger.java`

### 步骤5：集成
- `mvel/MvelExpressionEngine.java`
- `controller/EtlExpressionController.java`

### 步骤6：编译验证

---

## 三、文件清单

### 新增文件（5个）
1. `model/ExtendedInfo.java`
2. `context/GlobalContext.java`
3. `exception/MvelExecutionException.java`
4. `exception/GlobalExceptionHandler.java`
5. `util/ChainCallLogger.java`

### 修改文件（2个）
1. `model/ExecuteResult.java`
2. `controller/EtlExpressionController.java`

---

## 四、响应示例

### 成功响应
```json
{
  "sessionId": "ETL-ABC123",
  "originExpr": "result = sql(\"SELECT...\")",
  "success": true,
  "finalResult": [...],
  "errorMsg": null,
  "contextVars": {...},
  "extended": {
    "requestId": "REQ-XYZ789",
    "apiStatusCode": 200,
    "executionTimeMs": 50,
    "thirdPartyStatusCode": 200,
    "sqlAffectedRows": 5,
    "errorDetail": null
  }
}
```

### 失败响应
```json
{
  "sessionId": "ETL-ABC123",
  "originExpr": "result = sql(\"SELECT...\")",
  "success": false,
  "finalResult": null,
  "errorMsg": "表不存在",
  "contextVars": {},
  "extended": {
    "requestId": "REQ-XYZ789",
    "apiStatusCode": 500,
    "executionTimeMs": 10,
    "thirdPartyStatusCode": null,
    "sqlAffectedRows": null,
    "errorDetail": "表不存在: invalid_table"
  }
}
```
