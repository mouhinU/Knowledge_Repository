-- ============================================================
-- V9: 考试评分过程落库 — 支持成绩复核页展示 AI 输入 / 原始输出
--   kb_exam_answer 添加 ai_input（发给模型的完整 prompt）
--                     ai_raw_output（模型原始返回文本，未解析）
--   注：主观题两者均记录；客观题也记录（比对过程 + 依据），
--       以便复核页统一按 📥输入 / 💭思考 / 📤输出 三段展示。
-- ============================================================

ALTER TABLE kb_exam_answer ADD COLUMN ai_input TEXT DEFAULT NULL;
ALTER TABLE kb_exam_answer ADD COLUMN ai_raw_output TEXT DEFAULT NULL;
