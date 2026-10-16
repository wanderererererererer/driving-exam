package com.driving.exam.pojo.enums;

/**
 * 错题本分类（由后端推导，前端只负责渲染，不自行计算）。
 *
 * <p>判定规则见 {@code doc/data-conventions.md} §8：按**优先级从高到低**判定，
 * 每条错题**只属于一类**。判定依据为 {@code wrong_book.mastered} 与 {@code wrong_book.wrong_count}，
 * 不额外存储分类字段。</p>
 *
 * <p>枚举声明顺序即优先级顺序。</p>
 */
public enum WrongBookCategory {

    /** 已攻克：mastered = 1（答对过，含曾经错很多次后攻克的） */
    MASTERED,

    /** 高频错：mastered = 0 且 wrong_count >= 阈值（反复错且尚未攻克） */
    HIGH_FREQUENCY,

    /** 待攻克：mastered = 0 且 wrong_count < 阈值（新错、尚待攻克） */
    PENDING
}
