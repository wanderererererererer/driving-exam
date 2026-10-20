-- =====================================================================
-- Docker 演示账号（仅用于隔离演示库；生产/共享环境请立即替换密码）
-- 账号：student01 / coach01 / admin01 / student02
-- 默认演示口令：Demo@2026（BCrypt，cost 10；不要在真实环境沿用）
-- 角色：STUDENT / COACH / ADMIN；密码由 Spring BCryptPasswordEncoder 校验。
-- =====================================================================
SET NAMES utf8mb4;

INSERT IGNORE INTO sys_user
(id, username, password, real_name, phone, role, status, deleted)
VALUES
(1, 'student01', '$2b$10$2spLQ9BhZbo5Ei03sJUNJuH4R4eZqEhLWMXOGqfEqebXXP5gg68c.', '演示学员一', '13800000001', 'STUDENT', 1, 0),
(2, 'coach01',   '$2b$10$2spLQ9BhZbo5Ei03sJUNJuH4R4eZqEhLWMXOGqfEqebXXP5gg68c.', '演示教练一', '13800000002', 'COACH',   1, 0),
(3, 'admin01',   '$2b$10$2spLQ9BhZbo5Ei03sJUNJuH4R4eZqEhLWMXOGqfEqebXXP5gg68c.', '演示管理员', '13800000003', 'ADMIN',   1, 0),
(4, 'student02', '$2b$10$2spLQ9BhZbo5Ei03sJUNJuH4R4eZqEhLWMXOGqfEqebXXP5gg68c.', '演示学员二', '13800000004', 'STUDENT', 1, 0);

INSERT IGNORE INTO coach_student (id, coach_id, student_id, status)
VALUES
(1, 2, 1, 1),
(2, 2, 4, 1);
