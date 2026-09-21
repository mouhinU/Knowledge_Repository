-- ============================================================
-- V17：看图题配图绑定（阶段 2）——结构化题目表补充配图列
--   V16 已把知识库文档摄入时抽取的位图落库为 kb_document_image（配图来源池）。
--   本迁移给 kb_exam_question 增加 images_json 列，保存「校对页人工为某题绑定的
--   配图 assetKey 有序数组」（如 ["k1","k2"]）。学生答题页 / 校对页据 assetKey 经
--   /api/exam/assets/{asset_key} 拉取展示。重新切分(resplit)时按印刷题号保留本列。
--   语法 H2 + MySQL 兼容：ADD COLUMN 不用 AFTER / COMMENT。
-- ============================================================

ALTER TABLE kb_exam_question ADD COLUMN images_json TEXT DEFAULT NULL;
