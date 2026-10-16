package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 单题判题结果（{@code POST /api/student/exam/{examId}/judge}）。
 *
 * <p><b>只回"对不对"，绝不回正确答案 / 解析 / 选项对错</b>——避免考试中答案泄露。</p>
 */
@Data
public class ExamJudgeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 题目 ID */
    private Long questionId;

    /**
     * 该题是否已有【服务器已保存】的答案。
     * <p>{@code false} 时 {@link #isCorrect} 无意义（前端应显示"未判题"）。</p>
     */
    private Boolean answered;

    /** 是否答对（仅当 {@code answered=true} 时有意义） */
    private Boolean isCorrect;
}
