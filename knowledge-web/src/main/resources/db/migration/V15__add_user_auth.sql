-- ============================================================
-- V15：管理端登录认证改造——sys_user 由「硬编码 HTTP Basic」迁移到「账号密码登录 + JWT」。
--   原 P3/SEC-2 阶段管理端凭据来自 spring.security.user（内存单用户 + HTTP Basic），
--   sys_user 表本身没有 password_hash / status，无法支撑真正的用户体系登录。本迁移补齐：
--     * password_hash：BCrypt（$2a，强度 10）哈希，与学生端 kb_student.password_hash 同版本、可被
--       Spring Security 的 BCryptPasswordEncoder 直接校验；NULL 表示「尚未设置密码、不可登录」。
--     * status：ACTIVE / DISABLED，默认 ACTIVE。JWT 无状态，禁用账号通过登录 & 校验过滤器实时读库生效。
--   种子：为内置管理员 admin 置默认密码 admin123（生产首次登录后应立即在用户管理中改密）。
--   语法 H2 + MySQL 兼容：ADD COLUMN 不用 AFTER / COMMENT；DEFAULT 值不带单引号歧义。
-- ============================================================

ALTER TABLE sys_user ADD COLUMN password_hash VARCHAR(256) DEFAULT NULL;
ALTER TABLE sys_user ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

-- 内置管理员（user_key='user-admin'）设默认口令 admin123 的 BCrypt($2a) 哈希并激活。
UPDATE sys_user
SET password_hash = '$2a$10$oOK9bUg4us93CtEqrFihk.R7feZL4F3OQ0o220.pNqQ9udi/DQ2A2',
    status = 'ACTIVE'
WHERE user_key = 'user-admin';
