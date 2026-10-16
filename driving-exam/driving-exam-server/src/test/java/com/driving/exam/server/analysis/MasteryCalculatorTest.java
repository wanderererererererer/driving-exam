package com.driving.exam.server.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link MasteryCalculator} 掌握度计算单元测试（JUnit 5 + AssertJ）。
 *
 * <p>无状态纯逻辑类，直接 {@code new}，**不启动 Spring 容器**。</p>
 *
 * <p>用例口径见 {@code doc/analysis-design.md} §1.5（交通信号灯算例）与 §二（短板对照算例）。</p>
 */
@DisplayName("MasteryCalculator 掌握度计算")
class MasteryCalculatorTest {

    private final MasteryCalculator calculator = new MasteryCalculator();

    /** 固定的"当前时间"，保证时间衰减可复现 */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 12, 0);

    /* ==================== A. 综合算例（文档 §1.5） ==================== */

    @Test
    @DisplayName("A. 交通信号灯算例：A=0.571 × D=0.707 × B=1.0 ≈ 0.40")
    void trafficLightCase() {
        // (难度, 是否答对)： (2,✔) (2,✘) (3,✔) (3,✔) (4,✘)，最近一次答题在 7 天前
        List<AnswerPoint> answers = List.of(
                new AnswerPoint(2, true, NOW.minusDays(20)),
                new AnswerPoint(2, false, NOW.minusDays(18)),
                new AnswerPoint(3, true, NOW.minusDays(15)),
                new AnswerPoint(3, true, NOW.minusDays(10)),
                new AnswerPoint(4, false, NOW.minusDays(7)));   // ← 最近一次

        // 错题本 1 条：wrong_count=2 且未掌握 → 既非"已攻克"也非"高频错"
        WrongBookStats stats = new WrongBookStats(1, 0, 0);

        // 中间项可分别核对
        assertThat(calculator.computeA(answers)).isCloseTo(8.0 / 14, within(1e-9));  // Σ(d·c)/Σd
        assertThat(calculator.computeD(answers, NOW)).isCloseTo(Math.pow(0.5, 0.5), within(1e-9)); // 0.5^(7/14)
        assertThat(calculator.computeB(stats)).isCloseTo(1.0, within(1e-9));

        // 合成结果 ≈ 0.404
        assertThat(calculator.computeMastery(answers, stats, NOW)).isCloseTo(0.40, within(0.01));
    }

    /* ==================== B. 边界：空记录 ==================== */

    @Test
    @DisplayName("B. 空记录 → 抛 IllegalArgumentException（服务层应先剔除未练习的知识点）")
    void emptyAnswersThrows() {
        assertThatThrownBy(() -> calculator.computeMastery(List.of(), WrongBookStats.EMPTY, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("答题记录为空");

        assertThatThrownBy(() -> calculator.computeMastery(null, WrongBookStats.EMPTY, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /* ==================== C. 全对 / 全错 ==================== */

    @Test
    @DisplayName("C1. 全对 + 无错题 + 刚答过 → 掌握度 = 1.0")
    void allCorrectNoWrong() {
        List<AnswerPoint> answers = List.of(
                new AnswerPoint(1, true, NOW),
                new AnswerPoint(3, true, NOW),
                new AnswerPoint(5, true, NOW));
        assertThat(calculator.computeMastery(answers, WrongBookStats.EMPTY, NOW)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("C2. 全错 → 掌握度 = 0.0")
    void allWrong() {
        List<AnswerPoint> answers = List.of(
                new AnswerPoint(1, false, NOW),
                new AnswerPoint(5, false, NOW));
        assertThat(calculator.computeMastery(answers, WrongBookStats.EMPTY, NOW)).isCloseTo(0.0, within(1e-9));
    }

    /* ==================== D. clamp 边界 ==================== */

    @Test
    @DisplayName("D. 全对 + 全部错题已攻克（B=1.15）→ A×D×B=1.15 被 clamp 到 1.0")
    void clampOverOne() {
        List<AnswerPoint> answers = List.of(new AnswerPoint(3, true, NOW));
        WrongBookStats stats = new WrongBookStats(10, 10, 0); // 已攻克率=1 → B=1.15
        assertThat(calculator.computeB(stats)).isCloseTo(1.15, within(1e-9));
        assertThat(calculator.computeMastery(answers, stats, NOW)).isCloseTo(1.0, within(1e-9));
    }

    /* ==================== E. 除零保护 ==================== */

    @Test
    @DisplayName("E. 错题总数为 0 → B = 1.0（不除零）")
    void behaviorNoDivisionByZero() {
        assertThat(calculator.computeB(WrongBookStats.EMPTY)).isCloseTo(1.0, within(1e-9));
        assertThat(calculator.computeB(new WrongBookStats(0, 0, 0))).isCloseTo(1.0, within(1e-9));
        assertThat(calculator.computeB(null)).isCloseTo(1.0, within(1e-9));
    }

    /* ==================== F. 三个子项单独验证 ==================== */

    @Test
    @DisplayName("F1. 行为修正 B：10 条错题中 5 条已攻克、2 条高频错 → 1.025")
    void computeBFormula() {
        // 1 + 0.15×0.5 − 0.25×0.2 = 1.075 − 0.05 = 1.025
        assertThat(calculator.computeB(new WrongBookStats(10, 5, 2))).isCloseTo(1.025, within(1e-9));
    }

    @Test
    @DisplayName("F2. 时间衰减 D：恰好半衰期(14天)前 → 0.5；刚答过 → 1.0")
    void computeDHalfLife() {
        List<AnswerPoint> fourteenDaysAgo = List.of(new AnswerPoint(3, true, NOW.minusDays(14)));
        assertThat(calculator.computeD(fourteenDaysAgo, NOW)).isCloseTo(0.5, within(1e-9));

        List<AnswerPoint> justNow = List.of(new AnswerPoint(3, true, NOW));
        assertThat(calculator.computeD(justNow, NOW)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("F3. 时间衰减取『最近一次』——不分对错（最近的是一道错题也照样刷新）")
    void computeDUsesLatestRegardlessOfCorrectness() {
        List<AnswerPoint> answers = List.of(
                new AnswerPoint(3, true, NOW.minusDays(30)),   // 很久以前答对
                new AnswerPoint(3, false, NOW.minusDays(0)));  // 刚刚答错 ← 最近
        // Δ=0 → D=1.0（衰减依据是"最近接触时间"，与对错无关）
        assertThat(calculator.computeD(answers, NOW)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("F4. 难度加权 A：难题权重更大")
    void computeAWeightsByDifficulty() {
        // 只答对难题：Σ(d·c)=5, Σd=1+5=6 → 5/6
        List<AnswerPoint> hardRightEasyWrong = List.of(
                new AnswerPoint(1, false, NOW),
                new AnswerPoint(5, true, NOW));
        assertThat(calculator.computeA(hardRightEasyWrong)).isCloseTo(5.0 / 6, within(1e-9));

        // 反过来：只答对简单题 → 1/6（明显更低，证明难度加权生效）
        List<AnswerPoint> easyRightHardWrong = List.of(
                new AnswerPoint(1, true, NOW),
                new AnswerPoint(5, false, NOW));
        assertThat(calculator.computeA(easyRightHardWrong)).isCloseTo(1.0 / 6, within(1e-9));
    }
}
