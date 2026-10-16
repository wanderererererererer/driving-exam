package com.driving.exam.server.service;

import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.enums.WrongBookCategory;
import com.driving.exam.pojo.vo.QuestionVO;
import com.driving.exam.pojo.vo.WrongBookItemVO;
import com.driving.exam.pojo.vo.WrongBookSummaryVO;

import java.util.List;

/**
 * 错题本 Service。
 *
 * <p>分类口径见 {@code doc/data-conventions.md} §8：按「已攻克 &gt; 高频错 &gt; 待攻克」
 * 优先级互斥判定，完全由 {@code mastered} + {@code wrong_count} 推导，不额外存字段。</p>
 */
public interface WrongBookService {

    /**
     * 错题列表（分类可选，分页）。
     *
     * @param category 为 null 表示不分类过滤（返回全部）；排序规则由 category 决定
     */
    PageResult<WrongBookItemVO> list(Long userId, String subjectCode, WrongBookCategory category,
                                     int pageNum, int pageSize);

    /** 各分类数量统计（前端 tab 角标）。 */
    WrongBookSummaryVO summary(Long userId, String subjectCode);

    /** 抽取错题重练题集：优先高频错，不够再补待攻克。 */
    List<QuestionVO> retry(Long userId, String subjectCode, int count);

    /** 标记某条错题已掌握（带越权保护）。 */
    void markMastered(Long userId, Long wrongBookId);
}
