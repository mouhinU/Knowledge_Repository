-- ============================================================
-- V18：试卷作废级联 —— kb_exam_session 增加 voided 布尔标记
--   当 AI 试卷（kb_exam_history.status）被作废为 VOIDED 时，
--   其下已存在的所有考试场次级联标注 voided=TRUE（仍可显示与查阅，前端加「已作废」徽标）。
--   同时新增复合索引以支撑「一人一卷一次」开考守卫的存在性查询。
--   语法 H2 + MySQL 兼容：ADD COLUMN 不用 AFTER / COMMENT。
-- ============================================================

ALTER TABLE kb_exam_session ADD COLUMN voided BOOLEAN DEFAULT FALSE;

CREATE INDEX idx_exam_session_student_history ON kb_exam_session(student_id, exam_history_id);
