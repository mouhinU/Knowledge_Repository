-- ============================================================
-- V7: 考试时长支持 — kb_exam_history 和 kb_exam_session 添加 duration_minutes
-- ============================================================

-- 出卷历史表添加考试时长字段（AI 根据科目特性设定）
ALTER TABLE kb_exam_history ADD COLUMN duration_minutes INT DEFAULT NULL;

-- 考试场次表添加考试时长字段（从历史表继承或管理员覆盖）
ALTER TABLE kb_exam_session ADD COLUMN duration_minutes INT DEFAULT NULL;
