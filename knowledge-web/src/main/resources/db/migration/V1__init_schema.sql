-- ============================================================
-- V1: Knowledge Repository 初始化 Schema
-- ============================================================

-- 部门表
CREATE TABLE IF NOT EXISTS sys_department (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    department_key VARCHAR(64) NOT NULL,
    department_name VARCHAR(128) NOT NULL,
    parent_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dept_key ON sys_department(department_key);

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_key VARCHAR(64) NOT NULL,
    username VARCHAR(128) NOT NULL,
    department_id VARCHAR(64),
    is_admin BOOLEAN DEFAULT FALSE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_key ON sys_user(user_key);
CREATE INDEX idx_user_username ON sys_user(username);

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_key VARCHAR(64) NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_role_key ON sys_role(role_key);

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id)
);

CREATE INDEX idx_ur_user ON sys_user_role(user_id);
CREATE INDEX idx_ur_role ON sys_user_role(role_id);

-- 文档表
CREATE TABLE IF NOT EXISTS kb_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_key VARCHAR(64) NOT NULL,
    file_name VARCHAR(512) NOT NULL,
    file_type VARCHAR(128),
    file_size BIGINT,
    storage_path VARCHAR(1024),
    file_checksum VARCHAR(64),
    total_pages INT DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    visibility VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',
    owner_id VARCHAR(64) NOT NULL,
    department_id VARCHAR(64) NOT NULL,
    allowed_roles VARCHAR(1024),
    summary VARCHAR(2048),
    chunk_max_size INT DEFAULT 500,
    chunk_overlap INT DEFAULT 50,
    respect_paragraph BOOLEAN DEFAULT TRUE,
    respect_page BOOLEAN DEFAULT TRUE,
    error_message VARCHAR(2048),
    tags VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_doc_key ON kb_document(document_key);
CREATE INDEX idx_doc_checksum ON kb_document(file_checksum);
CREATE INDEX idx_doc_owner ON kb_document(owner_id);
CREATE INDEX idx_doc_dept ON kb_document(department_id);
CREATE INDEX idx_doc_status ON kb_document(status);
CREATE INDEX idx_doc_visibility ON kb_document(visibility);

-- 文档分块表
CREATE TABLE IF NOT EXISTS kb_document_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chunk_key VARCHAR(64) NOT NULL,
    document_id BIGINT NOT NULL,
    document_key VARCHAR(64) NOT NULL,
    chunk_index INT NOT NULL,
    start_page INT,
    end_page INT,
    content TEXT,
    token_count INT,
    vector_id VARCHAR(128),
    department_id VARCHAR(64),
    visibility VARCHAR(32),
    allowed_roles VARCHAR(1024),
    owner_id VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chunk_key ON kb_document_chunk(chunk_key);
CREATE INDEX idx_chunk_doc ON kb_document_chunk(document_id);
CREATE INDEX idx_chunk_doc_key ON kb_document_chunk(document_key);
CREATE INDEX idx_chunk_dept ON kb_document_chunk(department_id);

-- ============================================================
-- 初始数据
-- ============================================================

-- 默认部门
INSERT INTO sys_department (department_key, department_name, parent_id) VALUES
('dept-root', '总公司', NULL),
('dept-tech', '技术部', 1),
('dept-hr', '人力资源部', 1),
('dept-finance', '财务部', 1);

-- 默认角色
INSERT INTO sys_role (role_key, role_name, description) VALUES
('ADMIN', '管理员', '系统管理员，拥有所有权限'),
('MANAGER', '部门经理', '部门经理，可管理部门文档'),
('MEMBER', '普通成员', '普通成员，可检索授权文档');

-- 管理员用户
INSERT INTO sys_user (user_key, username, department_id, is_admin) VALUES
('user-admin', 'admin', 'dept-root', TRUE);

-- 管理员角色关联
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
