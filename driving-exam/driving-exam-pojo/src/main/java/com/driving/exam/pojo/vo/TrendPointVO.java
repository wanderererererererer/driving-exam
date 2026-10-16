package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 成绩趋势数据点（{@code GET /api/student/analysis/trend} 的列表元素）。
 *
 * <p>统计口径见 {@code doc/analysis-design.md} §3.3。**只返回"有答题"的日期**（不补空白日）；
 * {@code correctRate} 为 0~1，Service 组装时保留 2 位小数。</p>
 */
@Data
public class TrendPointVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日期，{@code yyyy-MM-dd} */
    private String date;

    /** 当日正确率，0~1 */
    private Double correctRate;

    /** 当日答题数 */
    private Integer answeredCount;

    /** 当日模拟考分数：为 Day 16~17 预留，当前恒为 null */
    private Integer mockScore;
}
