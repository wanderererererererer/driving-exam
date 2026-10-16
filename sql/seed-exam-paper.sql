-- =====================================================================
-- 驾考智能辅导系统 · 模拟考试种子数据（试卷 + 试卷题目关联）
-- 目标库：driving_exam（MySQL 8.0）
-- 用途：为模拟考试模块提供一份"科一演示卷"
-- 说明：可重复执行（开头先按固定 id 清理本批次数据）
-- 依赖：先执行 sql/seed-question.sql 与 sql/seed-question-extra.sql（共 36 题，id 1~36）
--
-- ⚠️ 关于参数：题量只有 36（题库现状），凑不出真实的 100 题 / 90 分——
--    exam_paper_question.score 是 int，无法用 2.5 分/题凑 90。
--    故演示卷取：每题 1 分 → 总分 36、及格 32（约 88.9%）。
--    若日后题库扩充，需同步调整本文件的 total_score / pass_score / question_count。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 0. 清理旧演示卷（按固定 id=1，可重复执行）
--    ⚠️ 若已有基于该试卷的 mock_exam 记录，删除试卷会让那些历史记录查不到标题
--       （本项目无外键约束，不会报错）。开发环境可直接清；若有想保留的历史请先备份。
-- ---------------------------------------------------------------------
DELETE FROM exam_paper_question WHERE paper_id = 1;
DELETE FROM exam_paper          WHERE id = 1;

-- ---------------------------------------------------------------------
-- 1. 试卷：科目一模拟考试（演示卷）  id 固定为 1
--    total_score=36 / pass_score=32 / duration=20 分钟 / question_count=36
-- ---------------------------------------------------------------------
INSERT INTO exam_paper
    (id, subject_code, title, paper_type, total_score, pass_score, duration, question_count, status, deleted)
VALUES
    (1, 'SUBJECT_1', '科目一模拟考试（演示卷）', 'MOCK', 36, 32, 20, 36, 1, 0);

-- ---------------------------------------------------------------------
-- 2. 试卷题目关联：把该科目全部启用题目挂到试卷上
--    每题 1 分；sort_order 按 question.id 升序从 1 递增
--    （ROW_NUMBER() 是 MySQL 8.0 窗口函数）
-- ---------------------------------------------------------------------
INSERT INTO exam_paper_question (paper_id, question_id, score, sort_order)
SELECT 1,
       q.id,
       1,
       ROW_NUMBER() OVER (ORDER BY q.id)
FROM question q
WHERE q.subject_code = 'SUBJECT_1'
  AND q.status = 1
  AND q.deleted = 0;

-- =====================================================================
-- 3. 验证 SQL（执行后人工核对）
-- =====================================================================

-- 3.1 关联题目数 —— 期望 36
SELECT COUNT(*) AS question_count
FROM exam_paper_question
WHERE paper_id = 1;

-- 3.2 总分（Σ每题分值）—— 期望 36，应与 exam_paper.total_score 一致
SELECT SUM(score) AS total_score
FROM exam_paper_question
WHERE paper_id = 1;

-- 3.3 试卷本体 —— 期望 title='科目一模拟考试（演示卷）'、total_score=36、pass_score=32、duration=20、question_count=36
SELECT id, subject_code, title, paper_type, total_score, pass_score, duration, question_count, status
FROM exam_paper
WHERE id = 1;
