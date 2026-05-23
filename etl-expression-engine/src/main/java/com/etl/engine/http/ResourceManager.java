package com.etl.engine.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.Cleaner;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ResourceManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ResourceManager.class);
    
    private static final Cleaner CLEANER = Cleaner.create();
    
    private static final Map<Long, TrackedResource> ACTIVE_RESOURCES = new ConcurrentHashMap<>();
    
    private static final AtomicLong RESOURCE_ID_GENERATOR = new AtomicLong(0);
    
    private static final long MAX_RESOURCE_HOLD_TIME = 300_000;
    
    private static volatile boolean monitorStarted = false;
    
    private ResourceManager() {
    }
    
    public static synchronized void startMonitor() {
        if (!monitorStarted) {
            monitorStarted = true;
            Thread.Builder.OfVirtual builder = Thread.ofVirtual();
            Thread monitorThread = builder.unstarted(() -> {
                while (monitorStarted) {
                    try {
                        Thread.sleep(30_000);
                        checkResourceLeaks();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            monitorThread.setName("resource-monitor");
            monitorThread.setDaemon(true);
            monitorThread.start();
            logger.info("资源监控线程已启动");
        }
    }
    
    public static synchronized void stopMonitor() {
        monitorStarted = false;
        logger.info("资源监控线程已停止");
    }
    
    public static <T extends AutoCloseable> T track(T resource, String description) {
        if (resource == null) {
            return null;
        }
        
        long resourceId = RESOURCE_ID_GENERATOR.incrementAndGet();
        String threadName = Thread.currentThread().getName();
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        
        TrackedResource tracked = new TrackedResource(
            resourceId,
            resource,
            description,
            threadName,
            System.currentTimeMillis(),
            stackTrace
        );
        
        ACTIVE_RESOURCES.put(resourceId, tracked);
        
        CLEANER.register(resource, new ResourceCleanup(resourceId, description));
        
        logger.debug("资源已注册跟踪: id={}, type={}, description={}", 
                resourceId, resource.getClass().getName(), description);
        
        return resource;
    }
    
    public static Connection trackConnection(Connection connection, String description) {
        if (connection == null) {
            return null;
        }
        
        long resourceId = RESOURCE_ID_GENERATOR.incrementAndGet();
        String threadName = Thread.currentThread().getName();
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        
        TrackedResource tracked = new TrackedResource(
            resourceId,
            connection,
            description,
            threadName,
            System.currentTimeMillis(),
            stackTrace
        );
        
        ACTIVE_RESOURCES.put(resourceId, tracked);
        
        CLEANER.register(connection, new ConnectionCleanup(resourceId, description, connection));
        
        logger.debug("数据库连接已注册跟踪: id={}, description={}", resourceId, description);
        
        return connection;
    }
    
    public static void release(AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        
        Iterator<Map.Entry<Long, TrackedResource>> iterator = ACTIVE_RESOURCES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, TrackedResource> entry = iterator.next();
            if (entry.getValue().resource() == resource) {
                long resourceId = entry.getKey();
                iterator.remove();
                
                try {
                    resource.close();
                    logger.debug("资源已释放: id={}", resourceId);
                } catch (Exception e) {
                    logger.warn("资源释放失败: id={}, error={}", resourceId, e.getMessage());
                }
                return;
            }
        }
        
        try {
            resource.close();
        } catch (Exception e) {
            logger.warn("未跟踪资源释放失败: {}", e.getMessage());
        }
    }
    
    public static void releaseConnection(Connection connection) {
        if (connection == null) {
            return;
        }
        
        Iterator<Map.Entry<Long, TrackedResource>> iterator = ACTIVE_RESOURCES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, TrackedResource> entry = iterator.next();
            if (entry.getValue().resource() == connection) {
                long resourceId = entry.getKey();
                iterator.remove();
                
                try {
                    if (!connection.isClosed()) {
                        connection.close();
                    }
                    logger.debug("数据库连接已释放: id={}", resourceId);
                } catch (SQLException e) {
                    logger.warn("数据库连接释放失败: id={}, error={}", resourceId, e.getMessage());
                }
                return;
            }
        }
        
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.warn("未跟踪连接释放失败: {}", e.getMessage());
        }
    }
    
    public static List<ResourceStatus> getActiveResources() {
        List<ResourceStatus> statusList = new ArrayList<>();
        long now = System.currentTimeMillis();
        
        for (TrackedResource tracked : ACTIVE_RESOURCES.values()) {
            long holdTime = now - tracked.createTime();
            statusList.add(new ResourceStatus(
                tracked.resourceId(),
                tracked.resource().getClass().getName(),
                tracked.description(),
                tracked.threadName(),
                holdTime,
                holdTime > MAX_RESOURCE_HOLD_TIME
            ));
        }
        
        return statusList;
    }
    
    public static int getActiveResourceCount() {
        return ACTIVE_RESOURCES.size();
    }
    
    public static void checkResourceLeaks() {
        long now = System.currentTimeMillis();
        List<Long> leakedIds = new ArrayList<>();
        
        for (Map.Entry<Long, TrackedResource> entry : ACTIVE_RESOURCES.entrySet()) {
            long holdTime = now - entry.getValue().createTime();
            if (holdTime > MAX_RESOURCE_HOLD_TIME) {
                leakedIds.add(entry.getKey());
                
                TrackedResource tracked = entry.getValue();
                logger.warn("检测到潜在资源泄漏: id={}, type={}, holdTime={}ms, thread={}, description={}",
                        tracked.resourceId(),
                        tracked.resource().getClass().getName(),
                        holdTime,
                        tracked.threadName(),
                        tracked.description());
                
                logger.warn("资源创建堆栈: {}", 
                        Arrays.toString(Arrays.copyOfRange(tracked.stackTrace(), 2, Math.min(10, tracked.stackTrace().length))));
            }
        }
        
        if (!leakedIds.isEmpty()) {
            logger.warn("资源泄漏检测完成: 发现{}个潜在泄漏资源", leakedIds.size());
        }
    }
    
    public static void releaseAllForThread(String threadName) {
        List<Long> toRemove = new ArrayList<>();
        
        for (Map.Entry<Long, TrackedResource> entry : ACTIVE_RESOURCES.entrySet()) {
            if (entry.getValue().threadName().equals(threadName)) {
                toRemove.add(entry.getKey());
            }
        }
        
        for (Long resourceId : toRemove) {
            TrackedResource tracked = ACTIVE_RESOURCES.remove(resourceId);
            if (tracked != null) {
                try {
                    if (tracked.resource() instanceof Connection conn && !conn.isClosed()) {
                        conn.close();
                    } else if (tracked.resource() instanceof AutoCloseable closeable) {
                        closeable.close();
                    }
                    logger.debug("线程资源已释放: thread={}, resourceId={}", threadName, resourceId);
                } catch (Exception e) {
                    logger.warn("线程资源释放失败: thread={}, resourceId={}, error={}", 
                            threadName, resourceId, e.getMessage());
                }
            }
        }
    }
    
    public record TrackedResource(
        long resourceId,
        Object resource,
        String description,
        String threadName,
        long createTime,
        StackTraceElement[] stackTrace
    ) {}
    
    public record ResourceStatus(
        long resourceId,
        String resourceType,
        String description,
        String threadName,
        long holdTimeMs,
        boolean potentialLeak
    ) {}
    
    private static final class ResourceCleanup implements Runnable {
        private final long resourceId;
        private final String description;
        
        ResourceCleanup(long resourceId, String description) {
            this.resourceId = resourceId;
            this.description = description;
        }
        
        @Override
        public void run() {
            ACTIVE_RESOURCES.remove(resourceId);
            logger.debug("资源已通过Cleaner清理: id={}, description={}", resourceId, description);
        }
    }
    
    private static final class ConnectionCleanup implements Runnable {
        private final long resourceId;
        private final String description;
        private final Connection connection;
        
        ConnectionCleanup(long resourceId, String description, Connection connection) {
            this.resourceId = resourceId;
            this.description = description;
            this.connection = connection;
        }
        
        @Override
        public void run() {
            ACTIVE_RESOURCES.remove(resourceId);
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                    logger.debug("数据库连接已通过Cleaner关闭: id={}, description={}", resourceId, description);
                }
            } catch (SQLException e) {
                logger.warn("Cleaner关闭连接失败: id={}, error={}", resourceId, e.getMessage());
            }
        }
    }
    
    static {
        startMonitor();
    }
}
