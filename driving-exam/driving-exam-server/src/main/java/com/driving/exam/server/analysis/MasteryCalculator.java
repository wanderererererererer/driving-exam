package com.driving.exam.server.analysis;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 掌握度计算器（纯逻辑，无状态，无外部依赖）。
 *
 * <p>公式：<b>掌握度 = 难度加权正确率 A × 时间衰减 D × 行为修正 B</b>，再 clamp 到 [0,1]。
 * 完整口径见 {@code doc/analysis-design.md} §一。</p>
 *
 * <p>与 {@code AnswerGrader} 同构：标 {@code @Component} 以便被 Service 注入，
 * 但<b>没有任何 Spring 依赖</b>——单测直接 {@code new MasteryCalculator()} 即可，无需启动容器。</p>
 *
 * <p><b>不做四舍五入</b>：保留全精度；"保留 2 位小数"在组装 VO 时进行。</p>
 */
@Component
public class MasteryCalculator {

    /** 时间衰减半衰期（天） */
    public static final int HALF_LIFE_DAYS = 14;

    /** 已攻克率对行为修正的**增益**系数 */
    public static final double MASTERED_RATE_WEIGHT = 0.15;

    /** 高频错率对行为修正的**惩罚**系数 */
    public static final double HIGH_FREQ_RATE_WEIGHT = 0.25;

    /**
     * 计算某知识点的掌握度。
     *
     * @param answers 该知识点（含子孙）的答题记录；**不可为空**（服务层应先剔除未练习的知识点）
     * @param stats   该知识点关联的错题统计（可为 {@link WrongBookStats#EMPTY}）
     * @param now     当前时间（由调用方传入，便于测试）
     * @return 掌握度，值域 [0,1]
     * @throws IllegalArgumentException 当 {@code answers} 为空/为 null（fail-fast，避免静默产出错误的 0）
     */
    public double computeMastery(List<AnswerPoint> answers, WrongBookStats stats, LocalDateTime now) {
        if (answers == null || answers.isEmpty()) {
            throw new IllegalArgumentException("答题记录为空，无法计算掌握度");
        }
        double a = computeA(answers);
        double d = computeD(answers, now);
        double b = computeB(stats);
        return clamp(a * d * b);
    }

    /**
     * A = Σ(难度 × 是否答对) / Σ(难度)。值域 [0,1]。
     *
     * <p>难度即权重：难度 5 的题权重是难度 1 的 5 倍。</p>
     */
    public double computeA(List<AnswerPoint> answers) {
        double weightedCorrect = 0;
        double totalWeight = 0;
        for (AnswerPoint p : answers) {
            totalWeight += p.difficulty();
            if (p.correct()) {
                weightedCorrect += p.difficulty();
            }
        }
        return totalWeight == 0 ? 0.0 : weightedCorrect / totalWeight;
    }

    /**
     * D = 0.5^(Δ / 半衰期)，Δ = 距**最近一次答题**的天数（不分对错）。
     *
     * <p>刚答过 → 1；半衰期前 → 0.5。若最近答题时间晚于 {@code now}（时钟偏差），视 Δ=0 → 1。</p>
     */
    public double computeD(List<AnswerPoint> answers, LocalDateTime now) {
        LocalDateTime latest = null;
        for (AnswerPoint p : answers) {
            if (latest == null || p.answerTime().isAfter(latest)) {
                latest = p.answerTime();
            }
        }
        if (latest == null) {
            return 1.0; // 理论上不可达（answers 非空）；防御性返回 1
        }
        long minutes = Duration.between(latest, now).toMinutes();
        double days = Math.max(0.0, minutes / (60.0 * 24));
        return Math.pow(0.5, days / HALF_LIFE_DAYS);
    }

    /**
     * B = 1 + 0.15×已攻克率 − 0.25×高频错率。值域 [0.75, 1.15]。
     *
     * <p>错题总数为 0（或无错题）时返回 1.0（中性，含除零保护）。</p>
     */
    public double computeB(WrongBookStats stats) {
        if (stats == null || stats.total() <= 0) {
            return 1.0;
        }
        double masteredRate = (double) stats.masteredCount() / stats.total();
        double highFreqRate = (double) stats.highFreqCount() / stats.total();
        return 1.0 + MASTERED_RATE_WEIGHT * masteredRate - HIGH_FREQ_RATE_WEIGHT * highFreqRate;
    }

    /** 掌握度专用：clamp 到 [0,1]。 */
    public static double clamp(double value) {
        return clamp(value, 0.0, 1.0);
    }

    /** 通用区间夹取。 */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
