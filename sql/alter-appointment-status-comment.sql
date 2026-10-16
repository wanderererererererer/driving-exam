-- =====================================================================
-- 驾考智能辅导系统 · 预约状态枚举扩展：更新 appointment.status 的列注释
-- 目标库：driving_exam（MySQL 8.0）
--
-- 说明：
--   ★ 本次**仅更新列注释**（语义变更：四值 → 五值），**不改结构**。
--     列类型仍是 varchar(20)、NOT NULL、DEFAULT 'PENDING' —— 维持原样。
--     新增的 REJECTED 长度只有 8，远在 varchar(20) 之内，**无需扩容**。
--
--   变更内容：值集合
--     旧：PENDING / CONFIRMED / CANCELLED / COMPLETED
--     新：PENDING / CONFIRMED / CANCELLED / REJECTED / COMPLETED
--   （REJECTED = 教练拒绝，与学员主动取消的 CANCELLED 区分）
--
--   **可重复执行**：MODIFY COLUMN 幂等——重复执行结果相同。
--
-- 配套：实体侧另加枚举 com.driving.exam.pojo.enums.AppointmentStatus；
--       口径文档见 doc/data-conventions.md §6（已同步为五值）。
-- =====================================================================

SET NAMES utf8mb4;

ALTER TABLE `appointment`
  MODIFY COLUMN `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING'
  COMMENT '状态：PENDING/CONFIRMED/CANCELLED/REJECTED/COMPLETED';

-- ---------------------------------------------------------------------
-- 验证：应看到 Comment 列已是五值
-- ---------------------------------------------------------------------
SHOW FULL COLUMNS FROM `appointment` LIKE 'status';
