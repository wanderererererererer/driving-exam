-- =====================================================================
-- 教练端 · 学员管理 —— 测试准备【只读预检查】（★独立文件 · 仅 SELECT）
-- 目标库：driving_exam（MySQL 8.0）
-- 对应主准备脚本：doc/testsql/coach-student-test-setup.sql（★禁止对主脚本用 Execute All）
-- 对应用例：doc/test-cases-coach-student.md
--
-- ★ 本文件【只包含 SELECT】——不含 SET / INSERT / UPDATE / DELETE / DDL，
--   不写库，【可放心整段执行】（Execute All 也安全）。
-- ★ 用法：先在 DataGrip 执行本文件 → 人工核对下方每个「期望」→ 全部通过后，
--   再去【单独分块】执行主准备脚本的【第 1、2 步】写入语句。
-- ★ 所有表名统一加 `driving_exam.` 前缀；即使 DataGrip 当前 schema 不是 driving_exam 也可执行。
--
-- ★★★ 判定规则（必须遵守）★★★
--   只要下列任一用户名已存在，但其 `role` 不符预期、`status != 1`、或 `deleted != 0`：
--     → 【停止】，不要执行主脚本的任何写步骤；由你【人工】确认/处理后再继续。
--   同理：若 P4 显示两组测试绑定中任一已存在且 status=0 → 【停止】，人工确认
--         （主脚本第 2 步【不会】把它复活）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- P1 · 账号预检查（★必须覆盖 coach01 / coach02 / coach03 / student_cs01 / student_cs02 / cs_new_01）
--   期望（当前库 2026-10-13 快照）：
--     · coach01                                   → 应出现 1 行：role='COACH'   / status=1 / deleted=0
--     · coach02 / coach03                         → 期望【0 行】（尚不存在）
--     · student_cs01 / student_cs02 / cs_new_01   → 期望【0 行】（尚不存在）
--   若某行出现：逐行核对 role / status / deleted 是否恰为「按其用途应为 COACH 或 STUDENT / 1 / 0」；
--     任一不符 → 【停止】，人工处理（脚本不会自动修复）。
-- ---------------------------------------------------------------------
SELECT id, username, role, status, deleted
  FROM driving_exam.sys_user
 WHERE username IN ('coach01','coach02','coach03','student_cs01','student_cs02','cs_new_01')
 ORDER BY username;

-- ---------------------------------------------------------------------
-- P2 · 既有绑定全览（确认 coach01 ↔ student01 存在且 status=1；并看清是否有别的既有绑定）
--   期望：仅 1 行：bind_id=1 = coach01 ↔ student01，status=1。
-- ---------------------------------------------------------------------
SELECT cs.id AS bind_id, c.username AS coach, s.username AS student, cs.status, cs.bind_time
  FROM driving_exam.coach_student cs
  JOIN driving_exam.sys_user c ON c.id = cs.coach_id
  JOIN driving_exam.sys_user s ON s.id = cs.student_id
 ORDER BY cs.id;

-- ---------------------------------------------------------------------
-- P3 · 保护性基线（★测试全程不得改变这一条绑定）
--   期望：1 行：protected_bind_id = 1，status = 1。
-- ---------------------------------------------------------------------
SELECT cs.id AS protected_bind_id, cs.status
  FROM driving_exam.coach_student cs
  JOIN driving_exam.sys_user s ON s.id = cs.student_id
 WHERE s.username = 'student01' AND cs.status = 1;

-- ---------------------------------------------------------------------
-- P4 · 两组测试绑定是否已存在？看 status（决定主脚本第 2 步要不要写）
--   期望：0 行（首次运行）。
--     · 1 行且 status=1 → 主脚本第 2 步不改动它（幂等），可继续；
--     · 1 行且 status=0 → 【停止】，人工确认（主脚本不会复活它）。
-- ---------------------------------------------------------------------
SELECT c.username AS coach, s.username AS student, cs.id AS bind_id, cs.status, cs.bind_time
  FROM driving_exam.coach_student cs
  JOIN driving_exam.sys_user c ON c.id = cs.coach_id
  JOIN driving_exam.sys_user s ON s.id = cs.student_id
 WHERE (c.username = 'coach01' AND s.username = 'student_cs01')
    OR (c.username = 'coach02' AND s.username = 'student_cs02');

-- ---------------------------------------------------------------------
-- P5 · student_cs01 的学情记录（只读）
--   期望：0 行（当前 student_cs01 账号不存在）。
-- ---------------------------------------------------------------------
SELECT s.username, ss.subject_code, ss.status, ss.mock_pass_count, ss.coach_remark
  FROM driving_exam.sys_user s
  LEFT JOIN driving_exam.student_subject_status ss ON ss.student_id = s.id
 WHERE s.username = 'student_cs01'
 ORDER BY ss.subject_code;
