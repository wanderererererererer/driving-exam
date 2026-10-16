package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 学情总览（{@code GET /api/student/analysis/overview} 的返回）。
 *
 * <p>统计口径见 {@code doc/analysis-design.md} §3.1。<b>所有比率均为 0~1 的小数</b>，
 * 由 Service 组装时保留 2 位小数（本类只承载数据，不做计算）。</p>
 */
@Data
public class AnalysisOverviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 该科目题目总数（启用且未删除） */
    private Integer totalQuestions;

    /** 已答题数（去重：同一道题答多次只算一道） */
    private Integer answeredQuestions;

    /** 总体正确率（答对次数 / 总答题次数），0~1 */
    private Double correctRate;

    /** 练习进度 = 已答题数 / 总题数，0~1 */
    private Double progress;

    /** 判为短板的知识点数量 */
    private Integer weakCount;

    /* ---------- 模拟考相关：为 Day 16~17 预留，当前恒为 null ---------- */

    /** 模拟考次数 */
    private Integer mockExamCount;

    /** 模拟考平均分 */
    private Double mockExamAvgScore;

    /** 模拟考及格率，0~1 */
    private Double mockExamPassRate;
}
