package com.driving.exam.pojo.vo;

import com.driving.exam.pojo.enums.WeakReason;
import lombok.Data;

import java.io.Serializable;

/**
 * 知识点掌握度条目（{@code GET /api/student/analysis/knowledge} 的列表元素）。
 *
 * <p>统计口径见 {@code doc/analysis-design.md} §3.2。每个知识点的数据是
 * <b>含子孙</b>聚合的（根节点 = 其下所有题目合计）。</p>
 *
 * <ul>
 *   <li>未练习的知识点**不会出现在列表中**（Service 层剔除）；</li>
 *   <li>{@code mastery} / {@code correctRate} 均为 0~1，Service 组装时保留 2 位小数。</li>
 * </ul>
 */
@Data
public class KPStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long knowledgePointId;

    private String knowledgePointName;

    /** 层级（1=根）；前端可按层级分组展示 */
    private Integer level;

    /** 掌握度，0~1 */
    private Double mastery;

    /**
     * 该知识点（含子孙）的答题数。
     *
     * <p><b>含子孙合计</b>：父节点的值**已包含**其所有子节点的数据。因此前端要展示"科目总答题数"时，
     * 必须用 {@code AnalysisOverviewVO.answeredQuestions}，<b>不能把各 {@code KPStatVO} 的 answeredCount 相加</b>
     * ——父子会重复计算、结果偏大。</p>
     */
    private Integer answeredCount;

    /** 该知识点（含子孙）的正确率，0~1 */
    private Double correctRate;

    /**
     * 是否短板。
     *
     * <p><b>刻意用包装类型 {@code Boolean} 而非 {@code boolean}</b>：Lombok 对包装类型生成
     * {@code getIsWeak()}，Jackson 序列化出的 JSON 键才是 {@code isWeak}；若用基本类型
     * 会生成 {@code isWeak()} 方法、JSON 键变成 {@code weak}，与前端约定不符。</p>
     */
    private Boolean isWeak;

    /** 短板原因；非短板时为 null */
    private WeakReason weakReason;
}
