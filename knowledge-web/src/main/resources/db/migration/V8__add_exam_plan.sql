-- ============================================================
-- V8: 题型分布方案落库 — 保证考试端渲染与出卷方案强一致
--   kb_exam_history / kb_exam_session 各添加 exam_plan（JSON）
-- ============================================================

-- 出卷历史表添加题型分布方案（生成试卷时确认的权威方案，JSON 字符串）
ALTER TABLE kb_exam_history ADD COLUMN exam_plan TEXT DEFAULT NULL;

-- 考试场次表添加题型分布方案（从历史表继承，供考试端按方案渲染题型/分值）
ALTER TABLE kb_exam_session ADD COLUMN exam_plan TEXT DEFAULT NULL;
