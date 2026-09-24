-- ============================================================
-- V21: 系统配置表（特性开关 + 运行时可调参数）
--   三层获取：内存缓存 → 数据库 → 系统默认值
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_config (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key   VARCHAR(128) NOT NULL COMMENT '配置键（全局唯一）',
    config_value VARCHAR(1024) DEFAULT '' COMMENT '配置值',
    value_type   VARCHAR(32)   DEFAULT 'STRING' COMMENT '值类型：STRING / INTEGER / BOOLEAN / DECIMAL',
    description  VARCHAR(512)  DEFAULT '' COMMENT '配置说明',
    category     VARCHAR(64)   DEFAULT 'general' COMMENT '分组：feature-toggle / exam / system',
    editable     TINYINT(1)    DEFAULT 1 COMMENT '是否允许页面编辑：0=只读 1=可编辑',
    create_time  DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_config_key (config_key)
);

-- 特性开关 & 可调参数种子数据
INSERT INTO sys_config (config_key, config_value, value_type, description, category, editable) VALUES
('exam.plan.auto-balance.enabled',    'true',  'BOOLEAN', '方案生成后基于合理性评估建议自动修正方案',       'feature-toggle', 1),
('exam.plan.auto-balance.max-rounds', '3',     'INTEGER', '方案自动修正最大轮次（1-5）',                  'feature-toggle', 1),
('exam.quality-score-threshold',      '80',    'INTEGER', '出卷质量分阈值（低于此值触发改进重试）',       'feature-toggle', 1),
('exam.review-required',              'true',  'BOOLEAN', '试卷是否需人工校对后方可发布',                 'feature-toggle', 1),
('exam.grading-delay-minutes',        '30',    'INTEGER', '自动评分延迟分钟数（等待所有考生交卷）',       'exam',           1),
('exam.grading-timeout-minutes',      '15',    'INTEGER', '评分超时分钟数（超时后强制结束评分）',         'exam',           1);
