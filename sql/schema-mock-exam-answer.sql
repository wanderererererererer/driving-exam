-- =====================================================================
-- 驾考智能辅导系统 · 模拟考试：新增「考试暂存答案」表
-- 目标库：driving_exam（MySQL 8.0）
-- 用途：断点续考 —— 考试进行中（mock_exam.status = ONGOING）逐题暂存用户答案。
--       交卷判分后答案写入 answer_record(source=EXAM)，本表仅作考试期间的草稿。
-- 说明：可重复执行（IF NOT EXISTS）。注意：**必须先于后端启动执行**，
--       否则 MockExamAnswerMapper 对应的表不存在，调用即报错。
-- =====================================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `mock_exam_answer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `exam_id` bigint NOT NULL COMMENT '模拟考试ID（mock_exam.id）',
  `question_id` bigint NOT NULL COMMENT '题目ID',
  `user_answer` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户答案（考试中暂存，尚未判分）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exam_question` (`exam_id`,`question_id`),
  KEY `idx_exam_id` (`exam_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试暂存答案（断点续考用）';
