-- ETL Expression Engine Demo Data
-- 初始化测试数据表

CREATE TABLE IF NOT EXISTS etl_config (
    id INT PRIMARY KEY AUTO_INCREMENT,
    config_key VARCHAR(100) NOT NULL,
    config_value VARCHAR(500),
    description VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS etl_task (
    id INT PRIMARY KEY AUTO_INCREMENT,
    task_name VARCHAR(100) NOT NULL,
    task_type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'PENDING',
    priority INT DEFAULT 5,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO etl_config (config_key, config_value, description) VALUES
('batch.size', '1000', '批处理大小'),
('timeout.seconds', '300', '超时时间(秒)'),
('retry.count', '3', '重试次数'),
('parallel.threads', '4', '并行线程数');

INSERT INTO etl_task (task_name, task_type, status, priority) VALUES
('数据抽取任务A', 'EXTRACT', 'COMPLETED', 1),
('数据转换任务B', 'TRANSFORM', 'RUNNING', 2),
('数据加载任务C', 'LOAD', 'PENDING', 3),
('数据校验任务D', 'VALIDATE', 'FAILED', 1);
