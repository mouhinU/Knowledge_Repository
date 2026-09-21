-- ============================================================
-- V10: 出卷历史落库质量评分六维度明细 — 支持前端按规则还原每份试卷的加权计算
--   kb_exam_history 添加 score_detail（JSON：
--     accuracy/wording/coverage/typeReasonable/difficulty/format/total）
--   与列表"评分 ?"提示的分数计算规则一一对应，历史旧记录该列为 NULL（前端自动降级为仅显示总分）。
-- ============================================================

ALTER TABLE kb_exam_history ADD COLUMN score_detail TEXT DEFAULT NULL;
