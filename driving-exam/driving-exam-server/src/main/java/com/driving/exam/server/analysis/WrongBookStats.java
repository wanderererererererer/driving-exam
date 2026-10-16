package com.driving.exam.server.analysis;

/**
 * 掌握度"行为修正"的输入载体：某知识点关联的**错题本**统计。
 *
 * <p>口径（见 {@code doc/analysis-design.md} §1.3）：</p>
 * <ul>
 *   <li>{@code total} = 该知识点（含子孙）下的错题总数</li>
 *   <li>{@code masteredCount} = 其中 {@code mastered=1} 的数量</li>
 *   <li>{@code highFreqCount} = 其中（{@code mastered=0} 且 {@code wrong_count>=3}）的数量</li>
 * </ul>
 */
public record WrongBookStats(int total, int masteredCount, int highFreqCount) {

    /** 无错题的"中性"统计（行为修正 = 1.0）。 */
    public static final WrongBookStats EMPTY = new WrongBookStats(0, 0, 0);
}
