package com.etl.engine.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.Cleaner;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 资源管理器，用于跟踪和管理需要显式释放的资源。
 * <p>
 * 该类提供资源注册、跟踪、泄漏检测和自动清理功能。
 * 使用{@link Cleaner}实现资源的自动释放，支持数据库连接等需要显式关闭的资源。
 * 内置监控线程定期检查资源持有时间，发现潜在泄漏并输出警告日志。
 * </p>
 *
 * @author ETL Engine
 * @version 1.0
 * @since 1.0
 */
public final class ResourceManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ResourceManager.class);
    
    /**
     * Java Cleaner实例，用于资源自动清理
     */
    private static final Cleaner CLEANER = Cleaner.create();
    
    /**
     * 活跃资源映射表
     */
    private static final Map<Long, TrackedResource> ACTIVE_RESOURCES = new ConcurrentHashMap<>();
    
    /**
     * 资源ID生成器
     */
    private static final AtomicLong RESOURCE_ID_GENERATOR = new AtomicLong(0);
    
    /**
     * 最大资源持有时间（毫秒），超过此时间视为潜在泄漏
     */
    private static final long MAX_RESOURCE_HOLD_TIME = 300_000;
    
    /**
     * 监控线程启动标志
     */
    private static volatile boolean monitorStarted = false;
    
    /**
     * 私有构造方法，防止实例化。
     */
    private ResourceManager() {
    }
    
    /**
     * 启动资源监控线程。
     * <p>
     * 监控线程每30秒检查一次活跃资源，发现持有时间过长的资源输出警告日志。
     * </p>
     */
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
    
    /**
     * 停止资源监控线程。
     */
    public static synchronized void stopMonitor() {
        monitorStarted = false;
        logger.info("资源监控线程已停止");
    }
    
    /**
     * 跟踪AutoCloseable资源。
     * <p>
     * 注册资源到活跃列表，并设置Cleaner回调在对象被垃圾回收时自动清理。
     * </p>
     *
     * @param <T> 资源类型
     * @param resource 要跟踪的资源对象
     * @param description 资源描述
     * @return 跟踪的资源对象
     */
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
    
    /**
     * 跟踪数据库连接资源。
     * <p>
     * 专门为数据库连接提供跟踪，确保连接被正确关闭。
     * </p>
     *
     * @param connection 要跟踪的数据库连接
     * @param description 资源描述
     * @return 跟踪的数据库连接对象
     */
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
    
    /**
     * 释放跟踪的资源。
     *
     * @param resource 要释放的资源对象
     */
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
    
    /**
     * 释放跟踪的数据库连接。
     *
     * @param connection 要释放的数据库连接
     */
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
    
    /**
     * 获取所有活跃资源的状态列表。
     *
     * @return 资源状态列表
     */
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
    
    /**
     * 获取活跃资源数量。
     *
     * @return 活跃资源数量
     */
    public static int getActiveResourceCount() {
        return ACTIVE_RESOURCES.size();
    }
    
    /**
     * 检查资源泄漏。
     * <p>
     * 遍历所有活跃资源，检查持有时间是否超过阈值，输出潜在泄漏警告。
     * </p>
     */
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
    
    /**
     * 释放指定线程的所有资源。
     *
     * @param threadName 线程名称
     */
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
    
    /**
     * 跟踪资源记录，封装资源的元数据信息。
     *
     * @param resourceId 资源ID
     * @param resource 资源对象
     * @param description 资源描述
     * @param threadName 创建线程名称
     * @param createTime 创建时间戳
     * @param stackTrace 创建时的堆栈跟踪
     */
    public record TrackedResource(
        long resourceId,
        Object resource,
        String description,
        String threadName,
        long createTime,
        StackTraceElement[] stackTrace
    ) {}
    
    /**
     * 资源状态记录，封装资源的状态信息。
     *
     * @param resourceId 资源ID
     * @param resourceType 资源类型名称
     * @param description 资源描述
     * @param threadName 创建线程名称
     * @param holdTimeMs 持有时间（毫秒）
     * @param potentialLeak 是否潜在泄漏
     */
    public record ResourceStatus(
        long resourceId,
        String resourceType,
        String description,
        String threadName,
        long holdTimeMs,
        boolean potentialLeak
    ) {}
    
    /**
     * 资源清理回调，在对象被垃圾回收时执行。
     */
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
    
    /**
     * 数据库连接清理回调，在连接对象被垃圾回收时执行。
     */
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
