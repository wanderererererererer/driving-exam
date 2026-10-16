package com.driving.exam.pojo.enums;

/**
 * 知识点"短板"原因（由后端推导，前端负责映射中文文案）。
 *
 * <p>与 {@link WrongBookCategory} 同风格：**枚举只放 code，不携带中文**——
 * 文案由前端映射，后端不掺展示逻辑。Jackson 默认按 {@code name()} 序列化，
 * 前端拿到的是 {@code "LOW_MASTERY"} / {@code "BASE_WEAK"}。</p>
 *
 * <p>判定逻辑见 {@code doc/analysis-design.md} §二。</p>
 */
public enum WeakReason {

    /** 掌握度偏低：掌握度 &lt; 0.6 且答题数 ≥ 3 */
    LOW_MASTERY,

    /** 基础不牢：低难度题错误率 &gt; 高难度题错误率 */
    BASE_WEAK
}
