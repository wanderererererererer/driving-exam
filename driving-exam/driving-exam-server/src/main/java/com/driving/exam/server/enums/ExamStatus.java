package com.driving.exam.server.enums;

/**
 * 模拟考试状态。值写入 {@code mock_exam.status}。
 *
 * <p>用枚举替代魔法字符串，避免"ONGOING / SUBMITTED / TIMEOUT"在多处硬编码不一致。
 * 落库时取 {@link #name()}。</p>
 */
public enum ExamStatus {

    /** 进行中 */
    ONGOING,

    /** 已交卷（含手动交卷与超时自动交卷） */
    SUBMITTED,

    /** 超时交卷（判分口径同 SUBMITTED，仅状态不同） */
    TIMEOUT
}
