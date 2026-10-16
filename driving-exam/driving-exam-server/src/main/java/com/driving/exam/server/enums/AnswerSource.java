package com.driving.exam.server.enums;

/**
 * 作答来源场景。
 *
 * <p>该值写入 {@code answer_record.source}，用于区分三条提交路径：</p>
 * <ul>
 *   <li>{@code PRACTICE} 普通练习 —— 答对不动错题本；</li>
 *   <li>{@code RETRY} 错题重练 —— 答对把 {@code wrong_book.mastered} 置 1；</li>
 *   <li>{@code EXAM} 模拟考试 —— 交卷统一判分时写入（{@code exam_id} 一并落库），答错进错题本。</li>
 * </ul>
 *
 * <p>由**调用的接口**决定，不来自请求体——防止前端伪造来源影响副作用。</p>
 */
public enum AnswerSource {

    /** 普通练习（顺序 / 章节 / 随机） */
    PRACTICE,

    /** 错题重练 */
    RETRY,

    /** 模拟考试（交卷时写入） */
    EXAM
}
