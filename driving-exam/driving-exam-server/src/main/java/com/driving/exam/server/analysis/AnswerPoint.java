package com.driving.exam.server.analysis;

import java.time.LocalDateTime;

/**
 * 掌握度计算的输入载体：**一条答题记录中"算掌握度所需"的三项**。
 *
 * <p>为什么不直接用 {@code AnswerRecord} 实体？——实体里**没有难度**（难度在 {@code question} 表），
 * 而掌握度必须按难度加权。因此把"该记录 + 其题目的难度"在查询阶段合成这个最小载体再传进来。</p>
 *
 * @param difficulty 题目难度，1~5（来自 {@code question.difficulty}）
 * @param correct    是否答对（{@code is_correct}）
 * @param answerTime 答题时间（{@code answer_time}）
 */
public record AnswerPoint(int difficulty, boolean correct, LocalDateTime answerTime) {
}
