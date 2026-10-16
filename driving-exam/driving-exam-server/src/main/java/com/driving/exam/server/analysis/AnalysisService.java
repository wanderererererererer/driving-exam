package com.driving.exam.server.analysis;

import com.driving.exam.pojo.vo.AnalysisOverviewVO;
import com.driving.exam.pojo.vo.KPStatVO;
import com.driving.exam.pojo.vo.TrendPointVO;

import java.util.List;

/**
 * 学情分析 Service。
 *
 * <p>三个接口全部**只读**（{@code doc/analysis-design.md} §三）；<b>不加 {@code @Transactional}</b>——
 * 没有写操作，事务只是无谓开销。</p>
 */
public interface AnalysisService {

    /** 学情总览（总题数、已答数、正确率、进度、短板数）。 */
    AnalysisOverviewVO overview(Long userId, String subjectCode);

    /** 知识点掌握度列表（含子孙聚合；**按掌握度升序**，未练习的知识点不返回）。 */
    List<KPStatVO> knowledge(Long userId, String subjectCode);

    /** 成绩趋势（只含"有答题"的日期，不补空白日）。 */
    List<TrendPointVO> trend(Long userId, String subjectCode, int days);
}
