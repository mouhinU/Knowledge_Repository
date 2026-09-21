-- ============================================================
-- V13：错题本展示纯结构化收尾（P1）——结构化题目表补充评分标准列
--   V11 建表注释即声明「下游（评分/展示/错题本）纯读结构化行，废弃对 answer_key
--   自由文本的重复解析」，但当时漏建 scoring_criteria 列，导致错题本只能回头解析
--   answer_key 取评分标准（双口径残留）。本迁移补齐该列，切分时由 AnswerKeyParser
--   解析结果一次性落库，错题本 / 答案列表改读结构化行，彻底消除重复解析。
--   语法 H2 + MySQL 兼容：ADD COLUMN 不用 AFTER / COMMENT。
-- ============================================================

ALTER TABLE kb_exam_question ADD COLUMN scoring_criteria TEXT DEFAULT NULL;
