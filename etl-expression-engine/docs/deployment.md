# ETL Expression Engine - 安装部署指南

---

## 目录

- [环境要求](#环境要求)
- [编译打包](#编译打包)
- [配置说明](#配置说明)
- [启动方式](#启动方式)
- [停止方式](#停止方式)
- [Docker部署方案](#docker部署方案)
- [Kubernetes部署方案](#kubernetes部署方案)
- [常见问题排查](#常见问题排查)
- [部署检查清单](#部署检查清单)

---

## 1. 环境要求

### 1.1 必需环境

| 软件 | 最低版本 | 推荐版本 | 说明 |
|------|----------|----------|------|
| JDK | 21 | 21+ | 必须使用JDK 21，系统依赖虚拟线程、ScopedValue、Record等特性 |
| Maven | 3.8 | 3.9+ | 项目构建工具 |

### 1.2 可选环境

| 软件 | 最低版本 | 说明 |
|------|----------|------|
| Redis | 6.0 | 分布式会话存储。不安装时系统自动降级为本地内存模式 |

### 1.3 操作系统

支持以下操作系统：

- Windows 10/11 / Windows Server 2019+
- Linux（CentOS 7+, Ubuntu 18.04+）
- macOS 12+

### 1.4 硬件建议

| 资源 | 最低配置 | 推荐配置 |
|------|----------|----------|
| CPU | 2核 | 4核 |
| 内存 | 1GB | 2GB |
| 磁盘 | 500MB | 1GB |

---

## 2. 编译打包

### 2.1 获取源码

将项目源码下载或克隆到本地目录。

### 2.2 Maven编译

在项目根目录（包含`pom.xml`的目录）执行以下命令：

```bash
mvn clean package -DskipTests
```

编译成功后，可执行JAR包生成于：

```
target/etl-expression-engine-1.0.0.jar
```

### 2.3 包含测试的完整编译

```bash
mvn clean package
```

**注意**：测试执行需要`--enable-preview`JVM参数，已在`maven-surefire-plugin`中配置。

### 2.4 编译参数说明

Maven编译器插件已配置以下参数：

- `source`: 21
- `target`: 21
- `--enable-preview`: 启用JDK 21预览特性（ScopedValue等）

---

## 3. 配置说明

### 3.1 配置文件位置

```
src/main/resources/application.yml
```

打包后位于JAR包内的`BOOT-INF/classes/application.yml`，可通过Spring Boot的外部配置机制覆盖。

### 3.2 完整配置项

```yaml
server:
  port: 8080                    # 服务监听端口

spring:
  application:
    name: etl-expression-engine  # 应用名称

  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password:                    # H2默认无密码

  h2:
    console:
      enabled: true              # 启用H2控制台
      path: /h2-console          # H2控制台访问路径

  sql:
    init:
      mode: always               # 每次启动都执行初始化SQL
      data-locations: classpath:data.sql  # 初始化数据脚本

  data:
    redis:
      host: 127.0.0.1           # Redis服务器地址（Spring Boot 3.x配置路径）
      port: 6379                # Redis服务器端口
      database: 0               # Redis数据库编号
      timeout: 2000             # 连接超时时间（毫秒）
      password: ${REDIS_PASSWORD:} # Redis密码，支持环境变量
      lettuce:
        pool:
          max-active: 8         # 最大活跃连接数
          max-idle: 8           # 最大空闲连接数
          min-idle: 0           # 最小空闲连接数
          max-wait: 10000       # 最大等待时间（毫秒）

  threads:
    virtual:
      enabled: true              # 启用虚拟线程支持

etl:
  engine:
    expression-timeout: 5000     # 表达式执行超时时间（毫秒）
    max-expression-length: 10000 # 表达式最大长度（字符数）
    enable-sql-execution: true   # 是否启用SQL执行功能
    sql-readonly: true           # SQL是否只读模式

logging:
  level:
    root: INFO                   # 全局日志级别
    com.etl.engine: DEBUG        # 项目代码日志级别
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

### 3.3 配置项详解

#### 服务端口

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| server.port | 8080 | HTTP服务监听端口。修改后需同步更新防火墙规则和前端访问地址 |

#### 数据源配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| spring.datasource.url | jdbc:h2:mem:testdb;... | H2内存数据库连接URL。`DB_CLOSE_DELAY=-1`保持数据库在JVM运行期间不关闭，`DB_CLOSE_ON_EXIT=FALSE`防止JVM退出时关闭数据库 |
| spring.datasource.username | sa | H2默认管理员用户 |
| spring.datasource.password | （空） | H2默认无密码 |

#### H2控制台

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| spring.h2.console.enabled | true | 是否启用H2 Web控制台，生产环境建议关闭 |
| spring.h2.console.path | /h2-console | H2控制台访问路径，访问地址为`http://host:port/h2-console` |

#### Redis配置（Spring Boot 3.x）

**重要**：Spring Boot 3.x使用`spring.data.redis`配置路径，而非`spring.redis`。

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| spring.data.redis.host | 127.0.0.1 | Redis服务器地址 |
| spring.data.redis.port | 6379 | Redis服务器端口 |
| spring.data.redis.database | 0 | Redis数据库编号（0-15） |
| spring.data.redis.timeout | 2000 | 连接超时时间（毫秒） |
| spring.data.redis.password | ${REDIS_PASSWORD:} | Redis密码。支持通过环境变量`REDIS_PASSWORD`传入，未设置时默认为空 |
| spring.data.redis.lettuce.pool.max-active | 8 | 连接池最大活跃连接数 |
| spring.data.redis.lettuce.pool.max-idle | 8 | 连接池最大空闲连接数 |
| spring.data.redis.lettuce.pool.min-idle | 0 | 连接池最小空闲连接数 |
| spring.data.redis.lettuce.pool.max-wait | 10000 | 获取连接最大等待时间（毫秒），-1表示无限等待 |

**禁用Redis**：如果不需要Redis，可以通过Spring Profile或排除自动配置来禁用：

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
      - org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration
```

#### 引擎配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| etl.engine.expression-timeout | 5000 | 表达式执行超时时间（毫秒）。超时后虚拟线程被中断，返回超时错误。建议根据SQL查询复杂度适当调大 |
| etl.engine.max-expression-length | 10000 | 表达式最大长度（字符数）。防止恶意提交超大表达式消耗内存 |
| etl.engine.enable-sql-execution | true | 是否启用SQL执行功能。设为false时，表达式中的sql()和sqlValue()调用将失败 |
| etl.engine.sql-readonly | true | SQL是否只读模式。设为true时，仅允许SELECT语句 |

### 3.4 外部配置覆盖

Spring Boot支持多种外部配置方式，优先级从高到低：

1. 命令行参数：`--server.port=9090`
2. 环境变量：`SERVER_PORT=9090`
3. 外部配置文件：`config/application.yml`（JAR包同级目录下的config文件夹）
4. JAR包内配置文件：`classpath:/application.yml`

**生产环境推荐**：在JAR包同级目录创建`config/application.yml`，覆盖需要修改的配置项。

---

## 4. 启动方式

### 4.1 使用启动脚本（Windows）

项目提供了Windows批处理脚本`etl-engine.bat`，支持以下命令：

#### 构建应用

```bash
etl-engine.bat build
```

执行`mvn clean package -DskipTests`编译打包。

#### 启动应用

```bash
etl-engine.bat start
```

启动参数：

- JVM参数：`-Xms256m -Xmx512m -XX:+UseZGC --enable-preview`
- 日志输出：`app.log`（标准输出）和`app-error.log`（错误输出）
- PID文件：`application.pid`

启动成功后显示进程PID和访问地址。

#### 停止应用

```bash
etl-engine.bat stop
```

通过PID文件查找进程并发送终止信号。如果PID文件不存在或进程已失效，脚本会尝试通过JAR名称搜索并终止相关Java进程。

#### 重启应用

```bash
etl-engine.bat restart
```

依次执行停止和启动操作，中间间隔3秒。

#### 查看状态

```bash
etl-engine.bat status
```

显示应用运行状态，包括：

- 进程PID
- JAR包路径
- 日志文件路径
- 端口监听状态

### 4.2 使用java -jar方式

#### 基本启动

```bash
java --enable-preview -jar target/etl-expression-engine-1.0.0.jar
```

**注意**：`--enable-preview`参数是必需的，用于启用JDK 21的预览特性（ScopedValue）。

#### 自定义JVM参数

```bash
java -Xms256m -Xmx512m -XX:+UseZGC --enable-preview -jar target/etl-expression-engine-1.0.0.jar
```

JVM参数说明：

| 参数 | 说明 |
|------|------|
| -Xms256m | 初始堆内存256MB |
| -Xmx512m | 最大堆内存512MB |
| -XX:+UseZGC | 使用ZGC垃圾收集器，适合低延迟场景 |
| --enable-preview | 启用JDK预览特性 |

#### 指定配置参数

```bash
java --enable-preview -jar target/etl-expression-engine-1.0.0.jar \
  --server.port=9090 \
  --etl.engine.expression-timeout=10000 \
  --spring.data.redis.host=192.168.1.100
```

#### 后台运行（Linux）

```bash
nohup java --enable-preview -jar etl-expression-engine-1.0.0.jar > app.log 2>&1 &
```

### 4.3 启动验证

应用启动成功后，可通过以下方式验证：

1. **访问Web控制台**：浏览器打开`http://localhost:8080`
2. **执行测试表达式**：在控制台输入`1+1`，应返回结果2
3. **检查健康状态**：查看日志输出中是否有启动成功信息
4. **H2控制台**：访问`http://localhost:8080/h2-console`，使用JDBC URL `jdbc:h2:mem:testdb`连接

---

## 5. 停止方式

### 5.1 使用启动脚本

```bash
etl-engine.bat stop
```

### 5.2 手动停止

**Windows**：

```bash
# 查找Java进程
tasklist | findstr java

# 终止指定PID
taskkill /PID <pid> /F
```

**Linux**：

```bash
# 查找进程
ps aux | grep etl-expression-engine

# 终止进程
kill <pid>

# 强制终止
kill -9 <pid>
```

### 5.3 优雅停机

Spring Boot默认启用优雅停机。发送SIGTERM信号后，应用会等待当前正在处理的请求完成后再关闭。默认等待时间为30秒。

---

## 6. Docker部署方案

### 6.1 Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="ETL Engine"
LABEL description="ETL Expression Engine"

WORKDIR /app

COPY target/etl-expression-engine-1.0.0.jar app.jar

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseZGC --enable-preview"
ENV SERVER_PORT=8080
ENV REDIS_HOST=redis
ENV REDIS_PORT=6379
ENV REDIS_PASSWORD=""

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar \
  --server.port=${SERVER_PORT} \
  --spring.data.redis.host=${REDIS_HOST} \
  --spring.data.redis.port=${REDIS_PORT} \
  --spring.data.redis.password=${REDIS_PASSWORD}"]
```

### 6.2 构建Docker镜像

```bash
# 先编译打包
mvn clean package -DskipTests

# 构建镜像
docker build -t etl-expression-engine:1.0.0 .
```

### 6.3 Docker Compose部署

创建`docker-compose.yml`文件：

```yaml
version: '3.8'

services:
  etl-engine:
    image: etl-expression-engine:1.0.0
    container_name: etl-expression-engine
    ports:
      - "8080:8080"
    environment:
      - SERVER_PORT=8080
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - REDIS_PASSWORD=redis2026!
      - JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseZGC --enable-preview
    depends_on:
      - redis
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:8080"]
      interval: 30s
      timeout: 10s
      retries: 3

  redis:
    image: redis:7-alpine
    container_name: etl-redis
    ports:
      - "6379:6379"
    command: redis-server --requirepass redis2026!
    volumes:
      - redis-data:/data
    restart: unless-stopped

volumes:
  redis-data:
```

### 6.4 启动Docker Compose

```bash
docker-compose up -d
```

### 6.5 查看日志

```bash
docker-compose logs -f etl-engine
```

### 6.6 停止Docker Compose

```bash
docker-compose down
```

---

## 7. Kubernetes部署方案

### 7.1 Deployment配置

创建`deployment.yaml`文件：

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: etl-expression-engine
  labels:
    app: etl-expression-engine
spec:
  replicas: 2
  selector:
    matchLabels:
      app: etl-expression-engine
  template:
    metadata:
      labels:
        app: etl-expression-engine
    spec:
      containers:
      - name: etl-engine
        image: etl-expression-engine:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: SERVER_PORT
          value: "8080"
        - name: REDIS_HOST
          value: "redis-service"
        - name: REDIS_PORT
          value: "6379"
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: redis-secret
              key: password
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 10
```

### 7.2 Service配置

创建`service.yaml`文件：

```yaml
apiVersion: v1
kind: Service
metadata:
  name: etl-expression-engine
spec:
  selector:
    app: etl-expression-engine
  ports:
  - port: 8080
    targetPort: 8080
  type: ClusterIP
```

### 7.3 Redis Secret配置

```bash
kubectl create secret generic redis-secret --from-literal=password=redis2026!
```

### 7.4 Redis部署（可选）

如果需要在K8s中部署Redis，创建`redis-deployment.yaml`：

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis
  labels:
    app: redis
spec:
  replicas: 1
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
      - name: redis
        image: redis:7-alpine
        ports:
        - containerPort: 6379
        command: ["redis-server", "--requirepass", "$(REDIS_PASSWORD)"]
        env:
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: redis-secret
              key: password
        resources:
          requests:
            memory: "128Mi"
            cpu: "100m"
        volumeMounts:
        - name: redis-data
          mountPath: /data
      volumes:
      - name: redis-data
        persistentVolumeClaim:
          claimName: redis-pvc
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: redis-pvc
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
---
apiVersion: v1
kind: Service
metadata:
  name: redis-service
spec:
  selector:
    app: redis
  ports:
  - port: 6379
    targetPort: 6379
```

### 7.5 部署命令

```bash
# 部署Redis（如果需要）
kubectl apply -f redis-deployment.yaml

# 部署ETL引擎
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
```

### 7.6 查看部署状态

```bash
kubectl get pods
kubectl get services
kubectl logs -f <pod-name>
```

---

## 8. 常见问题排查

### 8.1 启动失败 - JDK版本不正确

**现象**：

```
UnsupportedClassVersionError: com/etl/engine/EtlExpressionEngineApplication has been compiled by a more recent version of the Java Runtime
```

**解决方案**：

确认JDK版本为21或更高：

```bash
java -version
```

如果版本不正确，安装JDK 21并设置`JAVA_HOME`环境变量。

### 8.2 启动失败 - 缺少--enable-preview

**现象**：

```
java.lang.UnsupportedOperationException: ScopedValue
```

**解决方案**：

启动命令中必须包含`--enable-preview`参数：

```bash
java --enable-preview -jar etl-expression-engine-1.0.0.jar
```

### 8.3 Redis连接失败

**现象**：

日志中出现以下警告：

```
Redis写入失败，降级为本地存储: Unable to connect to Redis
```

**解决方案**：

1. 检查Redis服务是否启动：`redis-cli ping`
2. 检查Redis连接配置是否正确（host、port、password）
3. 检查防火墙是否放行Redis端口（默认6379）
4. 如果不需要Redis，系统会自动降级为本地内存模式，不影响基本功能

### 8.4 端口被占用

**现象**：

```
Web server failed to start. Port 8080 was already in use.
```

**解决方案**：

1. 查找占用端口的进程并终止
2. 或修改服务端口：`--server.port=9090`

### 8.5 表达式执行超时

**现象**：

表达式返回"表达式执行超时, 超过 5000ms"。

**解决方案**：

1. 检查SQL查询是否过于复杂，考虑添加查询条件缩小结果集
2. 调大超时时间：`--etl.engine.expression-timeout=10000`
3. 检查H2数据库是否正常响应

### 8.6 H2控制台无法访问

**现象**：

访问`http://localhost:8080/h2-console`返回404。

**解决方案**：

1. 确认配置`spring.h2.console.enabled=true`
2. 确认配置`spring.h2.console.path=/h2-console`
3. H2控制台访问时，JDBC URL需填写`jdbc:h2:mem:testdb`

### 8.7 内存不足

**现象**：

```
java.lang.OutOfMemoryError: Java heap space
```

**解决方案**：

1. 增大JVM堆内存：`-Xmx1024m`
2. 检查是否有大量会话未释放
3. 缩短会话超时时间

### 8.8 日志级别调整

生产环境建议将项目日志级别调整为INFO：

```yaml
logging:
  level:
    com.etl.engine: INFO
```

或通过命令行参数：

```bash
java --enable-preview -jar etl-expression-engine-1.0.0.jar --logging.level.com.etl.engine=INFO
```

---

## 9. 部署检查清单

| 检查项 | 说明 | 状态 |
|--------|------|------|
| JDK版本 | 确认JDK版本 >= 21 | ☐ |
| Maven版本 | 确认Maven版本 >= 3.8 | ☐ |
| 编译打包 | `mvn clean package -DskipTests` 成功 | ☐ |
| JAR包生成 | `target/etl-expression-engine-1.0.0.jar` 存在 | ☐ |
| Redis服务（可选） | Redis服务启动正常（如启用） | ☐ |
| 端口检查 | 8080端口未被占用 | ☐ |
| 启动命令 | 包含`--enable-preview`参数 | ☐ |
| 启动验证 | 访问`http://localhost:8080`成功 | ☐ |
| 表达式测试 | 执行`1+1`返回结果2 | ☐ |
| SQL测试 | 执行`sql("SELECT * FROM etl_config")`成功 | ☐ |
| 会话管理 | 变量赋值后可正确读取 | ☐ |

---

## 附录：配置参数汇总表

| 配置类别 | 配置项 | 默认值 | 说明 |
|----------|--------|--------|------|
| 服务配置 | server.port | 8080 | HTTP服务端口 |
| 引擎配置 | etl.engine.expression-timeout | 5000 | 表达式超时(ms) |
| 引擎配置 | etl.engine.max-expression-length | 10000 | 表达式最大长度 |
| 引擎配置 | etl.engine.enable-sql-execution | true | 启用SQL执行 |
| 引擎配置 | etl.engine.sql-readonly | true | SQL只读模式 |
| Redis配置 | spring.data.redis.host | 127.0.0.1 | Redis地址 |
| Redis配置 | spring.data.redis.port | 6379 | Redis端口 |
| Redis配置 | spring.data.redis.database | 0 | Redis数据库编号 |
| Redis配置 | spring.data.redis.timeout | 2000 | Redis连接超时(ms) |
| H2配置 | spring.h2.console.enabled | true | 启用H2控制台 |
| H2配置 | spring.h2.console.path | /h2-console | H2控制台路径 |