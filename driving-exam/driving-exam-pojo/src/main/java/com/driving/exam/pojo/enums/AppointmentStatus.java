package com.driving.exam.pojo.enums;

/**
 * 预约状态。取值与 {@code appointment.status}（varchar(20)）一一对应。
 *
 * <p>Jackson 按 {@code name()} 序列化，前端拿到的是 {@code "PENDING"} 等英文名，<b>中文文案由前端映射</b>
 * （与 {@link WrongBookCategory} / {@code WeakReason} 同一做法）。</p>
 *
 * <p><b>状态机</b>：</p>
 * <pre>
 *                      学员创建
 *                         │
 *                         ▼
 *                   [ PENDING ] ──学员取消──► [ CANCELLED ]（终态）
 *                    │        │
 *          教练 approve│        │教练 reject
 *                    ▼        ▼
 *             [ CONFIRMED ]  [ REJECTED ]（终态）
 *                    │
 *          教练 complete│
 *                    ▼
 *             [ COMPLETED ]（终态）
 * </pre>
 *
 * <p><b>三个终态</b>：{@link #CANCELLED} / {@link #REJECTED} / {@link #COMPLETED} —— 不可再流转，
 * 且<b>不占用时段</b>（时段占用只算 {@link #PENDING} 与 {@link #CONFIRMED}，终态会释放时段）。</p>
 *
 * <p><b>取消规则</b>：仅 {@link #PENDING} 允许取消；{@link #CONFIRMED} 之后需联系教练
 * （见 {@code doc/data-conventions.md} §6 与 {@code doc/api-design.md} §5 第 2 条）。</p>
 */
public enum AppointmentStatus {

    /** 待确认（学员刚创建） */
    PENDING,

    /** 已确认（教练审批通过） */
    CONFIRMED,

    /** 已取消（学员主动取消） */
    CANCELLED,

    /** 已拒绝（教练拒绝，拒绝原因记在 {@code coach_remark}） */
    REJECTED,

    /** 已完成（教练标记完成） */
    COMPLETED
}
