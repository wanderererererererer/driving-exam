package com.driving.exam.server.exam;

import com.driving.exam.pojo.entity.Question;

/**
 * 试卷题目条目：**一道考试题 + 它在试卷里的分值与会顺序**。
 *
 * <p>为什么装 {@link Question} 实体而不是 {@code questionId}：交卷判分与报告组装都需要
 * {@code questionType} / {@code answer} / {@code analysis} / {@code subjectCode}，
 * 一次查回实体最省事、**避免在 finalize 里二次查库**。</p>
 *
 * <p>⚠️ {@code Question} 含答案与解析（敏感），但本载体<b>只在服务端内存使用</b>；
 * 考试中下发给前端的仍是 {@code QuestionVO}（不含 answer / analysis）。</p>
 *
 * @param question  题目实体（含答案、解析、题型、科目）
 * @param score     本题分值（来自 {@code exam_paper_question.score}）
 * @param sortOrder 试卷内顺序（来自 {@code exam_paper_question.sort_order}）
 */
public record PaperQuestionItem(Question question, Integer score, Integer sortOrder) {
}
