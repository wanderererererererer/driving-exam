package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 错题本分类统计（用于前端 tab 上的数量角标）。
 *
 * <p>一次聚合查询得出，分类口径与 {@link WrongBookItemVO#getCategory()} 完全一致
 * （见 {@code doc/data-conventions.md} §8）。</p>
 */
@Data
public class WrongBookSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 待攻克：mastered = 0 且 wrong_count < 阈值 */
    private Integer pending;

    /** 高频错：mastered = 0 且 wrong_count >= 阈值 */
    private Integer highFrequency;

    /** 已攻克：mastered = 1 */
    private Integer mastered;

    /** 合计 */
    private Integer total;
}
