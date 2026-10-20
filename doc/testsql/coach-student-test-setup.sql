-- ★★★ 脱敏与不可执行提示（务必先读）★★★
--   本文件【已脱敏】：不含任何可登录口令，也不含其 BCrypt 哈希。
--   未经【本地生成哈希并替换 <FILL_BCRYPT_HASH> 占位符 + 人工检查】，本脚本【不可执行】。
-- =====================================================================
-- 教练端 · 学员管理 —— 测试准备 / 清理 SQL（★草案 · 需人工审阅后手动执行）
-- 目标库：driving_exam（MySQL 8.0）
-- 对应集合：doc/testjson/coach-student-api.postman_collection.json
-- 对应用例：doc/test-cases-coach-student.md
-- 独立只读预检查：doc/testsql/coach-student-precheck.sql
--
-- ★★★ 执行方式（务必遵守）★★★
--   1. 【禁止对本文件使用 Execute All / Ctrl+A 整段执行】——本文件含【写库】语句（第 1、2 步），
--      整段执行会在你尚未人工核对前就直接写库。
--   2. 正确流程：
--        ① 先单独执行【独立只读预检查文件】 doc/testsql/coach-student-precheck.sql（仅 SELECT，可整段跑）；
--        ② 人工核对其中每个「期望」全部通过；
--        ③ 回到本文件，【仅选中第 1 步的执行块】运行，再【仅选中第 2 步】运行；
--        ④ 最后单独运行第 4 步（只读自检）。
--   3. 建议先单独运行本文件顶部的 `SET NAMES utf8mb4;`（本会话执行一次即可，保证中文按 utf8mb4 发送）。
--   ⚠️ 诚实说明：SQL 文件本身【无法】阻止你点击 Execute All——上述流程【只能靠人工遵守】。
--
-- ★★★ 安全边界 ★★★
--   1. 本脚本【不触碰 appointment 表】——不做 INSERT / UPDATE / DELETE / TRUNCATE / DROP。
--   2. 本脚本对【任何既有绑定行】【不修改、不解绑、不删除、不复活（不改其 status）】——
--      含 coach01 ↔ student01（bind_id=1）与 coach01 ↔ student02 等【全部】已有绑定。
--      （当前库 2026-10-13 只读核对：sys_user 仅 student01 / coach01 / admin01 / student02 四个账号；
--        coach_student 仅 1 条：bind_id=1 = coach01 ↔ student01，status=1。）
--   3. 创建账号一律「存在则跳过」（WHERE NOT EXISTS），【绝不覆盖】已有账号的
--      密码 / 角色 / 状态 / 手机号 / deleted。
--   4. 若专用用户名已被占用且【role 不符 / status != 1 / deleted != 0】，请【停止】并人工处理，
--      不要自动改角色/状态，也不要自动恢复逻辑删除。第 2 步的绑定 INSERT 已同时要求
--      两侧账号 role 正确、status = 1、deleted = 0；不满足时【不会】建立绑定，
--      但脚本本身无法「中止」——须由执行者按【独立预检查】的结果人工把关。
--   5. 清理段【默认禁用】（整段被注释），且只按【专用用户名】定向清理；启用前请阅读其风险说明。
--   6. 【逻辑删除账号】sys_user.deleted = 1 视为【无效】：
--        第 2 步只命中 deleted = 0 的有效账号，【不会】为已逻辑删除的账号建绑定；
--        逻辑删除账号需【人工处理】，本脚本【不自动恢复、不覆盖、不改 deleted】。
--        注意：第 1 步的 WHERE NOT EXISTS 只看「行是否存在」，不排除 deleted=1 的同名行——
--        若预检查显示同名账号 deleted=1，请【停止】并人工确认。
--   7. 【测试绑定 · 不复活】第 2 步两条绑定 INSERT 【仅当该「教练+学员」组合尚无绑定行时插入】；
--        若组合已存在绑定（status=0 或 1），第 2 步【什么都不做】——
--        【绝不】把既有的 status=0 自动改回 1，也不刷新既有行的 bind_time。
--        遇到既存 status=0 时脚本会停在该状态，需你按独立预检查人工确认后再决定。
--   8. 【库名统一】本文件所有表名统一使用 `driving_exam.` 前缀（写入不会落到别的 schema）；
--      即使 DataGrip 当前默认库不是 driving_exam 也可执行。
--
-- 【口令与哈希 · 本地提供】本脚本不保存任何可登录口令，也不保存其 BCrypt 密文。
--   执行前请在本地用本项目的 passwordEncoder.encode() 生成哈希，替换下方
--   <FILL_BCRYPT_HASH> 占位符后再执行。（仓库不保存真实口令与哈希）
-- =====================================================================

-- 会话字符集（建议先单独运行本行；本会话执行一次即可）
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 第 0 步 · 只读预检查（★已拆分到独立文件）
-- ---------------------------------------------------------------------
-- 为避免「整段执行主脚本」时夹带写入，预检查已移到独立文件（仅 SELECT，可整段执行）：
--     doc/testsql/coach-student-precheck.sql
-- ★ 请【先】执行该文件并人工核对全部「期望」通过后，再执行本文件的第 1、2 步。
--   预检查覆盖：coach01 / coach02 / coach03 / student_cs01 / student_cs02 / cs_new_01
--   的 role / status / deleted；既有绑定全览；保护基线；两组测试绑定状态；student_cs01 学情。


-- ---------------------------------------------------------------------
-- 第 1 步 ·【写库】幂等创建专用账号（存在则跳过；绝不覆盖既有资料）
--   ★ 仅在本代码块被【单独选中】时执行；不要整段执行整份文件。
-- ---------------------------------------------------------------------
-- ★ <FILL_BCRYPT_HASH>：需在本地生成 BCrypt 哈希后替换（仓库不保存哈希）
INSERT INTO driving_exam.sys_user (username, password, real_name, phone, role, status, deleted)
SELECT 'coach02', '<FILL_BCRYPT_HASH>', '陈教练B', '13900000002', 'COACH', 1, 0
 WHERE NOT EXISTS (SELECT 1 FROM driving_exam.sys_user WHERE username = 'coach02');

INSERT INTO driving_exam.sys_user (username, password, real_name, phone, role, status, deleted)
SELECT 'coach03', '<FILL_BCRYPT_HASH>', '赵教练C', '13900000003', 'COACH', 1, 0
 WHERE NOT EXISTS (SELECT 1 FROM driving_exam.sys_user WHERE username = 'coach03');

INSERT INTO driving_exam.sys_user (username, password, real_name, phone, role, status, deleted)
SELECT 'student_cs01', '<FILL_BCRYPT_HASH>', '专用学员1', '13900000011', 'STUDENT', 1, 0
 WHERE NOT EXISTS (SELECT 1 FROM driving_exam.sys_user WHERE username = 'student_cs01');

INSERT INTO driving_exam.sys_user (username, password, real_name, phone, role, status, deleted)
SELECT 'student_cs02', '<FILL_BCRYPT_HASH>', '专用学员2', '13900000012', 'STUDENT', 1, 0
 WHERE NOT EXISTS (SELECT 1 FROM driving_exam.sys_user WHERE username = 'student_cs02');

-- ★ 若预检查显示上述用户名【已存在但 role 不符 / status != 1 / deleted != 0】，请**停止**并人工处理；
--   本脚本不会自动修改既有账号的角色、状态或逻辑删除标记。


-- ---------------------------------------------------------------------
-- 第 2 步 ·【写库】建立两组专用绑定（★仅当「教练+学员」组合尚无绑定行时插入）
--   ★ 仅在本代码块被【单独选中】时执行。
--   ★【不复活、不改动任何既有绑定】：本条只在组合【不存在】时插入新行；
--     若组合已存在绑定（无论 status=0 还是 1），本条【什么都不做】——
--       · status=0 的既存绑定【不会】被自动置回 1（须人工确认）；
--       · status=1 的既存绑定不改，也不刷新其 bind_time。
--     （故【不使用】ON DUPLICATE KEY UPDATE —— 那会自动复活解绑记录。）
--   ★ 只绑定【有效账号】：两侧同时要求 role 正确、status = 1、deleted = 0。
--     教练 role='COACH'；学员 role='STUDENT'（角色取值来自项目实现：
--     CoachStudentServiceImpl.ROLE_STUDENT = "STUDENT"，PathRoleRule 的教练前缀角色为 COACH）。
--   ★ 逐对精确匹配，未放宽为批量匹配。
-- ---------------------------------------------------------------------
-- 教练A ↔ student_cs01（主流程 / 更新学情 / 解绑重绑 用）
INSERT INTO driving_exam.coach_student (coach_id, student_id, status)
SELECT c.id, s.id, 1
  FROM driving_exam.sys_user c JOIN driving_exam.sys_user s
  LEFT JOIN driving_exam.coach_student cs
         ON cs.coach_id = c.id AND cs.student_id = s.id
 WHERE c.username = 'coach01'      AND c.role = 'COACH'   AND c.status = 1 AND c.deleted = 0
   AND s.username = 'student_cs01' AND s.role = 'STUDENT' AND s.status = 1 AND s.deleted = 0
   AND cs.id IS NULL;                 -- ← 仅当该组合【尚无绑定行】时才插入

-- 教练B ↔ student_cs02（越权用例夹具）
INSERT INTO driving_exam.coach_student (coach_id, student_id, status)
SELECT c.id, s.id, 1
  FROM driving_exam.sys_user c JOIN driving_exam.sys_user s
  LEFT JOIN driving_exam.coach_student cs
         ON cs.coach_id = c.id AND cs.student_id = s.id
 WHERE c.username = 'coach02'      AND c.role = 'COACH'   AND c.status = 1 AND c.deleted = 0
   AND s.username = 'student_cs02' AND s.role = 'STUDENT' AND s.status = 1 AND s.deleted = 0
   AND cs.id IS NULL;                 -- ← 仅当该组合【尚无绑定行】时才插入

-- ★ 第 2 步的真实行为对照：
--   · 账号不存在 / role 不符 / status != 1 / deleted=1 → 上面 SELECT 无行 → 【不建绑定】；
--   · 该组合从未有过绑定            → 插入 1 行，status=1、bind_time 取当前时间；
--   · 该组合已存在绑定(status=0)     → LEFT JOIN 命中 → cs.id IS NULL 为假 → 【不插入、不改动】（不复活！）
--                                    → 随后 00e / 00f 守卫会失败 → 请【停止】并按预检查人工确认；
--   · 该组合已存在绑定(status=1)     → 同上【不改动】（幂等，无副作用）。
--   本脚本【不自动改角色、不自动改状态、不自动恢复逻辑删除账号、不自动复活解绑记录】。

-- ★ coach03 刻意【不建任何绑定】——它是「空列表」用例（TC-CS-19 / 准备组 00g）的前置。


-- ---------------------------------------------------------------------
-- 第 3 步 ·【可选操作 · 默认禁用 · 仅重复运行 TC-CS-04 时人工启用】复位 student02 的绑定
--   ★ 本条【默认被注释掉，不可执行】——不是本模块测试的通用前置。
--   ★ 触发条件：TC-CS-04「绑定已有学员 student02」要求 student02 与 coach01【无有效绑定】。
--     首次运行时 student02 尚无任何绑定（当前库也仅 bind_id=1 一条），故【无需】本步；
--     仅当你【再次】运行本集合、希望让 TC-CS-04 可重复跑时才考虑启用。
--   ★ 影响面：下方语句只按 student_id 过滤、未限定 coach_id——
--     若 student02 被【多个教练】绑定，启用后会把它【全部】绑定的 status 一次性置 0。
--     因此启用前【必须】先只读确认 student02 的绑定关系，并把范围限定到明确的「教练 + 学员」组合。
--   ★ 重要：启用本条会【修改既有绑定】（coach01 ↔ student02）——这是【显式的、有意的人工覆盖】，
--     与第 2 步「绝不改动既有绑定」的原则不同；故【默认关闭】，本脚本【绝不自动执行】。
--   ★ 本步【不删除、不重置】任何既有绑定行；也【不得】以其它写操作替代。
-- ---------------------------------------------------------------------
-- 【可选 · 默认禁用】如需启用：先只读确认影响范围，再用下面「限定 coach_id」的形式单独执行：
-- UPDATE driving_exam.coach_student SET status = 0
--  WHERE student_id = (SELECT id FROM driving_exam.sys_user WHERE username = 'student02')
--    AND coach_id   = (SELECT id FROM driving_exam.sys_user WHERE username = 'coach01');   -- ★ 启用时务必保留此限定
--


-- ---------------------------------------------------------------------
-- 第 4 步 · 执行后自检（只读）
-- ---------------------------------------------------------------------
SELECT c.username AS coach, s.username AS student, cs.status, cs.bind_time
  FROM driving_exam.coach_student cs
  JOIN driving_exam.sys_user c ON c.id = cs.coach_id
  JOIN driving_exam.sys_user s ON s.id = cs.student_id
 WHERE c.username IN ('coach01','coach02','coach03')
 ORDER BY c.username, cs.id;
-- ★ 判定规则（务必逐条核对，不要只看「有没有报错」）：
--   · 结果【必须非空】，且【至少】能看到受保护的 coach01 ↔ student01 且 status=1。
--     → 若结果为【0 行】，或看不到 coach01 ↔ student01 → 视为【严重异常】（连受保护绑定都丢了），
--       【立即停止】后续测试，【不得】当作通过。
--   · 期望（首次运行）：
--       coach01 → student01(status=1，★保护对象) + student_cs01(status=1)
--       coach02 → student_cs02(status=1)
--       coach03 → 无任何行（空列表用例前置）
--   · 缺 student_cs01 / student_cs02 → 该账号未建成（role/status/deleted 不满足），或该绑定此前为 status=0（未复活）
--                                      → 按独立预检查人工处理，【不要】让脚本自动覆盖；
--   · 出现 status=0 的测试绑定        → 第 2 步【不会】复活它，请人工确认后再决定是否手动置 1；
--   · student01 那条(status=1)必须【始终存在且状态不变】（预检查 P3 的 protected_bind_id 全程一致）。


-- =====================================================================
-- ★★★ 清理段（默认禁用 · 启用前请先读风险说明）★★★
-- =====================================================================
-- 风险说明：
--   ① 删除 sys_user 行会使其历史数据（answer_record / wrong_book / mock_exam /
--      student_subject_status / ai_chat_record / appointment 等）变成"悬空引用"：
--      本库**不使用物理外键**，删除不会报错，但那些记录会指向一个不存在的用户。
--   ② coach_student 行被删后，绑定的历史痕迹随之消失；若只想"让绑定失效"，
--      应改用 status=0（软解绑），而不是删除行。
--   ③ cs_new_01 是运行时由接口创建的账号，可能已被写入 subject_status 等数据；
--      删除前请先执行下面的「引用检查」。
--
-- 建议顺序：先只读检查 → 再决定是否删除。

-- 【只读】cs_new_01 的引用情况（先看清楚再动手）
-- SELECT 'coach_student' AS tbl, COUNT(*) FROM driving_exam.coach_student WHERE student_id = (SELECT id FROM driving_exam.sys_user WHERE username='cs_new_01')
-- UNION ALL SELECT 'student_subject_status', COUNT(*) FROM driving_exam.student_subject_status WHERE student_id = (SELECT id FROM driving_exam.sys_user WHERE username='cs_new_01');

-- 【可选 · 定向清理】仅清理本轮专用账号产生的数据（★取消注释后才会生效）
-- DELETE FROM driving_exam.student_subject_status
--  WHERE student_id IN (SELECT id FROM driving_exam.sys_user
--                        WHERE username IN ('student_cs01','student_cs02','cs_new_01'));
--
-- DELETE FROM driving_exam.coach_student
--  WHERE student_id IN (SELECT id FROM driving_exam.sys_user
--                        WHERE username IN ('student_cs01','student_cs02','cs_new_01'));
--
-- DELETE FROM driving_exam.sys_user WHERE username = 'cs_new_01';   -- ★ 仅运行时创建的那个；删前先跑上面的引用检查
--
-- ★ 默认【保留】coach02 / coach03 / student_cs01 / student_cs02 四个专用账号：
--   第 1 步是幂等的，下次重跑会自动跳过，无需删除。
-- ★ 绝不删除：coach01 / student01 / student02 及其任何绑定行。


-- ---------------------------------------------------------------------
-- 附加只读检查 · 某学员的学情记录（用于核对 student_cs01 相关学情用例）
--   ★【只读】SELECT，不写库。student_cs01 尚不存在时返回 0 行（当前即如此）。
-- ---------------------------------------------------------------------
SELECT
    s.username,
    ss.subject_code,
    ss.status,
    ss.mock_pass_count,
    ss.coach_remark
FROM driving_exam.sys_user s
LEFT JOIN driving_exam.student_subject_status ss
    ON ss.student_id = s.id
WHERE s.username = 'student_cs01'
ORDER BY ss.subject_code;
