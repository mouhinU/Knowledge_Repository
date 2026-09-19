-- ============================================================
-- V14：评分并发围栏令牌（CONC-1）——为考试场次增加评分认领 fencing token
--   原并发控制仅有「SUBMITTED→GRADING」状态 CAS，但状态位可被超时回收翻转后重新抢占：
--   慢速旧评分者 A 被回收（→SUBMITTED）、新评分者 B 抢占（→GRADING）后，A 的心跳与终态
--   CAS 只校验 status==GRADING，会与 B 互相误判「仍持有所有权」，产生交叉写 / 覆盖终态。
--   新增 grading_token：认领时写入一次性 UUID，心跳 / 终态 / 失败回退均以
--   「status==GRADING AND grading_token=:token」为谓词；超时回收回退时置空 token，
--   使被接管的旧评分者令牌立刻失效、其自然退出。
--   语法 H2 + MySQL 兼容：ADD COLUMN 不用 AFTER / COMMENT。
-- ============================================================

ALTER TABLE kb_exam_session ADD COLUMN grading_token VARCHAR(64) DEFAULT NULL;
