package com.driving.exam.pojo.vo;

import com.driving.exam.pojo.enums.WrongBookCategory;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 错题本条目（列表展示用）。
 *
 * <p>由 {@code wrong_book} 与 {@code question} 两张表组装而成：
 * id / wrongCount / lastWrongTime / mastered 来自 wrong_book，
 * questionId / questionType / content 来自 question。</p>
 */
@Data
public class WrongBookItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 错题记录 ID（wrong_book.id），用于移出错题本 */
    private Long id;

    /** 题目 ID */
    private Long questionId;

    /** 题型：SINGLE / MULTIPLE / JUDGE */
    private String questionType;

    /** 题干 */
    private String content;

    /** 累计答错次数 */
    private Integer wrongCount;

    /** 最近一次答错时间 */
    private LocalDateTime lastWrongTime;

    /** 是否已掌握：1 是，0 否 */
    private Integer mastered;

    /**
     * 分类（由后端按「已攻克 > 高频错 > 待攻克」优先级推导）。
     * 规则见 {@code doc/data-conventions.md} §8；前端据此渲染 tab，不自行计算。
     */
    private WrongBookCategory category;
}
